package com.zhiwei.filemanager.ui.files

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.zhiwei.filemanager.core.fs.FileOps
import com.zhiwei.filemanager.core.fs.FileRepo
import com.zhiwei.filemanager.core.model.FileItem
import com.zhiwei.filemanager.core.model.SortMode
import com.zhiwei.filemanager.core.model.SortOrder
import com.zhiwei.filemanager.core.prefs.AppPrefs
import com.zhiwei.filemanager.core.util.FileKind
import com.zhiwei.filemanager.core.util.Format
import com.zhiwei.filemanager.tool.archive.ArchiveTool
import com.zhiwei.filemanager.ui.theme.KindApk
import com.zhiwei.filemanager.ui.theme.KindArchive
import com.zhiwei.filemanager.ui.theme.KindAudio
import com.zhiwei.filemanager.ui.theme.KindCode
import com.zhiwei.filemanager.ui.theme.KindDex
import com.zhiwei.filemanager.ui.theme.KindFolder
import com.zhiwei.filemanager.ui.theme.KindImage
import com.zhiwei.filemanager.ui.theme.KindOther
import com.zhiwei.filemanager.ui.theme.KindPdf
import com.zhiwei.filemanager.ui.theme.KindText
import com.zhiwei.filemanager.ui.theme.KindVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 导航回调（由 AppRoot 注入）。 */
data class FileNav(
    val openText: (String) -> Unit,
    val openHex: (String) -> Unit,
    val openDex: (String) -> Unit,
    val toast: (String) -> Unit
)

private sealed interface FileDialog {
    data class Input(val title: String, val initial: String, val confirmText: String, val onConfirm: (String) -> Unit) : FileDialog
    data class Confirm(val title: String, val message: String, val onConfirm: () -> Unit) : FileDialog
    data class Props(val item: FileItem) : FileDialog
    data class ItemMenu(val item: FileItem) : FileDialog
    data object Sort : FileDialog
}

@Composable
fun FileBrowserScreen(state: FileBrowserState, nav: FileNav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<FileDialog?>(null) }

    Column(Modifier.fillMaxSize()) {
        PaneTabs(state)
        state.panes.forEachIndexed { index, pane ->
            PaneBox(
                state = state,
                pane = pane,
                index = index,
                nav = nav,
                onDialog = { dialog = it },
                onLongPress = { dialog = FileDialog.ItemMenu(it) },
                modifier = Modifier.weight(1f)
            )
            if (index < state.panes.size - 1) HorizontalDivider()
        }
        BottomBar(state, nav, onDialog = { dialog = it })
    }

    when (val d = dialog) {
        null -> Unit
        is FileDialog.Input -> InputDialog(d) { dialog = null }
        is FileDialog.Confirm -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(d.title) },
            text = { Text(d.message) },
            confirmButton = {
                TextButton(onClick = {
                    d.onConfirm()
                    dialog = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } }
        )
        is FileDialog.Props -> PropsDialog(d.item) { dialog = null }
        is FileDialog.Sort -> SortDialog { dialog = null }
        is FileDialog.ItemMenu -> ItemMenuDialog(
            item = d.item,
            state = state,
            nav = nav,
            onDialog = { dialog = it },
            onClose = { dialog = null },
            onAction = { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                nav.toast(msg)
            },
            scope = scope
        )
    }
}

