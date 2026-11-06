package com.zhiwei.filemanager.ui.files

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.zhiwei.filemanager.core.fs.FileOps
import com.zhiwei.filemanager.core.fs.FileRepo
import com.zhiwei.filemanager.core.fs.RecycleBin
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 导航回调（由 AppRoot 注入）。 */
data class FileNav(
    val openText: (String) -> Unit,
    val openHex: (String) -> Unit,
    val openDex: (String) -> Unit,
    val toast: (String) -> Unit,
    /** 多选批量重命名。 */
    val openRename: (List<String>) -> Unit = {},
    /** 左右两侧路径对比（可为文件或目录）。 */
    val openCompare: (String, String) -> Unit = { _, _ -> },
    /** 打开文本批量合并（带默认目录）。 */
    val openMerge: (String) -> Unit = {},
    /** 打开回收站。 */
    val openRecycle: () -> Unit = {},
    /** 通用预览器：图片 / 视频 / 音频 / 字体 / APK / 归档 / 网页 / 文本 / Hex 兜底。 */
    val openPreview: (String) -> Unit = {},
    /** APK 反编译工具箱（一键解包 / 提 dex / 列 so）。 */
    val openApkTool: (String) -> Unit = {}
)

internal sealed interface FileDialog {
    data class Input(val title: String, val initial: String, val confirmText: String, val onConfirm: (String) -> Unit) : FileDialog
    data class Confirm(val title: String, val message: String, val onConfirm: () -> Unit) : FileDialog
    data class Props(val item: FileItem) : FileDialog
    data class ItemMenu(val item: FileItem) : FileDialog
    /** 窗格顶部「⋮」菜单：作用域是该窗口。 */
    data class PaneMenu(val index: Int) : FileDialog
    data object Sort : FileDialog
}

