package com.zhiwei.filemanager.tool.text

import com.zhiwei.filemanager.core.task.TaskCenter
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.util.Locale

/** 合并排序方式。 */
enum class MergeOrder(val label: String) {
    NAME("按名称"),
    DATE("按修改时间"),
    SIZE("按体积")
}

/** 文本批量合并参数。 */
data class MergeOptions(
    val recursive: Boolean = true,
    val exts: Set<String> = MergeTool.DEFAULT_EXTS,
    val order: MergeOrder = MergeOrder.NAME,
    val withHeader: Boolean = true,
    val blankLineBetween: Boolean = true,
    val detectSourceCharset: Boolean = true,
    val outCharset: String = "UTF-8",
    val maxFileBytes: Long = 64L * 1024 * 1024,
    val maxTotalBytes: Long = 512L * 1024 * 1024
)

/** 合并前的计划：给用户看清单与体积，再决定是否执行。 */
data class MergePlan(val files: List<File>, val notes: List<String>) {
    val totalBytes: Long get() = files.fold(0L) { acc, f -> acc + f.length() }

    fun summary(): String {
        val sb = StringBuilder()
        sb.append("待合并 ${files.size} 个文本文件，合计 ${totalBytes} 字节")
        files.take(40).forEach { sb.append("\n  · ").append(it.absolutePath) }
        if (files.size > 40) sb.append("\n  · …另有 ${files.size - 40} 个")
        if (notes.isNotEmpty()) {
            sb.append("\n提示：")
            notes.forEach { sb.append("\n  ! ").append(it) }
        }
        return sb.toString()
    }
}

/** 合并结果。 */
data class MergeResult(
    val merged: Int,
    val skipped: Int,
    val bytes: Long,
    val outPath: String,
    val notes: List<String>
) {
    fun summary(): String {
        val sb = StringBuilder("合并完成：写入 $merged 个，跳过 $skipped 个，输出 ${bytes} 字节")
        sb.append("\n输出文件：").append(outPath)
        if (notes.isNotEmpty()) {
            sb.append("\n备注：")
            notes.forEach { sb.append("\n  ! ").append(it) }
        }
        return sb.toString()
    }
}

/**
 * 文本文件批量合并引擎。
 *
 * 特点：流式逐行写盘（不把源文件整体读进内存）、按文件独立探测编码后再转成目标编码、
 * 超限保护（单文件 / 总体积）、未完成前写入目标文件，可随时中断只影响输出文件。
 */
object MergeTool {

    val DEFAULT_EXTS = setOf(
        "txt", "md", "log", "csv", "json", "xml", "java", "kt", "smali", "properties",
        "gradle", "js", "ts", "html", "css", "yml", "yaml", "ini", "sh"
    )

    /** 把 sniff 的返回（UTF-8(BOM) / GBK / EMPTY / UNKNOWN）规范成可用字符集名。 */
    fun canonicalCharset(detected: String): String {
        val base = detected.substringBefore("(").trim().uppercase(Locale.ROOT)
        if (base.isEmpty() || base == "EMPTY" || base == "UNKNOWN") return "UTF-8"
        return base
    }

    /** 把用户输入的扩展名串解析成小写集合（容忍逗号/中文逗号/空格/点号前缀）。 */
    fun parseExts(raw: String): Set<String> {
        val set = raw.split(',', '，', ';', '；', ' ', '\n')
            .map { it.trim().lowercase(Locale.ROOT).removePrefix(".") }
            .filter { it.isNotEmpty() }
            .toSet()
        return set.ifEmpty { DEFAULT_EXTS }
    }

    /** 扫描待合并清单并排序。 */
    fun scan(dir: File, opts: MergeOptions): MergePlan {
        val raw: List<File> = when {
            dir.isDirectory -> EncodingTool.collect(dir, opts.recursive, opts.exts)
            dir.isFile -> listOf(dir)
            else -> emptyList()
        }
        val sorted: List<File> = when (opts.order) {
            MergeOrder.NAME -> raw.sortedBy { it.name.lowercase(Locale.ROOT) }
            MergeOrder.DATE -> raw.sortedBy { it.lastModified() }
            MergeOrder.SIZE -> raw.sortedBy { it.length() }
        }
        val notes = mutableListOf<String>()
        if (dir.isFile) notes += "单文件模式：直接合并该文件本身"
        val oversize = sorted.count { it.length() > opts.maxFileBytes }
        if (oversize > 0) notes += "$oversize 个文件超出单文件上限，执行时将被跳过"
        if (sorted.isEmpty()) notes += "没有匹配的文本文件，检查路径与扩展名白名单"
        return MergePlan(sorted, notes)
    }

    /** 默认输出路径：源目录下 merged_<时间戳>.txt。 */
    fun defaultOut(root: File): File {
        val dir = if (root.isDirectory) root else root.parentFile ?: File("/sdcard")
        return File(dir, "merged_${System.currentTimeMillis() / 1000}.txt")
    }

    /** 执行合并。onProgress 在 IO 线程回调，进度按已处理文件数计。 */
    fun merge(
        files: List<File>,
        outFile: File,
        opts: MergeOptions,
        onProgress: ((Float, String) -> Unit)? = null
    ): MergeResult {
        val notes = mutableListOf<String>()
        outFile.parentFile?.mkdirs()
        var written = 0L
        var merged = 0
        var skipped = 0
        val taskId = TaskCenter.start("文本批量合并", outFile.name)
        try {
            FileOutputStream(outFile).use { fos ->
                val w = BufferedWriter(OutputStreamWriter(fos, Charset.forName(opts.outCharset)))
                files.forEachIndexed { index, f ->
                    val step = (index + 1).toFloat() / files.size.coerceAtLeast(1)
                    val oversize = f.length() > opts.maxFileBytes
                    val overTotal = written + f.length() > opts.maxTotalBytes
                    if (oversize) {
                        skipped++
                        notes += "跳过（超单文件上限）：${f.name}"
                    } else if (overTotal) {
                        skipped++
                        notes += "跳过（超总体积上限）：${f.name}"
                    } else if (appendOne(w, f, opts, notes)) {
                        written += f.length()
                        merged++
                    } else {
                        skipped++
                    }
                    onProgress?.invoke(step, f.name)
                    TaskCenter.update(taskId, step, f.name)
                }
                w.flush()
            }
            TaskCenter.finish(taskId, true)
        } catch (t: Throwable) {
            TaskCenter.finish(taskId, false, t.message)
            notes += "写入失败：${t.javaClass.simpleName} ${t.message}"
        }
        return MergeResult(merged, skipped, outFile.length(), outFile.absolutePath, notes)
    }

    /** 单个文件：按探测到的源编码逐行解码，再以目标编码逐行写出。 */
    private fun appendOne(w: BufferedWriter, f: File, opts: MergeOptions, notes: MutableList<String>): Boolean {
        try {
            val src = if (opts.detectSourceCharset) canonicalCharset(EncodingTool.sniff(f.absolutePath)) else opts.outCharset
            if (opts.withHeader) {
                w.write("===== ${f.name} =====")
                w.newLine()
            }
            var first = true
            BufferedReader(InputStreamReader(FileInputStream(f), Charset.forName(src))).useLines { lines ->
                lines.forEach { raw ->
                    val line = if (first) raw.removePrefix("\uFEFF") else raw
                    first = false
                    w.write(line)
                    w.newLine()
                }
            }
            if (opts.blankLineBetween) w.newLine()
            return true
        } catch (t: Throwable) {
            notes += "读取失败：${f.name}（${t.javaClass.simpleName} ${t.message}）"
            return false
        }
    }
}
