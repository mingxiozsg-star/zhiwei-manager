# 知微管理器（ZhiweiManager）

Android 文件管理器，交互模型参照 MT 管理器（双窗口、无剪贴板中转、直接跨窗口操作），
内置面向逆向工程与日常维护的工具链。包名 `com.zhiwei.filemanager`，版本 `1.0.0`。

> 构建策略：**不在本地出正式包**。正式包统一由 GitHub Actions 构建（`.github/workflows/android-ci.yml`）。

## 一、当前已落地能力（真实进度）

### 文件管理
- 单窗 / 双窗（可继续加窗）浏览，窗格标签切换，路径实时同步，不做剪贴板中转
- 复制 / 移动 / 删除 / 重命名 / 新建文件与文件夹 / 属性查看
- 多选（水平滑动手势进入多选）与批量操作、跨窗口「复制/移动到另一窗口」
- 排序（名称 / 大小 / 日期 / 类型 + 升降序）、隐藏文件开关、当前目录过滤
- 文件类型色标（APK / DEX / 归档 / 图片 / 视频 / 音频 / 文本 / 代码 / PDF / 字体 / 数据库）
- Shizuku（shell 身份）读取 `/Android/data` 等受保护目录；ROOT 通道可选
- 书签 / 最近访问（`BookmarkStore`，独立 SP，互不影响清理）：一键直达、当前目录置顶收藏、最近 30 条
- 常用目录快捷入口（内部存储 / 下载 / 图片 / 相机 / 文档 / 音乐 / Android/data / 根目录，只列真实存在的）
- 目录状态条：项目数、文件夹数、合计体积、已选数量
- 递归搜索面板：广度优先流式遍历、实时路径进度、文件名 / 文件内容双模式、可限当前层、结果截断保护
- 批量重命名：查找替换（可正则）/ 前缀后缀 / 序号（起始、补零、前置）/ 大小写 / 去空格 / 保留扩展名；
  先预览再执行，冲突项标出并跳过，执行走两阶段改名避免互相覆盖
- 文件与目录对比：文本 diff、字节流式对比（首个差异偏移 / 差异块 / MD5）、目录求差集（可递归 / 比内容 / 比时间）
- 文本批量合并：按名称 / 时间 / 体积排序，逐行流式写盘，逐文件探测编码后统一转 UTF-8，单文件与总体积双上限保护

### Dex / Jar / Smali
- Dex 属性解析、字符串常量提取、混淆字典提取（纯 Kotlin 解析器，无 JNI）
- `dex <-> smali` 互转、`smali -> dex` 汇编、Dex 合并（DexPool）、Dex 分割
- 引擎层 `SmaliEngine` 采用多候选包名反射（兼容 `com.android.tools.smali` 与 `org.jf`）+ CLI 回退
- Jar / AAR 解包与 `classes.dex` 提取：部分（解包已可用，写回未做）

### 归档 / 文本 / 编码
- Zip / Tar / Gz / Xz / Lz4 / Zstd 解压与压缩（commons-compress + xz + zstd-jni + lz4）
- 7z 解压（commons-compress `SevenZFile`，支持 LZMA / LZMA2 / BZip2 / Deflate）
- RAR 解压（junrar 7.5.5，加密 / 分卷缺失走失败计数不崩溃）
- 文件夹 GBK ↔ UTF-8 批量转码（含编码嗅探）
- 字符串工具箱：Base64 / Hex / Unicode / URL / 哈希 / DES / JSON / 时间戳
- Hex 查看器（分页 + 二进制搜索）、文本 Diff（java-diff-utils）、文本/XML/JSON 编辑器
- 工具箱按「文本与编码 / 查看与对比 / 逆向工程」分组，能力清单与真实状态同页可见

### 系统
- 内置终端：普通（`sh -c`）/ ROOT（`su -c`）/ Shizuku（uid 2000）三通道，会话内保留输出
- 后台任务看板：所有耗时操作统一上报进度与结果
- 提权控制台：所有文件访问权限、Shizuku 授权、ROOT 检测
- 权限清单：`MANAGE_EXTERNAL_STORAGE`、`READ/WRITE_EXTERNAL_STORAGE`、`INTERNET`、
  `POST_NOTIFICATIONS`、`QUERY_ALL_PACKAGES`、`REQUEST_INSTALL_PACKAGES`、`FOREGROUND_SERVICE*`、
  `WAKE_LOCK`；FileProvider 已配置（`${applicationId}.fileprovider` → `res/xml/provider_paths.xml`）
- 分享目标：`ACTION_SEND` / `ACTION_VIEW`（APK、zip、txt）可直接送入本应用

