package com.zhiwei.filemanager.core.fs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/** SAF 上的一个条目（只读浏览用的最小字段集）。 */
data class SafEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long,
    val docId: String,
    val uri: Uri
)

/**
 * 受限目录的「免提权」通道。
 *
 * 背景：Android 11 起 `/sdcard/Android/data`、`/sdcard/Android/obb` 这类目录
 * 不允许应用用普通文件路径直接列举，多数管理器因此直接要求 Shizuku / ROOT。
 * 系统其实留了另一条**不需要任何高权限**的口子：`ACTION_OPEN_DOCUMENT_TREE`
 * 让用户亲手把某个目录「授权」给本应用，授权结果以 `content://` 树 URI 的形式
 * 持久保存，之后用 ContentResolver 就能正常浏览——不装 Shizuku、不要 ROOT、
 * 连「所有文件访问权限」都不需要。
 *
 * 本类只负责三件事：路径 ⇄ SAF documentId 的翻译、已授权树的持久化、列子项。
 * 界面在 `ui/files/SafBrowseSheet.kt`。
 */
object SafTree {

    private const val AUTHORITY = "com.android.externalstorage.documents"
    private const val SP = "zhiwei_manager_saf"
    private const val EXTRA_INITIAL_URI = "android.provider.extra.INITIAL_URI"

    /** 把 `/sdcard/Android/data/foo` 翻译成 SAF 的 documentId（`primary:Android/data/foo`）。 */
    fun docIdOf(path: String): String? {
        val p = path.trimEnd('/')
        val primaryRoots = listOf("/sdcard", "/storage/emulated/0", "/storage/self/primary")
        primaryRoots.firstOrNull { p == it || p.startsWith("$it/") }?.let { root ->
            return "primary:" + p.removePrefix(root).trim('/')
        }
        val storage = File("/storage")
        val vols = runCatching { storage.listFiles() }.getOrNull() ?: return null
        for (v in vols) {
            if (!v.isDirectory) continue
            val abs = v.absolutePath
            if (abs == "/storage/emulated" || abs == "/storage/self") continue
            if (p == abs || p.startsWith("$abs/")) {
                return v.name + ":" + p.removePrefix(abs).trim('/')
            }
        }
        return null
    }

    /** 目录在 SAF 里的 document URI（用作选择器的初始位置）。 */
    fun documentUri(path: String): Uri? = docIdOf(path)?.let {
        runCatching { DocumentsContract.buildDocumentUri(AUTHORITY, it) }.getOrNull()
    }

    /** 「选择该目录并授权」的 Intent；不需要 Shizuku / ROOT / 所有文件访问。 */
    fun requestIntent(path: String): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
        documentUri(path)?.let { intent.putExtra(EXTRA_INITIAL_URI, it) }
        return intent
    }

    /** 记住用户授权出来的树 URI（按目录路径存，下次进来直接可用）。 */
    fun remember(context: Context, path: String, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        context.getSharedPreferences(SP, Context.MODE_PRIVATE).edit()
            .putString(key(path), uri.toString()).apply()
    }

    fun saved(context: Context, path: String): Uri? =
        context.getSharedPreferences(SP, Context.MODE_PRIVATE)
            .getString(key(path), null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun forget(context: Context, path: String) {
        context.getSharedPreferences(SP, Context.MODE_PRIVATE).edit().remove(key(path)).apply()
    }

    private fun key(path: String) = "tree_" + path.trimEnd('/').replace('/', '_')

    /** 列出某个 documentId 下的子项；无授权或读取失败都返回空表，不抛异常。 */
    fun listChildren(context: Context, tree: Uri, docId: String): List<SafEntry> {
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        }.getOrNull() ?: return emptyList()
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val out = ArrayList<SafEntry>()
        runCatching {
            context.contentResolver.query(childrenUri, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    val modified = if (c.isNull(4)) 0L else c.getLong(4)
                    out += SafEntry(
                        name = name,
                        isDir = isDir,
                        size = size,
                        lastModified = modified,
                        docId = id,
                        uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    )
                }
            }
        }
        return out
    }

    /** 父 documentId（用于「返回上一层」）；已在卷根时返回 null。 */
    fun parentDocId(docId: String): String? {
        val colon = docId.indexOf(':')
        if (colon < 0) return null
        val volume = docId.substring(0, colon)
        val rel = docId.substring(colon + 1)
        val idx = rel.lastIndexOf('/')
        if (idx < 0) return null
        return volume + ":" + rel.substring(0, idx)
    }

    /** documentId 的展示名（取末段）。 */
    fun displayName(docId: String): String {
        val colon = docId.indexOf(':')
        val rel = if (colon >= 0) docId.substring(colon + 1) else docId
        val idx = rel.lastIndexOf('/')
        val tail = if (idx >= 0) rel.substring(idx + 1) else rel
        return tail.ifBlank { docId.substringBefore(':').ifBlank { "存储" } }
    }

    /** docId 是否落在已授权的树里（用来禁止越界向上爬）。 */
    fun within(treeDocId: String, docId: String): Boolean =
        docId == treeDocId || docId.startsWith("$treeDocId/")
}
