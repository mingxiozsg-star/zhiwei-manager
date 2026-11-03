package com.zhiwei.filemanager.ui.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.filemanager.core.task.TaskCenter
import com.zhiwei.filemanager.core.task.TaskInfo

/** 后台任务看板：所有耗时操作（复制/解压/转码/反汇编）的进度与结果。 */
@Composable
fun TaskBoardScreen() {
    val tasks by TaskCenter.tasks.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "运行中 ${tasks.count { !it.finished }} · 已完成 ${tasks.count { it.finished }}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { TaskCenter.clearFinished() }) { Text("清除已完成", fontSize = 12.sp) }
            TextButton(onClick = { TaskCenter.clearAll() }) { Text("全部清空", fontSize = 12.sp) }
        }
        if (tasks.isEmpty()) {
            Text(
                text = "暂无任务。复制、解压、Dex 反汇编等操作会自动出现在这里。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(tasks.asReversed(), key = { it.id }) { t -> TaskRow(t) }
            }
        }
    }
}

@Composable
private fun TaskRow(t: TaskInfo) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = t.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = when {
                    t.finished && t.error != null -> "失败"
                    t.finished -> "完成"
                    else -> "${(t.progress * 100).toInt()}%"
                },
                fontSize = 12.sp,
                color = when {
                    t.finished && t.error != null -> MaterialTheme.colorScheme.error
                    t.finished -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (!t.finished) {
            LinearProgressIndicator(
                progress = { t.progress },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
        val detail = t.error ?: t.detail
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                fontSize = 11.sp,
                color = if (t.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}