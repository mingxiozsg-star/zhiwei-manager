package com.zhiwei.filemanager.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.filemanager.core.prefs.AppPrefs
import com.zhiwei.filemanager.core.shell.ShellRunner
import com.zhiwei.filemanager.core.shell.ShizukuHelper
import com.zhiwei.filemanager.core.util.AppInfo
import com.zhiwei.filemanager.tool.FeatureStatus
import com.zhiwei.filemanager.tool.ToolRegistry
import com.zhiwei.filemanager.ui.files.FileBrowserState
import com.zhiwei.filemanager.ui.theme.KindArchive
import com.zhiwei.filemanager.ui.theme.KindFolder
import com.zhiwei.filemanager.ui.theme.KindOther
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** 设置：外观 / 浏览 / 提权与权限 / 功能清单 / 关于。 */
@Composable
fun SettingsScreen(state: FileBrowserState, onOpenTerminal: () -> Unit = {}) {
    val context = LocalContext.current
    var shizukuText by remember { mutableStateOf(describeShizuku()) }
    var rootText by remember { mutableStateOf("检测中…") }
    var allFiles by remember { mutableStateOf(hasAllFiles()) }
    var showBoard by remember { mutableStateOf(true) }

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
        allFiles = hasAllFiles()
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
            ActionRow("去授权所有文件访问") { openAllFilesSettings(context) }
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

        SectionCard("关于") {
            InfoRow("应用", AppInfo.NAME + "（" + AppInfo.EN_NAME + "）")
            InfoRow("版本", "v" + AppInfo.VERSION)
            InfoRow("包名", context.packageName)
            InfoRow("Android", Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT)
            Text(
                text = AppInfo.DESIGN_NOTE,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Text(
                text = "下一步规划：Res 资源混淆、APK 签名与共存、控制流混淆、SO 编辑与对比、PDF/FFmpeg 处理、网络存储（FTP/SFTP/SMB/WebDAV）、HTTP 与 MCP 服务。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

private fun hasAllFiles(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    } else {
        true
    }

private fun openAllFilesSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val direct = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:" + context.packageName)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val ok = runCatching { context.startActivity(direct) }.isSuccess
    if (!ok) runCatching { context.startActivity(fallback) }
}