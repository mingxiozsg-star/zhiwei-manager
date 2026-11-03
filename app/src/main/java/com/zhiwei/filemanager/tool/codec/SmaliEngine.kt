package com.zhiwei.filemanager.tool.codec

import java.io.File
import java.io.OutputStream

/**
 * Smali 引擎适配层。
 *
 * smali / baksmali 3.x 的 API 仍在小幅变动，因此全部用反射调用，
 * 并在多个候选入口间依次回退，避免与某个具体版本硬耦合。
 *
 * 候选：com.android.tools.smali.*（Apktool 2.9+ 使用的 Google 分支）；org.jf.*（2.x）。
 */
object SmaliEngine {

    data class Result(val ok: Boolean, val message: String)

    private val DEXLIB2 = listOf("com.android.tools.smali.dexlib2", "org.jf.dexlib2")
    private val BAKSMALI_CLS = listOf("com.android.tools.smali.baksmali.Baksmali", "org.jf.baksmali.Baksmali")
    private val BAKSMALI_OPTS = listOf("com.android.tools.smali.baksmali.BaksmaliOptions", "org.jf.baksmali.BaksmaliOptions")
    private val BAKSMALI_MAIN = listOf("com.android.tools.smali.baksmali.Main", "org.jf.baksmali.Main")
    private val SMALI_CLS = listOf("com.android.tools.smali.smali.Smali", "org.jf.smali.Smali")
    private val SMALI_OPTS = listOf("com.android.tools.smali.smali.SmaliOptions", "org.jf.smali.SmaliOptions")
    private val SMALI_MAIN = listOf("com.android.tools.smali.smali.Main", "org.jf.smali.Main")

    fun engineInfo(): String {
        val found = mutableListOf<String>()
        (BAKSMALI_CLS + SMALI_CLS).forEach { c ->
            val ok = try {
                Class.forName(c)
                true
            } catch (t: Throwable) {
                false
            }
            if (ok) found += c
        }
        return if (found.isEmpty()) "未检测到 smali 引擎（依赖未打包？）" else found.joinToString("\n")
    }

    private fun firstClass(names: List<String>): Class<*>? {
        names.forEach { n ->
            try {
                return Class.forName(n)
            } catch (t: Throwable) {
            }
        }
        return null
    }

    private fun invokeMain(mains: List<String>, args: Array<String>): Result {
        var last = ""
        mains.forEach { n ->
            try {
                val cls = Class.forName(n)
                val m = cls.getMethod("main", Array<String>::class.java)
                m.invoke(null, args)
                return Result(true, "CLI 通道执行完成：$n")
            } catch (t: Throwable) {
                last = "${t.javaClass.simpleName}: ${t.message}"
            }
        }
        return Result(false, "无可用 CLI 入口（$last）")
    }

    private fun newOptions(names: List<String>): Pair<Class<*>, Any>? {
        val cls = firstClass(names) ?: return null
        return try {
            cls to cls.getDeclaredConstructor().newInstance()
        } catch (t: Throwable) {
            null
        }
    }

    private fun setField(opts: Any, name: String, value: Any) {
        try {
            opts.javaClass.getField(name).set(opts, value)
        } catch (_: Throwable) {
        }
    }

    private fun loadDex(path: String, apiLevel: Int): Any? {
        DEXLIB2.forEach { pkg ->
            try {
                val factory = Class.forName("$pkg.DexFileFactory")
                val opcodesCls = Class.forName("$pkg.Opcodes")
                val opcodes = opcodesCls.getMethod("forApi", Int::class.javaPrimitiveType).invoke(null, apiLevel)
                val load = factory.getMethod("loadDexFile", File::class.java, opcodesCls)
                return load.invoke(null, File(path), opcodes)
            } catch (t: Throwable) {
            }
        }
        return null
    }

    /** dex -> smali 目录。 */
    fun disassemble(dexPath: String, outDir: String, apiLevel: Int = 35, jobs: Int = 4): Result {
        if (!File(dexPath).isFile) return Result(false, "dex 文件不存在：$dexPath")
        File(outDir).mkdirs()
        val dexFile = loadDex(dexPath, apiLevel)
        val baksmaliCls = firstClass(BAKSMALI_CLS)
        val optPair = newOptions(BAKSMALI_OPTS)
        if (dexFile != null && baksmaliCls != null && optPair != null) {
            try {
                setField(optPair.second, "apiLevel", apiLevel)
                setField(optPair.second, "jobs", jobs)
                val m = baksmaliCls.getMethod(
                    "disassembleDexFile",
                    Class.forName("${DEXLIB2.first()}.DexFile"),
                    File::class.java,
                    Int::class.javaPrimitiveType,
                    optPair.first
                )
                m.invoke(null, dexFile, File(outDir), jobs, optPair.second)
                return Result(true, "反汇编完成 → $outDir")
            } catch (t: Throwable) {
                // 落到 CLI 通道
            }
        }
        val r = invokeMain(
            BAKSMALI_MAIN,
            arrayOf("d", dexPath, "-o", outDir, "--api", apiLevel.toString(), "-j", jobs.toString())
        )
        return if (r.ok) Result(true, "反汇编完成（CLI） → $outDir") else Result(false, "反汇编失败：${r.message}")
    }

