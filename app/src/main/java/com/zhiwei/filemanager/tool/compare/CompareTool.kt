package com.zhiwei.filemanager.tool.compare

import com.zhiwei.filemanager.core.fs.SearchHandle
import com.zhiwei.filemanager.core.util.Format
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** 一段连续差异。hex 只保留前若干字节，避免大文件把 UI 拖死。 */
data class DiffBlock(
    val offset: Long,
    val length: Int,
    val aHex: String,
    val bHex: String
)

data class ByteDiffReport(
    val sizeA: Long,
    val sizeB: Long,
    val identical: Boolean,
    /** 首个差异偏移，完全一致为 -1。 */
    val firstDiff: Long,
    val differingBytes: Long,
    val blocks: List<DiffBlock>,
    val blocksTruncated: Boolean,
    val md5A: String,
    val md5B: String,
    val error: String? = null
)

enum class DirDiffKind(val label: String) {
    ONLY_A("仅左侧存在"),
    ONLY_B("仅右侧存在"),
    TYPE("类型不同"),
    SIZE("大小不同"),
    CONTENT("内容不同"),
    MTIME("时间不同"),
    SAME("一致")
}

data class DirDiffEntry(
    val kind: DirDiffKind,
    val relPath: String,
    val detail: String
)

data class DirDiffReport(
    val countA: Int,
    val countB: Int,
    val same: Int,
    val changed: Int,
    val onlyA: Int,
    val onlyB: Int,
    val entries: List<DirDiffEntry>,
    val truncated: Boolean,
    val error: String? = null
) {
    val total: Int get() = same + changed + onlyA + onlyB
}

/**
 * 文件 / 目录对比。
 *
 * 文件级走「流式分块字节比对」，不整文件读进内存；目录级先建相对路径索引再求差集，
 * 顺序稳定（按相对路径排序），方便用户逐条核对而不是每次刷新都变位置。
 */
object CompareTool {

    private const val CHUNK = 64 * 1024
    private const val MAX_BLOCKS = 200
    private const val HEX_BYTES = 16
    private const val DEFAULT_DIR_LIMIT = 2000

    fun isTextFile(path: String): Boolean {
        val f = File(path)
        if (!f.isFile) return false
        if (Format.isTextLikely(f.name)) return true
        return try {
            RandomAccessFile(f, "r").use { raf ->
                val buf = ByteArray(2048)
                val n = raf.read(buf)
                if (n <= 0) return false
                for (i in 0 until n) if (buf[i] == 0.toByte()) return false
                true
            }
        } catch (t: Throwable) {
            false
        }
    }

