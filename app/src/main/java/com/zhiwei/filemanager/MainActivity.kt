package com.zhiwei.filemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.zhiwei.filemanager.core.prefs.AppPrefs
import com.zhiwei.filemanager.core.prefs.BookmarkStore
import com.zhiwei.filemanager.core.util.Permissions
import com.zhiwei.filemanager.ui.AppRoot
import com.zhiwei.filemanager.ui.theme.ZhiweiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.init(applicationContext)
        BookmarkStore.init(applicationContext)
        enableEdgeToEdge()
        setContent { ZhiweiApp() }
        // 进 App 一次性把所有权限申请完（运行时权限一把梭 + 首次引导「所有文件访问」）
        Permissions.requestAllOnStart(this)
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
}
