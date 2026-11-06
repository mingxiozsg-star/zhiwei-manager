package com.zhiwei.filemanager.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.filemanager.core.fs.SearchEngine
import com.zhiwei.filemanager.core.fs.SearchHandle
import com.zhiwei.filemanager.core.fs.SearchHit
import com.zhiwei.filemanager.core.fs.SearchMode
import com.zhiwei.filemanager.core.prefs.AppPrefs
import com.zhiwei.filemanager.core.prefs.BookmarkStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * P0：收藏 / 最近访问面板。
 *
 * 与 MT 管理器一致的地方：书签是「跳转目标」而不是「文件对象」，
 * 点击即把当前活动窗口切到该路径，不弹层、不再确认。
 */
@Composable
fun BookmarkSheet(
    state: FileBrowserState,
    onOpen: (String) -> Unit,
    onClose: () -> Unit
) {
    val pane = state.active
    // 读一次本地快照作为 recompose key，保证增删书签后列表即时刷新
    val marks = BookmarkStore.bookmarks
    val recents = BookmarkStore.recents
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("收藏与最近访问", fontSize = 16.sp) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "当前目录：${pane.path}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    TextButton(onClick = { BookmarkStore.toggleBookmark(pane.path) }) {
                        Text(
                            if (BookmarkStore.isBookmarked(pane.path)) "★ 取消收藏" else "☆ 收藏当前目录",
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = { BookmarkStore.pushRecent(pane.path) }) {
                        Text("记入最近", fontSize = 12.sp)
                    }
                }
                HorizontalDivider()

                SectionTitle("书签（${marks.size}）")
                if (marks.isEmpty()) {
                    EmptyHint("还没有书签。进入目标目录后点「☆ 收藏当前目录」。")
                } else {
                    marks.forEach { b ->
                        PathRow(
                            title = b.title,
                            subtitle = b.path,
                            onClick = {
                                onOpen(b.path)
                                onClose()
                            },
                            trailing = "×",
                            onTrailing = { BookmarkStore.removeBookmark(b.path) }
                        )
                    }
                }

                HorizontalDivider()
                SectionTitle("最近访问（${recents.size}）")
                if (recents.isEmpty()) {
                    EmptyHint("暂无记录。")
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { BookmarkStore.clearRecents() }) {
                            Text("清空", fontSize = 12.sp)
                        }
                    }
                    recents.forEach { p ->
                        PathRow(
                            title = File(p).name.ifBlank { p },
                            subtitle = p,
                            onClick = {
                                onOpen(p)
                                onClose()
                            },
                            trailing = "×",
                            onTrailing = { BookmarkStore.removeRecent(p) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("关闭") } }
    )
}

/**
 * P0：递归搜索面板（当前目录 / 含子目录，文件名 / 文件内容）。
 *
 * 扫描跑在 IO 线程，通过 [SearchHandle] 可随时中断；进度与命中回主线程发布，
 * 避免在 IO 回调里直接写 Compose State 造成快照竞争。
 */