## 二、待办路线（按优先级）

**P0 — 基础补完**
1. 书签 / 最近访问 / 当前目录递归搜索
2. 文件与文件夹对比、文本批量合并
3. Jar/AAR 写回与重新打包（含 `classes.dex` 注入）

**P1 — 逆向主力**
4. APK 签名（v1/v2/v3、自定义 keystore）、共存（applicationId / 包名改写）、安装引导
5. Res 资源混淆（`resources.arsc` 字符串池重写）、Apk 加固判定
6. apktool 全流程接入（反编译 / 回编译 / 资源替换），与内置 smali 引擎共用
7. SO 查看/编辑/对比（字符串常量扫描、节区视图、Hex 级差异）
8. 十六进制对比（文件级差异 + 字节级跳转）
9. 高兼容性省略库（.so 依赖裁剪）编辑

**P2 — 保护与增强**
10. Dex / Jar 混淆、字符串加密
11. 控制流混淆 8.0、VM 保护
12. 布局查看器（运行时 View 层级 / Layout Inspector 简版）

**P3 — 媒体与网络**
13. PDF 合并 / 加密 / 分割 / 提取 / 解除加密限制 / 删页（pdfbox-android）
14. FFmpeg：转码、剪切、提取视频流/音频流、自定义命令（ffmpeg-kit 或自编译 so）
15. Markdown / HTML 预览器
16. 网络存储：FTP / SFTP / SMB / WebDAV
17. HTTP 服务、APK MCP 服务（由 App 进程对外提供接口）

> 全量清单与逐项状态见应用内「设置 → 功能清单」，数据源为 `tool/ToolRegistry.kt`
> （`READY` / `PARTIAL` / `PLANNED` 三态，不做包装）。

## 三、工程结构

```
app/src/main/java/com/zhiwei/filemanager/
├── MainActivity.kt              入口（主题模式、通知权限）
├── core/
│   ├── fs/       FileRepo（列举/排序/路径）、FileOps（增删改查/复制/移动）
│   ├── model/    FileItem、SortMode/SortOrder
│   ├── prefs/    AppPrefs（Compose State 包装的 SharedPreferences）
│   ├── shell/    ShellRunner（sh/su）、ShizukuHelper（Shizuku 通道）
│   ├── task/     TaskCenter（任务看板）、AppScope
│   └── util/     Format（尺寸/时间/类型判定）、AppInfo
├── tool/
│   ├── ToolRegistry.kt           全量功能清单与真实进度
│   ├── archive/ArchiveTool.kt    zip/tar/gz/xz/lz4/zstd/rar
│   ├── codec/    DexReader（纯 Kotlin Dex 解析）、SmaliEngine（反射兼容层）
│   ├── diff/     DiffTool（统一 diff）
│   ├── hex/      HexTool（分页浏览 + 搜索）
│   └── text/     EncodingTool（GBK/UTF-8 嗅探与批量转码）、StringCodec
└── ui/
    ├── AppRoot.kt               底部四页 + 二级路由（工具页 / 终端）
    ├── files/     FileBrowserScreen（多窗格/列表/批量/菜单/属性）、FileBrowserState
    ├── settings/  SettingsScreen（外观/浏览/提权/功能清单/关于）
    ├── tasks/     TaskBoardScreen
    ├── terminal/  TerminalScreen（三通道 shell）
    ├── tools/     ToolboxScreen 与各工具页
    └── theme/     ZhiweiTheme（靛蓝 × 青）
```

## 四、关键依赖

| 用途 | 依赖 |
| --- | --- |
| Dex/Smali | `com.android.tools.smali:smali / baksmali / smali-dexlib2:3.0.10`（Google Maven） |
| 归档 | `commons-compress:1.27.1`、`xz:1.10`、`zstd-jni:1.5.6-4`、`lz4-java:1.8.0`、`junrar:7.5.5` |
| 提权 | `dev.rikka.shizuku:api / provider:13.1.5` |
| 对比 | `io.github.java-diff-utils:java-diff-utils:4.12` |
| UI | Compose BOM + Material3，`coreLibraryDesugaring:2.1.4` |

## 五、构建（云端）

```
GitHub → Actions → Android CI → Run workflow
```

- 触发器：`push`（main/master、`v*` tag）、`pull_request`、手动 `workflow_dispatch`
- JDK 17 + Gradle Wrapper（9.1.0）；`assembleDebug` 与 `assembleRelease` 都会跑
- 签名：仓库 Secrets 提供 `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`
  时用正式 keystore；未提供则 CI 内临时生成，仅用于构建验证
