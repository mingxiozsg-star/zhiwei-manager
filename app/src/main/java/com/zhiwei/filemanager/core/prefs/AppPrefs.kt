package com.zhiwei.filemanager.core.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zhiwei.filemanager.core.model.SortMode
import com.zhiwei.filemanager.core.model.SortOrder

/** 全局偏好（Compose State 包装，读到即刷新 UI）。 */
object AppPrefs {

    private const val SP_NAME = "zhiwei_manager_prefs"
    private lateinit var sp: SharedPreferences

    var showHidden by mutableStateOf(false)
        private set
    var sortMode by mutableStateOf(SortMode.NAME)
        private set
    var sortOrder by mutableStateOf(SortOrder.ASC)
        private set
    var dualPane by mutableStateOf(false)
        private set
    var lastLeftPath by mutableStateOf("/sdcard")
        private set
    var lastRightPath by mutableStateOf("/sdcard/Download")
        private set
    var themeMode by mutableStateOf(0)
        private set
    var rootEnabled by mutableStateOf(false)
        private set
    var shizukuEnabled by mutableStateOf(true)
        private set
    var editorCharset by mutableStateOf("UTF-8")
        private set
    /** 双窗口排列：true=左右两栏（MT 默认），false=上下两栏。 */
    var splitLeftRight by mutableStateOf(true)
        private set
    /** 左右（或上下）分割比例。 */
    var splitFraction by mutableStateOf(0.5f)
        private set
    /** 删除时移入回收站而非直接销毁。 */
    var recycleEnabled by mutableStateOf(true)
        private set

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        showHidden = sp.getBoolean("showHidden", false)
        sortMode = runCatching { SortMode.valueOf(sp.getString("sortMode", SortMode.NAME.name)!!) }.getOrDefault(SortMode.NAME)
        sortOrder = runCatching { SortOrder.valueOf(sp.getString("sortOrder", SortOrder.ASC.name)!!) }.getOrDefault(SortOrder.ASC)
        dualPane = sp.getBoolean("dualPane", true)
        lastLeftPath = sp.getString("lastLeftPath", "/sdcard") ?: "/sdcard"
        lastRightPath = sp.getString("lastRightPath", "/sdcard/Download") ?: "/sdcard/Download"
        themeMode = sp.getInt("themeMode", 0)
        rootEnabled = sp.getBoolean("rootEnabled", false)
        shizukuEnabled = sp.getBoolean("shizukuEnabled", true)
        editorCharset = sp.getString("editorCharset", "UTF-8") ?: "UTF-8"
        splitLeftRight = sp.getBoolean("splitLeftRight", true)
        splitFraction = sp.getFloat("splitFraction", 0.5f)
        recycleEnabled = sp.getBoolean("recycleEnabled", true)
    }

    fun updateShowHidden(v: Boolean) {
        showHidden = v
        sp.edit().putBoolean("showHidden", v).apply()
    }

    fun setSort(mode: SortMode, order: SortOrder) {
        sortMode = mode
        sortOrder = order
        sp.edit().putString("sortMode", mode.name).putString("sortOrder", order.name).apply()
    }

    fun updateDualPane(v: Boolean) {
        dualPane = v
        sp.edit().putBoolean("dualPane", v).apply()
    }

    fun setLastPath(left: String, right: String) {
        lastLeftPath = left
        lastRightPath = right
        sp.edit().putString("lastLeftPath", left).putString("lastRightPath", right).apply()
    }

    fun updateThemeMode(v: Int) {
        themeMode = v
        sp.edit().putInt("themeMode", v).apply()
    }

    fun updateRootEnabled(v: Boolean) {
        rootEnabled = v
        sp.edit().putBoolean("rootEnabled", v).apply()
    }

    fun updateShizukuEnabled(v: Boolean) {
        shizukuEnabled = v
        sp.edit().putBoolean("shizukuEnabled", v).apply()
    }

    fun updateEditorCharset(v: String) {
        editorCharset = v
        sp.edit().putString("editorCharset", v).apply()
    }

    fun updateSplitLeftRight(v: Boolean) {
        splitLeftRight = v
        sp.edit().putBoolean("splitLeftRight", v).apply()
    }

    fun updateSplitFraction(v: Float) {
        splitFraction = v.coerceIn(0.2f, 0.8f)
        sp.edit().putFloat("splitFraction", splitFraction).apply()
    }

    fun updateRecycleEnabled(v: Boolean) {
        recycleEnabled = v
        sp.edit().putBoolean("recycleEnabled", v).apply()
    }
}
