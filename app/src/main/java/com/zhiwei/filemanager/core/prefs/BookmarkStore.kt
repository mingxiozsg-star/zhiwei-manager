package com.zhiwei.filemanager.core.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** 一条书签：path 是绝对路径，label 为空时用目录名展示。 */
data class Bookmark(val path: String, val label: String = "") {
    val title: String get() = label.ifBlank { path.trimEnd('/').substringAfterLast('/').ifEmpty { "/" } }
}

/**
 * 书签 + 最近访问。
 *
 * 用独立 SP 文件而不是塞进 AppPrefs：这两个列表是需要增删的集合，
 * 分开存可以让迁移/清空互不影响（清书签不会碰偏好，清偏好不会丢最近访问）。
 * 仅存绝对路径，不做符号链接解析——解析结果可能在下次启动时已失效。
 */
object BookmarkStore {

    private const val SP_NAME = "zhiwei_manager_bookmarks"
    private const val KEY_MARKS = "bookmarks"
    private const val KEY_RECENTS = "recents"
    private const val MAX_RECENTS = 30

    private var sp: SharedPreferences? = null

    var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
        private set

    /** 最近访问的目录，最新在最前。 */
    var recents by mutableStateOf<List<String>>(emptyList())
        private set

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        sp = p
        bookmarks = decodeMarks(p.getString(KEY_MARKS, null))
        recents = decodePaths(p.getString(KEY_RECENTS, null))
    }

    fun isBookmarked(path: String): Boolean = bookmarks.any { it.path == normalize(path) }

    /** 返回操作后是否处于「已收藏」状态，便于 UI 直接回显。 */
    fun toggleBookmark(path: String, label: String = ""): Boolean {
        val p = normalize(path)
        return if (isBookmarked(p)) {
            removeBookmark(p)
            false
        } else {
            addBookmark(p, label)
            true
        }
    }

    fun addBookmark(path: String, label: String = "") {
        val p = normalize(path)
        if (p.isEmpty() || isBookmarked(p)) return
        bookmarks = bookmarks + Bookmark(p, label)
        persistMarks()
    }

    fun removeBookmark(path: String) {
        val p = normalize(path)
        val next = bookmarks.filterNot { it.path == p }
        if (next.size == bookmarks.size) return
        bookmarks = next
        persistMarks()
    }

    fun renameBookmark(path: String, label: String) {
        val p = normalize(path)
        if (!isBookmarked(p)) return
        bookmarks = bookmarks.map { if (it.path == p) it.copy(label = label) else it }
        persistMarks()
    }

    /** 上移/下移，用于让常用目录排在前面；到边界时不动。 */
    fun moveBookmark(path: String, delta: Int) {
        val p = normalize(path)
        val i = bookmarks.indexOfFirst { it.path == p }
        if (i < 0) return
        val j = (i + delta).coerceIn(0, bookmarks.size - 1)
        if (i == j) return
        val list = bookmarks.toMutableList()
        val item = list.removeAt(i)
        list.add(j, item)
        bookmarks = list
        persistMarks()
    }

    fun clearBookmarks() {
        bookmarks = emptyList()
        persistMarks()
    }

    /** 进入目录时调用；同路径去重并提到最前，超出上限裁掉尾部。 */
    fun pushRecent(path: String) {
        val p = normalize(path)
        if (p.isEmpty()) return
        val next = (listOf(p) + recents.filterNot { it == p }).take(MAX_RECENTS)
        if (next == recents) return
        recents = next
        sp?.edit()?.putString(KEY_RECENTS, encodePaths(next))?.apply()
    }

    fun removeRecent(path: String) {
        val p = normalize(path)
        val next = recents.filterNot { it == p }
        if (next.size == recents.size) return
        recents = next
        sp?.edit()?.putString(KEY_RECENTS, encodePaths(next))?.apply()
    }

    fun clearRecents() {
        recents = emptyList()
        sp?.edit()?.putString(KEY_RECENTS, "")?.apply()
    }

    private fun normalize(path: String): String =
        path.trim().let { if (it.length > 1) it.trimEnd('/') else it }

    private fun persistMarks() {
        sp?.edit()?.putString(KEY_MARKS, encodeMarks(bookmarks))?.apply()
    }

    // —— 序列化：org.json 随 Android 平台提供，不必为此引入额外依赖 ——

    private fun encodeMarks(list: List<Bookmark>): String {
        val arr = JSONArray()
        list.forEach { b ->
            val o = JSONObject()
            o.put("path", b.path)
            o.put("label", b.label)
            arr.put(o)
        }
        return arr.toString()
    }

    private fun decodeMarks(raw: String?): List<Bookmark> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Bookmark>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val p = o.optString("path", "")
                if (p.isNotEmpty()) out += Bookmark(p, o.optString("label", ""))
            }
            out
        } catch (t: Throwable) {
            // 存储损坏时宁可返回空列表，也不要让浏览器启动即崩
            emptyList()
        }
    }

    private fun encodePaths(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun decodePaths(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val p = arr.optString(i, "")
                if (p.isNotEmpty()) out += p
            }
            out
        } catch (t: Throwable) {
            emptyList()
        }
    }
}
