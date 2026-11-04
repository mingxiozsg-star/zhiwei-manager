package com.zhiwei.filemanager.tool.archive

import com.github.luben.zstd.ZstdInputStream
import com.zhiwei.filemanager.core.task.TaskCenter
import net.jpountz.lz4.LZ4FrameInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ArchiveEntry(val name: String, val size: Long, val isDir: Boolean)

data class ArchiveResult(val ok: Int, val failed: Int, val destDir: String?)

/** 归档：zip/jar/apk/tar/gz/xz/lz4/zst/7z/rar 解压，zip 压缩。 */
object ArchiveTool {

    private const val BUF = 256 * 1024

    private val ZIP_EXTS = setOf("zip", "jar", "apk", "aar", "war", "apks", "xapk", "kmz", "epub")
    private val SINGLE_EXTS = setOf("gz", "xz", "lz4", "zst", "zstd")

    fun canExtract(name: String): Boolean {
        val n = name.lowercase()
        val ext = n.substringAfterLast('.', "")
        return ext in ZIP_EXTS || ext in SINGLE_EXTS || ext == "tar" || ext == "7z" || ext == "rar" ||
            n.endsWith(".tar.gz") || n.endsWith(".tgz") || n.endsWith(".tar.xz") || n.endsWith(".txz") ||
            n.endsWith(".tar.lz4") || n.endsWith(".tar.zst")
    }

