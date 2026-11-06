package com.zhiwei.filemanager.ui.preview

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaPlayer
import android.webkit.WebView
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.zhiwei.filemanager.core.util.ApkInspector
import com.zhiwei.filemanager.core.util.FileKind
import com.zhiwei.filemanager.core.util.Format
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 通用文件预览器。
 *
 * 覆盖范围与取舍：
 * - 图片：自带降采样解码（大图不会 OOM）+ EXIF 方向校正
 * - 视频：VideoView + 媒体控制器（不引 ExoPlayer，少一份依赖与体积）
 * - 音频：MediaPlayer + 进度条（可拖动定位）
 * - 网页：WebView 加载本地 file://（必须显式开 allowFileAccess，targetSdk 30+ 默认为 false）
 * - 文本/代码：只读渲染（超过 2 MB 截断提示，避免一次读进几百 MB）
 * - APK：走 [ApkInspector] 出包名/版本/权限/签名/加固判定
 * - 归档：ZipFile 列条目（zip/jar/apk 系）
 * - 字体：Typeface 直接渲染示例字
 * - PDF / Office：系统没有解码器，不假装能渲染，改为引导外部打开
 */
@Composable
fun PreviewScreen(
    path: String,
    onOpenHex: (String) -> Unit,
    onEditText: (String) -> Unit,
    onOpenDex: (String) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val file = remember(path) { File(path) }
    val ext = remember(path) { Format.extOf(file.name) }
    val kind = remember(path) { Format.kindOf(file.name, false) }

    val texty = remember(path) { Format.isTextLikely(file.name) }
    val webish = ext == "html" || ext == "htm" || ext == "mhtml"

    Column(Modifier.fillMaxSize()) {
        // —— 头部：文件名 + 体积 / 时间 ——
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = file.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = (if (file.isFile) Format.size(file.length()) else "—") + " · " +
                    (if (file.exists()) Format.time(file.lastModified()) else "文件不存在"),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !file.exists() -> PreviewHint("文件不存在或已被移动", path)
                file.isDirectory -> PreviewHint("这是目录，请返回文件列表浏览", path)
                kind == FileKind.IMAGE -> ImagePreview(file)
                kind == FileKind.VIDEO -> VideoPreview(file)
                kind == FileKind.AUDIO -> AudioPreview(file)
                kind == FileKind.FONT -> FontPreview(file)
                kind == FileKind.APK -> ApkPreview(context, file)
                kind == FileKind.ARCHIVE -> ZipListPreview(file)
                webish -> WebPreview(file)
                kind == FileKind.PDF -> PreviewHint(
                    "系统未内置 PDF 渲染引擎，已提供「外部打开」",
                    "后续版本接入 pdfbox-android 后可直接在这里翻页"
                )
                kind == FileKind.DOC -> PreviewHint(
                    "Office 文档需要外部应用打开",
                    "推荐用 WPS / Office 打开，或先另存为文本"
                )
                texty -> TextPreview(file, onToast)
                else -> HexHeadPreview(file, onToast)
            }
        }

        HorizontalDivider()
        // —— 底部动作 ——
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewAction("外部打开") { openExternal(context, file, onToast) }
            PreviewAction("分享") { shareFile(context, file, onToast) }
            PreviewAction("Hex 查看") { onOpenHex(path) }
            if (texty) PreviewAction("文本编辑") { onEditText(path) }
            if (kind == FileKind.DEX || kind == FileKind.APK) PreviewAction("Dex / Smali") { onOpenDex(path) }
        }
    }
}

// ————————————————————————— 图片 —————————————————————————

@Composable
private fun ImagePreview(file: File) {
    var bitmap by remember(file.absolutePath) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(file.absolutePath) { mutableStateOf(false) }

    LaunchedEffect(file.absolutePath) {
        val bmp = withContext(Dispatchers.IO) { runCatching { decodeSampled(file, 2400) }.getOrNull() }
        if (bmp == null) failed = true else bitmap = bmp
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        when {
            failed -> PreviewHint("图片解码失败（格式不受支持或文件损坏）", file.absolutePath)
            bmp == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("解码中…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(6.dp)
            )
        }
    }
}

/** 按最长边降采样解码，顺带按 EXIF 旋转，避免大图 OOM 与「横竖颠倒」。 */
private fun decodeSampled(file: File, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) {
        sample *= 2
    }
    val raw = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: return null

    val degrees = runCatching {
        @Suppress("DEPRECATION")
        val exif = android.media.ExifInterface(file.absolutePath)
        @Suppress("DEPRECATION")
        val orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
        when (orientation) {
            6 -> 90f
            3 -> 180f
            8 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)

    if (degrees == 0f) return raw
    return runCatching {
        val m = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (rotated !== raw) raw.recycle()
        rotated
    }.getOrDefault(raw)
}

// ————————————————————————— 视频 —————————————————————————

