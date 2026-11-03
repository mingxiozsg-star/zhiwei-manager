package com.zhiwei.filemanager.tool.codec

import java.io.File

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

    /**
     * 写出 dex 的版本上限（api 34 → dex 040）。
     *
     * 实测 smali-dexlib2 3.0.10：`Opcodes.forApi(35)` 会把 dex 写成 041 新容器，
     * 而它自己的 `DexFileFactory.loadDexFile` 无法回读 041（container_size 校验失败），
     * 因此写出统一封顶到 api 34；041 输入在加载前直接拦截并提示。
     */
    private const val MAX_WRITE_API = 34
    private const val UNSUPPORTED_DEX_VERSION = 41

    private val DEXLIB2 = listOf("com.android.tools.smali.dexlib2", "org.jf.dexlib2")
    private val BAKSMALI_CLS = listOf("com.android.tools.smali.baksmali.Baksmali", "org.jf.baksmali.Baksmali")
    private val BAKSMALI_OPTS = listOf("com.android.tools.smali.baksmali.BaksmaliOptions", "org.jf.baksmali.BaksmaliOptions")
    private val BAKSMALI_MAIN = listOf("com.android.tools.smali.baksmali.Main", "org.jf.baksmali.Main")
    private val SMALI_CLS = listOf("com.android.tools.smali.smali.Smali", "org.jf.smali.Smali")
    private val SMALI_OPTS = listOf("com.android.tools.smali.smali.SmaliOptions", "org.jf.smali.SmaliOptions")
    private val SMALI_MAIN = listOf("com.android.tools.smali.smali.Main", "org.jf.smali.Main")

    fun engineInfo(): String {
        val probes = linkedMapOf(
            "汇编入口 smali" to SMALI_CLS,
            "汇编选项 SmaliOptions" to SMALI_OPTS,
            "反汇编入口 baksmali" to BAKSMALI_CLS,
            "反汇编选项 BaksmaliOptions" to BAKSMALI_OPTS,
            "dex 加载 DexFileFactory" to DEXLIB2.map { "$it.DexFileFactory" },
            "dex 写出 DexPool" to DEXLIB2.map { "$it.writer.pool.DexPool" },
            "指令集 Opcodes" to DEXLIB2.map { "$it.Opcodes" }
        )
        val lines = mutableListOf<String>()
        probes.forEach { (label, names) ->
            val hit = names.firstOrNull { runCatching { Class.forName(it) }.isSuccess }
            lines += if (hit != null) "✔ $label → $hit" else "✘ $label（未打包）"
        }
        return lines.joinToString("\n")
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

    /** dexlib2 的 Opcodes 实例：3.x 起 DexFileFactory / DexPool 的参数类型已从 int 改为 Opcodes。 */
    private fun opcodes(apiLevel: Int): Pair<Class<*>, Any>? {
        DEXLIB2.forEach { pkg ->
            try {
                val cls = Class.forName("$pkg.Opcodes")
                val inst = cls.getMethod("forApi", Int::class.javaPrimitiveType).invoke(null, apiLevel)
                if (inst != null) return cls to inst
            } catch (t: Throwable) {
            }
        }
        return null
    }

    /** 读取 dex 头部的版本号（magic 中的 3 位数字）。 */
    private fun dexVersion(path: String): Int? = try {
        File(path).inputStream().use { input ->
            val b = ByteArray(8)
            if (input.read(b) != 8 ||
                b[0] != 'd'.code.toByte() || b[1] != 'e'.code.toByte() || b[2] != 'x'.code.toByte()
            ) {
                null
            } else {
                String(b, 4, 3).trim().toIntOrNull()
            }
        }
    } catch (t: Throwable) {
        null
    }

    /** 041 容器在 dexlib2 3.0.10 中读不回，提前拦截并给出可操作提示。 */
    private fun rejectUnsupported(path: String): Result? {
        val v = dexVersion(path) ?: return null
        return if (v >= UNSUPPORTED_DEX_VERSION) {
            Result(
                false,
                "该 dex 是版本 $v 的新容器（Android 15+），当前 dexlib2 3.0.10 解析不了；" +
                    "可用本应用的 Dex 属性/字符串查看（纯 Kotlin 解析器）读元数据，或先把 dex 降到 040/039 再反汇编"
            )
        } else {
            null
        }
    }

    private fun loadDex(path: String, apiLevel: Int): Any? {
        val pair = opcodes(apiLevel) ?: return null
        DEXLIB2.forEach { pkg ->
            try {
                val factory = Class.forName("$pkg.DexFileFactory")
                val load = factory.getMethod("loadDexFile", File::class.java, pair.first)
                return load.invoke(null, File(path), pair.second)
            } catch (t: Throwable) {
            }
        }
        return null
    }

    private fun dexlib2PkgOf(poolCls: Class<*>): String =
        poolCls.name.substringBefore(".writer.pool.DexPool")

    private fun newPool(poolCls: Class<*>, opcodesCls: Class<*>, opcodes: Any): Any =
        poolCls.getConstructor(opcodesCls).newInstance(opcodes)

    /** DexPool -> 磁盘 dex（DexPool(Opcodes) 构造 + writeTo(DexDataStore) 落盘）。 */
    private fun writePool(pool: Any, poolCls: Class<*>, outFile: File) {
        val pkg = dexlib2PkgOf(poolCls)
        val storeCls = Class.forName("$pkg.writer.io.FileDataStore")
        val store = storeCls.getConstructor(File::class.java).newInstance(outFile)
        try {
            poolCls.getMethod("writeTo", Class.forName("$pkg.writer.io.DexDataStore")).invoke(pool, store)
        } finally {
            runCatching { storeCls.getMethod("close").invoke(store) }
        }
    }

    private fun classesOf(dex: Any): List<Any> {
        val set = dex.javaClass.getMethod("getClasses").invoke(dex) as? Set<*> ?: return emptyList()
        return set.filterNotNull()
    }

    /** dex -> smali 目录。 */
    fun disassemble(dexPath: String, outDir: String, apiLevel: Int = 34, jobs: Int = 4): Result {
        if (!File(dexPath).isFile) return Result(false, "dex 文件不存在：$dexPath")
        rejectUnsupported(dexPath)?.let { return it }
        File(outDir).mkdirs()
        val api = apiLevel.coerceAtMost(MAX_WRITE_API)
        val dexFile = loadDex(dexPath, api)
        val baksmaliCls = firstClass(BAKSMALI_CLS)
        val optPair = newOptions(BAKSMALI_OPTS)
        if (dexFile != null && baksmaliCls != null && optPair != null) {
            try {
                setField(optPair.second, "apiLevel", api)
                setField(optPair.second, "jobs", jobs)
                val m = baksmaliCls.getMethod(
                    "disassembleDexFile",
                    Class.forName("${DEXLIB2.first()}.iface.DexFile"),
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
            arrayOf("d", dexPath, "-o", outDir, "--api", api.toString(), "-j", jobs.toString())
        )
        return if (r.ok) Result(true, "反汇编完成（CLI） → $outDir") else Result(false, "反汇编失败：${r.message}")
    }

    /** smali 目录 -> dex（写成 dex 040，避开 3.0.10 读不回的 041 容器）。 */
    fun assemble(smaliDir: String, outDex: String, apiLevel: Int = 34, jobs: Int = 4): Result {
        if (!File(smaliDir).isDirectory) return Result(false, "smali 目录不存在：$smaliDir")
        File(outDex).parentFile?.mkdirs()
        File(outDex).delete()
        val api = apiLevel.coerceAtMost(MAX_WRITE_API)
        val smaliCls = firstClass(SMALI_CLS)
        val optPair = newOptions(SMALI_OPTS)
        if (smaliCls != null && optPair != null) {
            try {
                setField(optPair.second, "apiLevel", api)
                setField(optPair.second, "jobs", jobs)
                setField(optPair.second, "outputDexFile", outDex)
                val m = smaliCls.getMethod("assemble", optPair.first, Array<String>::class.java)
                m.invoke(null, optPair.second, arrayOf(smaliDir))
                if (File(outDex).length() > 0L) return Result(true, "已生成 ${File(outDex).name}")
            } catch (t: Throwable) {
            }
        }
        val r = invokeMain(SMALI_MAIN, arrayOf("a", smaliDir, "-o", outDex, "--api", api.toString()))
        return if (r.ok && File(outDex).length() > 0L) Result(true, "已生成（CLI）${File(outDex).name}")
        else Result(false, "汇编失败：${r.message}")
    }

    /** 合并多个 dex（DexPool 反射实现）。 */
    fun merge(dexPaths: List<String>, outDex: String, apiLevel: Int = 35): Result {
        if (dexPaths.size < 2) return Result(false, "至少需要 2 个 dex")
        return try {
            val op = opcodes(apiLevel) ?: return Result(false, "未找到 dexlib2 Opcodes")
            val poolCls = firstClass(DEXLIB2.map { "$it.writer.pool.DexPool" })
                ?: return Result(false, "未找到 DexPool")
            val pkg = dexlib2PkgOf(poolCls)
            val pool = newPool(poolCls, op.first, op.second)
            val internClass = poolCls.getMethod("internClass", Class.forName("$pkg.iface.ClassDef"))
            var count = 0
            dexPaths.forEach { path ->
                val dex = loadDex(path, apiLevel) ?: return@forEach
                classesOf(dex).forEach { cd ->
                    internClass.invoke(pool, cd)
                    count++
                }
            }
            File(outDex).parentFile?.mkdirs()
            writePool(pool, poolCls, File(outDex))
            Result(true, "合并完成：$count 个类 → ${File(outDex).name}")
        } catch (t: Throwable) {
            Result(false, "合并失败：${t.javaClass.simpleName} ${t.message}")
        }
    }

    /** 按类数量拆分 dex。 */
    fun split(dexPath: String, outDir: String, classesPerDex: Int = 2000, apiLevel: Int = 35): Result {
        return try {
            val op = opcodes(apiLevel) ?: return Result(false, "未找到 dexlib2 Opcodes")
            val poolCls = firstClass(DEXLIB2.map { "$it.writer.pool.DexPool" })
                ?: return Result(false, "未找到 DexPool")
            val pkg = dexlib2PkgOf(poolCls)
            val dex = loadDex(dexPath, apiLevel) ?: return Result(false, "无法加载 dex")
            val list = classesOf(dex)
            if (list.isEmpty()) return Result(false, "dex 中没有类")
            val internClass = poolCls.getMethod("internClass", Class.forName("$pkg.iface.ClassDef"))
            val baseName = File(dexPath).nameWithoutExtension
            File(outDir).mkdirs()
            var produced = 0
            list.chunked(classesPerDex.coerceAtLeast(1)).forEachIndexed { i, chunk ->
                val pool = newPool(poolCls, op.first, op.second)
                chunk.forEach { cd -> internClass.invoke(pool, cd) }
                val name = if (i == 0) "$baseName.dex" else "$baseName-$i.dex"
                writePool(pool, poolCls, File(outDir, name))
                produced++
            }
            Result(true, "拆分完成：${list.size} 个类 → $produced 个 dex")
        } catch (t: Throwable) {
            Result(false, "拆分失败：${t.javaClass.simpleName} ${t.message}")
        }
    }
}