    fun list(archive: String, limit: Int = 3000): List<ArchiveEntry> {
        val f = File(archive)
        if (!f.isFile) return emptyList()
        val n = f.name.lowercase()
        val ext = n.substringAfterLast('.', "")
        return try {
            when {
                ext in ZIP_EXTS -> zipEntries(f, limit)
                n.endsWith(".tar.gz") || n.endsWith(".tgz") -> tarEntries(GZIPInputStream(f.inputStream()), limit)
                n.endsWith(".tar.xz") || n.endsWith(".txz") -> tarEntries(XZInputStream(f.inputStream()), limit)
                n.endsWith(".tar.lz4") -> tarEntries(LZ4FrameInputStream(f.inputStream()), limit)
                n.endsWith(".tar.zst") -> tarEntries(ZstdInputStream(f.inputStream()), limit)
                ext == "tar" -> tarEntries(f.inputStream(), limit)
                else -> emptyList()
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun zipEntries(f: File, limit: Int): List<ArchiveEntry> {
        val out = ArrayList<ArchiveEntry>()
        ZipFile(f).use { zf ->
            val e = zf.entries()
            while (e.hasMoreElements() && out.size < limit) {
                val entry = e.nextElement()
                out += ArchiveEntry(entry.name, entry.size, entry.isDirectory)
            }
        }
        return out
    }

    private fun tarEntries(raw: InputStream, limit: Int): List<ArchiveEntry> {
        val out = ArrayList<ArchiveEntry>()
        TarArchiveInputStream(raw).use { tar ->
            while (out.size < limit) {
                val e = tar.nextEntry ?: break
                out += ArchiveEntry(e.name, e.size, e.isDirectory)
            }
        }
        return out
    }

    fun extract(archive: String, destDir: String, onProgress: ((Float, String) -> Unit)? = null): ArchiveResult {
        val f = File(archive)
        val dest = File(destDir)
        if (!dest.isDirectory && !dest.mkdirs()) return ArchiveResult(0, 1, null)
        val n = f.name.lowercase()
        val ext = n.substringAfterLast('.', "")
        val base = when {
            n.endsWith(".tar.gz") -> n.removeSuffix(".tar.gz")
            n.endsWith(".tar.xz") -> n.removeSuffix(".tar.xz")
            n.endsWith(".tar.lz4") -> n.removeSuffix(".tar.lz4")
            n.endsWith(".tar.zst") -> n.removeSuffix(".tar.zst")
            n.endsWith(".tgz") -> n.removeSuffix(".tgz")
            n.endsWith(".txz") -> n.removeSuffix(".txz")
            else -> n.substringBeforeLast('.')
        }
        val taskId = TaskCenter.start("解压", f.name)
        val result = try {
            when {
                ext in ZIP_EXTS -> extractZip(f, dest, taskId, onProgress)
                n.endsWith(".tar.gz") || n.endsWith(".tgz") -> extractTar(GZIPInputStream(f.inputStream()), dest, taskId, onProgress)
                n.endsWith(".tar.xz") || n.endsWith(".txz") -> extractTar(XZInputStream(f.inputStream()), dest, taskId, onProgress)
                n.endsWith(".tar.lz4") -> extractTar(LZ4FrameInputStream(f.inputStream()), dest, taskId, onProgress)
                n.endsWith(".tar.zst") -> extractTar(ZstdInputStream(f.inputStream()), dest, taskId, onProgress)
                ext == "tar" -> extractTar(f.inputStream(), dest, taskId, onProgress)
                ext == "gz" -> extractSingle(GZIPInputStream(f.inputStream()), dest, base, taskId)
                ext == "xz" -> extractSingle(XZInputStream(f.inputStream()), dest, base, taskId)
                ext == "lz4" -> extractSingle(LZ4FrameInputStream(f.inputStream()), dest, base, taskId)
                ext == "zst" || ext == "zstd" -> extractSingle(ZstdInputStream(f.inputStream()), dest, base, taskId)
                ext == "7z" -> extract7z(f, dest, taskId, onProgress)
                ext == "rar" -> extractRar(f, dest, taskId, onProgress)
                else -> ArchiveResult(0, 1, null)
            }
        } catch (t: Throwable) {
            ArchiveResult(0, 1, null)
        }
        val success = result.failed == 0 && result.ok > 0
        TaskCenter.finish(taskId, success, if (!success) "解压失败或不支持的格式" else null)
        return result
    }

    private fun extractZip(f: File, dest: File, taskId: Long, onProgress: ((Float, String) -> Unit)?): ArchiveResult {
        var ok = 0
        var failed = 0
        ZipFile(f).use { zf ->
            val entries = zf.entries().toList()
            val total = entries.size.coerceAtLeast(1)
            entries.forEachIndexed { i, entry ->
                val target = safeTarget(dest, entry.name)
                if (target == null) {
                    failed++
                } else if (entry.isDirectory) {
                    if (!target.isDirectory && !target.mkdirs()) failed++ else ok++
                } else {
                    try {
                        target.parentFile?.mkdirs()
                        zf.getInputStream(entry).use { input ->
                            BufferedOutputStream(FileOutputStream(target), BUF).use { output -> copy(input, output) }
                        }
                        ok++
                    } catch (t: Throwable) {
                        failed++
                    }
                }
                val p = (i + 1).toFloat() / total
                onProgress?.invoke(p, entry.name)
                TaskCenter.update(taskId, p, entry.name)
            }
        }
        return ArchiveResult(ok, failed, dest.absolutePath)
    }

    private fun extractTar(raw: InputStream, dest: File, taskId: Long, onProgress: ((Float, String) -> Unit)?): ArchiveResult {
        var ok = 0
        var failed = 0
        var processed = 0
        TarArchiveInputStream(raw).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val target = safeTarget(dest, entry.name)
                if (target == null) {
                    failed++
                } else if (entry.isDirectory) {
                    if (target.isDirectory || target.mkdirs()) ok++ else failed++
                } else {
                    try {
                        target.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(target), BUF).use { output -> copy(tar, output) }
                        ok++
                    } catch (t: Throwable) {
                        failed++
                    }
                }
                processed++
                if (processed % 32 == 0) {
                    onProgress?.invoke(0.5f, entry.name)
                    TaskCenter.update(taskId, 0.5f, entry.name)
                }
            }
        }
        onProgress?.invoke(1f, "完成")
        TaskCenter.update(taskId, 1f, "完成")
        return ArchiveResult(ok, failed, dest.absolutePath)
    }

    private fun extractSingle(raw: InputStream, dest: File, baseName: String, taskId: Long): ArchiveResult {
        return try {
            val target = File(dest, baseName.ifEmpty { "output" })
            BufferedOutputStream(FileOutputStream(target), BUF).use { output -> copy(raw, output) }
            TaskCenter.update(taskId, 1f, target.name)
            ArchiveResult(1, 0, dest.absolutePath)
        } catch (t: Throwable) {
            ArchiveResult(0, 1, dest.absolutePath)
        }
    }

