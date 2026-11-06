package com.zhiwei.filemanager.ui.files

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.filemanager.core.fs.FileOps
import com.zhiwei.filemanager.core.model.FileItem
import com.zhiwei.filemanager.core.task.AppScope
import com.zhiwei.filemanager.core.util.ApkInstaller
import com.zhiwei.filemanager.core.util.FileKind
import com.zhiwei.filemanager.core.util.Format
import com.zhiwei.filemanager.tool.archive.ArchiveTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 一个条目的动作。
 *
 * 为什么要单独建模：长按菜单、安装包弹窗的「更多」、以后的多选菜单，用的都是同一套动作，
 * 只是排布方式不同。以前把动作写死在 LazyColumn 里，结果每加一个入口都要在三个地方抄一遍。
 */
data class ItemAction(val label: String, val run: () -> Unit)

/**
 * 按文件类型产出可用动作，**顺序即优先级**：
 * 1. 类型专属工具（安装包给安装包的工具、压缩包给压缩包的工具，互不串味）
 * 2. 通用文件操作
 * 前 7 项直接摆出来，剩下的进「更多」。
 */
fun itemActions(
    context: Context,
    item: FileItem,
    state: FileBrowserState,
    pane: PaneState,
    nav: FileNav,
    onDialog: (FileDialog) -> Unit,
    onClose: () -> Unit,
    onAction: (String) -> Unit,
    scope: CoroutineScope
): List<ItemAction> {
    val list = mutableListOf<ItemAction>()
    val file = File(item.path)

    // ——— 打开 ———
    if (item.isDir) {
        list += ItemAction("打开") { onClose(); pane.open(item.path) }
    } else if (!item.isApk && isPreviewable(item.name)) {
        // APK 不给「预览」：它的第一入口是「安装」，等同整页预览的入口已由速览弹窗承担
        list += ItemAction("预览") { onClose(); nav.openPreview(item.path) }
    }

    // ——— 类型专属工具 ———
    when {
        item.isApk -> {
            list += ItemAction("安装") { ApkInstaller.install(context, file) }
            list += ItemAction("安装包信息") { onClose(); state.apkQuickRequest = item }
            list += ItemAction("反编译工具箱") { onClose(); nav.openApkTool(item.path) }
        }
        item.isDex || item.isJar -> {
            list += ItemAction("Dex 工具") { onClose(); nav.openDex(item.path) }
        }
        item.kind == FileKind.IMAGE -> {
            list += ItemAction("设为壁纸") { onClose(); setWallpaper(context, file, nav) }
        }
        item.kind == FileKind.FONT -> {
            list += ItemAction("字体预览") { onClose(); nav.openPreview(item.path) }
        }
        item.kind == FileKind.ARCHIVE && ArchiveTool.canExtract(item.name) -> {
            list += ItemAction("解压到当前目录") {
                val dest = file.parentFile?.absolutePath ?: "/sdcard"
                onClose()
                onAction("解压中：${item.name}")
                AppScope.io.launch {
                    ArchiveTool.extract(item.path, dest)
                    state.panes.forEach { it.refresh() }
                }
            }
        }
    }

    if (!item.isDir && Format.isTextLikely(item.name)) {
        list += ItemAction("文本编辑") { onClose(); nav.openText(item.path) }
    }

    // ——— 通用操作 ———
    list += ItemAction("复制") {
        state.setClipboard(listOf(item.path), false)
        onClose()
        onAction("已复制：${item.name}")
    }
    list += ItemAction("剪切") {
        state.setClipboard(listOf(item.path), true)
        onClose()
        onAction("已剪切：${item.name}")
    }
    list += ItemAction("重命名") {
        onDialog(
            FileDialog.Input("重命名", item.name, "确定") { newName ->
                scope.launch {
                    withContext(Dispatchers.IO) { FileOps.rename(item.path, newName) }
                    state.panes.forEach { it.refresh() }
                }
            }
        )
    }
    list += ItemAction("删除") {
        val toRecycle = com.zhiwei.filemanager.core.prefs.AppPrefs.recycleEnabled
        onDialog(
            FileDialog.Confirm(
                "删除",
                if (toRecycle) "将「${item.name}」移入回收站？" else "永久删除「${item.name}」？不可恢复。"
            ) {
                deleteSelection(state, pane, listOf(item.path), scope, nav)
            }
        )
    }
    list += ItemAction("分享") { onClose(); shareFile(context, item, nav) }
    list += ItemAction("属性") { onDialog(FileDialog.Props(item)) }

    // ——— 收进「更多」的工具 ———
    list += ItemAction("十六进制查看") { onClose(); nav.openHex(item.path) }
    if (!item.isDir && !ArchiveTool.canExtract(item.name)) {
        list += ItemAction("压缩为 ZIP") {
            val out = FileOps.uniqueTarget(file.parentFile?.absolutePath ?: "/sdcard", item.name + ".zip")
            onClose()
            onAction("压缩中：${out.name}")
            AppScope.io.launch {
                ArchiveTool.compressToZip(listOf(item.path), out.absolutePath)
                state.panes.forEach { it.refresh() }
            }
        }
    }
    if (item.isDir) {
        list += ItemAction("压缩为 ZIP") {
            val out = FileOps.uniqueTarget(file.parentFile?.absolutePath ?: "/sdcard", item.name + ".zip")
            onClose()
            onAction("压缩中：${out.name}")
            AppScope.io.launch {
                ArchiveTool.compressToZip(listOf(item.path), out.absolutePath)
                state.panes.forEach { it.refresh() }
            }
        }
    }
    if (state.panes.size > 1) {
        list += ItemAction("复制到另一窗口") {
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
    if (item.isDir) {
        list += ItemAction("与另一窗口对比") {
            val other = state.other
            onClose()
            if (other != null) nav.openCompare(item.path, other.path)
        }
    }
    list += ItemAction("复制路径") {
        state.setClipboard(listOf(item.path), false)
        onClose()
        onAction(item.path)
    }
    return list
}

/** 图片设为壁纸（走系统裁剪器，不自己写 SurfaceControl）。 */
private fun setWallpaper(context: Context, file: File, nav: FileNav) {
    val uri = runCatching {
        androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
    }.getOrNull()
    if (uri == null) {
        nav.toast("设置壁纸需要先把文件放到可共享目录")
        return
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_ATTACH_DATA).apply {
        setDataAndType(uri, "image/*")
        putExtra("mimeType", "image/*")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { nav.toast("没有可用的壁纸应用") }
}

/** 双列动作面板：最多 7 个直接可见，其余走「更多」，不霸占整屏。 */
@Composable
fun ActionGrid(actions: List<ItemAction>, onMore: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        actions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { action ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(3.dp)
                            .clickable(onClick = action.run)
                    ) {
                        Text(
                            text = action.label,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp)
                        )
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
        if (onMore != null) {
            Row(Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(3.dp)
                        .clickable(onClick = onMore)
                ) {
                    Text(
                        text = "更多工具…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp)
                    )
                }
            }
        }
    }
}

/** 「更多」：把剩余动作全列出来，同样是双列，不滚动也能看全。 */
@Composable
fun MoreActionsDialog(actions: List<ItemAction>, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("更多工具", fontSize = 15.sp) },
        text = { ActionGrid(actions) },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭", fontSize = 12.sp) } }
    )
}