package com.zhiwei.filemanager.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.filemanager.core.fs.FileOps
import com.zhiwei.filemanager.core.util.ApkInspector
import com.zhiwei.filemanager.core.util.Format
import com.zhiwei.filemanager.tool.archive.ArchiveTool
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * APK 反编译工具箱。
 *
 * 「缝合」思路：不引入 apktool 二进制（几十 MB 且需要 aapt 资产），
 * 而是把已内置的能力串成一条链路：
 *   解析（[ApkInspector]）→ 解包（commons-compress）→ 提取 classes*.dex
 *   → 交给内置 Dex/Smali 引擎（smali + baksmali + dexlib2）反汇编
 *   → 交给字符串工具箱 / Hex 查看器做进一步分析。
 * 每一步的产物都落在真实目录里，用户能接着在双窗口里直接操作。
 */
@Composable
fun ApkToolScreen(
    initialPath: String = "/sdcard",
    onOpenDex: (String) -> Unit,
    onOpenHex: (String) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var path by remember { mutableStateOf(initialPath.ifBlank { "/sdcard" }) }
    var summary by remember { mutableStateOf<ApkInspector.Summary?>(null) }
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("选择一个 APK 后点「解析」开始。") }

    suspend fun inspect(p: String) {
        busy = true
        log = "正在解析 " + p
        val s = withContext(Dispatchers.IO) { ApkInspector.inspect(context, File(p)) }
        summary = s
        log = "解析完成：\n" +
            "包名 " + s.packageName + "\n" +
            "版本 " + s.versionName + " (" + s.versionCode + ")\n" +
            "签名 v1=" + s.signature.v1 + " v2=" + s.signature.v2 + " v3=" + s.signature.v3 + "\n" +
            "加固 " + (s.hardening ?: "未命中已知特征") + "\n" +
            "条目 " + s.entryCount + " · dex " + s.dexCount + " · so " + s.soCount
        busy = false
    }

    // 从文件页长按「Dex / Apk 工具」进来时，路径是现成的，直接解析
    LaunchedEffect(initialPath) {
        if (initialPath.isNotBlank() && File(initialPath).isFile) inspect(initialPath)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {

        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text("APK 路径") },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            TextButton(onClick = { scope.launch { inspect(path) } }, enabled = !busy) {
                Text(if (busy) "解析中…" else "解析", fontSize = 13.sp)
            }
            TextButton(
                onClick = {
                    onToast("已复制包名：" + (summary?.packageName ?: "（先解析）"))
                }
            ) { Text("复制包名", fontSize = 13.sp) }
            TextButton(onClick = { onOpenHex(path) }) { Text("Hex 查看", fontSize = 13.sp) }
            TextButton(onClick = { onOpenDex(path) }) { Text("Dex / Smali 工具", fontSize = 13.sp) }
        }

        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        summary?.let { s ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            InfoLine("应用", s.label)
            InfoLine("包名", s.packageName)
            InfoLine("版本", s.versionName + " (" + s.versionCode + ")")
            InfoLine("SDK", "min " + s.minSdk + " / target " + s.targetSdk)
            InfoLine("Activity", s.activityCount.toString() + " 个")
            InfoLine(
                "签名",
                buildString {
                    append(if (s.signature.v1) "v1 ✓ " else "v1 ✗ ")
                    append(if (s.signature.v2) "v2 ✓ " else "v2 ✗ ")
                    append(if (s.signature.v3) "v3 ✓" else "v3 ✗")
                } + if (s.signature.signerFiles.isEmpty()) "" else "  (" + s.signature.signerFiles.joinToString("、") + ")"
            )
            InfoLine("加固", s.hardening ?: "未命中已知特征")
            InfoLine("结构", s.entryCount.toString() + " 条目 · " + s.dexCount + " dex · " + s.soCount + " so")

            if (s.permissions.isNotEmpty()) {
                Text(
                    "权限（" + s.permissions.size + "）",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
                s.permissions.forEach {
                    Text(
                        "· " + it,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("一键操作", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp)) {
                ToolChip("解包到目录") {
                    scope.launch {
                        busy = true
                        val r = withContext(Dispatchers.IO) { unpackApk(File(path)) }
                        busy = false
                        log = r
                    }
                }
                ToolChip("只提取 dex") {
                    scope.launch {
                        busy = true
                        val r = withContext(Dispatchers.IO) { extractDex(File(path)) }
                        busy = false
                        log = r
                    }
                }
                ToolChip("列出 dex 清单") {
                    scope.launch {
                        busy = true
                        val r = withContext(Dispatchers.IO) { listDex(File(path)) }
                        busy = false
                        log = r
                    }
                }
                ToolChip("列出 so 清单") {
                    scope.launch {
                        busy = true
                        val r = withContext(Dispatchers.IO) { listSo(File(path)) }
                        busy = false
                        log = r
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Text("输出", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Text(
                text = log,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(8.dp).heightIn(min = 120.dp)
            )
        }

        Text(
            "说明：解包与 dex 提取使用项目内置的 commons-compress / zip，不依赖外部 apktool 二进制；" +
                "反汇编交给内置 smali / baksmali 引擎（Dex / Smali 工具页）。",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** 整包解压到同级 `<名字>_unpacked` 目录。 */
private fun unpackApk(apk: File): String {
    if (!apk.isFile) return "文件不存在：" + apk.absolutePath
    return runCatching {
        val parent = apk.parentFile ?: File("/sdcard")
        val dest = FileOps.uniqueTarget(parent.absolutePath, apk.nameWithoutExtension + "_unpacked")
        ArchiveTool.extract(apk.absolutePath, dest.absolutePath)
        "解包完成：\n" + dest.absolutePath + "\n（可在双窗口里直接查看 assets / res / META-INF）"
    }.getOrElse { "解包失败：" + (it.message ?: it.javaClass.simpleName) }
}

/** 只抽 classes*.dex，产出可直接喂给 baksmali 的目录。 */
private fun extractDex(apk: File): String {
    if (!apk.isFile) return "文件不存在：" + apk.absolutePath
    return runCatching {
        val parent = apk.parentFile ?: File("/sdcard")
        val dest = FileOps.uniqueTarget(parent.absolutePath, apk.nameWithoutExtension + "_dex")
        dest.mkdirs()
        var count = 0
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val name = entry.name
                val flat = !name.contains('/') && name.lowercase().endsWith(".dex")
                if (flat) {
                    val out = File(dest, name)
                    zip.getInputStream(entry).use { input ->
                        out.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                    }
                    count++
                }
            }
        }
        if (count == 0) "没抽到 classes*.dex（可能是加固壳把 dex 抽走了）"
        else "已提取 " + count + " 个 dex：\n" + dest.absolutePath
    }.getOrElse { "提取失败：" + (it.message ?: it.javaClass.simpleName) }
}

private fun listDex(apk: File): String = runCatching {
    val rows = ArrayList<String>()
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.lowercase().endsWith(".dex") }
            .sortedBy { it.name }
            .forEach { rows += it.name + "   " + Format.size(it.size) }
    }
    if (rows.isEmpty()) "包内没有 .dex 条目" else "dex 清单（" + rows.size + "）：\n" + rows.joinToString("\n")
}.getOrElse { "读取失败：" + (it.message ?: it.javaClass.simpleName) }

private fun listSo(apk: File): String = runCatching {
    val rows = ArrayList<String>()
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.lowercase().endsWith(".so") }
            .sortedBy { it.name }
            .forEach { rows += it.name + "   " + Format.size(it.size) }
    }
    if (rows.isEmpty()) "包内没有 .so 条目" else {
        "so 清单（" + rows.size + "）：\n" + rows.take(200).joinToString("\n") +
            if (rows.size > 200) "\n…（已截断）" else ""
    }
}.getOrElse { "读取失败：" + (it.message ?: it.javaClass.simpleName) }

@Composable
private fun InfoLine(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = key,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ToolChip(label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(end = 6.dp).clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}