@Composable
fun SearchSheet(
    pane: PaneState,
    onOpen: (SearchHit) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(SearchMode.NAME) }
    var recursive by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(0) }
    var current by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<SearchHit>()) }
    var summary by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf(SearchHandle()) }
    // 「仅当前层」在展示层过滤（引擎始终做广度优先遍历，避免维护两套遍历逻辑）
    val shown = if (recursive) hits else hits.filter { File(it.path).parent == pane.path }

    fun start() {
        if (running || query.isBlank()) return
        val h = SearchHandle()
        handle = h
        running = true
        hits = emptyList()
        scanned = 0
        summary = ""
        scope.launch {
            val stats = withContext(Dispatchers.IO) {
                SearchEngine.search(
                    root = pane.path,
                    query = query,
                    mode = mode,
                    showHidden = AppPrefs.showHidden,
                    maxResults = if (recursive) 500 else 100,
                    handle = h,
                    onProgress = { n, cur ->
                        scope.launch(Dispatchers.Main.immediate) {
                            scanned = n
                            current = cur
                        }
                    },
                    onHit = { hit ->
                        scope.launch(Dispatchers.Main.immediate) {
                            if (hits.size < 300) hits = hits + hit
                        }
                    }
                )
            }
            scanned = stats.scanned
            summary = "扫描 ${stats.scanned} 项 · 命中 ${stats.hits} · 跳过二进制 ${stats.skippedBinary} · 越权 ${stats.denied}"
            running = false
        }
    }

    AlertDialog(
        onDismissRequest = {
            handle.cancel()
            onClose()
        },
        title = { Text("搜索 · " + pane.title, fontSize = 16.sp) },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("关键词") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TextButton(onClick = { mode = SearchMode.NAME }) {
                        Text(if (mode == SearchMode.NAME) "● 文件名" else "○ 文件名", fontSize = 12.sp)
                    }
                    TextButton(onClick = { mode = SearchMode.CONTENT }) {
                        Text(if (mode == SearchMode.CONTENT) "● 内容" else "○ 内容", fontSize = 12.sp)
                    }
                    TextButton(onClick = { recursive = !recursive }) {
                        Text(if (recursive) "● 含子目录" else "○ 仅当前层", fontSize = 12.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (running) handle.cancel() else start() }) {
                        Text(if (running) "停止" else "开始搜索", fontSize = 12.sp)
                    }
                    Text(
                        if (running) "已扫描 $scanned" else summary,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (running && current.isNotEmpty()) {
                    Text(
                        current,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider()
                Text(
                    "结果 ${shown.size}" + if (hits.size >= 300) "（已截断）" else "",
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Column(
                    Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    shown.forEach { hit ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    handle.cancel()
                                    onOpen(hit)
                                    onClose()
                                }
                                .padding(vertical = 3.dp)
                        ) {
                            Text(
                                hit.name,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                buildString {
                                    append(hit.path)
                                    if (hit.lineNo > 0) append(" · 第 ${hit.lineNo} 行")
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (hit.snippet.isNotBlank()) {
                                Text(
                                    hit.snippet,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                handle.cancel()
                onClose()
            }) { Text("关闭") }
        }
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun PathRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: String,
    onTrailing: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            trailing,
            fontSize = 14.sp,
            modifier = Modifier
                .clickable(onClick = onTrailing)
                .padding(horizontal = 8.dp)
        )
    }
}
/**
 * 搜索「文件夹」：只列目录（不列文件），点一条即把窗口直接下钻过去。
 *
 * 与 [SearchSheet]（搜文件）的分工：这是「导航式搜索」——
 * 搜文件的结果点击是「定位到它所在的目录」，而本面板的结果点击是「**进入**该目录」。
 * 广度优先遍历，命中结果按离当前目录由近及远出现。
 */
@Composable
fun FolderSearchSheet(
    pane: PaneState,
    onOpen: (String) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var recursive by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(0) }
    var dirs by remember { mutableStateOf(emptyList<Pair<String, Int>>()) }
    var summary by remember { mutableStateOf("") }

    fun start() {
        val needle = query.trim().lowercase()
        if (running || needle.isEmpty()) return
        running = true
        dirs = emptyList()
        scanned = 0
        summary = ""
        val root = pane.path
        val maxDepth = if (recursive) 12 else 1
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                val out = mutableListOf<Pair<String, Int>>()
                var count = 0
                val queue = ArrayDeque<Pair<File, Int>>()
                queue.addLast(File(root) to 0)
                while (queue.isNotEmpty() && out.size < 300) {
                    val (dir, d) = queue.removeFirst()
                    val children = try {
                        dir.listFiles()
                    } catch (t: Throwable) {
                        null
                    } ?: continue
                    for (child in children) {
                        count++
                        if (!child.isDirectory) continue
                        if (child.name.lowercase().contains(needle)) {
                            val kids = try {
                                child.listFiles()?.size ?: 0
                            } catch (t: Throwable) {
                                0
                            }
                            out += child.absolutePath to kids
                        }
                        if (d + 1 <= maxDepth) queue.addLast(child to (d + 1))
                    }
                }
                (count to out)
            }
            scanned = found.first
            dirs = found.second
            summary = "扫描 ${found.first} 个条目 · 命中 ${found.second.size} 个文件夹"
            running = false
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("搜索文件夹 · " + pane.title, fontSize = 16.sp) },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("文件夹名关键词") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { recursive = !recursive }) {
                        Text(if (recursive) "● 含子目录" else "○ 仅当前层", fontSize = 12.sp)
                    }
                    TextButton(onClick = { start() }) {
                        Text(if (running) "搜索中…" else "开始搜索", fontSize = 12.sp)
                    }
                }
                if (summary.isNotEmpty()) {
                    Text(
                        summary,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (running) {
                    Text(
                        "已扫描 $scanned",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    if (dirs.isEmpty() && !running) {
                        EmptyHint("输入关键词后点「开始搜索」。结果点击即进入该目录。")
                    }
                    dirs.forEach { (path, kids) ->
                        PathRow(
                            title = File(path).name,
                            subtitle = path + " · $kids 个子项",
                            onClick = { onOpen(path) },
                            trailing = "进入",
                            onTrailing = { onOpen(path) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("关闭") } }
    )
}
