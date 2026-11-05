package com.zhiwei.filemanager.ui.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.filemanager.core.task.AppScope
import com.zhiwei.filemanager.tool.rename.BatchRenameTool
import com.zhiwei.filemanager.tool.rename.CaseMode
import com.zhiwei.filemanager.tool.rename.RenameOptions
import com.zhiwei.filemanager.tool.rename.RenamePreviewItem
import java.io.File
import kotlinx.coroutines.launch

/**
 * 批量重命名：先预览再执行。
 *
 * 规则叠加顺序：查找替换 → 大小写 → 去空格 → 前缀 / 序号 / 后缀 → 保留扩展名。
 * 冲突项（重名、目标已存在）会在预览里标出并跳过，执行采用两阶段改名避免互相覆盖。
 */
@Composable
fun BatchRenameScreen(initialPaths: List<String> = emptyList()) {
    var pathText by remember { mutableStateOf(initialPaths.joinToString("\n")) }
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var regex by remember { mutableStateOf(false) }
    var keepExt by remember { mutableStateOf(true) }
    var trimSpaces by remember { mutableStateOf(false) }
    var numbering by remember { mutableStateOf(false) }
    var startText by remember { mutableStateOf("1") }
    var padText by remember { mutableStateOf("2") }
    var numberAtFront by remember { mutableStateOf(false) }
    var case by remember { mutableStateOf(CaseMode.KEEP) }

    var preview by remember { mutableStateOf<List<RenamePreviewItem>>(emptyList()) }
    var output by remember { mutableStateOf("") }

    fun files(): List<File> = pathText.split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { File(it) }
        .filter { it.exists() }

    fun options(): RenameOptions = RenameOptions(
        find = find,
        replace = replace,
        regex = regex,
        prefix = prefix,
        suffix = suffix,
        numbering = numbering,
        startNumber = startText.toIntOrNull() ?: 1,
        padWidth = padText.toIntOrNull() ?: 2,
        numberAtFront = numberAtFront,
        case = case,
        keepExtension = keepExt,
        trimSpaces = trimSpaces
    )

    fun refreshPreview(): List<RenamePreviewItem> {
        val list = BatchRenameTool.preview(files(), options())
        preview = list
        output = BatchRenameTool.previewText(list)
        return list
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        OutlinedTextField(
            value = pathText,
            onValueChange = { pathText = it },
            label = { Text("待改名文件（每行一个路径；从文件页多选进入会自动带入）") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
        )

        OutlinedTextField(
            value = find,
            onValueChange = { find = it },
            label = { Text("查找内容（留空则不做替换）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
        OutlinedTextField(
            value = replace,
            onValueChange = { replace = it },
            label = { Text("替换为") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            ToggleChip("正则", regex) { regex = !regex }
            ToggleChip("保留扩展名", keepExt) { keepExt = !keepExt }
            ToggleChip("去空格", trimSpaces) { trimSpaces = !trimSpaces }
            ToggleChip("加序号", numbering) { numbering = !numbering }
            ToggleChip("序号在前", numberAtFront) { numberAtFront = !numberAtFront }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = prefix,
                onValueChange = { prefix = it },
                label = { Text("前缀") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = suffix,
                onValueChange = { suffix = it },
                label = { Text("后缀") },
                singleLine = true,
                modifier = Modifier.weight(1f).padding(start = 6.dp)
            )
        }
        if (numbering) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text("起始序号") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = padText,
                    onValueChange = { padText = it },
                    label = { Text("补零位数") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(start = 6.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text("大小写", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp, top = 14.dp))
            CaseMode.entries.forEach { m ->
                TextButton(onClick = { case = m }) {
                    Text(if (case == m) "● ${m.label}" else m.label, fontSize = 12.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = { refreshPreview() }) { Text("预览改名", fontSize = 12.sp) }
            TextButton(
                enabled = preview.any { !it.conflict && it.changed },
                onClick = {
                    AppScope.io.launch {
                        val r = BatchRenameTool.apply(preview)
                        output = r.summary() + "\n\n" + BatchRenameTool.previewText(
                            BatchRenameTool.preview(files(), options())
                        )
                        preview = emptyList()
                    }
                }
            ) { Text("执行改名", fontSize = 12.sp) }
            TextButton(onClick = { pathText = ""; preview = emptyList(); output = "" }) { Text("清空", fontSize = 12.sp) }
        }
        Text(
            "执行前请先预览：冲突项自动跳过，不会覆盖任何已有文件。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text(output.ifEmpty { "（预览结果在此显示）" }, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}