@Composable
private fun VideoPreview(file: File) {
    var error by remember(file.absolutePath) { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    setOnErrorListener { _, what, extra ->
                        error = "播放失败（code=$what/$extra）"
                        true
                    }
                    runCatching {
                        setVideoPath(file.absolutePath)
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setOnPreparedListener { it.isLooping = false; start() }
                    }.onFailure { error = it.message }
                }
            }
        )
        error?.let {
            Text(
                text = it + "\n可改用「外部打开」交给系统播放器",
                fontSize = 12.sp,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

// ————————————————————————— 音频 —————————————————————————

@Composable
private fun AudioPreview(file: File) {
    val player = remember(file.absolutePath) { MediaPlayer() }
    var playing by remember(file.absolutePath) { mutableStateOf(false) }
    var position by remember(file.absolutePath) { mutableStateOf(0) }
    var duration by remember(file.absolutePath) { mutableStateOf(0) }
    var error by remember(file.absolutePath) { mutableStateOf<String?>(null) }

    DisposableEffect(file.absolutePath) {
        runCatching {
            player.reset()
            player.setDataSource(file.absolutePath)
            player.prepare()
            duration = player.duration
        }.onFailure { error = it.message ?: "无法准备音频" }
        player.setOnCompletionListener { playing = false }
        onDispose { runCatching { player.release() } }
    }

    LaunchedEffect(playing) {
        while (playing) {
            position = runCatching { player.currentPosition }.getOrDefault(0)
            duration = runCatching { player.duration }.getOrDefault(duration)
            delay(400)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("♪", fontSize = 54.sp, color = MaterialTheme.colorScheme.primary)
        Text(file.name, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        error?.let {
            Text(
                it,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (duration > 0) {
            Slider(
                value = position.toFloat(),
                onValueChange = { v ->
                    position = v.toInt()
                    runCatching { player.seekTo(position) }
                },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            Text(
                text = "${position / 1000}s / ${duration / 1000}s",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(Modifier.padding(top = 12.dp)) {
            PreviewAction(if (playing) "暂停" else "播放") {
                runCatching {
                    if (playing) player.pause() else player.start()
                }.onFailure { error = it.message }
                playing = !playing
            }
            PreviewAction("回到开头") {
                runCatching { player.seekTo(0) }
                position = 0
            }
        }
    }
}

// ————————————————————————— 字体 —————————————————————————

@Composable
private fun FontPreview(file: File) {
    var typeface by remember(file.absolutePath) { mutableStateOf<android.graphics.Typeface?>(null) }

    LaunchedEffect(file.absolutePath) {
        typeface = withContext(Dispatchers.IO) {
            runCatching { android.graphics.Typeface.createFromFile(file) }.getOrNull()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        val tf = typeface
        if (tf == null) {
            Text(
                "字体加载失败（可能是 ttc 集合或非标准字体）",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "字体已加载 · " + file.name,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            listOf(24, 20, 16, 13).forEach { size ->
                Text(
                    text = FONT_SAMPLE,
                    fontSize = size.sp,
                    fontFamily = FontFamily(tf),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

private const val FONT_SAMPLE =
    "The quick brown fox 0123456789\n" +
        "始源创想科技IT · 知微管理器\n" +
        "永东国福鼎，一二三四五六七八九十\n" +
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ\n" +
        "abcdefghijklmnopqrstuvwxyz"

// ————————————————————————— 文本 —————————————————————————

@Composable
private fun TextPreview(file: File, onToast: (String) -> Unit) {
    val limit = 2L * 1024L * 1024L
    var text by remember(file.absolutePath) { mutableStateOf<String?>(null) }
    var truncated by remember(file.absolutePath) { mutableStateOf(false) }

    LaunchedEffect(file.absolutePath) {
        val out = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = minOf(file.length(), limit).toInt()
                java.io.FileInputStream(file).use { input ->
                    val buf = ByteArray(bytes)
                    var read = 0
                    while (read < bytes) {
                        val n = input.read(buf, read, bytes - read)
                        if (n <= 0) break
                        read += n
                    }
                    String(buf, 0, read, Charsets.UTF_8)
                }
            }.getOrElse { e ->
                onToast("读取失败：" + e.message)
                ""
            }
        }
        truncated = file.length() > limit
        text = out
    }

    val body = text
    if (body == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LinearProgressIndicator() }
        return
    }
    Column(Modifier.fillMaxSize()) {
        if (truncated) {
            Text(
                text = "文件过大，只预览前 2 MB（完整内容请用文本编辑器打开）",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Text(
            text = body,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(10.dp)
        )
    }
}

// ————————————————————————— 网页 —————————————————————————

@Composable
@Suppress("DEPRECATION")
private fun WebPreview(file: File) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.allowFileAccess = true
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                runCatching { loadUrl(file.toURI().toString()) }
            }
        },
        onRelease = { it.destroy() }
    )
}

// ————————————————————————— APK —————————————————————————

@Composable
private fun ApkPreview(context: Context, file: File) {
    var summary by remember(file.absolutePath) { mutableStateOf<ApkInspector.Summary?>(null) }

    LaunchedEffect(file.absolutePath) {
        summary = withContext(Dispatchers.IO) { ApkInspector.inspect(context, file) }
    }

    val s = summary
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LinearProgressIndicator() }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            s.icon?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 10.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(s.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(s.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        PreviewInfo("版本", s.versionName + " (" + s.versionCode + ")")
        PreviewInfo("SDK", "min " + s.minSdk + " · target " + s.targetSdk)
        PreviewInfo(
            "签名",
            buildString {
                append(if (s.signature.v1) "v1 ✓ " else "v1 ✗ ")
                append(if (s.signature.v2) "v2 ✓ " else "v2 ✗ ")
                append(if (s.signature.v3) "v3 ✓" else "v3 ✗")
            }
        )
        if (s.signature.signerFiles.isNotEmpty()) {
            PreviewInfo("证书文件", s.signature.signerFiles.joinToString("、").take(160))
        }
        PreviewInfo("加固判定", s.hardening ?: "未命中已知加固特征（可能是原包或自研壳）")
        PreviewInfo("条目数", s.entryCount.toString() + " 项 · dex " + s.dexCount + " · so " + s.soCount)
        PreviewInfo("Activity", s.activityCount.toString() + " 个")

        if (s.permissions.isNotEmpty()) {
            Text(
                text = "权限（" + s.permissions.size + "）",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
            s.permissions.forEach { p ->
                Text(
                    text = "· " + p.substringAfterLast('.'),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ————————————————————————— 归档 —————————————————————————

@Composable
private fun ZipListPreview(file: File) {
    var entries by remember(file.absolutePath) { mutableStateOf<List<Pair<String, Long>>?>(null) }
    var error by remember(file.absolutePath) { mutableStateOf<String?>(null) }

    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            runCatching {
                ZipFile(file).use { zip ->
                    zip.entries().asSequence()
                        .map { it.name to it.size }
                        .sortedBy { it.first }
                        .take(500)
                        .toList()
                }
            }.onSuccess { entries = it }.onFailure { error = it.message ?: "无法读取归档" }
        }
    }

    val list = entries
    when {
        error != null -> PreviewHint("归档读取失败：" + error, "长按文件 → 解压到当前目录")
        list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LinearProgressIndicator()
        }
        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
            Text(
                text = "共 " + list.size + " 个条目（最多列 500）",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            list.forEach { entry ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        text = entry.first,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (entry.first.endsWith("/")) "<目录>" else Format.size(entry.second),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ————————————————————————— 兜底：文件头 Hex —————————————————————————

@Composable
private fun HexHeadPreview(file: File, onToast: (String) -> Unit) {
    var lines by remember(file.absolutePath) { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(file.absolutePath) {
        lines = withContext(Dispatchers.IO) {
            runCatching {
                val head = ByteArray(512)
                val n = java.io.FileInputStream(file).use { it.read(head) }
                if (n <= 0) {
                    listOf("（空文件）")
                } else {
                    val out = ArrayList<String>()
                    var i = 0
                    while (i < n) {
                        val end = minOf(i + 16, n)
                        val hex = StringBuilder()
                        val ascii = StringBuilder()
                        for (j in i until end) {
                            val b = head[j].toInt() and 0xFF
                            hex.append(String.format("%02X ", b))
                            ascii.append(if (b in 32..126) b.toChar() else '.')
                        }
                        out += String.format("%08X  ", i) + hex.toString().padEnd(48) + " " + ascii
                        i = end
                    }
                    out
                }
            }.getOrElse { e ->
                onToast("读取失败：" + e.message)
                listOf("读取失败")
            }
        }
    }

    val body = lines
    if (body == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LinearProgressIndicator() }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            text = "无专用预览器，显示文件头 512 字节",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
        Text(
            text = body.joinToString("\n"),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)
        )
    }
}

// ————————————————————————— 公共小件 —————————————————————————

@Composable
private fun PreviewHint(title: String, detail: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("◌", fontSize = 30.sp, color = MaterialTheme.colorScheme.outline)
        Text(title, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        if (detail.isNotBlank()) {
            Text(
                detail,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun PreviewInfo(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = key,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(value, fontSize = 11.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PreviewAction(label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(end = 6.dp).clickable(onClick = onClick)
    ) {
        Text(label, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

private fun mimeOf(name: String): String {
    val ext = Format.extOf(name)
    return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        ?: "application/octet-stream"
}

private fun openExternal(context: Context, file: File, onToast: (String) -> Unit) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeOf(file.name))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { onToast("无法打开：" + it.message) }
}

private fun shareFile(context: Context, file: File, onToast: (String) -> Unit) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType(mimeOf(file.name))
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                "分享 " + file.name
            )
        )
    }.onFailure { onToast("分享失败：" + it.message) }
}