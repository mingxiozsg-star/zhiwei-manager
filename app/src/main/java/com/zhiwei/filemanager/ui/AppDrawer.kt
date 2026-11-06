package com.zhiwei.filemanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.zhiwei.filemanager.core.fs.StorageEntry
import com.zhiwei.filemanager.core.fs.StorageVolumes
import com.zhiwei.filemanager.core.util.AppInfo
import com.zhiwei.filemanager.core.util.CompanionApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 折叠栏（左侧抽屉）：所有「跳转类」入口的唯一归口。
 *
 * 为什么把快捷入口从窗格上方挪到这里：
 * 双窗格顶部原本排了一行快捷目录芯片，两个窗口各占一行，等于把屏幕最值钱的
 * 首屏高度吃掉两遍，而用户 90% 的时间就待在内部存储 —— 那些芯片是纯浪费。
 * 现在两个窗格顶部只剩「⋮ + 📁 + 目录名」，跳转能力全部收进抽屉，
 * 「按需展开」而不是「常驻占地」。
 */
@Composable
fun ZhiweiDrawer(
    currentPath: String,
    /** 在当前活动窗口打开某个目录（由 AppRoot 桥接到 state.active.open）。 */
    onOpen: (String) -> Unit,
    onBookmarks: () -> Unit,
    onRecycle: () -> Unit,
    onTerminal: () -> Unit
) {
    val context = LocalContext.current

    // 存储卷枚举要 StatFs，放 IO 线程；抽屉第一次组合时才做，不拖慢冷启动
    var volumes by remember { mutableStateOf<List<StorageEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        volumes = withContext(Dispatchers.IO) { StorageVolumes.list(context) }
    }

    // 应用联动：只有真的装了才显示入口
    val browserInstalled = remember {
        runCatching { CompanionApps.isInstalled(context) }.getOrDefault(false)
    }
    val browserVersion = remember {
        runCatching { CompanionApps.installedVersion(context) }.getOrNull()
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(300.dp)
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

            // ——— 头部 ———
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = AppInfo.NAME,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = AppInfo.VERSION_FULL + " · " + AppInfo.PKG,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()

            // ——— 存储 ———
            DrawerSection("存储")
            if (volumes.isEmpty()) {
                DrawerHint("正在探测存储卷…")
            } else {
                volumes.forEach { entry ->
                    DrawerRow(
                        glyph = entry.kind.glyph,
                        title = entry.label,
                        subtitle = entry.path + if (entry.usageText.isEmpty()) "" else " · " + entry.usageText,
                        highlighted = currentPath == entry.path || currentPath.startsWith(entry.path.trimEnd('/') + "/"),
                        onClick = { onOpen(entry.path) }
                    )
                }
            }

            // ——— 常用目录（真实存在的才列，不摆空壳）———
            DrawerSection("常用目录")
            commonDirs().forEach { d ->
                DrawerRow(
                    glyph = d.glyph,
                    title = d.title,
                    subtitle = d.path,
                    highlighted = currentPath == d.path,
                    onClick = { onOpen(d.path) }
                )
            }

            // ——— 应用联动 ———
            if (browserInstalled) {
                DrawerSection("应用联动")
                DrawerRow(
                    glyph = "◍",
                    title = "知微浏览器",
                    subtitle = "专属下载目录 · " + CompanionApps.BROWSER_DOWNLOAD_DIR +
                        (if (browserVersion.isNullOrBlank()) "" else "（浏览器 v$browserVersion）"),
                    highlighted = currentPath.startsWith(CompanionApps.BROWSER_DOWNLOAD_DIR),
                    onClick = {
                        val dir = runCatching { CompanionApps.browserDir() }.getOrNull()
                        if (dir != null) onOpen(dir)
                    }
                )
            }

            // ——— 工具 ———
            DrawerSection("工具与集合")
            DrawerRow("★", "书签与最近访问", "收藏的目录、最近 30 条访问记录", false) { onBookmarks() }
            DrawerRow("♻", "回收站", "误删还原 / 彻底清除", false) { onRecycle() }
            DrawerRow("▤", "终端模拟器", "普通 / ROOT / Shizuku 三通道", false) { onTerminal() }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Text(
                text = AppInfo.TEAM + " · QQ 群 " + AppInfo.QQ_GROUP,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            // 底部留白：抽屉最后一行贴着手势条会被系统手势抢走
            Column(Modifier.height(24.dp)) {}
        }
    }
}

/** 抽屉里的一行。 */
@Composable
private fun DrawerRow(
    glyph: String,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (highlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = glyph,
                fontSize = 14.sp,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerSection(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun DrawerHint(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

private data class CommonDir(val glyph: String, val title: String, val path: String)

/** 常用目录：逐个 exists() 过滤，不列不存在的空壳（不同 ROM 目录差异很大）。 */
private fun commonDirs(): List<CommonDir> = listOf(
    CommonDir("↓", "下载", "/sdcard/Download"),
    CommonDir("▤", "内部存储", "/sdcard"),
    CommonDir("▨", "图片", "/sdcard/Pictures"),
    CommonDir("◉", "相机 / 截图", "/sdcard/DCIM"),
    CommonDir("▧", "文档", "/sdcard/Documents"),
    CommonDir("♪", "音乐", "/sdcard/Music"),
    CommonDir("▷", "视频", "/sdcard/Movies"),
    CommonDir("⚙", "Android/data", "/sdcard/Android/data"),
    CommonDir("⚙", "Android/obb", "/sdcard/Android/obb")
).filter { File(it.path).exists() }