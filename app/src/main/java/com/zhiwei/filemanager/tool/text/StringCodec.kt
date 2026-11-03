package com.zhiwei.filemanager.tool.text

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** 工具箱：常见字符串编解码 / 哈希 / 加解密。 */
object StringCodec {

    fun base64Encode(s: String): String =
        Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    fun base64Decode(s: String): String? = try {
        String(Base64.decode(s.trim(), Base64.DEFAULT), Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }

    fun hexEncode(s: String): String =
        s.toByteArray(Charsets.UTF_8).joinToString("") { String.format(Locale.US, "%02x", it) }

    fun hexDecode(s: String): String? = try {
        val clean = s.replace(" ", "").replace("\n", "").replace("0x", "").replace("\\x", "")
        if (clean.isEmpty() || clean.length % 2 != 0) null
        else {
            val bytes = ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
            String(bytes, Charsets.UTF_8)
        }
    } catch (t: Throwable) {
        null
    }

    fun digest(s: String, algo: String): String =
        MessageDigest.getInstance(algo).digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(Locale.US, "%02x", it) }

    fun md5(s: String): String = digest(s, "MD5")
    fun sha1(s: String): String = digest(s, "SHA-1")
    fun sha256(s: String): String = digest(s, "SHA-256")
    fun sha512(s: String): String = digest(s, "SHA-512")

    fun toUnicode(s: String): String = buildString {
        s.forEach { c -> append("\\u").append(String.format(Locale.US, "%04x", c.code)) }
    }

    fun fromUnicode(s: String): String? = try {
        Regex("\\\\u([0-9a-fA-F]{4})").replace(s) { m ->
            m.groupValues[1].toInt(16).toChar().toString()
        }
    } catch (t: Throwable) {
        null
    }

    fun urlEncode(s: String): String = try {
        java.net.URLEncoder.encode(s, "UTF-8")
    } catch (t: Throwable) {
        s
    }

    fun urlDecode(s: String): String? = try {
        java.net.URLDecoder.decode(s, "UTF-8")
    } catch (t: Throwable) {
        null
    }

    fun stripSpaces(s: String): String = s.filterNot { it == ' ' || it == '\t' }

    fun stripBlankLines(s: String): String = s.lines().filter { it.isNotBlank() }.joinToString("\n")

    fun stripNewlines(s: String): String = s.replace("\n", "").replace("\r", "")

    fun desEncrypt(plain: String, key: String): String? = des(plain, key, true)

    fun desDecrypt(cipherText: String, key: String): String? = des(cipherText, key, false)

    private fun des(text: String, key: String, encrypt: Boolean): String? = try {
        val keyBytes = ByteArray(8)
        val raw = key.toByteArray(Charsets.UTF_8)
        System.arraycopy(raw, 0, keyBytes, 0, minOf(8, raw.size))
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "DES"))
        if (encrypt) {
            Base64.encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        } else {
            String(cipher.doFinal(Base64.decode(text.trim(), Base64.DEFAULT)), Charsets.UTF_8)
        }
    } catch (t: Throwable) {
        null
    }

    fun gzipBase64Decode(s: String): String? = try {
        val bytes = Base64.decode(s.trim(), Base64.DEFAULT)
        java.util.zip.GZIPInputStream(bytes.inputStream()).use { String(it.readBytes(), Charsets.UTF_8) }
    } catch (t: Throwable) {
        null
    }

    fun jsonPretty(s: String): String? = try {
        val t = s.trim()
        if (t.startsWith("[")) JSONArray(t).toString(2) else JSONObject(t).toString(2)
    } catch (t: Throwable) {
        null
    }

    fun jsonCompact(s: String): String? = try {
        val t = s.trim()
        if (t.startsWith("[")) JSONArray(t).toString() else JSONObject(t).toString()
    } catch (t: Throwable) {
        null
    }

    fun isJson(s: String): Boolean = jsonCompact(s) != null

    fun timestampToDate(s: String): String? = try {
        var v = s.trim().toLong()
        if (v < 10_000_000_000L) v *= 1000
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(v))
    } catch (t: Throwable) {
        null
    }

    fun dateToTimestamp(s: String): String? = try {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        fmt.parse(s.trim())?.time?.toString()
    } catch (t: Throwable) {
        null
    }

    fun reverse(s: String): String = s.reversed()

    fun stats(s: String): String =
        "字符 ${s.length} · 字节(UTF-8) ${s.toByteArray(Charsets.UTF_8).size} · 行 ${s.lines().size}"
}
