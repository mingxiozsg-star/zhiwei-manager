package com.zhiwei.filemanager.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.filemanager.core.fs.RecycleBin
import com.zhiwei.filemanager.core.fs.RecycleEntry
import com.zhiwei.filemanager.core.prefs.AppPrefs
import com.zhiwei.filemanager.core.task.AppScope
import com.zhiwei.filemanager.core.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 回收站：删除不再直接销毁，先进这里；支持还原、单条彻底删除与清空。
 * 顶部开关控制后续删除行为是否走回收站。
 */
@Composable
fun RecycleBinScreen(onToast: (String) -> Unit) {
    var entries by remember { mutableStateOf<List<RecycleEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf<RecycleEntry?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    fun reload() {
        AppScope.io.launch {
            val list = withContext(Dispatchers.IO) { RecycleBin.list() }
            entries = list
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${entries.size} 项 · ${Format.size(entries.sumOf { it.size })}",
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { reload() }) { Text("刷新", fontSize = 12.sp) }
            TextButton(
                enabled = entries.isNotEmpty(),
                onClick = { confirmEmpty = true }
            ) { Text("清空", fontSize = 12.sp) }
        }
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("删除时移入回收站", fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(checked = AppPrefs.recycleEnabled, onCheckedChange = { AppPrefs.updateRecycleEnabled(it) })
        }
        HorizontalDivider()

        if (entries.isEmpty()) {
            Text(
                text = if (loading) "读取中…" else "回收站是空的。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(entries, key = { it.bucket.absolutePath }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = entry.origin,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${Format.size(entry.size)} · ${Format.time(entry.deletedAt)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            AppScope.io.launch {
                                val ok = withContext(Dispatchers.IO) { RecycleBin.restore(entry) }
                                onToast(if (ok) "已还原：${entry.name}" else "还原失败（原路径不可写？）")
                                val list = withContext(Dispatchers.IO) { RecycleBin.list() }
                                entries = list
                            }
                        }) { Text("还原", fontSize = 12.sp) }
                        TextButton(onClick = { confirm = entry }) { Text("删除", fontSize = 12.sp) }
                    }
                    HorizontalDivider()
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "位置：${RecycleBin.ROOT}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }

    confirm?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("彻底删除") },
            text = { Text("「${entry.name}」将从回收站永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val target = entry
                    confirm = null
                    AppScope.io.launch {
                        withContext(Dispatchers.IO) { RecycleBin.purge(target) }
                        val list = withContext(Dispatchers.IO) { RecycleBin.list() }
                        entries = list
                        onToast("已彻底删除：${target.name}")
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } }
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("清空回收站") },
            text = { Text("将永久删除回收站内全部 ${entries.size} 项，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    AppScope.io.launch {
                        val n = withContext(Dispatchers.IO) { RecycleBin.empty() }
                        entries = withContext(Dispatchers.IO) { RecycleBin.list() }
                        onToast("已清空 $n 项")
                    }
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("取消") } }
        )
    }
}