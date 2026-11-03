package com.zhiwei.filemanager.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.filemanager.core.fs.FileOps
import com.zhiwei.filemanager.core.prefs.AppPrefs
import com.zhiwei.filemanager.core.task.AppScope
import com.zhiwei.filemanager.tool.codec.DexReader
import com.zhiwei.filemanager.tool.codec.SmaliEngine
import com.zhiwei.filemanager.tool.diff.DiffTool
import com.zhiwei.filemanager.tool.hex.HexTool
import com.zhiwei.filemanager.tool.text.EncodingTool
import com.zhiwei.filemanager.tool.text.StringCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ToolPage(val title: String, val desc: String) {
    STRING("字符串工具箱", "Base64 / Hex / Unicode / URL / 哈希 / DES / JSON / 时间戳"),
    ENCODING("文件夹编码转换", "GBK <-> UTF-8 批量转码（带编码探测）"),
    HEX("Hex 查看器", "十六进制浏览与二进制搜索"),
    DIFF("文本对比", "统一 diff 对比两段文本或两个文件"),
    DEX("Dex / Smali 工具", "属性、字符串常量、反汇编、汇编、合并、分割"),
    EDITOR("文本编辑器", "编辑 / 另存 / XML、JSON 格式化")
}

@Composable
fun ToolboxScreen(onOpen: (ToolPage) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        Text("工具箱", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "共 ${ToolPage.values().size} 个可用工具，其余功能进度见「设置 → 功能路线图」",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ToolPage.values().forEach { page ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable { onOpen(page) }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(page.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(page.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun StringToolsScreen() {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("12345678") }

    fun apply(block: (String) -> String?) {
        output = try {
            block(input) ?: "【失败】输入不合法或格式不支持"
        } catch (t: Throwable) {
            "【异常】${t.message}"
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("输入") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("DES 密钥（8 字节）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
        val actions: List<Pair<String, () -> Unit>> = listOf(
            "Base64 编码" to { apply { StringCodec.base64Encode(it) } },
            "Base64 解码" to { apply { StringCodec.base64Decode(it) } },
            "Hex 编码" to { apply { StringCodec.hexEncode(it) } },
            "Hex 解码" to { apply { StringCodec.hexDecode(it) } },
            "Unicode 编码" to { apply { StringCodec.toUnicode(it) } },
            "Unicode 解码" to { apply { StringCodec.fromUnicode(it) } },
            "URL 编码" to { apply { StringCodec.urlEncode(it) } },
            "URL 解码" to { apply { StringCodec.urlDecode(it) } },
            "MD5" to { apply { StringCodec.md5(it) } },
            "SHA-1" to { apply { StringCodec.sha1(it) } },
            "SHA-256" to { apply { StringCodec.sha256(it) } },
            "DES 加密" to { apply { StringCodec.desEncrypt(it, key) } },
            "DES 解密" to { apply { StringCodec.desDecrypt(it, key) } },
            "去空格" to { apply { StringCodec.stripSpaces(it) } },
            "去空行" to { apply { StringCodec.stripBlankLines(it) } },
            "去换行" to { apply { StringCodec.stripNewlines(it) } },
            "反转" to { apply { StringCodec.reverse(it) } },
            "JSON 美化" to { apply { StringCodec.jsonPretty(it) } },
            "JSON 压缩" to { apply { StringCodec.jsonCompact(it) } },
            "JSON 校验" to { apply { if (StringCodec.isJson(it)) "合法 JSON" else null } },
            "时间戳→日期" to { apply { StringCodec.timestampToDate(it) } },
            "日期→时间戳" to { apply { StringCodec.dateToTimestamp(it) } },
            "统计" to { apply { StringCodec.stats(it) } }
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("操作", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, action) ->
                    TextButton(onClick = action, modifier = Modifier.weight(1f)) { Text(label, fontSize = 12.sp) }
                }
                repeat(3 - row.size) { Text("", modifier = Modifier.weight(1f)) }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("输出", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text(
                text = output.ifEmpty { "（结果在此处显示）" },
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(10.dp)
            )
        }
        Row {
            TextButton(onClick = { input = output }) { Text("结果回填输入", fontSize = 12.sp) }
            TextButton(onClick = { output = "" }) { Text("清空输出", fontSize = 12.sp) }
        }
    }
}

@Composable
fun EncodingScreen(initialPath: String = "/sdcard") {
    var path by remember { mutableStateOf(initialPath) }
    var from by remember { mutableStateOf("GBK") }
    var to by remember { mutableStateOf("UTF-8") }
    var recursive by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        Text("文件夹编码转换", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text("目标文件夹") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        CharsetRow("源编码", from, EncodingTool.COMMON_CHARSETS) { from = it }
        CharsetRow("目标编码", to, EncodingTool.COMMON_CHARSETS) { to = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { recursive = !recursive }) {
                Text("递归子目录：${if (recursive) "开" else "关"}", fontSize = 12.sp)
            }
        }
        Text(
            "探测规则：严格校验 UTF-8 字节序列，非法则按 GBK 处理；只转换源编码匹配的文件。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = {
            result = "转换中…"
            AppScope.io.launch {
                val r = EncodingTool.convertTree(path, from, to, recursive)
                result = r.summary()
            }
        }) { Text("开始转换") }
        TextButton(onClick = {
            result = "探测中…"
            AppScope.io.launch {
                val f = File(path)
                result = if (f.isDirectory) {
                    val files = EncodingTool.collect(f, false, setOf("txt", "java", "kt", "xml", "json", "smali", "md", "csv", "log"))
                    files.take(30).joinToString("\n") { "${it.name} -> ${EncodingTool.sniff(it.absolutePath)}" }.ifEmpty { "无匹配文件" }
                } else {
                    "${f.name} -> ${EncodingTool.sniff(path)}"
                }
            }
        }) { Text("试探测编码（前 30 个）") }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text(result.ifEmpty { "（等待转换）" }, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun CharsetRow(label: String, current: String, options: List<String>, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            options.take(4).forEach { c ->
                TextButton(onClick = { onSelect(c) }) {
                    Text(if (c == current) "●$c" else c, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun HexViewerScreen(initialPath: String) {
    var path by remember { mutableStateOf(initialPath) }
    var offset by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(0L) }
    var lines by remember { mutableStateOf(emptyList<com.zhiwei.filemanager.tool.hex.HexLine>()) }
    var pattern by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<Long>()) }
    val pageSize = 200

    LaunchedEffect(path, offset) {
        total = withContext(Dispatchers.IO) { HexTool.fileSize(path) }
        lines = withContext(Dispatchers.IO) { HexTool.readPage(path, offset, pageSize) }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        OutlinedTextField(
            value = path,
            onValueChange = { path = it; offset = 0L },
            label = { Text("文件路径") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { offset = 0L }) { Text("⏮", fontSize = 12.sp) }
            TextButton(onClick = { offset = (offset - pageSize * 16L).coerceAtLeast(0L) }) { Text("上一页", fontSize = 12.sp) }
            TextButton(onClick = { offset += pageSize * 16L }) { Text("下一页", fontSize = 12.sp) }
            TextButton(onClick = { offset = ((total - pageSize * 16L).coerceAtLeast(0L) / 16L) * 16L }) { Text("末页", fontSize = 12.sp) }
        }
        Text(
            "偏移 0x${java.lang.Long.toHexString(offset).uppercase()} · 共 ${total} 字节",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                label = { Text("搜索（hex，如 1F8B08，或直接文本）") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                AppScope.io.launch {
                    val hex = HexTool.parseHexPattern(pattern)
                    val bytes = hex ?: pattern.toByteArray(Charsets.UTF_8)
                    val result = HexTool.search(path, bytes, 60)
                    hits = result
                }
            }) { Text("搜索", fontSize = 12.sp) }
        }
        if (hits.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = { offset = (hits.first() / 16L) * 16L }) {
                    Text("命中 ${hits.size} 处，点击跳转首个：0x${java.lang.Long.toHexString(hits.first()).uppercase()}", fontSize = 11.sp)
                }
            }
        }
        HorizontalDivider()
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
            androidx.compose.foundation.lazy.items(lines) { line ->
                Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text(
                        "${java.lang.Long.toHexString(line.offset).padStart(8, '0').uppercase()}  ${line.hex}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text("          |${line.ascii}|", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun DiffScreen(initialA: String = "", initialB: String = "") {
    var a by remember { mutableStateOf(initialA) }
    var b by remember { mutableStateOf(initialB) }
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        OutlinedTextField(
            value = a,
            onValueChange = { a = it },
            label = { Text("文本 A / 文件路径 A") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
        )
        OutlinedTextField(
            value = b,
            onValueChange = { b = it },
            label = { Text("文本 B / 文件路径 B") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
        )
        Row {
            TextButton(onClick = {
                scope.launch {
                    val r = withContext(Dispatchers.IO) { DiffTool.unified(a, b) }
                    result = r
                }
            }) { Text("对比（作为文本）") }
            TextButton(onClick = {
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        val fa = File(a.trim()).takeIf { it.isFile }?.readText()
                        val fb = File(b.trim()).takeIf { it.isFile }?.readText()
                        if (fa == null || fb == null) "路径无效：需为两个已存在的文件"
                        else DiffTool.stats(fa, fb) + "\n\n" + DiffTool.unified(fa, fb, File(a.trim()).name, File(b.trim()).name)
                    }
                    result = r
                }
            }) { Text("对比（作为文件）") }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(result.ifEmpty { "（结果在此处显示）" }, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun DexToolScreen(initialPath: String) {
    var path by remember { mutableStateOf(initialPath) }
    var output by remember { mutableStateOf("") }

    fun run(title: String, block: () -> String) {
        output = "[$title] 执行中…"
        AppScope.io.launch {
            output = "[$title]\n" + try {
                block()
            } catch (t: Throwable) {
                "失败：${t.javaClass.simpleName} ${t.message}"
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text("dex / apk / jar 路径，或 smali 目录路径") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = {
                run("属性") { DexReader.read(path)?.summary() ?: "不是有效的 dex 文件" }
            }) { Text("属性", fontSize = 12.sp) }
            TextButton(onClick = {
                run("字符串常量") {
                    val info = DexReader.read(path) ?: return@run "不是有效的 dex 文件"
                    "共 ${info.strings.size} 条，前 300 条：\n" + info.strings.take(300).joinToString("\n")
                }
            }) { Text("字符串常量", fontSize = 12.sp) }
            TextButton(onClick = {
                run("混淆字典") { DexReader.read(path)?.obfuscationDict() ?: "不是有效的 dex 文件" }
            }) { Text("混淆字典", fontSize = 12.sp) }
            TextButton(onClick = {
                run("引擎") { SmaliEngine.engineInfo() }
            }) { Text("引擎信息", fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = {
                val f = File(path)
                val outDir = File(f.parentFile, f.nameWithoutExtension + "_smali").absolutePath
                run("反汇编") { SmaliEngine.disassemble(path, outDir).let { it.message + "（输出：$outDir）" } }
            }) { Text("dex → smali", fontSize = 12.sp) }
            TextButton(onClick = {
                val f = File(path)
                val outDex = File(f.parentFile, f.name.removeSuffix("/") + ".dex").absolutePath
                run("汇编") { SmaliEngine.assemble(path, outDex).message }
            }) { Text("smali → dex", fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = {
                run("合并") {
                    val parent = File(path).parentFile
                    val dexes = parent?.listFiles()?.filter { it.extension == "dex" }?.map { it.absolutePath } ?: emptyList()
                    if (dexes.size < 2) "同目录下少于 2 个 dex"
                    else SmaliEngine.merge(dexes, File(parent, "merged.dex").absolutePath).message
                }
            }) { Text("合并同目录 dex", fontSize = 12.sp) }
            TextButton(onClick = {
                run("分割") {
                    val f = File(path)
                    SmaliEngine.split(path, File(f.parentFile, f.nameWithoutExtension + "_split").absolutePath, 2000).message
                }
            }) { Text("分割 dex", fontSize = 12.sp) }
        }
        Text("提示：反汇编 / 汇编 / 合并 / 分割依赖 smali 引擎，若引擎未就绪会直接给出原因。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text(output.ifEmpty { "（操作结果在此显示）" }, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun EditorScreen(initialPath: String) {
    var path by remember { mutableStateOf(initialPath) }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(path) {
        val loaded = withContext(Dispatchers.IO) { FileOps.readText(path, AppPrefs.editorCharset) }
        if (loaded != null) text = loaded
        status = if (loaded == null) "读取失败或文件不存在" else "已加载（${AppPrefs.editorCharset}，${loaded.length} 字符）"
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text("文件路径") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = {
                scope.launch { status = if (FileOps.writeText(path, text, AppPrefs.editorCharset)) "已保存" else "保存失败" }
            }) { Text("保存", fontSize = 12.sp) }
            TextButton(onClick = {
                val target = FileOps.uniqueTarget(File(path).parent ?: "/sdcard", File(path).name + ".copy")
                scope.launch {
                    status = if (FileOps.writeText(target.absolutePath, text, AppPrefs.editorCharset)) "已另存为 ${target.name}" else "另存失败"
                }
            }) { Text("另存副本", fontSize = 12.sp) }
            TextButton(onClick = { text = formatXml(text); status = "XML 已格式化" }) { Text("XML 格式化", fontSize = 12.sp) }
            TextButton(onClick = {
                status = StringCodec.jsonPretty(text)?.also { text = it } ?: "不是合法 JSON"
            }) { Text("JSON 美化", fontSize = 12.sp) }
            TextButton(onClick = {
                status = StringCodec.jsonCompact(text)?.also { text = it } ?: "不是合法 JSON"
            }) { Text("JSON 压缩", fontSize = 12.sp) }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("内容") },
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        )
        Text(status.ifEmpty { "" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatXml(src: String): String {
    val lines = src.replace("><", ">\n<").split("\n")
    var indent = 0
    return lines.joinToString("\n") { raw ->
        val line = raw.trim()
        if (line.startsWith("</")) indent = (indent - 1).coerceAtLeast(0)
        val out = "    ".repeat(indent) + line
        if (line.startsWith("<") && !line.startsWith("</") && !line.startsWith("<?") && !line.endsWith("/>") && !line.contains("</")) {
            indent++
        }
        out
    }
}
