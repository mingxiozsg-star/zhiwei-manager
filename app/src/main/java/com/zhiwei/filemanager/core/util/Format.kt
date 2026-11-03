package com.zhiwei.filemanager.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileKind { FOLDER, IMAGE, VIDEO, AUDIO, APK, ARCHIVE, DEX, TEXT, CODE, PDF, FONT, DB, DOC, OTHER }

object Format {

    val CODE_EXTS = setOf(
        "xml", "json", "txt", "md", "smali", "java", "kt", "kts", "js", "ts", "css", "html", "htm",
        "yml", "yaml", "ini", "properties", "cfg", "conf", "sh", "gradle", "toml", "csv", "log", "pro", "sql"
    )
    val ARCHIVE_EXTS = setOf(
        "zip", "jar", "apk", "7z", "tar", "gz", "xz", "lz4", "zst", "zstd", "bz2", "rar",
        "tgz", "txz", "aar", "war", "apks", "xapk"
    )
    val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "ico", "tif", "tiff", "avif")
    val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "flv", "wmv", "webm", "3gp", "ts", "m4v", "mpg", "mpeg", "rmvb")
    val AUDIO_EXTS = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "amr", "wma", "mid")
    val DEX_EXTS = setOf("dex", "odex", "vdex", "smali")
    val FONT_EXTS = setOf("ttf", "otf", "ttc", "woff", "woff2")
    val DB_EXTS = setOf("db", "sqlite", "sqlite3", "realm")
    val DOC_EXTS = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "rtf")
    val TEXT_EXTS = setOf("txt", "md", "log", "csv", "pro")

    private val dateTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val dateOnlyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun size(bytes: Long): String {
        if (bytes < 0) return "--"
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble() / 1024.0
        var i = 0
        while (v >= 1024.0 && i < units.size - 1) {
            v /= 1024.0
            i++
        }
        return if (v >= 100) String.format(Locale.US, "%.0f %s", v, units[i])
        else String.format(Locale.US, "%.1f %s", v, units[i])
    }

    fun time(ms: Long): String = if (ms <= 0L) "--" else dateTimeFmt.format(Date(ms))

    fun day(ms: Long): String = if (ms <= 0L) "--" else dateOnlyFmt.format(Date(ms))

    fun extOf(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.US)

    fun kindOf(name: String, isDir: Boolean): FileKind {
        if (isDir) return FileKind.FOLDER
        val e = extOf(name)
        return when {
            e in IMAGE_EXTS -> FileKind.IMAGE
            e in VIDEO_EXTS -> FileKind.VIDEO
            e in AUDIO_EXTS -> FileKind.AUDIO
            e == "apk" || e == "apks" || e == "xapk" -> FileKind.APK
            e in DEX_EXTS -> FileKind.DEX
            e == "pdf" -> FileKind.PDF
            e in FONT_EXTS -> FileKind.FONT
            e in DB_EXTS -> FileKind.DB
            e in DOC_EXTS -> FileKind.DOC
            e in ARCHIVE_EXTS -> FileKind.ARCHIVE
            e in TEXT_EXTS -> FileKind.TEXT
            e in CODE_EXTS -> FileKind.CODE
            else -> FileKind.OTHER
        }
    }

    fun isTextLikely(name: String): Boolean {
        val e = extOf(name)
        return e in CODE_EXTS || e in TEXT_EXTS || e.isEmpty()
    }
}
