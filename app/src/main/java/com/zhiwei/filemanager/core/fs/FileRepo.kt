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

    fun kindLabel(ext: String): String = ext.uppercase(Locale.US).ifEmpty { "FILE" }
}
