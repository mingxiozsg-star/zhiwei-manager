package com.zhiwei.filemanager.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.zhiwei.filemanager.core.prefs.AppPrefs
import com.zhiwei.filemanager.core.shell.ShellRunner
import com.zhiwei.filemanager.core.shell.ShizukuHelper
import com.zhiwei.filemanager.core.update.UpdateChecker
import com.zhiwei.filemanager.core.update.UpdateInfo
import com.zhiwei.filemanager.core.util.AppInfo
import com.zhiwei.filemanager.core.util.Format
import com.zhiwei.filemanager.core.util.Permissions
import com.zhiwei.filemanager.tool.FeatureStatus
import com.zhiwei.filemanager.tool.ToolRegistry
import com.zhiwei.filemanager.ui.files.FileBrowserState
import com.zhiwei.filemanager.ui.theme.KindArchive
import com.zhiwei.filemanager.ui.theme.KindFolder
import com.zhiwei.filemanager.ui.theme.KindOther
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** 设置：外观 / 浏览 / 提权与权限 / 功能清单 / 关于。 */
@Composable
fun SettingsScreen(state: FileBrowserState, onOpenTerminal: () -> Unit = {}) {
    val context = LocalContext.current
    var shizukuText by remember { mutableStateOf(describeShizuku()) }
    var rootText by remember { mutableStateOf("检测中…") }
    var allFiles by remember { mutableStateOf(Permissions.hasAllFilesAccess()) }
    var showBoard by remember { mutableStateOf(true) }

    // —— 内置更新（GitHub Releases + 镜像加速）——
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progressPercent by remember { mutableStateOf(0) }
    var progressText by remember { mutableStateOf("") }
    var readyApk by remember { mutableStateOf<File?>(null) }

    fun checkUpdate() {
        if (checking) return
        checking = true
        updateMessage = "正在查询最新版本…"
        scope.launch {
            val result = withContext(Dispatchers.IO) { UpdateChecker.checkLatest() }
            checking = false
            result.fold(
                onSuccess = { info ->
                    if (UpdateChecker.isNewer(info.version, AppInfo.VERSION)) {
                        updateMessage = null
                        updateInfo = info
                        showUpdateDialog = true
                    } else {
                        updateMessage = "已是最新版本（当前 " + AppInfo.VERSION_FULL + "）"
                    }
                },
                onFailure = { t -> updateMessage = "检查失败：" + (t.message ?: t.javaClass.simpleName) }
            )
        }
    }

    fun startDownload(info: UpdateInfo) {
        if (downloading) return
        downloading = true
        progressPercent = 0
        progressText = "准备下载…"
        scope.launch {
            val dir = File(context.cacheDir, "update").apply { mkdirs() }
            val dest = File(dir, info.assetName.ifBlank { "zhiwei-manager-" + info.version + ".apk" })
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    UpdateChecker.download(info, dest) { done, total ->
                        val pct = if (total > 0L) ((done * 100) / total).toInt() else 0
                        scope.launch {
                            progressPercent = pct.coerceIn(0, 100)
                            progressText = Format.size(done) + if (total > 0L) " / " + Format.size(total) else ""
                        }
                    }
                }
            }
            downloading = false
            outcome.fold(
                onSuccess = { readyApk = dest },
                onFailure = { t -> updateMessage = "下载失败：" + (t.message ?: t.javaClass.simpleName) }
            )
        }
    }

    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, grant ->
            shizukuText = if (grant == PackageManager.PERMISSION_GRANTED) "已授权（shell 身份可用）" else "已拒绝授权"
        }
        ShizukuHelper.addListener(listener)
        onDispose { ShizukuHelper.removeListener(listener) }
    }

    LaunchedEffect(Unit) {
        rootText = withContext(Dispatchers.IO) {
            if (ShellRunner.isRootAvailable()) "已获取 ROOT" else "未检测到 ROOT"
        }
        allFiles = Permissions.hasAllFilesAccess()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {

        SectionCard("外观") {
            ChoiceRow(
                label = "主题",
                options = listOf("跟随系统", "深色", "浅色"),
                selected = AppPrefs.themeMode
            ) { AppPrefs.updateThemeMode(it) }
        }

        SectionCard("浏览与操作") {
            SwitchRow("双窗口模式", AppPrefs.dualPane) {
                state.toggleDualPane()
            }
            SwitchRow("显示隐藏文件", AppPrefs.showHidden) {
                AppPrefs.updateShowHidden(!AppPrefs.showHidden)
                state.panes.forEach { it.refresh() }
            }
            ChoiceRow(
                label = "编辑器默认编码",
                options = listOf("UTF-8", "GBK", "GB18030", "ISO-8859-1"),
                selected = listOf("UTF-8", "GBK", "GB18030", "ISO-8859-1").indexOf(AppPrefs.editorCharset).coerceAtLeast(0)
            ) { AppPrefs.updateEditorCharset(listOf("UTF-8", "GBK", "GB18030", "ISO-8859-1")[it]) }
            InfoRow("当前窗口", state.active.path)
        }

        SectionCard("权限与提权") {
            InfoRow("所有文件访问", if (allFiles) "已授予" else "未授予（/Android/data 等目录不可读）")
            ActionRow("去授权所有文件访问") { Permissions.openAllFilesSettings(context) }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            InfoRow("Shizuku 通道", shizukuText)
            Row {
                ActionRow("申请 Shizuku 授权", compact = true) {
                    if (ShizukuHelper.ping()) ShizukuHelper.requestPermission()
                    shizukuText = describeShizuku()
                }
                ActionRow("刷新状态", compact = true) { shizukuText = describeShizuku() }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            InfoRow("ROOT 通道", rootText)
            SwitchRow("使用 ROOT 执行高危操作", AppPrefs.rootEnabled) {
                AppPrefs.updateRootEnabled(!AppPrefs.rootEnabled)
            }
            ActionRow("重新检测 ROOT") {
                rootText = "检测中…"
                rootText = if (ShellRunner.isRootAvailable()) "已获取 ROOT" else "未检测到 ROOT"
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            ActionRow("打开终端模拟器", compact = true) { onOpenTerminal() }
        }

        SectionCard("功能清单（工程真实进度）") {
            Text(
                text = ToolRegistry.statsText(),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { showBoard = !showBoard }) {
                Text(if (showBoard) "收起明细" else "展开明细", fontSize = 12.sp)
            }
            if (showBoard) {
                ToolRegistry.groups.forEach { (group, features) ->
                    Text(
                        text = group,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    features.forEach { f ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                text = statusTag(f.status),
                                fontSize = 11.sp,
                                color = statusColor(f.status),
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(f.name, fontSize = 12.sp)
                                if (f.note.isNotBlank()) {
                                    Text(
                                        text = f.note,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SectionCard("关于我们") {
            InfoRow("应用", AppInfo.NAME + "（" + AppInfo.EN_NAME + "）")
            InfoRow("版本", AppInfo.VERSION_FULL)
            InfoRow("包名", context.packageName)
            InfoRow("Android", Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT)
            InfoRow("团队", AppInfo.TEAM)
            InfoRow("邮箱", AppInfo.EMAIL)
            InfoRow("官方 QQ 群", AppInfo.QQ_GROUP)
            InfoRow("开源仓库", AppInfo.GITHUB_USER + "/zhiwei-manager")
            Row(Modifier.padding(top = 4.dp)) {
                ActionRow("检查更新", compact = true) { checkUpdate() }
                ActionRow("打开发布页", compact = true) { openUrl(context, AppInfo.RELEASES_PAGE) }
                ActionRow("打开仓库", compact = true) { openUrl(context, AppInfo.REPO_URL) }
            }
            Row {
                ActionRow("复制邮箱", compact = true) { copyText(context, AppInfo.EMAIL) }
                ActionRow("复制群号", compact = true) { copyText(context, AppInfo.QQ_GROUP) }
                if (updateInfo != null) {
                    ActionRow("下载更新包", compact = true) { updateInfo?.let { startDownload(it) } }
                }
            }
            if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                text = AppInfo.DESIGN_NOTE,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = "「" + AppInfo.TEAM + "」自研文件管理器：双窗口直接操作、删除进回收站。" +
                    "包名与签名固定不变，更新走内置通道，可直接覆盖安装、不丢数据。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = "下一步规划：Res 资源混淆、APK 签名与共存、控制流混淆、SO 编辑与对比、PDF/FFmpeg 处理、" +
                    "网络存储（FTP/SFTP/SMB/WebDAV）、HTTP 与 MCP 服务。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }

    // —— 更新弹窗：与启动时共用同一套「立即更新 / 去官网更新 / 取消更新」——
    if (showUpdateDialog) {
        updateInfo?.let { info ->
            UpdateNoticeDialog(info = info, onDismiss = { showUpdateDialog = false })
        }
    }

    if (downloading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载更新", fontSize = 16.sp) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        text = progressPercent.toString() + "%  " + progressText,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            },
            confirmButton = {}
        )
    }

    readyApk?.let { apk ->
        AlertDialog(
            onDismissRequest = { readyApk = null },
            title = { Text("下载完成", fontSize = 16.sp) },
            text = {
                Text(
                    "安装包已就绪：" + apk.name +
                        "\n包名与签名固定，可直接覆盖安装（不会丢数据）。",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    readyApk = null
                    installApk(context, apk)
                }) { Text("立即安装") }
            },
            dismissButton = { TextButton(onClick = { readyApk = null }) { Text("取消") } }
        )
    }

    updateMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { updateMessage = null },
            title = { Text("更新检查", fontSize = 16.sp) },
            text = { Text(msg, fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { updateMessage = null }) { Text("知道了") } }
        )
    }
}

private fun openInstallPermission(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** 用 FileProvider 把缓存里的更新包交给系统安装器。 */
private fun installApk(context: Context, file: File) {
    val uri = runCatching {
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }.getOrNull()
    if (uri == null) {
        openInstallPermission(context)
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }.onFailure { openInstallPermission(context) }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun copyText(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("zhiwei", text))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onChange() })
    }
}

@Composable
private fun ChoiceRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp)
        Row(Modifier.padding(top = 4.dp)) {
            options.forEachIndexed { i, opt ->
                val active = i == selected
                Surface(
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 6.dp).clickable { onSelect(i) }
                ) {
                    Text(
                        text = opt,
                        fontSize = 12.sp,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(key, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
    }
}

@Composable
private fun ActionRow(label: String, compact: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = if (compact) Modifier else Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun statusTag(s: FeatureStatus): String = when (s) {
    FeatureStatus.READY -> "● 已实现"
    FeatureStatus.PARTIAL -> "◐ 部分"
    FeatureStatus.PLANNED -> "○ 规划中"
}

@Composable
private fun statusColor(s: FeatureStatus): Color = when (s) {
    FeatureStatus.READY -> KindFolder
    FeatureStatus.PARTIAL -> KindArchive
    FeatureStatus.PLANNED -> KindOther
}

private fun describeShizuku(): String = when {
    !ShizukuHelper.ping() -> "服务未连接（需安装并启动 Shizuku）"
    ShizukuHelper.hasPermission() -> "已授权（shell 身份可用）"
    else -> "服务已连接，待授权"
}