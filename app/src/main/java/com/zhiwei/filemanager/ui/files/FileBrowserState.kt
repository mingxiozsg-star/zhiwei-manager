package com.zhiwei.filemanager.ui.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zhiwei.filemanager.core.fs.FileRepo
import com.zhiwei.filemanager.core.model.FileItem
import com.zhiwei.filemanager.core.prefs.AppPrefs
import com.zhiwei.filemanager.core.prefs.BookmarkStore
import java.io.File

/** 单个窗口（窗格）的状态。 */
class PaneState(initialPath: String) {

    var path by mutableStateOf(initialPath)
        private set
    var items by mutableStateOf<List<FileItem>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var keyword by mutableStateOf("")
        private set

    val selected = mutableStateListOf<String>()

    val title: String get() = FileRepo.displayName(path)

    fun refresh() {
        val raw = FileRepo.list(path, AppPrefs.showHidden)
        items = FileRepo.sort(raw, AppPrefs.sortMode, AppPrefs.sortOrder)
        error = if (raw.isEmpty() && !File(path).canRead())
            "该目录被系统限制，无法按路径读取（点下方「免提权浏览」用系统授权器授权即可）"
        else null
        selected.retainAll { s -> items.any { it.path == s } }
    }

    fun open(newPath: String) {
        path = newPath
        keyword = ""
        selected.clear()
        refresh()
        BookmarkStore.pushRecent(newPath)
    }

    fun up() {
        val parent = FileRepo.parentOf(path) ?: return
        open(parent)
    }

    fun updateKeyword(k: String) {
        keyword = k
    }

    fun visibleItems(): List<FileItem> =
        if (keyword.isBlank()) items else items.filter { it.name.contains(keyword, ignoreCase = true) }

    fun toggle(item: FileItem) {
        if (selected.contains(item.path)) selected.remove(item.path) else selected.add(item.path)
    }

    fun isSelected(item: FileItem): Boolean = selected.contains(item.path)

    /** 选中单项（滑动区间选择用；已在集合里则什么都不做）。 */
    fun select(item: FileItem) {
        if (!selected.contains(item.path)) selected.add(item.path)
    }

    fun selectAll() {
        selected.clear()
        selected.addAll(visibleItems().map { it.path })
    }

    fun invertSelection() {
        val current = selected.toSet()
        val next = visibleItems().map { it.path }.filter { !current.contains(it) }
        selected.clear()
        selected.addAll(next)
    }

    fun clearSelection() = selected.clear()
}

/** 多窗口浏览器的整体状态（对齐 MT 管理器的双窗口直接操作模型）。 */
class FileBrowserState {

    val panes = mutableStateListOf(PaneState(AppPrefs.lastLeftPath))
    var activeIndex by mutableStateOf(0)
        private set

    val clipboard = mutableStateListOf<String>()
    var clipboardCut by mutableStateOf(false)
        private set

    /** 顶栏放大镜：搜索「文件」（名称 / 内容匹配）。由文件页消费后清空。 */
    var searchFileRequest by mutableStateOf(false)

    /** 点击顶栏目录标题：搜索「文件夹」（按名字找目录并直接跳转）。由文件页消费后清空。 */
    var searchFolderRequest by mutableStateOf(false)

    /** 折叠栏里的「书签与最近访问」：置位后由文件页打开书签面板，消费完清空。 */
    var bookmarkRequest by mutableStateOf(false)

    /**
     * 待弹出的安装包速览弹窗。
     *
     * 点 APK 十次里有九次只想「装它」，为此切走整页再等用户退出是反人类的，
     * 所以预览 APK 一律走这个弹窗（安装 / 查看 / 更多），不再是整页。
     */
    var apkQuickRequest by mutableStateOf<FileItem?>(null)

    val active: PaneState
        get() = panes[activeIndex.coerceIn(0, panes.size - 1)]

    /** 非当前窗口（用于“复制到另一窗口”）。 */
    val other: PaneState?
        get() = panes.firstOrNull { it !== active }

    init {
        panes[0].refresh()
        if (AppPrefs.dualPane) {
            panes += PaneState(AppPrefs.lastRightPath).also { it.refresh() }
        }
    }

    fun setActive(i: Int) {
        activeIndex = i.coerceIn(0, panes.size - 1)
    }

    fun toggleDualPane(): Boolean {
        if (panes.size > 1) {
            val removed = panes.removeAt(panes.size - 1)
            AppPrefs.setLastPath(panes[0].path, removed.path)
        } else {
            panes += PaneState(AppPrefs.lastRightPath).also { it.refresh() }
        }
        AppPrefs.updateDualPane(panes.size > 1)
        activeIndex = activeIndex.coerceIn(0, panes.size - 1)
        return panes.size > 1
    }

    fun addPane(path: String = "/sdcard") {
        panes += PaneState(path).also { it.refresh() }
        activeIndex = panes.size - 1
        AppPrefs.updateDualPane(panes.size > 1)
    }

    fun closePane(index: Int) {
        if (panes.size <= 1) return
        panes.removeAt(index)
        activeIndex = activeIndex.coerceIn(0, panes.size - 1)
        AppPrefs.updateDualPane(panes.size > 1)
    }

    fun setClipboard(paths: List<String>, cut: Boolean) {
        clipboard.clear()
        clipboard.addAll(paths)
        clipboardCut = cut
    }

    fun clearClipboard() {
        clipboard.clear()
        clipboardCut = false
    }

    fun persistPaths() {
        AppPrefs.setLastPath(panes.getOrNull(0)?.path ?: "/sdcard", panes.getOrNull(1)?.path ?: "/sdcard/Download")
    }
}
