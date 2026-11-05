package com.zhiwei.filemanager.core.fs

import com.zhiwei.filemanager.core.task.TaskCenter
import java.io.File

/** 回收站条目。 */
data class RecycleEntry(
    val bucket: File,
    val name: String,
    val origin: String,
    val size: Long,
    val deletedAt: Long
)

/**
 * 回收站：删除的条目先搬进 `<根>/.zhiwei_recycle/<时间戳>_<名称>/`，
 * 原路径写在同级 `.origin` 文本里，支持还原、单条彻底删除与清空。
 *
 * 同一分区优先 `renameTo`（瞬时、零拷贝），跨分区（如 /data → /sdcard）退化为复制 + 删除。
 */
object RecycleBin {

    const val ROOT = "/sdcard/.zhiwei_recycle"
    private const val ORIGIN_FILE = ".origin"
    private const val UNKNOWN = "（原路径已丢失）"

    /** 移入回收站。 */
    fun moveIn(paths: List<String>, title: String = "删除到回收站"): OpResult {
        val id = TaskCenter.start(title, "${paths.size} 项")
        val root = File(ROOT)
        root.mkdirs()
        var ok = 0
        var failed = 0
        paths.forEachIndexed { i, p ->
            val src = File(p)
            val bucket = File(root, bucketName(src.name))
            var done = false
            try {
                if (bucket.mkdirs() && moveInto(src, File(bucket, src.name))) {
                    File(bucket, ORIGIN_FILE).writeText(src.absolutePath)
                    done = true
                }
            } catch (t: Throwable) {
                done = false
            }
            if (done) {
                ok++
            } else {
                failed++
                FileOps.deleteRecursively(bucket)
            }
            TaskCenter.update(id, (i + 1).toFloat() / paths.size.coerceAtLeast(1), src.name)
        }
        TaskCenter.finish(id, failed == 0, if (failed > 0) "$failed 项失败" else null)
        return OpResult(ok, failed)
    }

    /** 全部条目，最近的排最前。 */
    fun list(): List<RecycleEntry> {
        val root = File(ROOT)
        val dirs = try {
            root.listFiles()
        } catch (t: Throwable) {
            null
        } ?: return emptyList()
        return dirs.filter { it.isDirectory }.map { dir ->
            val content = try {
                dir.listFiles()?.firstOrNull { it.name != ORIGIN_FILE }
            } catch (t: Throwable) {
                null
            }
            val origin = try {
                File(dir, ORIGIN_FILE).takeIf { it.isFile }?.readText()?.trim().orEmpty()
            } catch (t: Throwable) {
                ""
            }
            RecycleEntry(
                bucket = dir,
                name = content?.name ?: dir.name,
                origin = origin.ifEmpty { UNKNOWN },
                size = sizeOf(content),
                deletedAt = dir.name.substringBefore('_').toLongOrNull() ?: dir.lastModified()
            )
        }.sortedByDescending { it.deletedAt }
    }

    /** 还原到原位置（原位置已被占用时自动加序号）。 */
    fun restore(entry: RecycleEntry): Boolean {
        if (!entry.origin.startsWith("/")) return false
        val content = try {
            entry.bucket.listFiles()?.firstOrNull { it.name != ORIGIN_FILE }
        } catch (t: Throwable) {
            null
        } ?: return false
        val origin = File(entry.origin)
        val parent = origin.parent ?: "/sdcard"
        val target = FileOps.uniqueTarget(parent, origin.name)
        val moved = try {
            moveInto(content, target)
        } catch (t: Throwable) {
            false
        }
        if (!moved) return false
        FileOps.deleteRecursively(entry.bucket)
        return true
    }

    fun purge(entry: RecycleEntry): Boolean = FileOps.deleteRecursively(entry.bucket)

    /** 清空回收站，返回清掉的条目数。 */
    fun empty(): Int {
        val all = list()
        var n = 0
        all.forEach { if (purge(it)) n++ }
        return n
    }

    fun totalSize(): Long = list().sumOf { it.size }

    private fun bucketName(name: String): String {
        val safe = name.replace('/', '_').take(60).ifEmpty { "item" }
        return System.currentTimeMillis().toString() + "_" + safe
    }

    /** 同分区 rename，跨分区复制 + 删源。 */
    private fun moveInto(src: File, dest: File): Boolean {
        if (!src.exists()) return false
        if (safeRename(src, dest)) return true
        val ok = if (src.isDirectory) FileOps.copyDir(src, dest) else FileOps.copyFile(src, dest)
        if (!ok) return false
        return FileOps.deleteRecursively(src)
    }

    private fun safeRename(src: File, dest: File): Boolean = try {
        dest.parentFile?.mkdirs()
        src.renameTo(dest)
    } catch (t: Throwable) {
        false
    }

    private fun sizeOf(f: File?): Long {
        if (f == null) return 0L
        if (f.isFile) return f.length()
        var total = 0L
        val children = try {
            f.listFiles()
        } catch (t: Throwable) {
            null
        }
        children?.forEach { total += sizeOf(it) }
        return total
    }
}
