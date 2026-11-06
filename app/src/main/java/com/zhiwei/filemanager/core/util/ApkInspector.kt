package com.zhiwei.filemanager.core.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile

/**
 * APK 静态检查器（预览页与 APK 工具箱共用）。
 *
 * 设计取舍：
 * - 不走 `com.android.apksig`：它的 `Result` 在不同大版本间字段名漂移过（signingCertificate ↔ signers），
 *   而这个项目无法在本机编译验证，一旦 API 猜错就是硬失败。改为按 APK 规范手写解析
 *   **APK Signing Block**，只用 zip + RandomAccessFile，零 API 风险、零额外依赖。
 * - `getPackageArchiveInfo` 会抛各种 `NameNotFoundException`，全部 runCatching 兜底，
 *   检查一个坏包不应该让预览页崩。
 */
object ApkInspector {

    /** 签名方案存在性。v1 = JAR 签名（META-INF/*.RSA|DSA|EC），v2/v3 = 签名块。 */
    data class SignatureInfo(
        val v1: Boolean,
        val v2: Boolean,
        val v3: Boolean,
        val signerFiles: List<String>
    )

    data class Summary(
        val label: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val minSdk: Int,
        val targetSdk: Int,
        val permissions: List<String>,
        val signature: SignatureInfo,
        /** 命中的加固特征描述；未命中为 null。 */
        val hardening: String?,
        val entryCount: Int,
        val dexCount: Int,
        val soCount: Int,
        val activityCount: Int,
        val icon: Bitmap?
    )

    private const val MAGIC = "APK Sig Block 42"
    private const val ID_V2 = 0x7109871aL
    private const val ID_V3 = 0xf05368c0L

    fun inspect(context: Context, file: File): Summary {
        val pm = context.packageManager

        // —— 1. 读 AndroidManifest 侧的信息 ——
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES
        @Suppress("DEPRECATION")
        val info = runCatching { pm.getPackageArchiveInfo(file.absolutePath, flags) }.getOrNull()

        var label = file.name
        var pkg = "（解析失败）"
        var versionName = "-"
        var versionCode = 0L
        var minSdk = 0
        var targetSdk = 0
        var icon: Bitmap? = null
        val permissions = info?.requestedPermissions?.toList().orEmpty()

        info?.applicationInfo?.let { ai ->
            // 关键：必须把 sourceDir 指到 apk，否则 getApplicationIcon / getApplicationLabel 拿不到资源
            ai.sourceDir = file.absolutePath
            ai.publicSourceDir = file.absolutePath
            label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(file.name)
            icon = runCatching { pm.getApplicationIcon(ai).toBitmapSafe() }.getOrNull()
            minSdk = runCatching { ai.minSdkVersion }.getOrDefault(0)
            targetSdk = runCatching { ai.targetSdkVersion }.getOrDefault(0)
        }
        info?.let {
            pkg = it.packageName ?: pkg
            versionName = it.versionName ?: "-"
            @Suppress("DEPRECATION")
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { it.longVersionCode }.getOrDefault(0L)
            } else {
                @Suppress("DEPRECATION")
                runCatching { it.versionCode.toLong() }.getOrDefault(0L)
            }
        }

        // —— 2. 扫 zip 条目：条目数 / dex / so / 签名文件 / 加固特征 ——
        var entryCount = 0
        var dexCount = 0
        var soCount = 0
        val signerFiles = ArrayList<String>()
        var hardening: String? = null

