package com.zhiwei.filemanager.tool.text

import com.zhiwei.filemanager.core.fs.FileOps
import com.zhiwei.filemanager.core.task.TaskCenter
import java.io.File
import java.io.FileInputStream

data class ConvertResult(val ok: Int, val failed: Int, val skipped: Int) {
    fun summary(): String = "转换完成：成功 $ok，失败 $failed，跳过 $skipped"
}

/** 文件夹编码批量转换（GBK <-> UTF-8 等）。 */
object EncodingTool {

    val COMMON_CHARSETS = listOf("UTF-8", "GBK", "GB18030", "GB2312", "UTF-16LE", "UTF-16BE", "ISO-8859-1")

    private val DEFAULT_EXTS = setOf(
        "txt", "java", "kt", "xml", "json", "smali", "md", "csv", "log",
        "properties", "gradle", "js", "ts", "html", "css", "yml", "yaml", "ini", "sh"
    )

    /** 严格校验是否为合法 UTF-8 序列；不合法一律按 GBK 处理。 */
    fun sniffCharset(bytes: ByteArray): String {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b < 0x80 -> i++
                b in 0xC2..0xDF -> {
                    if (i + 1 >= bytes.size || (bytes[i + 1].toInt() and 0xC0) != 0x80) return "GBK"
                    i += 2
                }
                b in 0xE0..0xEF -> {
                    if (i + 2 >= bytes.size) return "GBK"
                    if ((bytes[i + 1].toInt() and 0xC0) != 0x80 || (bytes[i + 2].toInt() and 0xC0) != 0x80) return "GBK"
                    i += 3
                }
                b in 0xF0..0xF4 -> {
                    if (i + 3 >= bytes.size) return "GBK"
                    if ((bytes[i + 1].toInt() and 0xC0) != 0x80 ||
                        (bytes[i + 2].toInt() and 0xC0) != 0x80 ||
                        (bytes[i + 3].toInt() and 0xC0) != 0x80
                    ) return "GBK"
                    i += 4
                }
                else -> return "GBK"
            }
        }
        return "UTF-8"
    }

    fun sniff(path: String): String {
        // 表达式体里不能 return（K1 会直接报错），改为块体。
        return try {
            val f = File(path)
            val size = minOf(f.length(), 64L * 1024L).toInt()
            if (size <= 0) return "EMPTY"
            val buf = ByteArray(size)
            FileInputStream(f).use { it.read(buf) }
            val bom = buf.size >= 3 && (buf[0].toInt() and 0xFF) == 0xEF &&
                (buf[1].toInt() and 0xFF) == 0xBB && (buf[2].toInt() and 0xFF) == 0xBF
            if (bom) "UTF-8(BOM)" else sniffCharset(buf)
        } catch (t: Throwable) {
            "UNKNOWN"
        }
    }

    fun convertFile(path: String, from: String, to: String, withBom: Boolean = false): Boolean = try {
        val src = File(path)
        val bytes = if (from.startsWith("UTF-8") && bytesHasBom(src)) src.readBytes().drop(3).toByteArray() else src.readBytes()
        val text = String(bytes, FileOps.charset(from.substringBefore("(")))
        val encoded = text.toByteArray(FileOps.charset(to.substringBefore("(")))
        val out = if (withBom && to.startsWith("UTF-8")) {
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + encoded
        } else encoded
        src.writeBytes(out)
        true
    } catch (t: Throwable) {
        false
    }

    private fun bytesHasBom(f: File): Boolean = try {
        if (f.length() < 3) false
        else {
            val head = ByteArray(3)
            FileInputStream(f).use { it.read(head) }
            (head[0].toInt() and 0xFF) == 0xEF && (head[1].toInt() and 0xFF) == 0xBB && (head[2].toInt() and 0xFF) == 0xBF
        }
    } catch (t: Throwable) {
        false
    }

    fun convertTree(
        root: String,
        from: String,
        to: String,
        recursive: Boolean = true,
        exts: Set<String> = DEFAULT_EXTS,
        onlyMatchingSource: Boolean = true,
        withBom: Boolean = false,
        onProgress: ((Float, String) -> Unit)? = null
    ): ConvertResult {
        val taskId = TaskCenter.start("编码转换", "$from → $to")
        val files = collect(File(root), recursive, exts)
        var ok = 0
        var failed = 0
        var skipped = 0
        files.forEachIndexed { i, f ->
            val detected = sniff(f.absolutePath)
            val should = !onlyMatchingSource || detected.equals(from, true) || detected == from
            if (!should) {
                skipped++
            } else if (convertFile(f.absolutePath, detected, to, withBom)) {
                ok++
            } else {
                failed++
            }
            val p = (i + 1).toFloat() / files.size.coerceAtLeast(1)
            onProgress?.invoke(p, f.name)
            TaskCenter.update(taskId, p, f.name)
        }
        TaskCenter.finish(taskId, failed == 0, if (failed > 0) "$failed 个文件失败" else null)
        return ConvertResult(ok, failed, skipped)
    }

    fun collect(dir: File, recursive: Boolean, exts: Set<String>): List<File> {
        val out = mutableListOf<File>()
        val children = try {
            dir.listFiles()
        } catch (t: Throwable) {
            null
        } ?: return out
        children.forEach { c ->
            if (c.isDirectory) {
                if (recursive) out += collect(c, true, exts)
            } else if (c.extension.lowercase() in exts) {
                out += c
            }
        }
        return out
    }
}