- 产物：Actions Artifacts → `zhiwei-manager-apk`（debug + release APK）
- 打 `v*` 标签时会自动创建 GitHub Release 并挂上 APK
- 本地 aarch64（proot）环境下 `aapt2` 会自动切到 `linux-aarch64`，云端 x86_64 用默认分发

### 工具链版本矩阵（已在 CI 实测校正）
| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Gradle | 9.1.0 | Wrapper 固化，AGP 9 要求 Gradle 9.x |
| AGP | 9.0.0 | 字节码 Java 11 基线，JDK 17 可运行 |
| Kotlin / KGP | 2.2.10（跟随 AGP 依赖树） | **不应用** `org.jetbrains.kotlin.android`（AGP 内建，见坑位 1）；版本号仅用于对齐 Compose 子插件 |
| Compose 编译器 | 2.2.10（`org.jetbrains.kotlin.plugin.compose`） | 必须显式应用，AGP 不会自动注入（见坑位 2） |
| compileSdk / targetSdk | 35 | Compose 1.10.2、material3 1.4.0、core 1.16.0 的 `minCompileSdk` 均为 35 |
| minSdk | 24 | 无多 dex 需求 |
| JVM target | 17 | 显式声明一次作为兜底，防止 JVM-target 一致性校验失败 |

**坑位 1：AGP 9 的 Kotlin 支持是内建的，手动应用 KGP 会直接崩。**
实测证据（CI Run #41737746093，commit `68084317`）：

```
* What went wrong:
An exception occurred applying plugin request [id: 'org.jetbrains.kotlin.android', version: '2.2.10']
> Failed to apply plugin 'org.jetbrains.kotlin.android'.
   > Cannot add extension with name 'kotlin', as there is an extension already registered with that name.
```

机理：`BooleanOption.BUILT_IN_KOTLIN`（`android.builtInKotlin`）默认值为 **true**，
`BasePlugin.configureProject` 会调 `BuiltInKotlinServicesKt.initBuiltInKotlinSupportIfRequired`，
由 AGP 通过 KGP 的 `KotlinBaseApiPlugin.createKotlinAndroidExtension()` 自行注册名为 `kotlin`、
类型为 `KotlinAndroidProjectExtension` 的扩展。随后脚本再 apply KGP，KGP 重复 `createKotlinExtension` → 崩。

因此 `plugins {}` 里 **只保留 `com.android.application`**。KGP 版本跟随 AGP 依赖树
（9.0.0 的 POM 绑定 kotlin-gradle-plugin 2.2.10），KGP 类仍以 runtime 依赖存在于 buildscript classpath，
故 `kotlin { compilerOptions { … } }` 与 `import org.jetbrains.kotlin.gradle.dsl.JvmTarget` 均可用。
兜底方案：`gradle.properties` 设 `android.builtInKotlin=false` 退回旧模型（AGP 10 会强制）。

**坑位 2：Compose 编译器插件反过来必须手动应用（AGP 只在内建 Kotlin 的“声明式 DSL”路径才自动注入）。**
反编译 AGP 9 的 `VariantTaskManager` 可见硬校验：只要任一组件 `buildFeatures.compose == true`、
Kotlin 版本 ≥ 2.0，且 `KgpUtils.isComposeCompilerPluginApplied(project) == false`，
就会以 `MISSING_COMPOSE_COMPILER_GRADLE_PLUGIN` 级别 **报错终止**：

```
Starting in Kotlin 2.0, the Compose Compiler Gradle plugin is required when compose is enabled.
```

`AppPlugin` 里唯一一处 `plugins.apply("org.jetbrains.kotlin.plugin.compose")` 位于
`BooleanOption.USE_DECLARATIVE_INTERFACES` 为真的分支（默认关闭），经典路径不会走。
因此 `:app` 模块必须显式写 `alias(libs.plugins.kotlin.compose)`（版本与 AGP 内建 KGP 对齐为 2.2.10）。
该插件本身只注册 `composeCompiler` 扩展、实现 `KotlinCompilerPluginSupportPlugin`，
会被 AGP 的 `addSubpluginOptionsForBuiltInKotlin` 扫描到并挂到内建 `KotlinCompilation` 上，不与内建 Kotlin 冲突。

## 六、辅助脚本

- `tools/check_kotlin.py <dir>`：Kotlin 源文件括号/引号平衡校验
- `tools/scan_expr_return.py <dir>`：粗筛「表达式体函数里出现 return」这类必然编译失败的写法
- `tools/check_refs.py <dir>`：跨文件静态调用名字级检查（发现拼写/漏实现）
- `setup_android_env.sh`：Ubuntu/proot 环境下重建 Android 工具链（JDK 17 + Gradle 9.1.0 + SDK/NDK）
