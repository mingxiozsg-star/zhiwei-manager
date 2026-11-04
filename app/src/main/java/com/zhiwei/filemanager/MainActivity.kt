package com.zhiwei.filemanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.zhiwei.filemanager.core.prefs.AppPrefs
import com.zhiwei.filemanager.core.prefs.BookmarkStore
import com.zhiwei.filemanager.ui.AppRoot
import com.zhiwei.filemanager.ui.theme.ZhiweiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.init(applicationContext)
        BookmarkStore.init(applicationContext)
        enableEdgeToEdge()
        setContent { ZhiweiApp() }
        requestNotificationPermission()
    }

    @Composable
    private fun ZhiweiApp() {
        val dark = when (AppPrefs.themeMode) {
            1 -> true
            2 -> false
            else -> isSystemInDarkTheme()
        }
        ZhiweiTheme(darkTheme = dark) {
            AppRoot()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATION)
        }
    }

    private companion object {
        const val REQ_NOTIFICATION = 0x2201
    }
}