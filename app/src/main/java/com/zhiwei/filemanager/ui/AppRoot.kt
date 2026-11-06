package com.zhiwei.filemanager.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
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
import com.zhiwei.filemanager.core.model.SortMode
import com.zhiwei.filemanager.core.model.SortOrder
import com.zhiwei.filemanager.core.prefs.AppPrefs
import com.zhiwei.filemanager.core.update.UpdateChecker
import com.zhiwei.filemanager.core.update.UpdateInfo
import com.zhiwei.filemanager.core.util.AppInfo
import com.zhiwei.filemanager.tool.ToolRegistry
import com.zhiwei.filemanager.ui.files.FileBrowserScreen
import com.zhiwei.filemanager.ui.files.FileBrowserState
import com.zhiwei.filemanager.ui.files.FileNav
import com.zhiwei.filemanager.ui.files.RecycleBinScreen
import com.zhiwei.filemanager.ui.preview.PreviewScreen
import com.zhiwei.filemanager.ui.settings.SettingsScreen
import com.zhiwei.filemanager.ui.settings.UpdateNoticeDialog
import com.zhiwei.filemanager.ui.tasks.TaskBoardScreen
import com.zhiwei.filemanager.ui.terminal.TerminalScreen
import com.zhiwei.filemanager.ui.tools.ApkToolScreen
import com.zhiwei.filemanager.ui.tools.BatchRenameScreen
import com.zhiwei.filemanager.ui.tools.CompareScreen
import com.zhiwei.filemanager.ui.tools.DexToolScreen
import com.zhiwei.filemanager.ui.tools.DiffScreen
import com.zhiwei.filemanager.ui.tools.EditorScreen
import com.zhiwei.filemanager.ui.tools.EncodingScreen
import com.zhiwei.filemanager.ui.tools.HexViewerScreen
import com.zhiwei.filemanager.ui.tools.MergeScreen
import com.zhiwei.filemanager.ui.tools.ScriptScreen
import com.zhiwei.filemanager.ui.tools.StringToolsScreen
import com.zhiwei.filemanager.ui.tools.ToolPage
import com.zhiwei.filemanager.ui.tools.ToolboxScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 底部一级导航。 */
private enum class MainTab(val label: String, val glyph: String) {
    FILES("文件", "▤"),
    TOOLS("工具", "◈"),
    TASKS("任务", "◔"),
    SETTINGS("设置", "⚙")
}

/** 二级路由（工具页 / 预览器 / 终端）。 */
private sealed interface Route {
    data class Tool(val page: ToolPage, val path: String) : Route

    /** 通用文件预览器（图片 / 视频 / 音频 / APK / 归档 / 网页 / 文本 …）。 */
    data class Preview(val path: String) : Route

    data object Terminal : Route
    data object RecycleBin : Route
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val state = remember { FileBrowserState() }
    var tab by remember { mutableStateOf(MainTab.FILES) }
    var route by remember { mutableStateOf<Route?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    // 折叠栏（左侧抽屉）：所有「跳转类」入口的唯一归口
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val nav = remember(state) {
        FileNav(
            openText = { route = Route.Tool(ToolPage.EDITOR, it) },
            openHex = { route = Route.Tool(ToolPage.HEX, it) },
            openDex = { route = Route.Tool(ToolPage.DEX, it) },
            toast = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
            openRename = { paths -> route = Route.Tool(ToolPage.BATCH_RENAME, paths.joinToString("\n")) },
            openCompare = { a, b -> route = Route.Tool(ToolPage.COMPARE, a + "\n" + b) },
            openMerge = { dir -> route = Route.Tool(ToolPage.MERGE, dir) },
            openRecycle = { route = Route.RecycleBin },
            openPreview = { route = Route.Preview(it) },
            openApkTool = { route = Route.Tool(ToolPage.APK_TOOL, it) }
        )
    }

    LaunchedEffect(Unit) { state.persistPaths() }

    // 启动时静默检查更新：只有真的存在更新版本才弹窗，静默失败（无网/限流）不打扰用户
    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        val info = withContext(Dispatchers.IO) { UpdateChecker.checkLatest().getOrNull() }
            ?: return@LaunchedEffect
        if (UpdateChecker.isNewer(info.version, AppInfo.VERSION)) pendingUpdate = info
    }

