package com.zhiwei.filemanager.ui.files

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.filemanager.core.model.FileItem
import com.zhiwei.filemanager.core.util.ApkInspector
import com.zhiwei.filemanager.core.util.ApkInstaller
import com.zhiwei.filemanager.core.util.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 点安装包 → 弹窗（不是整页预览）。
 *
 * 理由很直白：用户点 APK 十次里有九次只想「装它」，为此把整个屏幕切走、再等他退出，
 * 是反人类的。所以这里只给三件事：装上（安装）、看看里面是什么（查看 → 反编译工具箱）、
 * 以及这个包还能用什么工具（更多）。基础信息在上方一屏读完，不需要滚动。
 */
@Composable
internal fun ApkQuickDialog(
    item: FileItem,
    state: FileBrowserState,
    nav: FileNav,
    onDialog: (FileDialog) -> Unit,
    onClose: () -> Unit,
    onAction: (String) -> Unit,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    var summary by remember(item.path) { mutableStateOf<ApkInspector.Summary?>(null) }
    var showTools by remember(item.path) { mutableStateOf(false) }

    LaunchedEffect(item.path) {
        summary = withContext(Dispatchers.IO) {
            runCatching { ApkInspector.inspect(context, File(item.path)) }.getOrNull()
        }
    }

    val s = summary
    val actions = itemActions(context, item, state, state.active, nav, onDialog, onClose, onAction, scope)

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                text = if (showTools) "可用工具" else (s?.label ?: item.name),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            if (showTools) {
                ActionGrid(actions)
            } else {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        s?.icon?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(44.dp).padding(end = 2.dp)
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(item.name, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = s?.packageName ?: "读取中…",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (s == null) {
                        Box(Modifier.fillMaxWidth().padding(top = 12.dp)) { LinearProgressIndicator() }
                    } else {
                        Column(Modifier.padding(top = 10.dp)) {
                            ApkInfoLine("版本", s.versionName + " (" + s.versionCode + ")")
                            ApkInfoLine("大小", Format.size(item.size))
                            ApkInfoLine("SDK", "min " + s.minSdk + " · target " + s.targetSdk)
                            ApkInfoLine(
                                "签名",
                                buildString {
                                    append(if (s.signature.v1) "v1✓ " else "v1✗ ")
                                    append(if (s.signature.v2) "v2✓ " else "v2✗ ")
                                    append(if (s.signature.v3) "v3✓" else "v3✗")
                                }
                            )
                            ApkInfoLine("加固", s.hardening ?: "未命中已知特征")
                            ApkInfoLine("内容", s.entryCount.toString() + " 项 · dex " + s.dexCount + " · so " + s.soCount)
                        }
                    }

                    Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        Button(
                            onClick = { ApkInstaller.install(context, File(item.path)) },
                            modifier = Modifier.weight(1f)
                        ) { Text("安装", fontSize = 13.sp) }
                        Column(Modifier.width(8.dp)) {}
                        Button(
                            onClick = { onClose(); nav.openApkTool(item.path) },
                            modifier = Modifier.weight(1f)
                        ) { Text("查看", fontSize = 13.sp) }
                        Column(Modifier.width(8.dp)) {}
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                Modifier.fillMaxWidth().height(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(onClick = { showTools = true }) {
                                    Text("更多", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭", fontSize = 12.sp) } }
    )
}

@Composable
private fun ApkInfoLine(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            text = key,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(38.dp)
        )
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}