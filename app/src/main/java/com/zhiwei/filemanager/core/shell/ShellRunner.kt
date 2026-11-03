package com.zhiwei.filemanager.core.shell

import java.io.File
import java.util.concurrent.TimeUnit

data class ShellResult(val code: Int, val output: String) {
    val ok: Boolean get() = code == 0
}

/** 本地 shell 执行器（普通权限 / su root）。Shizuku 通道见 [ShizukuHelper]。 */
object ShellRunner {

    fun exec(command: String, asRoot: Boolean = false, timeoutSec: Long = 120L): ShellResult {
        return try {
            val argv = if (asRoot) arrayOf("su", "-c", command) else arrayOf("sh", "-c", command)
            val process = ProcessBuilder(*argv).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                ShellResult(-1, text + "\n[命令超时]")
            } else {
                ShellResult(process.exitValue(), text)
            }
        } catch (t: Throwable) {
            ShellResult(-1, t.message ?: "shell 执行失败")
        }
    }

    fun isRootAvailable(): Boolean = exec("id", asRoot = true, timeoutSec = 10L).output.contains("uid=0")

    /** 常用入口目录（含需要提权才能读的路径）。 */
    fun entryPoints(): List<Pair<String, String>> = listOf(
        "/sdcard" to "内部存储",
        "/storage" to "存储设备",
        "/data" to "应用数据",
        "/data/adb" to "ADB 目录",
        "/system" to "系统分区",
        "/" to "根目录"
    )

    fun storageVolumes(): List<String> {
        val out = mutableListOf<String>()
        try {
            File("/storage").listFiles()?.forEach { if (it.isDirectory) out += it.absolutePath }
        } catch (_: Throwable) {
        }
        return out
    }
}
