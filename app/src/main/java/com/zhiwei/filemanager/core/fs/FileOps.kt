package com.zhiwei.filemanager.core.fs

import com.zhiwei.filemanager.core.task.TaskCenter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset

data class OpResult(val ok: Int, val failed: Int) {
    val allOk: Boolean get() = failed == 0
    fun summary(action: String): String =
        if (allOk) "$action 完成：$ok 项" else "$action 完成：成功 $ok 项，失败 $failed 项"
}

/** 本地文件系统操作（增删改查 / 复制 / 移动）。 */
object FileOps {

    fun newFolder(parent: String, name: String): Boolean = try {
        File(parent, name).mkdirs()
    } catch (t: Throwable) {
        false
    }

    fun newFile(parent: String, name: String): Boolean {
        val f = File(parent, name)
        return try {
            f.parentFile?.mkdirs()
            f.createNewFile()
        } catch (t: Throwable) {
            false
        }
    }

    fun rename(path: String, newName: String): Boolean {
        val src = File(path)
        val target = File(src.parentFile, newName)
        if (target.absolutePath == src.absolutePath) return true
        if (target.exists()) return false
        return try {
            src.renameTo(target)
        } catch (t: Throwable) {
            false
        }
    }

    fun delete(paths: List<String>, title: String = "删除"): OpResult {
        val id = TaskCenter.start(title, "${paths.size} 项")
        var ok = 0
        var failed = 0
        paths.forEachIndexed { i, p ->
            if (deleteRecursively(File(p))) ok++ else failed++
            TaskCenter.update(id, (i + 1).toFloat() / paths.size.coerceAtLeast(1), FileRepo.displayName(p))
        }
        TaskCenter.finish(id, failed == 0, if (failed > 0) "$failed 项失败" else null)
        return OpResult(ok, failed)
    }

    fun deleteRecursively(f: File): Boolean {
        if (f.isDirectory) {
            try {
                f.listFiles()?.forEach { deleteRecursively(it) }
            } catch (_: Throwable) {
            }
        }
        return try {
            f.delete()
        } catch (t: Throwable) {
            false
        }
    }

    fun copy(paths: List<String>, destDir: String, title: String = "复制"): OpResult =
        transfer(paths, destDir, move = false, title = title)

    fun move(paths: List<String>, destDir: String, title: String = "移动"): OpResult =
        transfer(paths, destDir, move = true, title = title)

    private fun transfer(paths: List<String>, destDir: String, move: Boolean, title: String): OpResult {
        val total = paths.size.coerceAtLeast(1)
        val id = TaskCenter.start(title, destDir)
        var ok = 0
        var failed = 0
        val dest = File(destDir)
        paths.forEachIndexed { i, p ->
            val src = File(p)
            val success = try {
                when {
                    !dest.isDirectory -> false
                    dest.absolutePath.startsWith(src.absolutePath + "/") -> false
                    move && src.parentFile?.absolutePath == dest.absolutePath -> true
                    src.isDirectory -> copyDir(src, uniqueTarget(destDir, src.name))
                    else -> copyFile(src, uniqueTarget(destDir, src.name))
                }
            } catch (t: Throwable) {
                false
            }
            if (success) {
                ok++
                if (move) deleteRecursively(src)
            } else {
                failed++
            }
            TaskCenter.update(id, (i + 1).toFloat() / total, src.name)
        }
        TaskCenter.finish(id, failed == 0, if (failed > 0) "$failed 项失败" else null)
        return OpResult(ok, failed)
    }

    fun uniqueTarget(destDir: String, name: String): File {
        var f = File(destDir, name)
        if (!f.exists()) return f
        val hasExt = name.lastIndexOf('.') > 0
        val base = if (hasExt) name.substring(0, name.lastIndexOf('.')) else name
        val ext = if (hasExt) name.substring(name.lastIndexOf('.')) else ""
        var i = 1
        while (true) {
            f = File(destDir, "$base ($i)$ext")
            if (!f.exists()) return f
            i++
        }
    }

    fun copyFile(src: File, dst: File): Boolean = try {
        dst.parentFile?.mkdirs()
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { output ->
                input.copyTo(output, 256 * 1024)
            }
        }
        dst.setLastModified(src.lastModified())
        true
    } catch (t: Throwable) {
        false
    }

    fun copyDir(src: File, dst: File): Boolean {
        if (!dst.isDirectory && !dst.mkdirs()) return false
        var allOk = true
        val children = try {
            src.listFiles()
        } catch (t: Throwable) {
            null
        }
        children?.forEach { child ->
            val target = File(dst, child.name)
            val okChild = if (child.isDirectory) copyDir(child, target) else copyFile(child, target)
            if (!okChild) allOk = false
        }
        return allOk
    }

    fun readText(path: String, charsetName: String = "UTF-8"): String? = try {
        File(path).readText(charset(charsetName))
    } catch (t: Throwable) {
        null
    }

    fun writeText(path: String, text: String, charsetName: String = "UTF-8"): Boolean = try {
        val f = File(path)
        f.parentFile?.mkdirs()
        f.writeText(text, charset(charsetName))
        true
    } catch (t: Throwable) {
        false
    }

    fun charset(name: String): Charset = try {
        Charset.forName(name)
    } catch (t: Throwable) {
        Charsets.UTF_8
    }
}