@Composable
private fun PaneTabs(state: FileBrowserState) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.panes.forEachIndexed { i, pane ->
            val selected = state.activeIndex == i
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .padding(end = 6.dp)
                    .clickable { state.setActive(i) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (i == 0) "窗口1 · ${pane.title}" else "窗口${i + 1} · ${pane.title}",
                        fontSize = 12.sp,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = if (state.panes.size > 1) 2.dp else 10.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.panes.size > 1) {
                        Text(
                            text = "×",
                            fontSize = 14.sp,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { state.closePane(i) }
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
        TextButton(onClick = { state.toggleDualPane() }) {
            Text(if (state.panes.size > 1) "单窗" else "双窗", fontSize = 12.sp)
        }
        TextButton(onClick = { state.addPane("/sdcard") }) { Text("+窗口", fontSize = 12.sp) }
    }
}

@Composable
private fun PaneBox(
    state: FileBrowserState,
    pane: PaneState,
    index: Int,
    nav: FileNav,
    onDialog: (FileDialog) -> Unit,
    onLongPress: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(pane.path, refreshKey, AppPrefs.showHidden, AppPrefs.sortMode, AppPrefs.sortOrder) {
        loading = true
        withContext(Dispatchers.IO) { pane.refresh() }
        loading = false
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { pane.up() }) { Text("↑") }
            Text(
                text = pane.path,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { searching = !searching }) { Text("搜", fontSize = 12.sp) }
            TextButton(onClick = { refreshKey++ }) { Text("⟳") }
            TextButton(onClick = { onDialog(FileDialog.Sort) }) { Text("排序", fontSize = 12.sp) }
            TextButton(onClick = { onDialog(newFolderDialog(pane, nav)) }) { Text("新建", fontSize = 12.sp) }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileRepo.breadcrumbs(pane.path).forEach { crumb ->
                Text(
                    text = crumb.first,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { pane.open(crumb.second) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                Text(">", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
        }

        if (searching) {
            OutlinedTextField(
                value = pane.keyword,
                onValueChange = { pane.updateKeyword(it) },
                label = { Text("在当前目录搜索") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        pane.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        val list = pane.visibleItems()
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            if (list.isEmpty() && !loading) {
                item { Text("（空目录）", fontSize = 12.sp, modifier = Modifier.padding(12.dp)) }
            }
            items(list, key = { it.path }) { item ->
                FileRow(
                    item = item,
                    selected = pane.isSelected(item),
                    selecting = pane.selected.isNotEmpty(),
                    onToggle = { pane.toggle(item) },
                    onClick = {
                        if (pane.selected.isNotEmpty()) {
                            pane.toggle(item)
                        } else if (item.isDir) {
                            pane.open(item.path)
                        } else {
                            openFile(context, item, nav)
                        }
                    },
                    onLongClick = { onLongPress(item) },
                    onText = { pane.open(item.path) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: FileItem,
    selected: Boolean,
    selecting: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onText: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KindBadge(item)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(item.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle(item),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        } else if (item.isDir) {
            Text(
                text = ">",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .clickable { onText() }
                    .padding(horizontal = 10.dp)
            )
        }
    }
}

private fun subtitle(item: FileItem): String =
    if (item.isDir) "文件夹 · ${Format.time(item.lastModified)}"
    else "${Format.size(item.size)} · ${Format.time(item.lastModified)}"

@Composable
private fun KindBadge(item: FileItem) {
    val color = kindColor(item.kind)
    Box(
        Modifier
            .size(36.dp)
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (item.isDir) "DIR" else item.ext.uppercase().take(4).ifEmpty { "FILE" },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private fun kindColor(kind: FileKind): Color = when (kind) {
    FileKind.FOLDER -> KindFolder
    FileKind.IMAGE -> KindImage
    FileKind.VIDEO -> KindVideo
    FileKind.AUDIO -> KindAudio
    FileKind.APK -> KindApk
    FileKind.ARCHIVE -> KindArchive
    FileKind.DEX -> KindDex
    FileKind.CODE -> KindCode
    FileKind.TEXT -> KindText
    FileKind.PDF -> KindPdf
    else -> KindOther
}

@Composable
private fun BottomBar(state: FileBrowserState, nav: FileNav, onDialog: (FileDialog) -> Unit) {
    val pane = state.active
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pane.selected.isNotEmpty()) {
                Text("已选 ${pane.selected.size}", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
                TextButton(onClick = {
                    state.setClipboard(pane.selected.toList(), false)
                    pane.clearSelection()
                    nav.toast("已复制到剪贴板")
                }) { Text("复制", fontSize = 12.sp) }
                TextButton(onClick = {
                    state.setClipboard(pane.selected.toList(), true)
                    pane.clearSelection()
                    nav.toast("已剪切")
                }) { Text("剪切", fontSize = 12.sp) }
                if (state.panes.size > 1) {
                    TextButton(onClick = {
                        val other = state.other ?: return@TextButton
                        val paths = pane.selected.toList()
                        pane.clearSelection()
                        scope.launch {
                            withContext(Dispatchers.IO) { FileOps.copy(paths, other.path) }
                            state.panes.forEach { it.refresh() }
                        }
                        nav.toast("已复制到 ${other.title}")
                    }) { Text("复制到另一窗", fontSize = 12.sp) }
                }
                TextButton(onClick = {
                    val paths = pane.selected.toList()
                    onDialog(
                        FileDialog.Confirm("删除", "确认删除 ${paths.size} 项？不可恢复。") {
                            pane.clearSelection()
                            scope.launch {
                                withContext(Dispatchers.IO) { FileOps.delete(paths) }
                                state.panes.forEach { it.refresh() }
                            }
                        }
                    )
                }) { Text("删除", fontSize = 12.sp) }
                TextButton(onClick = { pane.selectAll() }) { Text("全选", fontSize = 12.sp) }
                TextButton(onClick = { pane.clearSelection() }) { Text("取消", fontSize = 12.sp) }
            } else if (state.clipboard.isNotEmpty()) {
                Text("剪贴板 ${state.clipboard.size} 项（${if (state.clipboardCut) "剪切" else "复制"}）", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
                TextButton(onClick = {
                    val items = state.clipboard.toList()
                    val cut = state.clipboardCut
                    state.clearClipboard()
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            if (cut) FileOps.move(items, pane.path) else FileOps.copy(items, pane.path)
                        }
                        state.panes.forEach { it.refresh() }
                    }
                    nav.toast(if (cut) "移动中…" else "复制中…")
                }) { Text("粘贴到此处", fontSize = 12.sp) }
                TextButton(onClick = { state.clearClipboard() }) { Text("清空", fontSize = 12.sp) }
            } else {
                Text(
                    "长按文件弹出菜单 · 「双窗」可并排操作并直接互传",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun InputDialog(d: FileDialog.Input, onClose: () -> Unit) {
    var text by remember { mutableStateOf(d.initial) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(d.title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("名称") }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val v = text.trim()
                if (v.isNotEmpty()) d.onConfirm(v)
                onClose()
            }) { Text(d.confirmText) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("取消") } }
    )
}

@Composable
private fun PropsDialog(item: FileItem, onClose: () -> Unit) {
    var sizeText by remember { mutableStateOf(if (item.isDir) "计算中…" else Format.size(item.size)) }
    LaunchedEffect(item.path) {
        if (item.isDir) {
            sizeText = withContext(Dispatchers.IO) { Format.size(FileRepo.sizeOf(item.path)) }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("属性") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                InfoLine("名称", item.name)
                InfoLine("路径", item.path)
                InfoLine("类型", if (item.isDir) "文件夹" else "${item.kind.name}${if (item.ext.isEmpty()) "" else " (.${item.ext})"}")
                InfoLine("大小", sizeText)
                InfoLine("修改时间", Format.time(item.lastModified))
                InfoLine("可读/可写", "${item.readable} / ${item.writable}")
                InfoLine("子项数", if (item.isDir) FileRepo.countChildren(item.path).toString() else "-")
                InfoLine("Symlink", item.isSymlink.toString())
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } }
    )
}

@Composable
private fun InfoLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(k, fontSize = 12.sp, modifier = Modifier.width(76.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SortDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("排序与显示") },
        text = {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(SortMode.values().toList(), key = { it.name }) { m ->
                    TextButton(onClick = { AppPrefs.setSort(m, AppPrefs.sortOrder) }) {
                        Text(if (AppPrefs.sortMode == m) "● ${m.label}" else "○ ${m.label}")
                    }
                }
                items(SortOrder.values().toList(), key = { it.name }) { o ->
                    TextButton(onClick = { AppPrefs.setSort(AppPrefs.sortMode, o) }) {
                        Text(if (AppPrefs.sortOrder == o) "● ${o.label}" else "○ ${o.label}")
                    }
                }
                item {
                    TextButton(onClick = { AppPrefs.updateShowHidden(!AppPrefs.showHidden) }) {
                        Text("隐藏文件：${if (AppPrefs.showHidden) "显示" else "隐藏"}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("完成") } }
    )
}

@Composable
private fun ItemMenuDialog(
    item: FileItem,
    state: FileBrowserState,
    nav: FileNav,
    onDialog: (FileDialog) -> Unit,
    onClose: () -> Unit,
    onAction: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    val pane = state.active
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(Modifier.fillMaxWidth()) {
                item {
                    MenuRow("打开") {
                        if (item.isDir) pane.open(item.path) else openFile(context, item, nav)
                        onClose()
                    }
                }
                item { MenuRow("十六进制查看") { onClose(); nav.openHex(item.path) } }
                if (item.isDex || item.isApk || item.isJar) {
                    item { MenuRow("Dex / Apk 工具") { onClose(); nav.openDex(item.path) } }
                }
                if (Format.isTextLikely(item.name) && !item.isDir) {
                    item { MenuRow("文本编辑") { onClose(); nav.openText(item.path) } }
                }
                item {
                    MenuRow("复制") {
                        state.setClipboard(listOf(item.path), false)
                        onClose()
                        onAction("已复制：${item.name}")
                    }
                }
                item {
                    MenuRow("剪切") {
                        state.setClipboard(listOf(item.path), true)
                        onClose()
                        onAction("已剪切：${item.name}")
                    }
                }
                item {
                    MenuRow("重命名") {
                        onDialog(
                            FileDialog.Input("重命名", item.name, "确定") { newName ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { FileOps.rename(item.path, newName) }
                                    state.panes.forEach { it.refresh() }
                                }
                            }
                        )
                    }
                }
                item {
                    MenuRow("删除") {
                        onDialog(
                            FileDialog.Confirm("删除", "确认删除「${item.name}」？") {
                                scope.launch {
                                    withContext(Dispatchers.IO) { FileOps.delete(listOf(item.path)) }
                                    state.panes.forEach { it.refresh() }
                                }
                            }
                        )
                    }
                }
                item {
                    MenuRow("压缩为 ZIP") {
                        val out = FileOps.uniqueTarget(File(item.path).parentFile?.absolutePath ?: "/sdcard", item.name + ".zip")
                        onClose()
                        onAction("压缩中：${out.name}")
                        com.zhiwei.filemanager.core.task.AppScope.io.launch {
                            ArchiveTool.compressToZip(listOf(item.path), out.absolutePath)
                            state.panes.forEach { it.refresh() }
                        }
                    }
                }
                if (!item.isDir && ArchiveTool.canExtract(item.name)) {
                    item {
                        MenuRow("解压到当前目录") {
                            val dest = File(item.path).parentFile?.absolutePath ?: "/sdcard"
                            onClose()
                            onAction("解压中：${item.name}")
                            com.zhiwei.filemanager.core.task.AppScope.io.launch {
                                ArchiveTool.extract(item.path, dest)
                                state.panes.forEach { it.refresh() }
                            }
                        }
                    }
                }
                if (state.panes.size > 1 && !item.isDir) {
                    item {
                        MenuRow("复制到另一窗口") {
                            val other = state.other
                            onClose()
                            if (other != null) {
                                scope.launch {
                                    withContext(Dispatchers.IO) { FileOps.copy(listOf(item.path), other.path) }
                                    state.panes.forEach { it.refresh() }
                                    onAction("已复制到 ${other.title}")
                                }
                            }
                        }
                    }
                }
                item { MenuRow("分享") { onClose(); shareFile(context, item, nav) } }
                item { MenuRow("属性") { onDialog(FileDialog.Props(item)) } }
                item {
                    MenuRow("复制路径") {
                        state.setClipboard(listOf(item.path), false)
                        onClose()
                        onAction(item.path)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } }
    )
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
    }
}

private fun newFolderDialog(pane: PaneState, nav: FileNav): FileDialog = FileDialog.Input("新建文件夹", "新建文件夹", "创建") { name ->
    if (FileOps.newFolder(pane.path, name)) {
        pane.refresh()
        nav.toast("已创建：$name")
    } else {
        nav.toast("创建失败（无写入权限？）")
    }
}

private fun openFile(context: android.content.Context, item: FileItem, nav: FileNav) {
    when {
        item.isDex -> nav.openDex(item.path)
        Format.isTextLikely(item.name) && item.size < 8L * 1024L * 1024L -> nav.openText(item.path)
        !item.isDir && ArchiveTool.canExtract(item.name) -> nav.toast("压缩包：长按 → 解压到当前目录")
        else -> openExternal(context, item, nav)
    }
}

private fun mimeOf(name: String): String {
    val ext = Format.extOf(name)
    val fromMap = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    return fromMap ?: "application/octet-stream"
}

private fun openExternal(context: android.content.Context, item: FileItem, nav: FileNav) {
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(item.path))
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeOf(item.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (t: Throwable) {
        nav.toast("无法打开：${t.message}")
    }
}

private fun shareFile(context: android.content.Context, item: FileItem, nav: FileNav) {
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(item.path))
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mimeOf(item.name))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent, "分享 ${item.name}"))
    } catch (t: Throwable) {
        nav.toast("分享失败：${t.message}")
    }
}
