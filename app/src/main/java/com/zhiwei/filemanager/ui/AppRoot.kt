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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.filemanager.core.prefs.AppPrefs
import com.zhiwei.filemanager.core.util.AppInfo
import com.zhiwei.filemanager.tool.ToolRegistry
import com.zhiwei.filemanager.ui.files.FileBrowserScreen
import com.zhiwei.filemanager.ui.files.FileBrowserState
import com.zhiwei.filemanager.ui.files.FileNav
import com.zhiwei.filemanager.ui.settings.SettingsScreen
import com.zhiwei.filemanager.ui.tasks.TaskBoardScreen
import com.zhiwei.filemanager.ui.terminal.TerminalScreen
import com.zhiwei.filemanager.ui.tools.BatchRenameScreen
import com.zhiwei.filemanager.ui.tools.CompareScreen
import com.zhiwei.filemanager.ui.tools.DexToolScreen
import com.zhiwei.filemanager.ui.tools.DiffScreen
import com.zhiwei.filemanager.ui.tools.EditorScreen
import com.zhiwei.filemanager.ui.tools.EncodingScreen
import com.zhiwei.filemanager.ui.tools.HexViewerScreen
import com.zhiwei.filemanager.ui.tools.MergeScreen
import com.zhiwei.filemanager.ui.tools.StringToolsScreen
import com.zhiwei.filemanager.ui.tools.ToolPage
import com.zhiwei.filemanager.ui.tools.ToolboxScreen

/** 底部一级导航。 */
private enum class MainTab(val label: String, val glyph: String) {
    FILES("文件", "▤"),
    TOOLS("工具", "◈"),
    TASKS("任务", "◔"),
    SETTINGS("设置", "⚙")
}

/** 二级路由（工具页 / 终端）。 */
private sealed interface Route {
    data class Tool(val page: ToolPage, val path: String) : Route
    data object Terminal : Route
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val state = remember { FileBrowserState() }
    var tab by remember { mutableStateOf(MainTab.FILES) }
    var route by remember { mutableStateOf<Route?>(null) }

    val nav = remember(state) {
        FileNav(
            openText = { route = Route.Tool(ToolPage.EDITOR, it) },
            openHex = { route = Route.Tool(ToolPage.HEX, it) },
            openDex = { route = Route.Tool(ToolPage.DEX, it) },
            toast = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
            openRename = { paths -> route = Route.Tool(ToolPage.BATCH_RENAME, paths.joinToString("\n")) },
            openCompare = { a, b -> route = Route.Tool(ToolPage.COMPARE, a + "\n" + b) },
            openMerge = { dir -> route = Route.Tool(ToolPage.MERGE, dir) }
        )
    }

    LaunchedEffect(Unit) { state.persistPaths() }

    BackHandler(enabled = route != null) { route = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Column {
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
                        GlyphAction(if (state.panes.size > 1) "▥" else "▢") { state.toggleDualPane() }
                        GlyphAction(if (AppPrefs.showHidden) "◉" else "○") {
                            AppPrefs.updateShowHidden(!AppPrefs.showHidden)
                            state.panes.forEach { it.refresh() }
                        }
                        GlyphAction("⟳") { state.panes.forEach { it.refresh() } }
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
                    RouteHost(r, onBack = { route = null })
                }
            }
        }
    }
}

/** 页面切换动画的比较键：路由 + 一级页签。 */
private data class RouteKey(val route: Route?, val tab: MainTab)

@Composable
private fun RouteHost(r: Route, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text(
                text = when (r) {
                    is Route.Tool -> r.page.desc
                    Route.Terminal -> "内置 shell 会话：普通权限 / ROOT / Shizuku 三通道"
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
                }
                Route.Terminal -> TerminalScreen()
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
    Route.Terminal -> "终端模拟器"
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
