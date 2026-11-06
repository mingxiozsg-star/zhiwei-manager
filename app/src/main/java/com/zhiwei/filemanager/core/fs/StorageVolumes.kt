package com.zhiwei.filemanager.core.fs

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import com.zhiwei.filemanager.core.util.Format
import java.io.File

/** 折叠栏里的一行存储入口。 */
data class StorageEntry(
    val label: String,
    val path: String,
    val kind: Kind,
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val removable: Boolean = false
) {
    enum class Kind(val glyph: String) {
        ROOT("⏣"),
        INTERNAL("▤"),
        SD_CARD("▥"),
        USB("▣"),
        OTHER("◇")
    }

    /** 形如 "58.2G 可用 / 共 128G"，stat 失败时返回空串。 */
    val usageText: String
        get() = if (totalBytes <= 0L) {
            ""
        } else {
            Format.size((totalBytes - usedBytes).coerceAtLeast(0L)) + " 可用 / 共 " + Format.size(totalBytes)
        }
}

/**
 * 存储卷枚举（折叠栏的数据源）。
 *
 * 顺序：根目录 → 内部存储 → 外置存储卡 → U 盘 → 其它挂载点。
 * 主要走 [StorageManager.getStorageVolumes]（API 24+），
 * API 30 以下没有公开的 getDirectory()，用反射取 `getPathFile` / `getPath`；
 * 全部失败时退化为扫描 `/storage` 下除了 emulated/self 之外的真实目录。
 */
object StorageVolumes {

    fun list(context: Context): List<StorageEntry> {
        val result = mutableListOf<StorageEntry>()
        result += StorageEntry("根目录", "/", StorageEntry.Kind.ROOT)

        val manager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        val volumes: List<StorageVolume> = runCatching { manager?.storageVolumes.orEmpty() }.getOrDefault(emptyList())
        val primaryPath = runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrNull()

        volumes.forEach { volume ->
            val path = pathOf(volume) ?: return@forEach
            val dir = File(path)
            if (!dir.exists()) return@forEach
            val isPrimary = runCatching { volume.isPrimary }.getOrDefault(false) ||
                isPrimaryPath(path, primaryPath)
            val removable = runCatching { volume.isRemovable }.getOrDefault(false)
            // 注意：只有 getDescription(Context) 是公开 API（API 30 起被标记废弃），
            // 无参 getDescription() 在公开 SDK 里不存在，别写成 volume.description。
            @Suppress("DEPRECATION")
            val description = runCatching { volume.getDescription(context).orEmpty() }.getOrDefault("")
            val kind = when {
                isPrimary -> StorageEntry.Kind.INTERNAL
                looksLikeUsb(path, description) -> StorageEntry.Kind.USB
                removable -> StorageEntry.Kind.SD_CARD
                else -> StorageEntry.Kind.SD_CARD
            }
            val label = when (kind) {
                StorageEntry.Kind.INTERNAL -> "内部存储"
                StorageEntry.Kind.SD_CARD -> "外置存储卡"
                StorageEntry.Kind.USB -> "U 盘"
                StorageEntry.Kind.ROOT -> "根目录"
                StorageEntry.Kind.OTHER -> "存储"
            }
            result += build(label, path, kind)
        }

        // 兜底：有些机型/ROM 的 StorageManager 拿不到路径，直接扫 /storage
        val storageRoot = File("/storage")
        storageRoot.listFiles()?.forEach { f ->
            val name = f.name
            if (!f.isDirectory) return@forEach
            if (name == "emulated" || name == "self") return@forEach
            if (result.any { it.path == f.absolutePath }) return@forEach
            val kind = if (looksLikeUsb(f.absolutePath, name)) StorageEntry.Kind.USB else StorageEntry.Kind.SD_CARD
            val label = if (kind == StorageEntry.Kind.USB) "U 盘（$name）" else "外置存储卡（$name）"
            result += build(label, f.absolutePath, kind)
        }

        // 内部存储如果没被 StorageManager 命中，补一条
        if (primaryPath != null && result.none { it.path == primaryPath }) {
            result += build("内部存储", primaryPath, StorageEntry.Kind.INTERNAL)
        }
        return result.distinctBy { it.path }
    }

    private fun build(label: String, path: String, kind: StorageEntry.Kind): StorageEntry {
        val stat = stat(path)
        return StorageEntry(
            label = label,
            path = path,
            kind = kind,
            totalBytes = stat.first,
            usedBytes = stat.second,
            removable = kind == StorageEntry.Kind.SD_CARD || kind == StorageEntry.Kind.USB
        )
    }

    /** 返回 (总容量, 已用容量)。 */
    private fun stat(path: String): Pair<Long, Long> = try {
        val fs = StatFs(path)
        val total = fs.totalBytes
        val free = fs.availableBytes
        total to (total - free).coerceAtLeast(0L)
    } catch (t: Throwable) {
        0L to 0L
    }

    private fun isPrimaryPath(path: String, primary: String?): Boolean {
        if (primary == null) return false
        return path.trimEnd('/') == primary.trimEnd('/') || path.contains("/storage/emulated/0")
    }

    private fun looksLikeUsb(path: String, description: String): Boolean {
        val lower = description.lowercase()
        if (lower.contains("usb") || description.contains("U 盘") || description.contains("U盘")) return true
        // /storage/XXXX-XXXX 是 UUID 命名，U 盘与 SD 卡都可能；再看描述里有没有 sd
        return false
    }

    /** API 30+ 直接用 getDirectory()；更低版本反射拿路径。 */
    private fun pathOf(volume: StorageVolume): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { volume.directory?.absolutePath }.getOrNull()?.let { return it }
        }
        for (method in listOf("getPathFile", "getPath")) {
            val p = runCatching {
                val m = StorageVolume::class.java.getMethod(method)
                m.isAccessible = true
                when (val v = m.invoke(volume)) {
                    is File -> v.absolutePath
                    is String -> v
                    else -> null
                }
            }.getOrNull()
            if (!p.isNullOrBlank()) return p
        }
        // 有些 ROM 只给 UUID，靠挂载目录拼
        val uuid = runCatching { volume.uuid }.getOrNull()
        if (!uuid.isNullOrBlank() && uuid != "primary") {
            val candidate = File("/storage", uuid)
            if (candidate.exists()) return candidate.absolutePath
        }
        return null
    }
}