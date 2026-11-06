package com.zhiwei.filemanager.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.filemanager.core.update.UpdateInfo
import com.zhiwei.filemanager.core.util.AppInfo
import com.zhiwei.filemanager.core.util.Format

/**
 * 更新弹窗（启动时检测到新版 / 设置页手动检查共用）。
 *
 * 三个动作严格对齐需求：
 * - 立即更新 → 打开项目主页
 * - 去官网更新 → 打开 Release 页（官网侧）
 * - 取消更新 → 关掉弹窗
 */
@Composable
fun UpdateNoticeDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 " + info.tag, fontSize = 16.sp) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "当前 " + AppInfo.VERSION_FULL + " → 最新 " + info.tag,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                if (info.sizeBytes > 0L) {
                    Text(
                        "安装包 " + Format.size(info.sizeBytes) + " · 覆盖安装不丢数据",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (info.notes.isNotBlank()) {
                    Text(
                        "更新日志",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        info.notes.take(1000),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                openWebUrl(context, AppInfo.REPO_URL)
                onDismiss()
            }) { Text("立即更新") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    openWebUrl(context, AppInfo.RELEASES_PAGE)
                    onDismiss()
                }) { Text("去官网更新") }
                TextButton(onClick = onDismiss) { Text("取消更新") }
            }
        }
    )
}

/** 打开外部网页链接。 */
internal fun openWebUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
