package com.zhiwei.filemanager.tool.rename

import com.zhiwei.filemanager.core.task.TaskCenter
import java.io.File
import java.util.Locale

/** 大小写处理。 */
enum class CaseMode(val label: String) {
    KEEP("不变"),
    LOWER("小写"),
    UPPER("大写")
}

/** 批量重命名规则。 */
data class RenameOptions(
    val find: String = "",
    val replace: String = "",
    val regex: Boolean = false,
    val prefix: String = "",
    val suffix: String = "",
    val numbering: Boolean = false,
    val startNumber: Int = 1,
    val padWidth: Int = 2,
    val numberAtFront: Boolean = false,
    val case: CaseMode = CaseMode.KEEP,
    val keepExtension: Boolean = true,
    val trimSpaces: Boolean = false
)

/** 预览项：原名 → 新名，以及冲突原因。 */
data class RenamePreviewItem(
    val file: File,
    val newName: String,
    val conflict: Boolean,
    val reason: String
) {
    val changed: Boolean get() = newName != file.name
}

data class RenameApplyResult(val renamed: Int, val failed: Int, val notes: List<String>) {
    fun summary(): String {
        val sb = StringBuilder("重命名完成：成功 $renamed 项，失败 $failed 项")
        if (notes.isNotEmpty()) {
            sb.append("\n")
            notes.take(30).forEach { sb.append("  ! ").append(it).append("\n") }
        }
        return sb.toString().trim()
    }
}

/**
 * 批量重命名引擎。
 *
 * 规则按顺序叠加：查找替换 → 大小写 → 去空格 → 前缀/序号/后缀 → 保留扩展名。
 * 执行采用两阶段改名（先临时名再目标名），互换名、环形改名都不会互相覆盖。
 */
object BatchRenameTool {

    private val INVALID = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

    fun sanitize(name: String): String = INVALID.replace(name, "_").trim()

    fun computeName(f: File, index: Int, opts: RenameOptions): String {
        val src = f.name
        val hasExt = src.contains('.') && !src.startsWith(".")
        val base0 = if (opts.keepExtension && hasExt) src.substringBeforeLast('.') else src
        val ext = if (opts.keepExtension && hasExt) "." + src.substringAfterLast('.') else ""
        var base = base0
        if (opts.find.isNotEmpty()) {
            base = if (opts.regex) {
                try {
                    Regex(opts.find).replace(base, opts.replace)
                } catch (t: Throwable) {
                    base
                }
            } else {
                base.replace(opts.find, opts.replace)
            }
        }
        when (opts.case) {
            CaseMode.LOWER -> base = base.lowercase(Locale.ROOT)
            CaseMode.UPPER -> base = base.uppercase(Locale.ROOT)
            CaseMode.KEEP -> Unit
        }
        if (opts.trimSpaces) base = base.replace(" ", "")
        val num = if (opts.numbering) {
            (opts.startNumber + index).toString().padStart(opts.padWidth.coerceIn(1, 6), '0')
        } else {
            ""
        }
        val core = buildString {
            if (num.isNotEmpty() && opts.numberAtFront) {
                append(num)
                append('_')
            }
            append(opts.prefix)
            append(base)
            if (num.isNotEmpty() && !opts.numberAtFront) {
                append('_')
                append(num)
            }
            append(opts.suffix)
        }
        return sanitize(core + ext)
    }

    fun preview(files: List<File>, opts: RenameOptions): List<RenamePreviewItem> {
        val sourcePaths = files.map { it.absolutePath }.toSet()
        val seen = HashMap<String, Int>()
        return files.mapIndexed { index, f ->
            val newName = computeName(f, index, opts)
            val dir = f.parentFile?.absolutePath ?: ""
            val key = dir + "/" + newName.lowercase(Locale.ROOT)
            val dup = (seen[key] ?: 0) > 0
            seen[key] = (seen[key] ?: 0) + 1
            val target = File(dir, newName)
            val clash = target.exists() && target.absolutePath !in sourcePaths
            val reason = when {
                newName.isBlank() -> "名称为空"
                dup -> "与本批其他文件重名"
                clash -> "目标已存在同名文件"
                else -> ""
            }
            RenamePreviewItem(f, newName, reason.isNotEmpty(), reason)
        }
    }

    fun previewText(items: List<RenamePreviewItem>, limit: Int = 60): String {
        if (items.isEmpty()) return "未选择任何文件"
        val changed = items.count { it.changed && !it.conflict }
        val blocked = items.count { it.conflict }
        val sb = StringBuilder("共 ${items.size} 项 · 将改名 $changed 项 · 冲突 $blocked 项")
        items.take(limit).forEach { it ->
            sb.append("\n").append(if (it.conflict) "✗ " else if (it.changed) "→ " else "· ")
                .append(it.file.name).append("  →  ").append(it.newName)
            if (it.conflict) sb.append("（").append(it.reason).append("）")
        }
        if (items.size > limit) sb.append("\n…另有 ${items.size - limit} 项")
        return sb.toString()
    }

    fun apply(items: List<RenamePreviewItem>): RenameApplyResult {
        val notes = mutableListOf<String>()
        val pending = items.filter { !it.conflict && it.changed }
        val taskId = TaskCenter.start("批量重命名", "共 ${pending.size} 项")
        val staged = mutableListOf<Pair<File, File>>()
        var failed = 0
        pending.forEachIndexed { index, item ->
            val temp = File(item.file.parentFile, ".zhiwei_tmp_${System.currentTimeMillis()}_$index")
            val ok = try {
                item.file.renameTo(temp)
            } catch (t: Throwable) {
                false
            }
            if (ok) {
                staged += temp to File(item.file.parentFile, item.newName)
            } else {
                failed++
                notes += "无法改名：${item.file.name}"
            }
        }
        var renamed = 0
        staged.forEachIndexed { index, (temp, target) ->
            val ok = try {
                temp.renameTo(target)
            } catch (t: Throwable) {
                false
            }
            if (ok) renamed++ else {
                failed++
                notes += "写入失败：${target.name}"
            }
            TaskCenter.update(taskId, (index + 1).toFloat() / staged.size.coerceAtLeast(1), target.name)
        }
        TaskCenter.finish(taskId, failed == 0, if (failed > 0) "$failed 项失败" else null)
        return RenameApplyResult(renamed, failed, notes)
    }
}