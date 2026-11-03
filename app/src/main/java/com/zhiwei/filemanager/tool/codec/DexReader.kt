package com.zhiwei.filemanager.tool.codec

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

data class DexHeader(
    val version: String,
    val fileSize: Long,
    val headerSize: Int,
    val checksum: Long,
    val sha1: String,
    val stringIds: Int,
    val typeIds: Int,
    val protoIds: Int,
    val fieldIds: Int,
    val methodIds: Int,
    val classDefs: Int
)

data class DexInfo(
    val header: DexHeader,
    val classes: List<String>,
    val strings: List<String>
) {
    fun summary(): String = buildString {
        append("dex 版本：${header.version}\n")
        append("文件大小：${header.fileSize} 字节\n")
        append("header_size：${header.headerSize}\n")
        append("校验和：0x${java.lang.Long.toHexString(header.checksum).uppercase(Locale.US)}\n")
        append("签名(SHA-1)：${header.sha1}\n")
        append("字符串数：${header.stringIds}\n")
        append("类型数：${header.typeIds}\n")
        append("方法原型数：${header.protoIds}\n")
        append("字段数：${header.fieldIds}\n")
        append("方法数：${header.methodIds}\n")
        append("类定义数：${header.classDefs}")
    }

    /** 混淆字典：字符串常量里出现过的字符集合。 */
    fun obfuscationDict(): String {
        val set = LinkedHashSet<Char>()
        strings.forEach { s -> s.forEach { c -> if (!c.isLetterOrDigit() || c.isLetter()) set += c } }
        return set.joinToString("")
    }
}

/** 纯 Kotlin 实现的 DEX 解析（无第三方依赖，用于属性查看与字符串常量提取）。 */
object DexReader {

    private const val MIN_HEADER = 112
    private val MAGIC = byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), 0x0A)

    fun isDex(bytes: ByteArray): Boolean =
        bytes.size >= 8 && bytes[0] == MAGIC[0] && bytes[1] == MAGIC[1] && bytes[2] == MAGIC[2] && bytes[3] == MAGIC[3]

    fun read(path: String, maxStrings: Int = 200000): DexInfo? {
        val f = File(path)
        if (!f.isFile) return null
        val bytes = try {
            f.readBytes()
        } catch (t: Throwable) {
            return null
        }
        return parse(bytes, maxStrings)
    }

    fun parse(bytes: ByteArray, maxStrings: Int = 200000): DexInfo? {
        if (bytes.size < MIN_HEADER || !isDex(bytes)) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = String(bytes, 4, 3, Charsets.US_ASCII)
        val checksum = u32(buf.getInt(8))
        val sha1 = bytes.copyOfRange(12, 32).joinToString("") { String.format(Locale.US, "%02x", it) }
        val fileSize = u32(buf.getInt(32))
        val headerSize = buf.getInt(36)
        val stringIds = buf.getInt(56)
        val stringIdsOff = buf.getInt(60)
        val typeIds = buf.getInt(64)
        val typeIdsOff = buf.getInt(68)
        val protoIds = buf.getInt(72)
        val fieldIds = buf.getInt(80)
        val methodIds = buf.getInt(88)
        val classDefs = buf.getInt(96)
        val classDefsOff = buf.getInt(100)
        val header = DexHeader(
            version = version,
            fileSize = fileSize,
            headerSize = headerSize,
            checksum = checksum,
            sha1 = sha1,
            stringIds = stringIds,
            typeIds = typeIds,
            protoIds = protoIds,
            fieldIds = fieldIds,
            methodIds = methodIds,
            classDefs = classDefs
        )
        val strings = readStrings(buf, stringIds, stringIdsOff, maxStrings)
        val classes = readClassNames(buf, classDefs, classDefsOff, typeIds, typeIdsOff, 20000)
        return DexInfo(header, classes, strings)
    }

    private fun readStrings(buf: ByteBuffer, size: Int, off: Int, limit: Int): List<String> {
        val out = ArrayList<String>(minOf(size.coerceAtLeast(0), limit))
        for (i in 0 until minOf(size, limit)) {
            val p = off + i * 4
            if (p + 4 > buf.capacity() || p < 0) break
            val s = parseStringAt(buf, buf.getInt(p))
            if (s != null) out += s
        }
        return out
    }

    private fun readClassNames(
        buf: ByteBuffer,
        defSize: Int,
        defOff: Int,
        typeSize: Int,
        typeOff: Int,
        limit: Int
    ): List<String> {
        val out = ArrayList<String>(minOf(defSize.coerceAtLeast(0), limit))
        for (i in 0 until minOf(defSize, limit)) {
            val base = defOff + i * 32
            if (base + 4 > buf.capacity() || base < 0) break
            val typeIdx = buf.getInt(base)
            if (typeIdx < 0 || typeIdx >= typeSize) continue
            val tp = typeOff + typeIdx * 4
            if (tp + 4 > buf.capacity()) continue
            val strIdx = buf.getInt(tp)
            val s = stringByIdx(buf, strIdx)
            if (s != null) out += s
        }
        return out
    }

    private fun stringByIdx(buf: ByteBuffer, stringIdx: Int): String? {
        val stringIdsSize = buf.getInt(56)
        val stringIdsOff = buf.getInt(60)
        if (stringIdx < 0 || stringIdx >= stringIdsSize) return null
        val p = stringIdsOff + stringIdx * 4
        if (p + 4 > buf.capacity()) return null
        return parseStringAt(buf, buf.getInt(p))
    }

    private fun parseStringAt(buf: ByteBuffer, dataOff: Int): String? {
        if (dataOff <= 0 || dataOff >= buf.capacity()) return null
        var p = dataOff
        while (p < buf.capacity() && (buf.get(p).toInt() and 0x80) != 0) p++
        p++
        val sb = StringBuilder()
        while (p < buf.capacity()) {
            val b = buf.get(p).toInt() and 0xFF
            if (b == 0) break
            when {
                b < 0x80 -> {
                    sb.append(b.toChar())
                    p++
                }
                (b and 0xE0) == 0xC0 && p + 1 < buf.capacity() -> {
                    val b2 = buf.get(p + 1).toInt() and 0xFF
                    sb.append((((b and 0x1F) shl 6) or (b2 and 0x3F)).toChar())
                    p += 2
                }
                (b and 0xF0) == 0xE0 && p + 2 < buf.capacity() -> {
                    val b2 = buf.get(p + 1).toInt() and 0xFF
                    val b3 = buf.get(p + 2).toInt() and 0xFF
                    sb.append((((b and 0x0F) shl 12) or ((b2 and 0x3F) shl 6) or (b3 and 0x3F)).toChar())
                    p += 3
                }
                else -> p++
            }
        }
        return sb.toString()
    }

    private fun u32(v: Int): Long = v.toLong() and 0xFFFFFFFFL
}
