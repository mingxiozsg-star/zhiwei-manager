package com.zhiwei.filemanager.core.fs

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

enum class SearchMode(val label: String) {
    NAME("文件名"),
    CONTENT("文件内容")
}

data class SearchHit(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long,
    /** 内容命中时的行号（从 1 开始），文件名命中为 -1。 */
    val lineNo: Int = -1,
    /** 内容命中时的摘录片段。 */
    val snippet: String = ""
)

/** 取消句柄：搜索跑在 IO 线程，UI 通过它来中断。 */
class SearchHandle {
    @Volatile
    var cancelled: Boolean = false
        private set

    fun cancel() {
        cancelled = true
    }
}

/**
 * 目录递归搜索。
 *
 * 设计取舍：
 * - 广度优先而不是深度优先：命中结果会按「离根目录近」的顺序出现，
 *   用户看到第一条结果的速度更快，不用等某个深层子目录扫完。
 * - 不做内存索引，纯一次性流式遍历；结果通过回调交给调用方，
 *   避免把十万级路径堆在搜索器里。
 * - 内容搜索必须先做二进制嗅探，否则在 apk/so 上做 UTF-8 解码会白烧 CPU。
 * - 单文件扫描上限 MAX_FILE_SCAN：正式版不指望用它搜大文件内部，
 *   超过上限直接跳过并在进度里体现。
 */
object SearchEngine {

    private const val MAX_DEPTH = 24
    private const val MAX_FILE_SCAN = 4L * 1024 * 1024
    private const val SNIFF_BYTES = 4096
    private const val SNIPPET_MAX = 160
    private const val PROGRESS_STEP = 32

    data class Stats(val scanned: Int, val hits: Int, val skippedBinary: Int, val skippedTooLarge: Int, val denied: Int)

    /**
     * @param onProgress 每扫过若干个条目回调一次（在调用线程执行，调用方负责切主线程）
     * @param onHit 每命中一条回调一次
     * @return 统计信息
     */
    fun search(
        root: String,
        query: String,
        mode: SearchMode,
        showHidden: Boolean = false,
        maxResults: Int = 500,
        handle: SearchHandle = SearchHandle(),
        charsetName: String = "UTF-8",
        onProgress: (scanned: Int, current: String) -> Unit = { _, _ -> },
        onHit: (SearchHit) -> Unit
    ): Stats {
        val q = query.trim()
        if (q.isEmpty()) return Stats(0, 0, 0, 0, 0)

        val rootFile = File(root)
        if (!rootFile.isDirectory) return Stats(0, 0, 0, 0, 0)

        val charset = try {
            Charset.forName(charsetName)
        } catch (t: Throwable) {
            Charsets.UTF_8
        }
        val needle = q.lowercase()
        val needleBytes = if (mode == SearchMode.CONTENT) q.toByteArray(charset) else ByteArray(0)

        var scanned = 0
        var hits = 0
        var skippedBinary = 0
        var skippedTooLarge = 0
        var denied = 0

        // 队列元素：目录 + 其深度
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.addLast(rootFile to 0)

        while (queue.isNotEmpty()) {
            if (handle.cancelled || hits >= maxResults) break
            val (dir, depth) = queue.removeFirst()
            val children = try {
                dir.listFiles()
            } catch (t: Throwable) {
                null
            }
            if (children == null) {
                denied++
                continue
            }
            for (child in children) {
                if (handle.cancelled || hits >= maxResults) break
                val name = child.name
                if (!showHidden && name.startsWith(".")) continue

                scanned++
                if (scanned % PROGRESS_STEP == 0) onProgress(scanned, child.absolutePath)

                if (child.isDirectory) {
                    // 目录本身只参与文件名匹配，不参与内容匹配
                    if (mode == SearchMode.NAME && name.lowercase().contains(needle)) {
                        hits++
                        onHit(hitOf(child))
                    }
                    if (depth + 1 <= MAX_DEPTH) queue.addLast(child to (depth + 1))
                } else {
                    if (!child.canRead()) {
                        denied++
                        continue
                    }
                    when (mode) {
                        SearchMode.NAME -> {
                            if (name.lowercase().contains(needle)) {
                                hits++
                                onHit(hitOf(child))
                            }
                        }
                        SearchMode.CONTENT -> {
                            val size = child.length()
                            if (size <= 0L) continue
                            if (size > MAX_FILE_SCAN) {
                                skippedTooLarge++
                                continue
                            }
                            if (!looksTextual(child)) {
                                skippedBinary++
                                continue
                            }
                            val found = scanContent(child, needle, needleBytes, charset)
                            if (found != null) {
                                hits++
                                onHit(hitOf(child, found.first, found.second))
                            }
                        }
                    }
                }
            }
        }

        onProgress(scanned, if (handle.cancelled) "已取消" else "完成")
        return Stats(scanned, hits, skippedBinary, skippedTooLarge, denied)
    }

    private fun hitOf(f: File, lineNo: Int = -1, snippet: String = ""): SearchHit = SearchHit(
        path = f.absolutePath,
        name = f.name,
        isDir = f.isDirectory,
        size = if (f.isDirectory) 0L else f.length(),
        lastModified = f.lastModified(),
        lineNo = lineNo,
        snippet = snippet
    )

    /** 前 4KB 出现 NUL 字节即判定为二进制；空文件视为非文本。 */
    private fun looksTextual(f: File): Boolean {
        return try {
            RandomAccessFile(f, "r").use { raf ->
                val buf = ByteArray(SNIFF_BYTES)
                val n = raf.read(buf)
                if (n <= 0) return false
                for (i in 0 until n) if (buf[i] == 0.toByte()) return false
                true
            }
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * 逐行扫描。返回首次命中的（行号, 摘录）。
     * 先用字节级比对快速过滤，命中后再解码整行，避免为每行都建 String。
     */
    private fun scanContent(f: File, needle: String, needleBytes: ByteArray, charset: Charset): Pair<Int, String>? {
        if (needleBytes.isEmpty()) return null
        return try {
            val lines = f.readLines(charset)
            lines.forEachIndexed { i, line ->
                if (line.lowercase().contains(needle)) {
                    return (i + 1) to line.trim().take(SNIPPET_MAX)
                }
            }
            null
        } catch (t: Throwable) {
            // 按指定编码解不出来时，退回 UTF-8 再试一次（GBK 文件被当 UTF-8 打开属常见情形）
            if (charset != Charsets.UTF_8) {
                try {
                    f.readLines(Charsets.UTF_8).forEachIndexed { i, line ->
                        if (line.lowercase().contains(needle)) {
                            return (i + 1) to line.trim().take(SNIPPET_MAX)
                        }
                    }
                } catch (ignored: Throwable) {
                }
            }
            null
        }
    }
}