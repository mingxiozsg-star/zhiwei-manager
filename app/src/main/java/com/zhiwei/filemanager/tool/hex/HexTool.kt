package com.zhiwei.filemanager.tool.hex

import java.io.File
import java.io.RandomAccessFile

data class HexLine(val offset: Long, val hex: String, val ascii: String)

/** 十六进制查看与搜索。 */
object HexTool {

    const val PAGE = 16

    fun fileSize(path: String): Long = try {
        File(path).length()
    } catch (t: Throwable) {
        0L
    }

    fun readPage(path: String, offset: Long, lineCount: Int = 256): List<HexLine> {
        val out = ArrayList<HexLine>(lineCount)
        try {
            RandomAccessFile(path, "r").use { raf ->
                raf.seek(offset.coerceAtLeast(0))
                val buf = ByteArray(PAGE)
                var pos = offset
                var count = 0
                while (count < lineCount) {
                    val n = raf.read(buf)
                    if (n <= 0) break
                    out += line(pos, buf, n)
                    pos += n
                    count++
                }
            }
        } catch (t: Throwable) {
        }
        return out
    }

    fun line(offset: Long, buf: ByteArray, len: Int): HexLine {
        val hex = StringBuilder()
        val ascii = StringBuilder()
        for (i in 0 until PAGE) {
            if (i < len) {
                hex.append(String.format("%02X ", buf[i]))
                ascii.append(asciiChar(buf[i]))
            } else {
                hex.append("   ")
                ascii.append(' ')
            }
        }
        return HexLine(offset, hex.toString().trimEnd(), ascii.toString())
    }

    private fun asciiChar(b: Byte): Char {
        val v = b.toInt() and 0xFF
        return if (v in 32..126) v.toChar() else '.'
    }

    fun parseHexPattern(text: String): ByteArray? {
        val clean = text.replace(" ", "").replace("\n", "").replace("0x", "").replace("\\x", "")
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        return try {
            ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (t: Throwable) {
            null
        }
    }

    /** 十六进制串或普通文本搜索，返回命中偏移。 */
    fun search(path: String, pattern: ByteArray, maxHits: Int = 300): List<Long> {
        val hits = ArrayList<Long>()
        if (pattern.isEmpty()) return hits
        try {
            RandomAccessFile(path, "r").use { raf ->
                val chunk = ByteArray(1 shl 20)
                var base = 0L
                var tail = ByteArray(0)
                while (hits.size < maxHits) {
                    val n = raf.read(chunk)
                    if (n <= 0) break
                    val window = tail + chunk.copyOf(n)
                    var i = 0
                    while (i <= window.size - pattern.size) {
                        if (matches(window, i, pattern)) {
                            hits += base - tail.size + i
                            if (hits.size >= maxHits) return hits
                        }
                        i++
                    }
                    val keep = minOf(pattern.size - 1, window.size)
                    tail = if (keep > 0) window.copyOfRange(window.size - keep, window.size) else ByteArray(0)
                    base += n
                }
            }
        } catch (t: Throwable) {
        }
        return hits
    }

    private fun matches(data: ByteArray, at: Int, pattern: ByteArray): Boolean {
        if (at < 0 || at + pattern.size > data.size) return false
        for (i in pattern.indices) if (data[at + i] != pattern[i]) return false
        return true
    }
}