@Composable
fun FileBrowserScreen(state: FileBrowserState, nav: FileNav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<FileDialog?>(null) }
    var bookmarkOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    // 「搜索文件夹」面板的目标窗口：点顶栏目录名或窗格目录名时置位（null = 关闭）
    var folderSearchPane by remember { mutableStateOf<PaneState?>(null) }
    // 受限目录的免提权浏览面板：非空即展示（走 SAF 授权，不用 Shizuku / ROOT）
    var safPath by remember { mutableStateOf<String?>(null) }

    // 顶栏放大镜 → 搜索「文件」；点击顶栏目录标题 → 搜索「文件夹」。
    LaunchedEffect(state.searchFileRequest) {
        if (state.searchFileRequest) {
            searchOpen = true
            state.searchFileRequest = false
        }
    }
    LaunchedEffect(state.searchFolderRequest) {
        if (state.searchFolderRequest) {
            folderSearchPane = state.active
            state.searchFolderRequest = false
        }
    }
    // 折叠栏 →「书签与最近访问」
    LaunchedEffect(state.bookmarkRequest) {
        if (state.bookmarkRequest) {
            bookmarkOpen = true
            state.bookmarkRequest = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 窗格上方的快捷目录栏已整体删除：它占掉两行首屏高度，而用户绝大多数时间就待在
        // 内部存储，属于纯占地。存储卷 / 常用目录 / 知微浏览器入口统一收进左侧折叠栏。
        if (state.panes.size > 1) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val spanPx = (if (AppPrefs.splitLeftRight) constraints.maxWidth else constraints.maxHeight).toFloat().coerceAtLeast(1f)
                if (AppPrefs.splitLeftRight) {
                    Row(Modifier.fillMaxSize()) {
                        state.panes.forEachIndexed { index, pane ->
                            PaneBox(
                                state = state,
                                pane = pane,
                                index = index,
                                active = state.activeIndex == index,
                                nav = nav,
                                onDialog = { dialog = it },
                                onLongPress = { dialog = FileDialog.ItemMenu(it) },
                                onSearchFolder = { folderSearchPane = pane },
                                onSafBrowse = { safPath = it },
                                modifier = Modifier.weight(paneWeight(state, index)).fillMaxHeight()
                            )
                            if (index < state.panes.size - 1) {
                                SplitHandle(vertical = true) { delta ->
                                    AppPrefs.updateSplitFraction(AppPrefs.splitFraction + delta / spanPx)
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        state.panes.forEachIndexed { index, pane ->
                            PaneBox(
                                state = state,
                                pane = pane,
                                index = index,
                                active = state.activeIndex == index,
                                nav = nav,
                                onDialog = { dialog = it },
                                onLongPress = { dialog = FileDialog.ItemMenu(it) },
                                onSearchFolder = { folderSearchPane = pane },
                                onSafBrowse = { safPath = it },
                                modifier = Modifier.weight(paneWeight(state, index)).fillMaxWidth()
                            )
                            if (index < state.panes.size - 1) {
                                SplitHandle(vertical = false) { delta ->
                                    AppPrefs.updateSplitFraction(AppPrefs.splitFraction + delta / spanPx)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            PaneBox(
                state = state,
                pane = state.panes[0],
                index = 0,
                active = true,
                nav = nav,
                onDialog = { dialog = it },
                onLongPress = { dialog = FileDialog.ItemMenu(it) },
                onSearchFolder = { folderSearchPane = state.panes[0] },
                onSafBrowse = { safPath = it },
                modifier = Modifier.weight(1f)
            )
        }
        // 底部快捷操作栏已整体移除：所有动作收进各窗口顶部的「⋮」菜单，不再占屏幕空间
    }

    if (bookmarkOpen) {
        BookmarkSheet(
            state = state,
            onOpen = { path -> state.active.open(path) },
            onClose = { bookmarkOpen = false }
        )
    }
    if (searchOpen) {
        SearchSheet(
            pane = state.active,
            onOpen = { hit ->
                val parent = java.io.File(hit.path).parent
                if (parent != null) state.active.open(parent)
                nav.toast("已定位到 " + (parent ?: hit.path))
            },
            onClose = { searchOpen = false }
        )
    }

    // 搜索「文件夹」：只列目录，点一条即把该窗口直接下钻过去
    folderSearchPane?.let { target ->
        FolderSearchSheet(
            pane = target,
            onOpen = { dir ->
                target.open(dir)
                folderSearchPane = null
                nav.toast("已定位到 $dir")
            },
            onClose = { folderSearchPane = null }
        )
    }
    // 受限目录（Android/data 等）：走系统 SAF 授权浏览，不需要 Shizuku / ROOT
    safPath?.let { path ->
        SafBrowseSheet(initialPath = path, nav = nav, onClose = { safPath = null })
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
        is FileDialog.PaneMenu -> PaneMenuDialog(
            state = state,
            index = d.index,
            nav = nav,
            onDialog = { dialog = it },
            onClose = { dialog = null },
            onBookmarks = { dialog = null; bookmarkOpen = true },
            onRecycle = { dialog = null; nav.openRecycle() },
            scope = scope
        )
    }

    // 安装包速览弹窗：点 APK、或动作里点「安装包信息」，都落到这里，不再切整页。
    state.apkQuickRequest?.let { apk ->
        ApkQuickDialog(
            item = apk,
            state = state,
            nav = nav,
            onDialog = { dialog = it },
            onClose = { state.apkQuickRequest = null },
            onAction = { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                nav.toast(msg)
            },
            scope = scope
        )
    }
}

/**
 * 窗格顶部「⋮」菜单：**该窗口专有**的全部动作入口。
 *
 * 设计取舍：双窗口上方/下方的快捷按钮已全部撤掉（占空间，且与 MT / NP 的观感不符），
 * 动作集中到本菜单，作用域天然限定在本窗口，不会误操作另一个窗口。
 */
@Composable
private fun PaneMenuDialog(
    state: FileBrowserState,
    index: Int,
    nav: FileNav,
    onDialog: (FileDialog) -> Unit,
    onClose: () -> Unit,
    onBookmarks: () -> Unit,
    onRecycle: () -> Unit,
    scope: CoroutineScope
) {
    val pane = state.panes.getOrNull(index) ?: state.active
    val other = state.panes.firstOrNull { it !== pane }
    val selecting = pane.selected.isNotEmpty()

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                text = "窗口菜单 · " + pane.title,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            LazyColumn(Modifier.fillMaxWidth()) {
                if (selecting) {
                    // 选中态：批量动作
                    item { MenuRow("取消选择（已选 ${pane.selected.size} 项）") { pane.clearSelection(); onClose() } }
                    item {
                        MenuRow("复制 ${pane.selected.size} 项") {
                            state.setClipboard(pane.selected.toList(), false)
                            pane.clearSelection()
                            onClose()
                            nav.toast("已复制到剪贴板")
                        }
                    }
                    item {
                        MenuRow("剪切 ${pane.selected.size} 项") {
                            state.setClipboard(pane.selected.toList(), true)
                            pane.clearSelection()
                            onClose()
                            nav.toast("已剪切")
                        }
                    }
                    item { MenuRow("批量重命名") { onClose(); nav.openRename(pane.selected.toList()) } }
                    item {
                        MenuRow("压缩为 ZIP") {
                            val paths = pane.selected.toList()
                            val out = FileOps.uniqueTarget(pane.path, "archive_${System.currentTimeMillis() / 1000}.zip")
                            pane.clearSelection()
                            onClose()
                            nav.toast("压缩中：${out.name}")
                            scope.launch {
                                withContext(Dispatchers.IO) { ArchiveTool.compressToZip(paths, out.absolutePath) }
                                state.panes.forEach { it.refresh() }
                            }
                        }
                    }
                    item {
                        MenuRow("对比") {
                            val paths = pane.selected.toList()
                            val first = paths.firstOrNull()
                            val second = paths.drop(1).firstOrNull() ?: other?.path
                            onClose()
                            if (first != null && second != null) nav.openCompare(first, second)
                            else nav.toast("对比需要两项，或开双窗")
                        }
                    }
                    if (other != null) {
                        item {
                            MenuRow("复制到另一窗口（${other.title}）") {
                                val paths = pane.selected.toList()
                                pane.clearSelection()
                                onClose()
                                scope.launch {
                                    withContext(Dispatchers.IO) { FileOps.copy(paths, other.path) }
                                    state.panes.forEach { it.refresh() }
                                }
                                nav.toast("已复制到 ${other.title}")
                            }
                        }
                    }
                    item {
                        MenuRow("删除 ${pane.selected.size} 项") {
                            val paths = pane.selected.toList()
                            val toRecycle = AppPrefs.recycleEnabled
                            onDialog(
                                FileDialog.Confirm(
                                    "删除",
                                    if (toRecycle) "将 ${paths.size} 项移入回收站？" else "永久删除 ${paths.size} 项？不可恢复。"
                                ) { deleteSelection(state, pane, paths, scope, nav) }
                            )
                        }
                    }
                } else {
                    // 浏览态：常用入口
                    item { MenuRow("新建文件夹") { onDialog(newFolderDialog(pane, nav)) } }
                    item { MenuRow("新建文件") { onDialog(newFileDialog(pane, nav)) } }
                    if (state.clipboard.isNotEmpty()) {
                        item {
                            MenuRow("粘贴 ${state.clipboard.size} 项到此处") {
                                onClose()
                                pasteInto(state, pane, scope, nav)
                            }
                        }
                    }
                    // 搜索统一收归顶栏（放大镜搜文件、点目录名搜文件夹），窗格里不再放重复入口
                    item { MenuRow("全选") { pane.selectAll(); onClose() } }
                    item { MenuRow("收藏与最近访问") { onBookmarks() } }
                    if (other != null) {
                        item { MenuRow("与另一窗口对比目录") { onClose(); nav.openCompare(pane.path, other.path) } }
                    }
                    item { MenuRow("合并文本（当前目录）") { onClose(); nav.openMerge(pane.path) } }
                    item { MenuRow("回收站") { onRecycle() } }
                }
                item { MenuRow("刷新本窗口") { onClose(); pane.refresh() } }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭", fontSize = 12.sp) } }
    )
}

@Composable
private fun PaneBox(
    state: FileBrowserState,
    pane: PaneState,
    index: Int,
    active: Boolean,
    nav: FileNav,
    onDialog: (FileDialog) -> Unit,
    onLongPress: (FileItem) -> Unit,
    /** 点击窗格目录名 = 在本窗口搜索文件夹。 */
    onSearchFolder: () -> Unit,
    /** 受限目录（Android/data 等）：走系统 SAF 授权浏览，不需要 Shizuku / ROOT。 */
    onSafBrowse: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    val tag = if (state.panes.size > 1) (if (index == 0) "左" else "右") else "窗口"

    LaunchedEffect(pane.path, refreshKey, AppPrefs.showHidden, AppPrefs.sortMode, AppPrefs.sortOrder) {
        loading = true
        withContext(Dispatchers.IO) { pane.refresh() }
        loading = false
    }

    // 手势：在本栏内向下滑 → 把操作焦点切到本栏（左栏下滑切左栏，右栏下滑切右栏）
    val swipeToActivate = Modifier.pointerInput(index) {
        val threshold = 56.dp.toPx()
        val headerZone = 110.dp.toPx()
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val fromHeader = down.position.y < headerZone
                var acc = 0f
                var fired = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    acc += change.positionChange().y
                    if (acc < 0f) break
                    val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                    if (!fired && acc > threshold && (fromHeader || atTop)) {
                        fired = true
                        state.setActive(index)
                        nav.toast("已切换到${tag}窗口")
                    }
                    if (!change.pressed) break
                }
            }
        }
    }

    // 列表数据先算好：顶栏要显示「文件夹数 · 文件数 · 合计体积」，底下那条独立状态栏已删除
    val list = pane.visibleItems()
    val dirCount = list.count { it.isDir }
    val counts = buildString {
        append(dirCount).append(" 个文件夹 · ").append(list.size - dirCount).append(" 个文件")
        if (list.isNotEmpty()) append(" · ").append(Format.size(list.sumOf { it.size }))
    }

    // 滑动多选的锚点：第一次滑到某行记住它，再滑到另一行时把两行之间的所有项一并选上
    var swipeAnchor by remember(pane.path) { mutableStateOf(-1) }

    fun swipeSelect(rowIndex: Int) {
        if (list.isEmpty() || rowIndex !in list.indices) return
        val anchor = swipeAnchor
        if (anchor < 0 || anchor == rowIndex) {
            pane.toggle(list[rowIndex])
        } else {
            for (i in minOf(anchor, rowIndex)..maxOf(anchor, rowIndex)) {
                if (i in list.indices) pane.select(list[i])
            }
        }
        swipeAnchor = rowIndex
    }

    Column(
        modifier
            .fillMaxWidth()
            .border(
                width = if (active) 1.dp else 0.dp,
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else Color.Transparent
            )
            .then(swipeToActivate)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                .padding(start = 2.dp, end = 6.dp, top = 1.dp, bottom = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 三个点：本窗口的操作菜单（所有动作都收在这里）
            HeaderGlyph("⋮") { onDialog(FileDialog.PaneMenu(index)) }
            // 顶部不再放「返回上一层」快捷键：列表第一行本身就是「…」文件夹项（见 UpRow）
            // 目录名：点它 = 搜索文件夹（在本窗口所在目录里按名字找目录，一键下钻）
            Text(
                text = pane.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable { onSearchFolder() }
                    .padding(horizontal = 2.dp, vertical = 4.dp)
            )
            // 目录计数上移到顶栏；下面那条独立状态栏已删除
            Text(
                text = counts,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
            )
            if (pane.selected.isNotEmpty()) {
                Text(
                    text = "已选 " + pane.selected.size,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            Text(
                text = tag,
                fontSize = 10.sp,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
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

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        pane.error?.let { msg ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)) {
                Text(text = msg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onSafBrowse(pane.path) }) {
                        Text("免提权浏览", fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        runCatching {
                            com.zhiwei.filemanager.core.util.Permissions.openAllFilesSettings(context)
                        }
                    }) {
                        Text("授予全部文件访问", fontSize = 11.sp)
                    }
                }
            }
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
            // 首行「返回上一层」：图标与普通文件夹同款，内容换成三个点
            if (pane.keyword.isBlank()) {
                val parent = FileRepo.parentOf(pane.path)
                if (parent != null) {
                    item(key = "__up__") {
                        UpRow(parent = parent) {
                            state.setActive(index)
                            pane.up()
                        }
                    }
                }
            }
            if (list.isEmpty() && !loading) {
                item { EmptyHint(pane.path, pane.keyword) }
            }
            itemsIndexed(list, key = { _, it -> it.path }) { rowIndex, item ->
                FileRow(
                    item = item,
                    selected = pane.isSelected(item),
                    selecting = pane.selected.isNotEmpty(),
                    onToggle = { pane.toggle(item) },
                    onClick = {
                        state.setActive(index)
                        if (pane.selected.isNotEmpty()) {
                            // 多选态下点一下 = 只取消/恢复这一项
                            pane.toggle(item)
                        } else if (item.isDir) {
                            pane.open(item.path)
                        } else if (item.isApk) {
                            // 安装包不走整页预览：点开就是速览弹窗（安装 / 查看 / 更多）
                            state.apkQuickRequest = item
                        } else {
                            openFile(context, item, nav)
                        }
                    },
                    onLongClick = {
                        state.setActive(index)
                        onLongPress(item)
                    },
                    onText = { pane.open(item.path) },
                    // 左右滑动进入多选：滑一行选中单项，再滑另一行则把两行之间整段一起选上
                    modifier = Modifier.pointerInput(item.path) {
                        val threshold = 30.dp.toPx()
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragCancel = { total = 0f },
                            onDragEnd = {
                                if (kotlin.math.abs(total) >= threshold) {
                                    state.setActive(index)
                                    swipeSelect(rowIndex)
                                }
                                total = 0f
                            },
                            onHorizontalDrag = { _, dragAmount -> total += dragAmount }
                        )
                    }
                )
            }
        }
    }
}

/** 双窗口的权重：两栏时按分割比例分配，多栏时等分。 */
private fun paneWeight(state: FileBrowserState, index: Int): Float {
    if (state.panes.size != 2) return 1f
    val f = AppPrefs.splitFraction
    return if (index == 0) f else 1f - f
}

/** 窗格标题条上的紧凑图标按钮（比 TextButton 省一半横向空间）。 */
@Composable
private fun HeaderGlyph(glyph: String, onClick: () -> Unit) {
    Text(
        text = glyph,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

/** 双窗口之间的可拖动分隔条：左右布局时竖向，上下布局时横向。 */
@Composable
private fun SplitHandle(vertical: Boolean, onDrag: (Float) -> Unit) {
    val barColor = MaterialTheme.colorScheme.outlineVariant
    if (vertical) {
        Box(
            Modifier
                .width(12.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectDragGestures { _, drag -> onDrag(drag.x) }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.width(2.dp).height(30.dp).background(barColor))
        }
    } else {
        Box(
            Modifier
                .height(12.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectDragGestures { _, drag -> onDrag(drag.y) }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.height(2.dp).width(30.dp).background(barColor))
        }
    }
}

@Composable
private fun EmptyHint(path: String, keyword: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("◌", fontSize = 26.sp, color = MaterialTheme.colorScheme.outline)
        Text(
            if (keyword.isBlank()) "当前目录为空" else "没有匹配「$keyword」的项目",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            path,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * 列表首行「返回上一层」。
 *
 * 长得和普通文件夹项完全一致（同一个 36dp 圆角色块 + 两行文字），
 * 只是色块里放的是「…」——用户一眼就知道「这是回到上面那一层」，
 * 不需要再去顶部找一个小图标按钮。
 */
@Composable
private fun UpRow(parent: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(KindFolder.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("…", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = KindFolder)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text("返回上一层", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                parent,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
    onText: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 选中态用动画过渡，别硬切：底色渐变 + 勾选框缩放
    val rowColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
        label = "rowColor"
    )
    val checkScale by animateFloatAsState(targetValue = if (selected) 1f else 0.85f, label = "checkScale")
    Row(
        modifier
            .fillMaxWidth()
            .background(rowColor)
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
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.scale(checkScale)
            )
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
    var showMore by remember(item.path) { mutableStateOf(false) }

    // 动作按类型产出（安装包先给安装包的工具、压缩包先给压缩包的工具），这里只管排布：
    // 双列、最多 6 个动作 + 1 个「更多工具…」 = 7 个按钮，不霸占整屏。
    val actions = itemActions(context, item, state, state.active, nav, onDialog, onClose, onAction, scope)
    val head = actions.take(6)
    val tail = actions.drop(6)

    if (showMore) {
        MoreActionsDialog(actions = tail, onClose = onClose)
        return
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(item.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        text = {
            ActionGrid(
                actions = head,
                onMore = if (tail.isEmpty()) null else { { showMore = true } }
            )
        },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭", fontSize = 12.sp) } }
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

private fun newFileDialog(pane: PaneState, nav: FileNav): FileDialog = FileDialog.Input("新建文件", "新建文件.txt", "创建") { name ->
    if (FileOps.newFile(pane.path, name)) {
        pane.refresh()
        nav.toast("已创建：$name")
    } else {
        nav.toast("创建失败（无写入权限？）")
    }
}

/** 粘贴剪贴板内容到指定窗口（跨窗口直接互传的底座）。 */
private fun pasteInto(state: FileBrowserState, pane: PaneState, scope: CoroutineScope, nav: FileNav) {
    val items = state.clipboard.toList()
    if (items.isEmpty()) return
    val cut = state.clipboardCut
    state.clearClipboard()
    scope.launch {
        withContext(Dispatchers.IO) {
            if (cut) FileOps.move(items, pane.path) else FileOps.copy(items, pane.path)
        }
        state.panes.forEach { it.refresh() }
    }
    nav.toast(if (cut) "移动中：${items.size} 项" else "复制中：${items.size} 项")
}

/** 删除：按偏好移入回收站，否则直接销毁。 */
internal fun deleteSelection(
    state: FileBrowserState,
    pane: PaneState,
    paths: List<String>,
    scope: CoroutineScope,
    nav: FileNav
) {
    if (paths.isEmpty()) return
    pane.clearSelection()
    val toRecycle = AppPrefs.recycleEnabled
    scope.launch {
        withContext(Dispatchers.IO) {
            if (toRecycle) RecycleBin.moveIn(paths) else FileOps.delete(paths)
        }
        state.panes.forEach { it.refresh() }
    }
    nav.toast(if (toRecycle) "已移入回收站：${paths.size} 项" else "已删除：${paths.size} 项")
}

private fun openFile(context: android.content.Context, item: FileItem, nav: FileNav) {
    when {
        item.isDex -> nav.openDex(item.path)
        Format.isTextLikely(item.name) && item.size < 8L * 1024L * 1024L -> nav.openText(item.path)
        // 能自己渲染的一律走内置预览器，不再无脑甩给外部应用
        isPreviewable(item.name) -> nav.openPreview(item.path)
        !item.isDir && ArchiveTool.canExtract(item.name) -> nav.toast("压缩包：长按 → 解压到当前目录")
        else -> openExternal(context, item, nav)
    }
}

/** 内置预览器覆盖的类型：其余交给系统「外部打开」。 */
internal fun isPreviewable(name: String): Boolean {
    val ext = Format.extOf(name)
    if (ext == "html" || ext == "htm" || ext == "mhtml") return true
    return when (Format.kindOf(name, false)) {
        FileKind.IMAGE, FileKind.VIDEO, FileKind.AUDIO, FileKind.FONT,
        FileKind.APK, FileKind.ARCHIVE, FileKind.PDF, FileKind.DOC -> true
        else -> false
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

internal fun shareFile(context: android.content.Context, item: FileItem, nav: FileNav) {
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
