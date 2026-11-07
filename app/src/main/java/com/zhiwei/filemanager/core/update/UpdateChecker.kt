package com.zhiwei.filemanager.core.update

import com.zhiwei.filemanager.core.util.AppInfo
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/** 一次「最新版本」查询的结果。 */
data class UpdateInfo(
    /** 去掉前缀 v 的版本号，例如 1.3.0。 */
    val version: String,
    /** 原始 tag，例如 v1.3.0。 */
    val tag: String,
    /** Release 正文（更新日志）。 */
    val notes: String,
    /** APK 资产名，例如 zhiwei-manager-v1.3.0.apk。 */
    val assetName: String,
    /** 直连下载地址。 */
    val apkUrl: String,
    /** Release 页面地址。 */
    val pageUrl: String,
    val sizeBytes: Long
)

/**
 * 内置更新检查。
 *
 * 设计取舍：
 * - 不引第三方依赖（okhttp / gson 都不加）：一个 Release 查询用 [HttpURLConnection] + `org.json` 足够，
 *   少一个依赖就少一份体积与混淆面。
 * - 网络失败一律返回 [Result.failure]，绝不抛出到 UI 线程上方——「检查更新」失败不应该让设置页崩。
 * - 下载走镜像加速：直连 GitHub 在国内常断流，先试 ghfast 镜像，失败再回直连。
 */
object UpdateChecker {

    private const val UA = "ZhiweiManager-Update/1.0 (Android)"
    private const val TIMEOUT_MS = 15000

    /** 镜像前缀（按顺序尝试）。2026-11-07 实测淘汰 gh-proxy.com / ghproxy.net（000 超时或 <1KB/s）。 */
    private val MIRRORS = listOf(
        "https://ghfast.top/",
        "https://cors.isteed.cc/",
        ""
    )

    /**
     * 查询最新 Release。
     *
     * @param currentVersion 当前版本号（如 1.3.0），用于判断是否「有新版」。
     * @return 成功时携带 [UpdateInfo] 与「是否比当前新」的判定结果（由调用方用 [isNewer] 自算）。
     */
    fun checkLatest(): Result<UpdateInfo> = runCatching {
        val conn = (URL(AppInfo.RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("GitHub 返回 HTTP $code（可能触发限流，稍后再试）")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): UpdateInfo {
        val root = JSONObject(body)
        val tag = root.optString("tag_name", "").ifBlank { throw IllegalStateException("Release 缺少 tag_name") }
        val page = root.optString("html_url", AppInfo.RELEASES_PAGE)
        val notes = root.optString("body", "").trim()
        val assets: JSONArray = root.optJSONArray("assets") ?: JSONArray()

        var apkUrl = ""
        var assetName = ""
        var size = 0L
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name", "")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            apkUrl = a.optString("browser_download_url", "")
            assetName = name
            size = a.optLong("size", 0L)
            break
        }
        if (apkUrl.isBlank()) throw IllegalStateException("该 Release 没有可用的 APK 资产")

        return UpdateInfo(
            version = tag.trimStart('v', 'V'),
            tag = tag,
            notes = notes,
            assetName = assetName,
            apkUrl = apkUrl,
            pageUrl = page,
            sizeBytes = size
        )
    }

    /** 语义化版本比较：仅比较点分数字段，多出来的预发布后缀忽略。 */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        val b = current.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * 下载 APK 到 [dest]。
     *
     * @param onProgress (已下载字节, 总字节(未知为 -1))
     * @return 真正命中的下载地址（便于在 UI 上标注走了哪个镜像）
     */
    fun download(
        info: UpdateInfo,
        dest: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): String {
        dest.parentFile?.mkdirs()
        var lastError: Throwable? = null
        for (prefix in MIRRORS) {
            val url = prefix + info.apkUrl
            try {
                downloadOnce(url, dest, info.sizeBytes, onProgress)
                return url
            } catch (t: Throwable) {
                lastError = t
                // 换下一个镜像重试
            }
        }
        throw lastError ?: IllegalStateException("下载失败")
    }

    private fun downloadOnce(url: String, dest: File, expected: Long, onProgress: (Long, Long) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = TIMEOUT_MS
            readTimeout = 30000
            setRequestProperty("User-Agent", UA)
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("下载返回 HTTP $code")
            val total = if (expected > 0L) expected else conn.contentLengthLong
            val tmp = File(dest.absolutePath + ".part")
            conn.inputStream.use { input: InputStream ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var n: Int
                    while (true) {
                        n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                    out.flush()
                }
            }
            // 先落临时文件再改名：避免半截包被当成完整包去安装
            if (tmp.length() <= 0L) throw IllegalStateException("下载内容为空")
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } finally {
            conn.disconnect()
        }
    }
}
