package com.zhiwei.filemanager.core.util

import android.content.Context
import java.io.File

/**
 * 同厂应用联动（「始源创想科技IT」全家桶）。
 *
 * 折叠栏里的「知微浏览器」入口不是写死的跳转：先探测设备上是否真的装了该应用，
 * 装了才显示，避免给用户一个点了没反应的死按钮。
 *
 * 知微浏览器的下载落盘位置已与其 [Downloader] 对齐：
 *   API 29+ → MediaStore.Downloads，RELATIVE_PATH = Download/知微浏览器
 *   API <29 → getExternalStoragePublicDirectory(Downloads)/知微浏览器
 * 两种路径在文件系统上都是 `/sdcard/Download/知微浏览器`。
 */
object CompanionApps {

    /** 知微浏览器包名（与其 build.gradle 的 applicationId 一致）。 */
    const val BROWSER_PKG = "com.java.myapplication"

    /** 知微浏览器专属下载目录。 */
    const val BROWSER_DOWNLOAD_DIR = "/sdcard/Download/知微浏览器"

    /** 知微浏览器安装时优先跳到这里（真实存在的下载目录优先）。 */
    val BROWSER_FALLBACK_DIRS = listOf(
        BROWSER_DOWNLOAD_DIR,
        "/sdcard/Download"
    )

    /** 是否已安装（不抛异常：未安装、被 suspender 挂起都算 false）。 */
    fun isInstalled(context: Context, pkg: String = BROWSER_PKG): Boolean = runCatching {
        context.packageManager.getPackageInfo(pkg, 0)
    }.isSuccess

    /** 已安装知微浏览器时它的版本名；未安装返回 null。 */
    fun installedVersion(context: Context, pkg: String = BROWSER_PKG): String? = runCatching {
        context.packageManager.getPackageInfo(pkg, 0).versionName
    }.getOrNull()

    /**
     * 解析出该跳转的目录：优先专属目录；不存在就试着建一个；
     * 建不出来（无权限）就退到公共下载目录；再不行给 null。
     */
    fun browserDir(): String? {
        val target = File(BROWSER_DOWNLOAD_DIR)
        if (target.isDirectory) return target.absolutePath
        runCatching { target.mkdirs() }
        if (target.isDirectory) return target.absolutePath
        return BROWSER_FALLBACK_DIRS.firstOrNull { File(it).isDirectory }
    }
}