    BackHandler(enabled = route != null) { route = null }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 只在「文件」页允许边缘滑动唤起折叠栏；其它页面横向手势有自己的语义
        gesturesEnabled = route == null && tab == MainTab.FILES,
        drawerContent = {
            ZhiweiDrawer(
                currentPath = state.active.path,
                onOpen = { path ->
                    state.active.open(path)
                    scope.launch { drawerState.close() }
                },
                onBookmarks = {
                    state.bookmarkRequest = true
                    scope.launch { drawerState.close() }
                },
                onRecycle = {
                    route = Route.RecycleBin
                    scope.launch { drawerState.close() }
                },
                onTerminal = {
                    route = Route.Terminal
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    // 折叠栏入口。只在一级页显示：二级页自己有「← 返回」，不需要再多一个汉堡
                    if (route == null) {
                        Text(
                            text = "☰",
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clickable { scope.launch { drawerState.open() } }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                },
                title = {
                    Column(
                        Modifier.clickable {
                            // 顶栏大标题就是「当前目录名」：点它 = 搜索文件夹
                            if (route == null && tab == MainTab.FILES) state.searchFolderRequest = true
                        }
                    ) {
                        Text(
                            text = topTitle(route, tab, state),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = topSubtitle(route, tab, state)
                        if (sub.isNotEmpty()) {
                            Text(
                                text = sub,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (route == null && tab == MainTab.FILES) {
                        // 顶栏只留两个入口：放大镜（搜文件）+ 更多菜单（杂乱按钮全部收进菜单）
                        GlyphAction("🔍") { state.searchFileRequest = true }
                        Box {
                            GlyphAction("⋮") { menuOpen = true }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (state.panes.size > 1) "切换为单窗口" else "切换为双窗口") },
                                    onClick = { menuOpen = false; state.toggleDualPane() }
                                )
                                if (state.panes.size > 1) {
                                    DropdownMenuItem(
                                        text = { Text(if (AppPrefs.splitLeftRight) "双窗改为上下排列" else "双窗改为左右排列") },
                                        onClick = { menuOpen = false; AppPrefs.updateSplitLeftRight(!AppPrefs.splitLeftRight) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("关闭右侧窗口") },
                                        onClick = { menuOpen = false; state.closePane(state.panes.size - 1) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(if (AppPrefs.showHidden) "隐藏系统隐藏文件" else "显示系统隐藏文件") },
                                    onClick = {
                                        menuOpen = false
                                        AppPrefs.updateShowHidden(!AppPrefs.showHidden)
                                        state.panes.forEach { it.refresh() }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("排序方式：${AppPrefs.sortMode.label}") },
                                    onClick = {
                                        val all = SortMode.values()
                                        AppPrefs.setSort(all[(AppPrefs.sortMode.ordinal + 1) % all.size], AppPrefs.sortOrder)
                                        state.panes.forEach { it.refresh() }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("排序方向：${AppPrefs.sortOrder.label}") },
                                    onClick = {
                                        AppPrefs.setSort(
                                            AppPrefs.sortMode,
                                            if (AppPrefs.sortOrder == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
                                        )
                                        state.panes.forEach { it.refresh() }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("刷新两个窗口") },
                                    onClick = { menuOpen = false; state.panes.forEach { it.refresh() } }
                                )
                                DropdownMenuItem(
                                    text = { Text("回收站") },
                                    onClick = { menuOpen = false; route = Route.RecycleBin }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = route == null && tab == t,
                        onClick = {
                            tab = t
                            route = null
                        },
                        icon = { Text(t.glyph, fontSize = 18.sp) },
                        label = { Text(t.label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AnimatedContent(
                targetState = RouteKey(route, tab),
                transitionSpec = {
                    (slideInHorizontally { it / 10 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 10 } + fadeOut())
                },
                label = "root"
            ) { target ->
                val r = target.route
                if (r == null) {
                    when (target.tab) {
                        MainTab.FILES -> FileBrowserScreen(state, nav)
                        MainTab.TOOLS -> ToolboxScreen(onOpen = { route = Route.Tool(it, "") })
                        MainTab.TASKS -> TaskBoardScreen()
                        MainTab.SETTINGS -> SettingsScreen(
                            state = state,
                            onOpenTerminal = { route = Route.Terminal }
                        )
                    }
                } else {
                    RouteHost(r, onBack = { route = null }, onNavigate = { route = it })
                }
            }
        }
    }
    } // 闭合 ModalNavigationDrawer 内容 lambda

    // 检测到新版才出现的更新弹窗：立即更新 / 去官网更新 / 取消更新
    pendingUpdate?.let { info ->
        UpdateNoticeDialog(info = info, onDismiss = { pendingUpdate = null })
    }
} // 闭合 AppRoot

/** 页面切换动画的比较键：路由 + 一级页签。 */
private data class RouteKey(val route: Route?, val tab: MainTab)

@Composable
private fun RouteHost(r: Route, onBack: () -> Unit, onNavigate: (Route) -> Unit) {
    val context = LocalContext.current
    val toast = { msg: String -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text(
                text = when (r) {
                    is Route.Tool -> r.page.desc
                    is Route.Preview -> "内置预览：图片 / 视频 / 音频 / 字体 / APK / 归档 / 网页 / 文本 / Hex"
                    Route.Terminal -> "内置 shell 会话：普通权限 / ROOT / Shizuku 三通道"
                    Route.RecycleBin -> "删除项集中管理：可还原到原位置，也可彻底清除"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider()
        Box(Modifier.weight(1f)) {
            when (r) {
                is Route.Tool -> when (r.page) {
                    ToolPage.STRING -> StringToolsScreen()
                    ToolPage.ENCODING -> EncodingScreen(r.path.ifBlank { "/sdcard" })
                    ToolPage.HEX -> HexViewerScreen(r.path.ifBlank { "/sdcard" })
                    ToolPage.DIFF -> DiffScreen(r.path, "")
                    ToolPage.DEX -> DexToolScreen(r.path.ifBlank { "/sdcard" })
                    ToolPage.MERGE -> MergeScreen(r.path.ifBlank { "/sdcard" })
                    ToolPage.BATCH_RENAME -> BatchRenameScreen(r.path.split('\n').filter { it.isNotBlank() })
                    ToolPage.COMPARE -> {
                        val parts = r.path.split('\n')
                        CompareScreen(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" })
                    }
                    ToolPage.EDITOR -> EditorScreen(r.path.ifBlank { "/sdcard" })
                    // —— 新接入的三件套 ——
                    ToolPage.SCRIPT -> ScriptScreen(r.path.ifBlank { "/sdcard" })
                    ToolPage.APK_TOOL -> ApkToolScreen(
                        initialPath = r.path.ifBlank { "/sdcard" },
                        onOpenDex = { p -> onNavigate(Route.Tool(ToolPage.DEX, p)) },
                        onOpenHex = { p -> onNavigate(Route.Tool(ToolPage.HEX, p)) },
                        onToast = toast
                    )
                }
                is Route.Preview -> PreviewScreen(
                    path = r.path,
                    onOpenHex = { p -> onNavigate(Route.Tool(ToolPage.HEX, p)) },
                    onEditText = { p -> onNavigate(Route.Tool(ToolPage.EDITOR, p)) },
                    onOpenDex = { p -> onNavigate(Route.Tool(ToolPage.DEX, p)) },
                    onToast = toast
                )
                Route.Terminal -> TerminalScreen()
                Route.RecycleBin -> RecycleBinScreen(onToast = toast)
            }
        }
    }
}

@Composable
private fun GlyphAction(glyph: String, tint: Color? = null, onClick: () -> Unit) {
    Text(
        text = glyph,
        fontSize = 16.sp,
        color = tint ?: MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

private fun topTitle(route: Route?, tab: MainTab, state: FileBrowserState): String = when (route) {
    is Route.Tool -> route.page.title
    is Route.Preview -> "文件预览"
    Route.Terminal -> "终端模拟器"
    Route.RecycleBin -> "回收站"
    null -> if (tab == MainTab.FILES) state.active.title else tab.label
}

private fun topSubtitle(route: Route?, tab: MainTab, state: FileBrowserState): String {
    if (route != null) return ""
    return when (tab) {
        MainTab.FILES -> {
            val sel = state.active.selected.size
            state.active.path + if (sel > 0) " · 已选 $sel 项" else ""
        }
        MainTab.TOOLS -> "能力清单 ${ToolRegistry.all().size} 项"
        MainTab.TASKS -> "后台任务进度与结果"
        MainTab.SETTINGS -> AppInfo.NAME + " v" + AppInfo.VERSION
    }
}
