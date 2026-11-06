package com.zhiwei.filemanager.ui.tools

import androidx.compose.foundation.background
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
import com.zhiwei.filemanager.core.shell.ShellRunner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SH 脚本执行器。
 *
 * 设计取舍：
 * - 双通道：普通（uid 2000 附近，即应用自身）与 ROOT（su -c）。Shizuku 通道的脚本执行
 *   依赖 Shizuku Binder 客户端，走「终端模拟器」页更合适，本页不重复造。
 * - 脚本默认写进临时文件再 `sh 文件` 执行，而不是拼进 `sh -c "..."`：
 *   拼接会在遇到引号、$、换行时被二次解析，是脚本执行器最常见的坑。
 * - 输出一次性返回（[ShellRunner] 是同步阻塞实现），超时上限可调，避免卡死 UI。
 */
@Composable
fun ScriptScreen(initialPath: String = "/sdcard") {
    val scope = rememberCoroutineScope()

    var scriptPath by remember { mutableStateOf(initialPath.ifBlank { "/sdcard/script.sh" }) }
    var script by remember { mutableStateOf(DEFAULT_SCRIPT) }
    var asRoot by remember { mutableStateOf(false) }
    var timeoutSec by remember { mutableStateOf(60) }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun run() {
        if (running) return
        running = true
        message = null
        output = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) { executeScript(script, asRoot, timeoutSec) }
            running = false
            output = result.first
            message = result.second
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {

        Text("脚本内容", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = script,
            onValueChange = { script = it },
            label = { Text("sh 脚本（支持 # 注释、多行）") },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).padding(top = 4.dp)
        )

        Text("执行通道", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 4.dp)) {
            ChannelChip("普通权限", !asRoot) { asRoot = false }
            ChannelChip("ROOT（su）", asRoot) { asRoot = true }
        }

        Text("超时（秒）", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 4.dp)) {
            listOf(15, 60, 180, 600).forEach { t ->
                ChannelChip(t.toString() + "s", timeoutSec == t) { timeoutSec = t }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            TextButton(onClick = { run() }, enabled = !running) {
                Text(if (running) "执行中…" else "执行脚本", fontSize = 13.sp)
            }
            TextButton(
                onClick = {
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching {
                                val f = File(scriptPath)
                                f.parentFile?.mkdirs()
                                f.writeText(script)
                                "已保存到 " + f.absolutePath + "（" + f.length() + " 字节）"
                            }.getOrElse { "保存失败：" + it.message }
                        }
                        message = r
                    }
                }
            ) { Text("保存为文件", fontSize = 13.sp) }
            TextButton(
                onClick = {
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching {
                                val f = File(scriptPath)
                                if (!f.isFile) "文件不存在：" + scriptPath
                                else { script = f.readText(); "已载入 " + f.absolutePath }
                            }.getOrElse { "载入失败：" + it.message }
                        }
                        message = r
                    }
                }
            ) { Text("从文件载入", fontSize = 13.sp) }
        }

        OutlinedTextField(
            value = scriptPath,
            onValueChange = { scriptPath = it },
            label = { Text("脚本文件路径（用于保存 / 载入）") },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )

        Text("常用脚本", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            PRESETS.forEach { (name, body) ->
                ChannelChip(name, false) { script = body }
            }
        }

        message?.let {
            Text(
                it,
                fontSize = 11.sp,
                color = if (it.contains("失败") || it.contains("不存在")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))

        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Text("输出", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Text(
                text = output.ifBlank { "（尚无输出）" },
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(8.dp).heightIn(min = 120.dp)
            )
        }
    }
}

/**
 * 把脚本落盘成临时文件再执行。
 *
 * @return (输出文本, 状态提示)
 */
private fun executeScript(script: String, asRoot: Boolean, timeoutSec: Int): Pair<String, String> {
    if (script.isBlank()) return "" to "脚本为空"

    val tmp = File.createTempFile("zhiwei_script_", ".sh")
    return try {
        tmp.writeText(script)
        // 注意：这里用 `sh 文件` 而不是把脚本体拼进 -c，避免引号 / $ / 换行被二次解析
        val r = ShellRunner.exec("sh " + quote(tmp.absolutePath), asRoot = asRoot, timeoutSec = timeoutSec.toLong())
        val head = "exit=" + r.code + (if (asRoot) "  [ROOT]" else "  [普通]") + "\n"
        (head + r.output) to if (r.ok) "执行成功" else "执行结束（非 0 退出码）"
    } catch (t: Throwable) {
        "" to "执行失败：" + (t.message ?: t.javaClass.simpleName)
    } finally {
        runCatching { tmp.delete() }
    }
}

/** 单引号包裹路径，防止路径里的空格/中文把命令拆散。 */
private fun quote(path: String): String = "'" + path.replace("'", "'\\''") + "'"

@Composable
private fun ChannelChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(end = 6.dp).clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private val DEFAULT_SCRIPT = """
# 知微脚本执行器示例
echo "当前身份：$(id)"
echo "工作目录：$(pwd)"
""".trimIndent()

/** 常用脚本：都是实际会用到的排查/维护命令。 */
private val PRESETS: List<Pair<String, String>> = listOf(
    "查看身份" to "id\ngetprop ro.product.model\ngetprop ro.build.version.release",
    "列出应用数据" to "ls -l /data/data | head -40",
    "导出已装应用" to "for p in /data/app/*/; do echo \${p}; done | head -60",
    "存储占用" to "df -h /sdcard\ndu -sh /sdcard/Download 2>/dev/null",
    "找大文件" to "find /sdcard -type f -size +100M 2>/dev/null | head -40",
    "清理空目录" to "find /sdcard/Download -type d -empty -print 2>/dev/null | head -40",
    "查 SELinux" to "getenforce\nls -Z /sdcard | head -5"
)