        runCatching {
            ZipFile(file).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                entryCount = names.size
                names.forEach { name ->
                    val lower = name.lowercase()
                    if (!name.contains('/') && lower.endsWith(".dex")) dexCount++
                    if (lower.endsWith(".so")) soCount++
                    if (lower.startsWith("meta-inf/") &&
                        (lower.endsWith(".rsa") || lower.endsWith(".dsa") || lower.endsWith(".ec"))
                    ) {
                        signerFiles += name.substringAfterLast('/')
                    }
                    if (hardening == null) hardening = matchHardening(lower)
                }
            }
        }

        // —— 3. 签名块：v2 / v3 ——
        val blockIds = runCatching { readSigningBlockIds(file) }.getOrDefault(emptySet())

        return Summary(
            label = label,
            packageName = pkg,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            permissions = permissions,
            signature = SignatureInfo(
                v1 = signerFiles.isNotEmpty(),
                v2 = ID_V2 in blockIds,
                v3 = ID_V3 in blockIds,
                signerFiles = signerFiles
            ),
            hardening = hardening,
            entryCount = entryCount,
            dexCount = dexCount,
            soCount = soCount,
            activityCount = info?.activities?.size ?: 0,
            icon = icon
        )
    }

    // ————————————————————————— 签名块解析 —————————————————————————

    /**
     * 读取 APK Signing Block 里出现的所有块 ID。
     *
     * 规范布局（start 为签名块起点）：
     *   [0,8)        块长度 v（不含本字段）→ 整块长度 = v + 8
     *   [8, 8+p)     长度前缀的 ID-value 对（每对：u64 长度 + u32 ID + value）
     *   [8+p,16+p)   第二个块长度（与 v 相同）
     *   [16+p,32+p)  魔数 "APK Sig Block 42"
     * 于是：p = v - 24，magicPos = start + 16 + p。
     *
     * 定位策略：EOCD/中央目录都在文件尾部，签名块紧贴在中央目录之前，
     * 因此在文件末尾 4 MB 内倒着搜魔数即可（签名块通常只有几十 KB）。
     */
    private fun readSigningBlockIds(file: File): Set<Long> {
        val length = file.length()
        if (length < 32) return emptySet()

        val window = minOf(length, 4L * 1024 * 1024).toInt()
        val start = length - window
        val tail = ByteArray(window)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            raf.readFully(tail)
        }

        val magic = MAGIC.toByteArray(Charsets.US_ASCII)
        var magicIndex = -1
        var i = window - magic.size
        while (i >= 0) {
            var ok = true
            for (j in magic.indices) {
                if (tail[i + j] != magic[j]) { ok = false; break }
            }
            if (ok) { magicIndex = i; break }
            i--
        }
        if (magicIndex < 0) return emptySet()

        val magicPos = start + magicIndex
        val ids = HashSet<Long>()
        RandomAccessFile(file, "r").use { raf ->
            val blockSize = readU64(raf, magicPos - 8)
            if (blockSize <= 24 || blockSize > length) return emptySet()

            val pairsStart = magicPos - blockSize + 16
            val pairsEnd = magicPos - 8
            if (pairsStart < 0 || pairsStart >= pairsEnd) return emptySet()

            var cursor = pairsStart
            var guard = 0
            while (cursor + 12 <= pairsEnd && guard++ < 512) {
                val pairLen = readU64(raf, cursor)
                if (pairLen < 4 || cursor + 8 + pairLen > pairsEnd + 1) break
                ids += readU32(raf, cursor + 8)
                cursor += 8 + pairLen
            }
        }
        return ids
    }

    private fun readU64(raf: RandomAccessFile, pos: Long): Long {
        if (pos < 0) throw IllegalStateException("签名块偏移越界")
        raf.seek(pos)
        var v = 0L
        for (i in 0 until 8) {
            val b = raf.read()
            if (b < 0) throw IllegalStateException("遍历签名块时文件提前结束")
            v = v or ((b.toLong() and 0xFF) shl (8 * i))
        }
        return v
    }

    private fun readU32(raf: RandomAccessFile, pos: Long): Long {
        if (pos < 0) throw IllegalStateException("签名块偏移越界")
        raf.seek(pos)
        var v = 0L
        for (i in 0 until 4) {
            val b = raf.read()
            if (b < 0) throw IllegalStateException("遍历签名块时文件提前结束")
            v = v or ((b.toLong() and 0xFF) shl (8 * i))
        }
        return v
    }

    // ————————————————————————— 加固特征 —————————————————————————

    /** 已知加固壳的 so / assets 特征。命中返回厂商描述。 */
    private fun matchHardening(lower: String): String? {
        val rules: List<Pair<String, String>> = listOf(
            "libjiagu" to "360 加固保（libjiagu）",
            "libjgdt" to "360 加固保（libjgdt）",
            "libdexhelper" to "梆梆安全（DexHelper）",
            "libsecexe" to "梆梆安全（SecShell）",
            "libsecmain" to "梆梆安全（SecShell）",
            "libsecshell" to "梆梆安全（SecShell）",
            "libshell" to "腾讯乐固（libshell）",
            "libtup" to "腾讯御安全（libtup）",
            "libmobisec" to "阿里聚安全（libmobisec）",
            "libnesec" to "网易易盾（libnesec）",
            "libnqshield" to "网秦加固",
            "libbaiduprotect" to "百度加固",
            "libexec.so" to "爱加密（libexec）",
            "libexecmain" to "爱加密（libexecmain）",
            "ijiami.ajm" to "爱加密（assets 特征）",
            "libkwscmm" to "几维安全 KiwiVM",
            "libapktoolplus" to "APKToolPlus 保护",
            "libtofucrypt" to "顶象加固",
            "libedog" to "珊瑚盾（libedog）",
            "libriskscan" to "腾讯安全（侦察特征，非加固）",
            "libvmp" to "VM 保护（libvmp）",
            "libprotectclass" to "通用类保护壳（libprotectclass）"
        )
        return rules.firstOrNull { lower.contains(it.first) }?.second
    }

    private fun Drawable.toBitmapSafe(): Bitmap? = runCatching {
        toBitmap(width = 96, height = 96)
    }.getOrNull()
}