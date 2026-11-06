package com.zhiwei.filemanager.core.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * 启动期权限门面。
 *
 * 设计取舍：
 * - 用户明确要求「进 App 默认一次性申请所有权限」，所以这里不按功能拆开懒申请，
 *   而是启动时把运行时权限**一次申请完**，避免后续每个功能各弹一次。
 * - 「所有文件访问」（MANAGE_EXTERNAL_STORAGE）没有运行时对话框，只能跳系统设置页。
 *   因此**只在首次自动跳一次**（用 SharedPreferences 记标记），否则用户每次杀进程重进
 *   都会被弹去系统设置，属于典型的权限骚扰。
 * - 判断逻辑与 [SettingsScreen] 里的状态展示共用同一套实现，避免两处各写一遍后走偏。
 */
object Permissions {

    /** 运行时权限的统一请求码。 */
    const val REQ_RUNTIME = 0x2201

    private const val SP = "zhiwei_manager_prefs"
    private const val KEY_ASKED_ALL_FILES = "askedAllFilesOnce"

    /** 当前系统版本下需要动态申请的运行时权限。 */
    fun runtimePermissions(): List<String> {
        val list = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
            list += Manifest.permission.READ_MEDIA_IMAGES
            list += Manifest.permission.READ_MEDIA_VIDEO
            list += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            list += Manifest.permission.READ_EXTERNAL_STORAGE
            // API 30 起 WRITE_EXTERNAL_STORAGE 已失效，交给所有文件访问权限
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                list += Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
        }
        return list
    }

    /** 尚未授予的运行时权限。 */
    fun missingRuntime(context: Context): List<String> =
        runtimePermissions().filter {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

    /** 是否已拿到「所有文件访问」（API 30 以下等同已拥有）。 */
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            true
        }

    /** 是否应该自动引导一次「所有文件访问」。 */
    private fun shouldPromptAllFiles(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (hasAllFilesAccess()) return false
        return !context.getSharedPreferences(SP, Context.MODE_PRIVATE)
            .getBoolean(KEY_ASKED_ALL_FILES, false)
    }

    private fun markAllFilesPrompted(context: Context) {
        context.getSharedPreferences(SP, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ASKED_ALL_FILES, true).apply()
    }

    /** 跳到「所有文件访问」授权页；某些 ROM 没有应用级入口，回落到列表页。 */
    fun openAllFilesSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val direct = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:" + context.packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(direct) }.isFailure) {
            runCatching { context.startActivity(fallback) }
        }
    }

    /** 启动时一次性申请：运行时权限一把梭，所有文件访问单独引导（仅首次）。 */
    fun requestAllOnStart(activity: Activity) {
        val missing = missingRuntime(activity)
        if (missing.isNotEmpty()) {
            activity.requestPermissions(missing.toTypedArray(), REQ_RUNTIME)
        }
        if (shouldPromptAllFiles(activity)) {
            markAllFilesPrompted(activity)
            openAllFilesSettings(activity)
        }
    }
}
