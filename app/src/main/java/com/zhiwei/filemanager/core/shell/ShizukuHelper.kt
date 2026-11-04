package com.zhiwei.filemanager.core.shell

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

/** Shizuku 提权通道：以 shell(uid 2000) 身份执行命令与访问 Android/data。 */
object ShizukuHelper {

    const val PERMISSION_CODE = 0x5A17

    fun ping(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        ping() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    /** 已被系统/用户永久拒绝时 true，此时不应再弹授权。 */
    fun shouldShowRationale(): Boolean = try {
        ping() && Shizuku.shouldShowRequestPermissionRationale()
    } catch (t: Throwable) {
        false
    }

    fun requestPermission() {
        try {
            if (ping()) Shizuku.requestPermission(PERMISSION_CODE)
        } catch (_: Throwable) {
        }
    }

    fun addListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (_: Throwable) {
        }
    }

    fun removeListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (_: Throwable) {
        }
    }

    /** 以 shell 身份执行命令。 */
    fun exec(command: String): ShellResult {
        if (!hasPermission()) return ShellResult(-1, "Shizuku 未授权")
        return try {
            val process = newProcess(arrayOf("sh", "-c", command))
            val out = process.inputStream.bufferedReader().use { it.readText() }
            val err = process.errorStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            ShellResult(code, out + if (err.isNotBlank()) "\n" + err else "")
        } catch (t: Throwable) {
            ShellResult(-1, t.message ?: "Shizuku 执行失败")
        }
    }

    // Shizuku API 13.x 把 newProcess 收成了 private（官方推荐 bindUserService + 自建 AIDL），
    // 这里反射调用它，省掉一个自定义服务进程，行为与旧版 Shizuku.newProcess 完全一致。
    private var newProcessMethod: java.lang.reflect.Method? = null

    private fun newProcess(cmd: Array<String>): ShizukuRemoteProcess {
        val m = newProcessMethod ?: Shizuku::class.java
            .getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            .apply { isAccessible = true }
            .also { newProcessMethod = it }
        return (m.invoke(null, *arrayOf<Any?>(cmd, null, null))) as ShizukuRemoteProcess
    }

    /** 读取受保护目录（如 Android/data）时优先走这里。 */
    fun listProtected(path: String): List<String> {
        val r = exec("ls -a " + quote(path))
        if (!r.ok) return emptyList()
        return r.output.lines().map { it.trim() }.filter { it.isNotEmpty() && it != "." && it != ".." }
    }

    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
