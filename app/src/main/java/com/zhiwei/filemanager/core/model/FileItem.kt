package com.zhiwei.filemanager.core.model

import com.zhiwei.filemanager.core.util.FileKind
import com.zhiwei.filemanager.core.util.Format

/** 文件浏览器列表的最小数据单元。 */
data class FileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long,
    val isSymlink: Boolean = false,
    val hidden: Boolean = false,
    val readable: Boolean = true,
    val writable: Boolean = true,
    val children: Int = -1
) {
    val ext: String get() = if (isDir) "" else Format.extOf(name)
    val kind: FileKind get() = Format.kindOf(name, isDir)
    val isApk: Boolean get() = ext == "apk" || ext == "apks" || ext == "xapk"
    val isDex: Boolean get() = ext == "dex"
    val isSmali: Boolean get() = ext == "smali"
    val isJar: Boolean get() = ext == "jar" || ext == "aar"
    val isArchive: Boolean get() = ext in Format.ARCHIVE_EXTS
}
