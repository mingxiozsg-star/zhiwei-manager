package com.zhiwei.filemanager.tool

enum class FeatureStatus(val label: String) {
    READY("已实现"),
    PARTIAL("部分"),
    PLANNED("规划中")
}

data class Feature(val name: String, val status: FeatureStatus, val note: String = "")

/**
 * 全量功能盘点（对应 MT 管理器同级能力清单）。
 * status 是工程真实进度，不做包装：READY 已可用，PARTIAL 已有骨架，PLANNED 未开工。
 */
object ToolRegistry {

    val groups: List<Pair<String, List<Feature>>> = listOf(
        "文件管理" to listOf(
            Feature("多窗口文件浏览（单/双窗、多标签）", FeatureStatus.READY),
            Feature("复制 / 剪切 / 粘贴 / 删除 / 重命名", FeatureStatus.READY),
            Feature("新建文件 / 文件夹 / 属性查看", FeatureStatus.READY),
            Feature("排序（名称/大小/日期/类型）与隐藏文件开关", FeatureStatus.READY),
            Feature("多选与批量操作", FeatureStatus.READY),
            Feature("搜索（当前目录）", FeatureStatus.PARTIAL),
            Feature("书签 / 最近访问", FeatureStatus.PLANNED),
            Feature("ROOT 授权访问文件系统", FeatureStatus.PARTIAL, "su 通道已接"),
            Feature("Shizuku 访问 Android/data", FeatureStatus.READY),
            Feature("文件/文件夹对比", FeatureStatus.PARTIAL),
            Feature("文本文件批量合并", FeatureStatus.PLANNED),
            Feature("数据复用优化（去重）", FeatureStatus.PLANNED)
        ),
        "Dex / Jar / Smali" to listOf(
            Feature("Dex 属性查看", FeatureStatus.READY),
            Feature("Dex 字符串常量查看", FeatureStatus.READY),
            Feature("Dex 混淆字典提取", FeatureStatus.READY),
            Feature("Dex <-> Smali 互转", FeatureStatus.READY, "smali/baksmali 引擎反射接入"),
            Feature("Smali -> Dex 汇编", FeatureStatus.READY),
            Feature("Dex 合并", FeatureStatus.READY, "DexPool"),
            Feature("Dex 分割", FeatureStatus.READY),
            Feature("Jar / AAR 解包与 classes.dex 提取", FeatureStatus.PARTIAL),
            Feature("class 文件 / jar 包反编译", FeatureStatus.PLANNED),
            Feature("Dex 批量修复", FeatureStatus.PLANNED),
            Feature("Dex 对比", FeatureStatus.PARTIAL),
            Feature("Dex 编辑 Plus（搜索/编辑 Smali）", FeatureStatus.PARTIAL, "编辑器+高亮待接"),
            Feature("Smali 语法查询 / 流程图 / IR / CodeOffset", FeatureStatus.PLANNED),
            Feature("Smali -> Java", FeatureStatus.PLANNED),
            Feature("Dex2C", FeatureStatus.PLANNED)
        ),
        "Apk 解析与签验" to listOf(
            Feature("Apk 解包 / 重打包（apktool 内核）", FeatureStatus.PARTIAL, "aapt2 二进制已内置"),
            Feature("Apk 签名 / 一键随机签名", FeatureStatus.PLANNED),
            Feature("Apk 共存（包名分身）", FeatureStatus.PLANNED),
            Feature("Apk 对齐优化", FeatureStatus.PLANNED),
            Feature("Apk 伪加密 / 反伪加密", FeatureStatus.PLANNED),
            Feature("Apk 加固判断", FeatureStatus.PLANNED),
            Feature("AndroidManifest / Apk 对比", FeatureStatus.PLANNED),
            Feature("Shizuku 安装 apk / apks", FeatureStatus.PLANNED)
        ),
        "混淆 / 加密 / 加固" to listOf(
            Feature("Apk / Dex / Jar 字符串加密", FeatureStatus.PLANNED),
            Feature("高兼容性字符串加密", FeatureStatus.PLANNED),
            Feature("控制流混淆（全量加密/方法隐藏/指令替换/扁平化）", FeatureStatus.PLANNED),
            Feature("Dex 超级混淆 / DEX 混淆对抗", FeatureStatus.PLANNED),
            Feature("Res 资源混淆 / 反混淆 / 加密资源内容", FeatureStatus.PLANNED),
            Feature("assets / res 资源加密保护", FeatureStatus.PLANNED),
            Feature("Arsc 编辑 / Arsc 翻译 / XML 翻译模式", FeatureStatus.PLANNED),
            Feature("VM 保护 / 函数抽取壳 / SO 保护", FeatureStatus.PLANNED),
            Feature("一键 Xposed 检测 / Toast / 对话框注射", FeatureStatus.PLANNED),
            Feature("LOG 注入 / 字符串解密 LOG", FeatureStatus.PLANNED),
            Feature("禁止截屏 / 崩溃日志记录注入", FeatureStatus.PLANNED),
            Feature("Dex 换包名 / 加密资源 ID", FeatureStatus.PLANNED)
        ),
        "编解码与工具箱" to listOf(
            Feature("Base64 / Hex / Unicode / URL 编解码", FeatureStatus.READY),
            Feature("MD5 / SHA-1 / SHA-256 / SHA-512", FeatureStatus.READY),
            Feature("DES 加解密", FeatureStatus.READY),
            Feature("去空格 / 去空行 / 去换行", FeatureStatus.READY),
            Feature("JSON 美化 / 压缩 / 校验", FeatureStatus.READY),
            Feature("时间戳 <-> 日期", FeatureStatus.READY),
            Feature("字符串反转 / 统计（字节、行数）", FeatureStatus.READY),
            Feature("GBK <-> UTF-8 文夹夹转码", FeatureStatus.READY),
            Feature("Xml 文本代码格式化", FeatureStatus.PARTIAL),
            Feature("Svg <-> VectorDrawable 互转", FeatureStatus.PLANNED)
        ),
        "归档与压缩" to listOf(
            Feature("ZIP / JAR / APK 解压与压缩", FeatureStatus.READY),
            Feature("TAR / GZ / XZ / LZ4 / ZSTD 解压", FeatureStatus.READY),
            Feature("7z / RAR 解压", FeatureStatus.PLANNED),
            Feature("归档内文件列表预览", FeatureStatus.READY)
        ),
        "编辑器与查看器" to listOf(
            Feature("文本编辑器（保存/另存，编码可选）", FeatureStatus.PARTIAL),
            Feature("代码语法高亮（Smali/Java/XML/Json）", FeatureStatus.PLANNED),
            Feature("Hex 查看器 + 二进制搜索", FeatureStatus.READY),
            Feature("Hex 编辑器（写回）", FeatureStatus.PLANNED),
            Feature("文本对比 / 十六进制对比", FeatureStatus.PARTIAL, "统一 diff 已实现"),
            Feature("Markdown / Html 预览器", FeatureStatus.PLANNED),
            Feature("字体预览器 / 音乐播放器", FeatureStatus.PLANNED),
            Feature("布局查看器 / Activity 记录", FeatureStatus.PLANNED),
            Feature("数据库编辑（SQLite）", FeatureStatus.PLANNED),
            Feature("SO 文件查看/编辑/对比（字符常量）", FeatureStatus.PARTIAL, "字符串扫描已备")
        ),
        "媒体与文档" to listOf(
            Feature("视频转码 / 剪切（FFmpeg）", FeatureStatus.PLANNED, "FFmpeg 二进制分发待定"),
            Feature("提取视频流 / 音频流", FeatureStatus.PLANNED),
            Feature("自定义 FFmpeg 命令", FeatureStatus.PARTIAL, "命令构建器待接"),
            Feature("PDF 合并 / 分割 / 加密 / 提取 / 删页", FeatureStatus.PLANNED),
            Feature("PDF 转 Word/PPT/Excel/长图", FeatureStatus.PLANNED)
        ),
        "网络与存储" to listOf(
            Feature("终端模拟器（内置 sh 会话）", FeatureStatus.READY),
            Feature("网络存储 FTP / SFTP / SMB / WebDAV", FeatureStatus.PLANNED),
            Feature("HTTP 服务 / Apk MCP 服务", FeatureStatus.PLANNED),
            Feature("NPMan 接口测试（含 HttpCanary 文件）", FeatureStatus.PLANNED)
        ),
        "系统与工具" to listOf(
            Feature("Shizuku / ROOT 提权控制台", FeatureStatus.READY),
            Feature("后台任务进度看板", FeatureStatus.READY),
            Feature("屏幕取色器 / 屏幕标尺", FeatureStatus.PLANNED),
            Feature("分贝测试仪", FeatureStatus.PLANNED)
        )
    )


    fun all(): List<Feature> = groups.flatMap { it.second }

    fun statsText(): String {
        val allFeatures = all()
        val ready = allFeatures.count { it.status == FeatureStatus.READY }
        val partial = allFeatures.count { it.status == FeatureStatus.PARTIAL }
        val planned = allFeatures.count { it.status == FeatureStatus.PLANNED }
        return "共 ${allFeatures.size} 项 · 已实现 $ready · 部分 $partial · 规划中 $planned"
    }
}