    private fun md5Of(f: File): String = try {
        val md = MessageDigest.getInstance("MD5")
        f.inputStream().use { input ->
            val buf = ByteArray(CHUNK)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (t: Throwable) {
        "-"
    }

    /**
     * 字节级对比。两侧等长且无差异时 identical=true。
     * 差异块按「同一分块内的连续差异」合并，块数超过 MAX_BLOCKS 后只统计不再记录。
     */
    fun compareBytes(pathA: String, pathB: String): ByteDiffReport {
        val a = File(pathA)
        val b = File(pathB)
        if (!a.isFile || !b.isFile) {
            return ByteDiffReport(
                sizeA = if (a.isFile) a.length() else 0L,
                sizeB = if (b.isFile) b.length() else 0L,
                identical = false,
                firstDiff = -1,
                differingBytes = 0L,
                blocks = emptyList(),
                blocksTruncated = false,
                md5A = "-",
                md5B = "-",
                error = "两侧都必须是文件（目录请用目录对比）"
            )
        }

        val blocks = ArrayList<DiffBlock>()
        var firstDiff = -1L
        var differing = 0L
        var truncated = false
        var offsetBase = 0L

        try {
            RandomAccessFile(a, "r").use { ra ->
                RandomAccessFile(b, "r").use { rb ->
                    val bufA = ByteArray(CHUNK)
                    val bufB = ByteArray(CHUNK)
                    while (true) {
                        val nA = ra.read(bufA)
                        val nB = rb.read(bufB)
                        if (nA <= 0 && nB <= 0) break
                        val n = maxOf(nA, nB).coerceAtLeast(0)
                        var blockStart = -1
                        var blockLen = 0
                        for (i in 0 until n) {
                            val va = if (i < nA) bufA[i] else null
                            val vb = if (i < nB) bufB[i] else null
                            val diff = va != vb
                            if (diff) {
                                differing++
                                if (firstDiff < 0) firstDiff = offsetBase + i
                                if (blockStart < 0) {
                                    blockStart = i
                                    blockLen = 0
                                }
                                blockLen++
                            } else if (blockStart >= 0) {
                                addBlock(blocks, offsetBase, blockStart, blockLen, bufA, nA, bufB, nB)
                                if (blocks.size >= MAX_BLOCKS) truncated = true
                                blockStart = -1
                                blockLen = 0
                            }
                        }
                        if (blockStart >= 0) {
                            addBlock(blocks, offsetBase, blockStart, blockLen, bufA, nA, bufB, nB)
                            if (blocks.size >= MAX_BLOCKS) truncated = true
                        }
                        offsetBase += n
                    }
                }
            }
        } catch (t: Throwable) {
            return ByteDiffReport(
                sizeA = a.length(), sizeB = b.length(), identical = false,
                firstDiff = firstDiff, differingBytes = differing, blocks = blocks,
                blocksTruncated = truncated, md5A = "-", md5B = "-", error = t.message
            )
        }

        val identical = a.length() == b.length() && differing == 0L
        return ByteDiffReport(
            sizeA = a.length(),
            sizeB = b.length(),
            identical = identical,
            firstDiff = if (identical) -1L else firstDiff,
            differingBytes = differing,
            blocks = blocks,
            blocksTruncated = truncated,
            // 完全一致时不必再算一遍 MD5，直接复用左侧结果
            md5A = md5Of(a),
            md5B = if (identical) md5Of(a) else md5Of(b)
        )
    }

    private fun addBlock(
        out: MutableList<DiffBlock>,
        base: Long,
        start: Int,
        len: Int,
        bufA: ByteArray,
        nA: Int,
        bufB: ByteArray,
        nB: Int
    ) {
        if (out.size >= MAX_BLOCKS) return
        out += DiffBlock(
            offset = base + start,
            length = len,
            aHex = hexSlice(bufA, nA, start, len),
            bHex = hexSlice(bufB, nB, start, len)
        )
    }

    private fun hexSlice(buf: ByteArray, valid: Int, start: Int, len: Int): String {
        val sb = StringBuilder()
        val end = (start + len).coerceAtMost(valid).coerceAtMost(start + HEX_BYTES)
        for (i in start until end) sb.append("%02X ".format(buf[i]))
        if (start >= valid) return "(无)"
        if (start + len > end) sb.append("…")
        return sb.toString().trimEnd()
    }

    /**
     * 目录对比。默认只比结构与大小，compareContent=true 时对「大小相同」的文件追加内容比对
     * （开销大，由调用方决定；内容比对是逐块流式，遇到第一处差异即提前返回）。
     */
    fun compareDirs(
        dirA: String,
        dirB: String,
        recursive: Boolean = true,
        compareMtime: Boolean = false,
        compareContent: Boolean = false,
        limit: Int = DEFAULT_DIR_LIMIT,
        handle: SearchHandle = SearchHandle(),
        onProgress: (done: Int, current: String) -> Unit = { _, _ -> }
    ): DirDiffReport {
        val rootA = File(dirA)
        val rootB = File(dirB)
        if (!rootA.isDirectory || !rootB.isDirectory) {
            return DirDiffReport(0, 0, 0, 0, 0, 0, emptyList(), false, "两侧都必须是文件夹")
        }

        val mapA = index(rootA, recursive, handle)
        val mapB = index(rootB, recursive, handle)
        val keys = (mapA.keys + mapB.keys).sorted()

        val entries = ArrayList<DirDiffEntry>()
        var same = 0
        var changed = 0
        var onlyA = 0
        var onlyB = 0
        var truncated = false

        keys.forEachIndexed { i, rel ->
            if (handle.cancelled) return@forEachIndexed
            if (i % 64 == 0) onProgress(i, rel)
            val fa = mapA[rel]
            val fb = mapB[rel]
            when {
                fa == null -> {
                    onlyB++
                    entries += DirDiffEntry(DirDiffKind.ONLY_B, rel, "")
                }
                fb == null -> {
                    onlyA++
                    entries += DirDiffEntry(DirDiffKind.ONLY_A, rel, "")
                }
                fa.isDirectory != fb.isDirectory -> {
                    changed++
                    entries += DirDiffEntry(DirDiffKind.TYPE, rel, "左=${typeOf(fa)} 右=${typeOf(fb)}")
                }
                fa.isDirectory -> {
                    same++
                }
                fa.length() != fb.length() -> {
                    changed++
                    entries += DirDiffEntry(
                        DirDiffKind.SIZE, rel,
                        "${Format.size(fa.length())} → ${Format.size(fb.length())}"
                    )
                }
                compareContent -> {
                    if (quickContentEquals(fa, fb)) {
                        if (compareMtime && fa.lastModified() != fb.lastModified()) {
                            changed++
                            entries += DirDiffEntry(DirDiffKind.MTIME, rel, "内容相同，时间不同")
                        } else {
                            same++
                        }
                    } else {
                        changed++
                        entries += DirDiffEntry(DirDiffKind.CONTENT, rel, "大小相同，内容不同")
                    }
                }
                compareMtime && fa.lastModified() != fb.lastModified() -> {
                    changed++
                    entries += DirDiffEntry(DirDiffKind.MTIME, rel, Format.time(fa.lastModified()) + " → " + Format.time(fb.lastModified()))
                }
                else -> same++
            }
            if (entries.size >= limit) truncated = true
        }

        return DirDiffReport(
            countA = mapA.size,
            countB = mapB.size,
            same = same,
            changed = changed,
            onlyA = onlyA,
            onlyB = onlyB,
            entries = if (truncated) entries.take(limit) else entries,
            truncated = truncated
        )
    }

    private fun typeOf(f: File): String = if (f.isDirectory) "目录" else "文件"

    /** 相对路径 -> File 的索引；不递归时只取第一层。 */
    private fun index(root: File, recursive: Boolean, handle: SearchHandle): Map<String, File> {
        val out = LinkedHashMap<String, File>()
        val queue = ArrayDeque<Pair<File, String>>()
        queue.addLast(root to "")
        while (queue.isNotEmpty()) {
            if (handle.cancelled) break
            val (dir, prefix) = queue.removeFirst()
            val children = try {
                dir.listFiles()
            } catch (t: Throwable) {
                null
            } ?: continue
            for (c in children) {
                val rel = if (prefix.isEmpty()) c.name else "$prefix/${c.name}"
                out[rel] = c
                if (recursive && c.isDirectory) queue.addLast(c to rel)
            }
        }
        return out
    }

    /** 逐块比对，第一处差异即提前收工（不做全量 MD5，省一次完整读盘）。 */
    private fun quickContentEquals(a: File, b: File): Boolean = try {
        RandomAccessFile(a, "r").use { ra ->
            RandomAccessFile(b, "r").use { rb ->
                val bufA = ByteArray(CHUNK)
                val bufB = ByteArray(CHUNK)
                var equal = true
                var done = false
                while (!done) {
                    val nA = ra.read(bufA)
                    val nB = rb.read(bufB)
                    if (nA != nB) {
                        equal = false
                        done = true
                    } else if (nA <= 0) {
                        done = true
                    } else {
                        for (i in 0 until nA) {
                            if (bufA[i] != bufB[i]) {
                                equal = false
                                done = true
                                break
                            }
                        }
                    }
                }
                equal
            }
        }
    } catch (t: Throwable) {
        false
    }

    fun byteDiffSummary(r: ByteDiffReport): String {
        if (r.error != null) return "【错误】${r.error}"
        if (r.identical) return "完全一致 · ${Format.size(r.sizeA)} · MD5 ${r.md5A}"
        return buildString {
            append("不一致 · 左侧 ${Format.size(r.sizeA)} / 右侧 ${Format.size(r.sizeB)}")
            append("\n首个差异偏移：0x").append(r.firstDiff.toString(16).uppercase()).append("（${r.firstDiff}）")
            append("\n差异字节数：").append(r.differingBytes)
            append("\n差异块数：").append(r.blocks.size).append(if (r.blocksTruncated) "+（已截断）" else "")
            append("\nMD5 左：").append(r.md5A)
            append("\nMD5 右：").append(r.md5B)
        }
    }

    fun dirDiffSummary(r: DirDiffReport): String {
        if (r.error != null) return "【错误】${r.error}"
        return "左侧 ${r.countA} 项 · 右侧 ${r.countB} 项\n一致 ${r.same} · 有差异 ${r.changed} · 仅左 ${r.onlyA} · 仅右 ${r.onlyB}" +
            if (r.truncated) "\n（结果已按上限截断）" else ""
    }
}