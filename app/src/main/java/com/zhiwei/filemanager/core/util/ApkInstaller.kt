package com.zhiwei.filemanager.core.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * APK 安装助手。
 *
 * 为什么单独抽出来：安装包弹窗、设置页的「立即安装」、长按菜单的「安装」是三条完全不同的
 * 调用路径，但要处理的坑是同一套（FileProvider 授权、未知来源授权、installer 崩溃兜底）。
 * 以前散落在设置页里，复制一份就是三份 bug。
 */
object ApkInstaller {

    /** 拉起系统安装器。返回 false 表示需要先去授权「安装未知应用」。 */
    fun install(context: Context, apk: File): Boolean {
        val uri = runCatching {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
        }.getOrNull() ?: run {
            openInstallPermission(context)
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            openInstallPermission(context)
            false
        }
    }

    /** 跳系统「安装未知应用」授权页（API 26+ 才有这个概念）。 */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 是否已获得「安装未知应用」授权。 */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }
}