    /** smali 目录 -> dex。 */
    fun assemble(smaliDir: String, outDex: String, apiLevel: Int = 35, jobs: Int = 4): Result {
        if (!File(smaliDir).isDirectory) return Result(false, "smali 目录不存在：$smaliDir")
        File(outDex).parentFile?.mkdirs()
        File(outDex).delete()
        val smaliCls = firstClass(SMALI_CLS)
        val optPair = newOptions(SMALI_OPTS)
        if (smaliCls != null && optPair != null) {
            try {
                setField(optPair.second, "apiLevel", apiLevel)
                setField(optPair.second, "jobs", jobs)
                setField(optPair.second, "outputDexFile", outDex)
                val m = smaliCls.getMethod("assemble", optPair.first, Array<String>::class.java)
                m.invoke(null, optPair.second, arrayOf(smaliDir))
                if (File(outDex).length() > 0L) return Result(true, "已生成 ${File(outDex).name}")
            } catch (t: Throwable) {
            }
        }
        val r = invokeMain(SMALI_MAIN, arrayOf("a", smaliDir, "-o", outDex, "--api", apiLevel.toString()))
        return if (r.ok && File(outDex).length() > 0L) Result(true, "已生成（CLI）${File(outDex).name}")
        else Result(false, "汇编失败：${r.message}")
    }

    /** 合并多个 dex（DexPool 反射实现）。 */
    fun merge(dexPaths: List<String>, outDex: String, apiLevel: Int = 35): Result {
        if (dexPaths.size < 2) return Result(false, "至少需要 2 个 dex")
        return try {
            val poolCls = firstClass(DEXLIB2.map { "$it.DexPool" }) ?: return Result(false, "未找到 DexPool")
            val pool = poolCls.getConstructor(Int::class.javaPrimitiveType).newInstance(apiLevel)
            val internClass = poolCls.getMethod("internClass", Class.forName("${DEXLIB2.first()}.iface.ClassDef"))
            var count = 0
            dexPaths.forEach { path ->
                val dex = loadDex(path, apiLevel) ?: return@forEach
                val classes = dex.javaClass.getMethod("getClasses").invoke(dex) as? Set<*> ?: return@forEach
                classes.forEach { cd ->
                    if (cd != null) {
                        internClass.invoke(pool, cd)
                        count++
                    }
                }
            }
            File(outDex).parentFile?.mkdirs()
            File(outDex).outputStream().use { os: OutputStream ->
                poolCls.getMethod("writeTo", OutputStream::class.java).invoke(pool, os)
            }
            Result(true, "合并完成：$count 个类 → ${File(outDex).name}")
        } catch (t: Throwable) {
            Result(false, "合并失败：${t.javaClass.simpleName} ${t.message}")
        }
    }

    /** 按类数量拆分 dex。 */
    fun split(dexPath: String, outDir: String, classesPerDex: Int = 2000, apiLevel: Int = 35): Result {
        return try {
            val poolCls = firstClass(DEXLIB2.map { "$it.DexPool" }) ?: return Result(false, "未找到 DexPool")
            val dex = loadDex(dexPath, apiLevel) ?: return Result(false, "无法加载 dex")
            val classes = dex.javaClass.getMethod("getClasses").invoke(dex) as? Set<*> ?: return Result(false, "读取类失败")
            val internClass = poolCls.getMethod("internClass", Class.forName("${DEXLIB2.first()}.iface.ClassDef"))
            val writeTo = poolCls.getMethod("writeTo", OutputStream::class.java)
            val baseName = File(dexPath).nameWithoutExtension
            File(outDir).mkdirs()
            var index = 0
            var produced = 0
            val list = classes.filterNotNull()
            list.chunked(classesPerDex.coerceAtLeast(1)).forEachIndexed { i, chunk ->
                val pool = poolCls.getConstructor(Int::class.javaPrimitiveType).newInstance(apiLevel)
                chunk.forEach { cd -> internClass.invoke(pool, cd) }
                val name = if (i == 0) "$baseName.dex" else "$baseName-$i.dex"
                File(outDir, name).outputStream().use { os -> writeTo.invoke(pool, os) }
                index += chunk.size
                produced++
            }
            Result(true, "拆分完成：${list.size} 个类 → $produced 个 dex")
        } catch (t: Throwable) {
            Result(false, "拆分失败：${t.javaClass.simpleName} ${t.message}")
        }
    }
}
