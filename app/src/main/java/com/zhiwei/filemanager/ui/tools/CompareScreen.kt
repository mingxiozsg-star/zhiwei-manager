package com.zhiwei.filemanager.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import com.zhiwei.filemanager.tool.compare.CompareTool
import com.zhiwei.filemanager.tool.diff.DiffTool
import java.io.File

/**
 * 文件与目录对比：左右两路可填文件或文件夹。
 *
 * 三种模式各自独立：文本 diff（逐行）、字节对比（流式分块，适合二进制 / so / dex）、目录对比（相对路径求差集）。
 */
@Composable
fun CompareScreen(initialA: String = "", initialB: String = "") {
    var a by remember { mutableStateOf(initialA) }
    var b by remember { mutableStateOf(initialB) }
    var recursive by remember { mutableStateOf(true) }
    var compareContent by remember { mutableStateOf(false) }
    var compareMtime by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }

    fun run(title: String, block: () -> String) {
        running = true
        output = "[$title] 执行中…"
        AppScope.io.launch {
            output = "[$title]\n" + try {
                block()
            } catch (t: Throwable) {
                "失败：${t.javaClass.simpleName} ${t.message}"
            }
            running = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        OutlinedTextField(
            value = a,
            onValueChange = { a = it },
            label = { Text("左侧：文件或文件夹路径") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
        )
        OutlinedTextField(
            value = b,
            onValueChange = { b = it },
            label = { Text("右侧：文件或文件夹路径") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(top = 6.dp)
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                val t = a
                a = b
                b = t
            }) { Text("⇄ 左右互换", fontSize = 12.sp) }
            TextButton(onClick = { recursive = !recursive }) {
                Text(if (recursive) "☑ 递归子目录" else "☐ 递归子目录", fontSize = 12.sp)
            }
            TextButton(onClick = { compareContent = !compareContent }) {
                Text(if (compareContent) "☑ 比对内容" else "☐ 比对内容", fontSize = 12.sp)
            }
            TextButton(onClick = { compareMtime = !compareMtime }) {
                Text(if (compareMtime) "☑ 比对时间" else "☐ 比对时间", fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = {
                run("文本对比") {
                    val fa = File(a.trim())
                    val fb = File(b.trim())
                    if (!fa.isFile || !fb.isFile) {
                        "两侧都必须是已存在的文本文件（二进制请用字节对比）"
                    } else {
                        val ta = fa.readText()
                        val tb = fb.readText()
                        DiffTool.stats(ta, tb) + "\n\n" + DiffTool.unified(ta, tb, fa.name, fb.name)
                    }
                }
            }) { Text("文本 diff", fontSize = 12.sp) }
            TextButton(onClick = {
                run("字节对比") {
                    CompareTool.byteDiffSummary(CompareTool.compareBytes(a.trim(), b.trim()))
                }
            }) { Text("字节对比", fontSize = 12.sp) }
            TextButton(onClick = {
                run("目录对比") {
                    val r = CompareTool.compareDirs(
                        dirA = a.trim(),
                        dirB = b.trim(),
                        recursive = recursive,
                        compareMtime = compareMtime,
                        compareContent = compareContent
                    )
                    val sb = StringBuilder(CompareTool.dirDiffSummary(r))
                    r.entries.take(200).forEach { e ->
                        sb.append("\n").append(e.kind.label).append(" · ").append(e.relPath)
                        if (e.detail.isNotEmpty()) sb.append("（").append(e.detail).append("）")
                    }
                    if (r.entries.size > 200) sb.append("\n…另有 ${r.entries.size - 200} 条")
                    sb.toString()
                }
            }) { Text("目录对比", fontSize = 12.sp) }
        }
        Text(
            "提示：从文件页双窗底栏点「对比」可直接把两个窗口的路径带进来。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text(output.ifEmpty { "（对比结果在此显示）" }, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}