    /** 7z 解压：走 commons-compress 的 SevenZFile，原生支持 LZMA/LZMA2/BZip2/Deflate。 */
    private fun extract7z(f: File, dest: File, taskId: Long, onProgress: ((Float, String) -> Unit)?): ArchiveResult {
        var ok = 0
        var failed = 0
        try {
            org.apache.commons.compress.archivers.sevenz.SevenZFile(f).use { sz ->
                var entry = sz.nextEntry()
                while (entry != null) {
                    val name = entry.name
                    val target = safeTarget(dest, name)
                    if (target == null) {
                        failed++
                    } else if (entry.isDirectory) {
                        if (target.isDirectory || target.mkdirs()) ok++ else failed++
                    } else {
                        try {
                            target.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(target), BUF).use { out -> copy(sz, out) }
                            ok++
                        } catch (t: Throwable) {
                            failed++
                        }
                    }
                    onProgress?.invoke(0.5f, name)
                    TaskCenter.update(taskId, 0.5f, name)
                    entry = sz.nextEntry()
                }
            }
        } catch (t: Throwable) {
            // 加密头 / BCJ2 等不支持的特性会抛异常，此处只累计失败数，不炸上层
            failed++
        }
        onProgress?.invoke(1f, "完成")
        TaskCenter.update(taskId, 1f, "完成")
        return ArchiveResult(ok, failed, dest.absolutePath)
    }

    /** rar 解压：走 junrar 的高层 extract，密码保护 / 分卷卷缺失会落到失败计数。 */
    private fun extractRar(f: File, dest: File, taskId: Long, onProgress: ((Float, String) -> Unit)?): ArchiveResult {
        return try {
            TaskCenter.update(taskId, 0.3f, f.name)
            onProgress?.invoke(0.3f, f.name)
            com.github.junrar.Junrar.extract(f, dest)
            TaskCenter.update(taskId, 1f, "完成")
            onProgress?.invoke(1f, "完成")
            ArchiveResult(1, 0, dest.absolutePath)
        } catch (t: Throwable) {
            ArchiveResult(0, 1, dest.absolutePath)
        }
    }

    fun compressToZip(sources: List<String>, outZip: String, onProgress: ((Float, String) -> Unit)? = null): Boolean {
        val taskId = TaskCenter.start("压缩", File(outZip).name)
        var count = 0
        return try {
            File(outZip).parentFile?.mkdirs()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outZip), BUF)).use { zos ->
                sources.forEachIndexed { i, path ->
                    val src = File(path)
                    if (src.isDirectory) addDir(zos, src, src.name) else addFile(zos, src, src.name)
                    count++
                    val p = (i + 1).toFloat() / sources.size.coerceAtLeast(1)
                    onProgress?.invoke(p, src.name)
                    TaskCenter.update(taskId, p, src.name)
                }
            }
            TaskCenter.finish(taskId, true)
            true
        } catch (t: Throwable) {
            TaskCenter.finish(taskId, false, t.message)
            false
        }
    }

    private fun addDir(zos: ZipOutputStream, dir: File, prefix: String) {
        val children = try {
            dir.listFiles()
        } catch (t: Throwable) {
            null
        }
        if (children.isNullOrEmpty()) {
            zos.putNextEntry(ZipEntry("$prefix/"))
            zos.closeEntry()
            return
        }
        children.forEach { c ->
            if (c.isDirectory) addDir(zos, c, "$prefix/${c.name}") else addFile(zos, c, "$prefix/${c.name}")
        }
    }

    private fun addFile(zos: ZipOutputStream, f: File, entryName: String) {
        zos.putNextEntry(ZipEntry(entryName))
        f.inputStream().use { input -> copy(input, zos) }
        zos.closeEntry()
    }

    private fun copy(input: InputStream, output: java.io.OutputStream) {
        val buf = ByteArray(BUF)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            output.write(buf, 0, n)
        }
    }

    private fun safeTarget(dest: File, name: String): File? {
        val normalized = name.replace('\\', '/').trimStart('/')
        if (normalized.isEmpty()) return null
        return try {
            val f = File(dest, normalized).canonicalFile
            if (f.path.startsWith(dest.canonicalFile.path)) f else null
        } catch (t: Throwable) {
            null
        }
    }
}
