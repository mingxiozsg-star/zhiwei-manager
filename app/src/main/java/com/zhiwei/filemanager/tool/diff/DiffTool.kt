package com.zhiwei.filemanager.tool.diff

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.patch.DeltaType

/** 文本 / 十六进制对比。 */
object DiffTool {

    fun unified(
        aText: String,
        bText: String,
        aName: String = "a",
        bName: String = "b",
        context: Int = 3
    ): String {
        val a = aText.split("\n")
        val b = bText.split("\n")
        val patch = DiffUtils.diff(a, b)
        val lines = UnifiedDiffUtils.generateUnifiedDiff(aName, bName, a, patch, context)
        return if (lines.isEmpty()) "（无差异）" else lines.joinToString("\n")
    }

    fun stats(aText: String, bText: String): String {
        val patch = DiffUtils.diff(aText.split("\n"), bText.split("\n"))
        var add = 0
        var del = 0
        patch.deltas.forEach { d ->
            when (d.type) {
                DeltaType.INSERT -> add += d.target.lines.size
                DeltaType.DELETE -> del += d.source.lines.size
                else -> {
                    add += d.target.lines.size
                    del += d.source.lines.size
                }
            }
        }
        return "差异块 ${patch.deltas.size} · 新增 $add 行 · 删除 $del 行"
    }
}
