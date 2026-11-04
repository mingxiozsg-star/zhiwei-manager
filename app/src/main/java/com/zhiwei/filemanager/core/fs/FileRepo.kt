package com.zhiwei.filemanager.core.fs

import com.zhiwei.filemanager.core.model.FileItem
import com.zhiwei.filemanager.core.model.SortMode
import com.zhiwei.filemanager.core.model.SortOrder
import java.io.File
import java.util.Locale

/** 目录读取 + 排序。 */
object FileRepo {

    fun list(path: String, showHidden: Boolean): List<FileItem> {
        val dir = File(path)
        if (!dir.isDirectory) return emptyList()
        val files = try {
            dir.listFiles()
        } catch (t: Throwable) {
            null
        } ?: return emptyList()
        return files.asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { it.toItem() }
            .toList()
    }

    fun itemOf(path: String): FileItem? {
        val f = File(path)
        return if (f.exists()) f.toItem() else null
    }

    fun sort(items: List<FileItem>, mode: SortMode, order: SortOrder): List<FileItem> {
        val cmp: Comparator<FileItem> = when (mode) {
            SortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortMode.SIZE -> compareBy { it.size }
            SortMode.DATE -> compareBy { it.lastModified }
            SortMode.TYPE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.ext }
        }
        val base = if (order == SortOrder.ASC) cmp else cmp.reversed()
        val dirFirst: Comparator<FileItem> = compareByDescending { it.isDir }
        return items.sortedWith(dirFirst.then(base))
    }

    fun parentOf(path: String): String? {
        val p = File(path).parent ?: return null
        return p
    }

    fun countChildren(path: String): Int {
        return try {
            File(path).listFiles()?.size ?: 0
        } catch (t: Throwable) {
            0
        }
    }

    fun sizeOf(path: String): Long {
        val f = File(path)
        if (!f.isDirectory) return f.length()
        var total = 0L
        try {
            f.listFiles()?.forEach { total += sizeOf(it.absolutePath) }
        } catch (t: Throwable) {
        }
        return total
    }

    fun breadcrumbs(path: String): List<Pair<String, String>> {
        val parts = path.trimEnd('/').split('/').filter { it.isNotEmpty() }
        val out = mutableListOf<Pair<String, String>>()
        out += "/" to "/"
        var acc = ""
        parts.forEach { acc = "$acc/$it"; out += it to acc }
        return out
    }

    fun displayName(path: String): String = path.trimEnd('/').substringAfterLast('/').ifEmpty { "/" }

    /**
     * 常用目录快捷入口。
     *
     * 只返回真实存在（或至少父目录存在）的路径，避免给用户一堆点进去报错的入口；
     * 外部存储根路径走 Environment，而不是硬编码 /sdcard，兼容多用户与分区变化。
     */
    fun quickPaths(): List<Pair<String, String>> {
        val storage = try {
            android.os.Environment.getExternalStorageDirectory().absolutePath
        } catch (t: Throwable) {
            "/sdcard"
        }
        val candidates = listOf(
            "内部存储" to storage,
            "下载" to "$storage/Download",
            "图片" to "$storage/Pictures",
            "相机" to "$storage/DCIM",
            "文档" to "$storage/Documents",
            "音乐" to "$storage/Music",
            "Android/data" to "$storage/Android/data",
            "根目录" to "/"
        )
        val out = candidates.filter { (_, p) -> File(p).exists() }
        return out.ifEmpty { listOf("内部存储" to storage, "根目录" to "/") }
    }

    fun kindLabel(ext: String): String = ext.uppercase(Locale.US).ifEmpty { "FILE" }
}

/** File → FileItem 的映射（原本缺失导致 FileRepo 无法编译）。 */
private fun File.toItem(): FileItem = FileItem(
    name = name,
    path = absolutePath,
    isDir = isDirectory,
    size = if (isDirectory) -1L else length(),
    lastModified = lastModified(),
    isSymlink = isSymbolicLinkCompat(this),
    hidden = name.startsWith("."),
    readable = canRead(),
    writable = canWrite()
)

private fun isSymbolicLinkCompat(file: File): Boolean = try {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        java.nio.file.Files.isSymbolicLink(file.toPath())
    } else {
        false
    }
} catch (t: Throwable) {
    false
}
