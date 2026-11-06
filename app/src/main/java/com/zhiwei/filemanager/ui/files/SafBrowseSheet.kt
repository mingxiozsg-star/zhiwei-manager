package com.zhiwei.filemanager.ui.files

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.filemanager.core.fs.SafEntry
import com.zhiwei.filemanager.core.fs.SafTree
import com.zhiwei.filemanager.core.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 受限目录的「免提权」浏览面板。
 *
 * 走的是系统 SAF 授权（ACTION_OPEN_DOCUMENT_TREE），**不需要 Shizuku、不需要 ROOT**，
 * 也不需要「所有文件访问权限」——用户亲手把目录授权一次，之后本应用就能正常列目录、
 * 打开文件。Android/data、Android/obb 这类按路径读不到的地方，这条是唯一不靠高权限的路。
 */
@Composable
fun SafBrowseSheet(initialPath: String, nav: FileNav, onClose: () -> Unit) {
    val context = LocalContext.current
    var tree by remember { mutableStateOf(SafTree.saved(context, initialPath)) }
    val treeDocId = tree?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
    var docId by remember(tree) { mutableStateOf(treeDocId) }
    var entries by remember { mutableStateOf<List<SafEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == Activity.RESULT_OK && uri != null) {
            SafTree.remember(context, initialPath, uri)
            tree = uri
        }
    }

    LaunchedEffect(tree, docId) {
        val t = tree ?: return@LaunchedEffect
        val d = docId ?: return@LaunchedEffect
        loading = true
        entries = withContext(Dispatchers.IO) { SafTree.listChildren(context, t, d) }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("免提权浏览受限目录", fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "Android 11 起 Android/data、Android/obb 不允许按路径直接读取。" +
                        "用系统授权器把目录授权给本应用即可正常浏览——不需要 Shizuku，也不需要 ROOT。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = initialPath,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )

                if (tree == null) {
                    Button(
                        onClick = { runCatching { picker.launch(SafTree.requestIntent(initialPath)) } },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) { Text("选择该目录并授权", fontSize = 13.sp) }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = docId?.let { SafTree.displayName(it) } ?: "已授权",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            SafTree.forget(context, initialPath)
                            tree = null
                            entries = emptyList()
                        }) { Text("重新授权", fontSize = 11.sp) }
                    }

                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        val parent = docId?.let { SafTree.parentDocId(it) }
                        if (parent != null && treeDocId != null && SafTree.within(treeDocId, parent)) {
                            item(key = "__up__") {
                                Text(
                                    text = "↑ 返回上一层",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { docId = parent }
                                        .padding(horizontal = 8.dp, vertical = 10.dp)
                                )
                            }
                        }
                        if (!loading && entries.isEmpty()) {
                            item(key = "__empty__") {
                                Text(
                                    text = "空目录，或该系统版本不允许授权此目录（可改用 Shizuku）",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                        items(entries, key = { it.docId }) { e ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { if (e.isDir) docId = e.docId else openSafFile(context, e, nav) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (e.isDir) "▣" else "▤", fontSize = 13.sp)
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(e.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = if (e.isDir) "文件夹" else Format.size(e.size) + " · " + Format.time(e.lastModified),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
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

/** 用 content:// URI 交给系统打开（SAF 里的文件拿不到普通路径）。 */
private fun openSafFile(context: Context, entry: SafEntry, nav: FileNav) {
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(Format.extOf(entry.name)) ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(entry.uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { nav.toast("无法打开：${entry.name}") }
}