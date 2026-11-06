# 知微管理器（ZhiweiManager）

Android 文件管理器，交互模型参照 MT 管理器（双窗口、无剪贴板中转、直接跨窗口操作），
内置面向逆向工程与日常维护的工具链。包名 `com.zhiwei.filemanager`，版本 `1.4.1`（versionCode 6）。

> 构建策略：**不在本地出正式包**。正式包统一由 GitHub Actions 构建（`.github/workflows/android-ci.yml`）。
> 版本变更记录见 [CHANGELOG.md](CHANGELOG.md)。

## 一、当前已落地能力（真实进度）

### 文件管理
- **双窗口默认左右两栏**（`左 | 右` 并列，与 MT / NP 管理器一致），分隔线可拖动改比例；`⋮` 菜单可切「左右 / 上下」与单/双窗
- **顶栏只显示当前目录 + 计数**：大字目录名 + 小字完整路径，右侧紧跟「N 个文件夹 · M 个文件 · 合计体积」；右上角放大镜 → 搜**文件**，点目录名 → 搜**文件夹**。原顶部那排「单窗 / 左右⇋ / +窗口 / ★收藏 / 🔍搜索」杂乱按钮已全部移除
- **搜索只留一份**：两个窗口不再各放一个搜索入口（既然支持栏内下滑切窗，重复的入口纯属占地），搜索统一收归顶栏
- **列表首行 =「返回上一层」**：视觉与普通文件夹项完全一致（同款 36dp 圆角色块 + 两行文字），色块内是「…」；点它返回上层，顶部那个快捷返回按钮已删除
- **左侧折叠栏（抽屉）**：窗格顶部与底部的快捷入口芯片全部删除，跳转能力归口到抽屉 —— 存储卷（含容量）、常用目录、应用联动、书签/回收站/终端。按需展开，不再常驻吃掉两遍首屏高度
- **栏内下滑切窗**：在左栏内向下滑 → 操作焦点切到左栏；右栏内向下滑 → 切到右栏（栏内任意位置点击同样切焦点，激活栏带高亮描边）
- **底栏与独立状态条已整体移除**：所有动作收进各窗口顶部的 `⋮` 菜单，计数移进顶栏，不再多吃两行首屏高度
- 每栏独立标题条（左/右标识 + 当前目录名 + `⋮` 菜单）
- 复制 / 移动 / 删除 / 重命名 / 新建文件与文件夹 / 属性查看
- 多选：左右滑动进入，**两次滑动之间的整段一起选中**；多选态点一下只取消／恢复那一项；全选 / 反选 / 取消选择与批量操作、跨窗口「复制/移动到另一窗口」；选中态带底色渐变与勾选框缩放动画
- 回收站：删除默认移入 `/sdcard/.zhiwei_recycle`，可单条还原（原位置占用自动加序号）、单条彻底删除、一键清空；开关可关掉改回直接销毁
- 打开方式 / 预览 / 分享 / 复制路径 / 十六进制查看 / 文本编辑 / 压缩 / 解压（**长按菜单为双列网格，最多 7 个按钮，其余收进「更多工具…」**；动作按文件类型分流出对应工具，安装包给安装包的工具、压缩包给压缩包的工具）
- 排序（名称 / 大小 / 日期 / 类型 + 升降序）、隐藏文件开关、当前目录过滤
- 文件类型色标（APK / DEX / 归档 / 图片 / 视频 / 音频 / 文本 / 代码 / PDF / 字体 / 数据库）
- **受限目录免提权浏览**：`Android/data`、`Android/obb` 这类按路径读不到的地方，改用系统授权器（SAF `ACTION_OPEN_DOCUMENT_TREE`）授权后正常列目录、打开文件 —— **不需要 Shizuku、不需要 ROOT、也不需要「所有文件访问权限」**；受限提示旁同时提供「授予全部文件访问」一键跳转
- Shizuku（shell 身份）作为受限目录的**可选**加强通道；ROOT 通道可选
- 书签 / 最近访问（`BookmarkStore`，独立 SP，互不影响清理）：一键直达、当前目录置顶收藏、最近 30 条
- 常用目录快捷入口（内部存储 / 下载 / 图片 / 相机 / 文档 / 音乐 / Android/data / 根目录，只列真实存在的）
- 目录计数（项目数 / 文件夹数 / 合计体积 / 已选数量）显示在窗格顶栏，不再单占一条状态栏
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
### 预览 / 脚本 / APK 工具箱
- **内置文件预览器**（点文件或长按菜单 →「预览」，按类型自动分流）：
  - 图片：降采样解码（长边 ≤ 2400）防 OOM + EXIF 方向自动纠正
  - 视频：`VideoView` 播放；音频：`MediaPlayer` + 进度条 / 播放暂停
  - 字体：`Typeface` 样张（中英文 / 数字 / 标点实时渲染）
  - 文本 / 代码 / 配置：≤ 2MB 截断读取后直接展示，超限明确提示
  - 网页：`WebView`（`allowFileAccess` 打开本地 html / mhtml）
   - APK：**弹窗速览**（不再是整页）—— 图标 + 包名 + 版本 / 大小 / SDK / 签名 v1v2v3 / 加固特征 / dex·so 计数，
     底部「安装 / 查看 / 更多」三按钮；整页的 `ApkInspector` 详情仍可从「查看」进
  - 归档：`ZipFile` 列出条目（≤ 500 条封顶，防炸弹）
  - 其余二进制：512 字节 Hex 兜底预览
- **SH 脚本执行器**：双通道（`sh` / `su`）+ 15 / 60 / 180 / 600 秒四档超时 + 7 组常用预设脚本；脚本先落临时文件再以 `sh 文件` 执行，避免多行命令转义地狱，输出带 exit code 回显
- **APK 反编译工具箱**：解析 APK 结构、一键解包（提 dex / 列 so 清单）、签名 v1 / v2 / v3 校验、加固特征判定（22 条特征库）、与内置 smali 引擎打通做 `dex <-> smali` 互转

### 系统

- 内置终端：普通（`sh -c`）/ ROOT（`su -c`）/ Shizuku（uid 2000）三通道，会话内保留输出
- 后台任务看板：所有耗时操作统一上报进度与结果
- 提权控制台：所有文件访问权限、Shizuku 授权、ROOT 检测
- **启动一次性申请权限**：进 App 即把所有运行时权限一次申请完（通知、存储、所有文件访问，按 API 分级），「所有文件访问」用 SP 标记只在首次自动引导，之后不再骚扰
- **内置更新检查**：启动静默比对 GitHub Release（多镜像加速：`ghfast.top` → `gh-proxy.com` → 直连），**仅有新版才弹窗**（去官网更新 / 取消更新 / 立即更新 → 跳项目主页）；「设置 → 关于我们 → 检查更新」可手动触发
- **应用联动**：检测到已安装知微浏览器（`com.java.myapplication`）时，折叠栏出现「知微浏览器」入口，一键跳其专属下载目录 `/sdcard/Download/知微浏览器`
- 权限清单：`MANAGE_EXTERNAL_STORAGE`、`READ/WRITE_EXTERNAL_STORAGE`、`INTERNET`、
  `POST_NOTIFICATIONS`、`QUERY_ALL_PACKAGES`、`REQUEST_INSTALL_PACKAGES`、`FOREGROUND_SERVICE*`、
  `WAKE_LOCK`；FileProvider 已配置（`${applicationId}.fileprovider` → `res/xml/provider_paths.xml`）
- **应用图标**：靛蓝 → 天青渐变底 + 白色双窗格（左格三条文件行、右格三点）；自适应图标（`mipmap-anydpi-v26`）含 Android 13+ 主题图标 `monochrome` 层，五档密度 PNG 由 `tools/make_icons.py` 纯标准库生成（与矢量 viewport 逐像素对齐）
- 分享目标：`ACTION_SEND` / `ACTION_VIEW`（APK、zip、txt）可直接送入本应用

## 二、待办路线（按优先级）

**已完成（1.4.1 收口）**
- ~~正式包每次构建签名都不同 → 覆盖安装报「应用未安装 / 软件包冲突」~~ → 固定 keystore（GitHub Secrets 注入，不进公开仓库）+ release 三档签名全开 + CI 签名指纹硬校验；缺少 Secrets 时 tag 构建直接失败

**已完成（1.4.0 收口）**
- ~~受限目录必须靠 Shizuku / ROOT 才能看~~ → 已落地免提权 SAF 授权浏览（`SafTree` + `SafBrowseSheet`）
- ~~安装包预览要切整页~~、~~长按菜单一长条~~、~~两窗各一个搜索~~、~~底部再占一条状态栏~~ → 全部按 MT 交互模型重做
- 新应用图标（自适应 + monochrome 主题图标 + 五档密度 PNG）

**已完成（1.3.0 收口，原 P0-1 / P0-2）**
- ~~书签 / 最近访问 / 当前目录递归搜索~~ → 已落地（`BookmarkStore` + 递归搜索面板）
- ~~文件与文件夹对比、文本批量合并~~ → 已落地（目录求差集、字节流对比、流式合并）
- 左侧折叠栏、内置预览器、SH 脚本执行器、APK 反编译工具箱、启动一次性权限、内置更新检查 → 全部落地

**P0 — 基础补完**
1. Jar / AAR 写回与重新打包（含 `classes.dex` 注入）—— 解包已可用，写回未做
2. apktool 全流程接入（反编译 / 回编译 / 资源替换），并入 APK 反编译工具箱
3. APK 签名（v1/v2/v3、自定义 keystore）、共存（applicationId / 包名改写）、安装引导
   —— 签名**校验**已可用，**签名与共存**未做

**P1 — 逆向主力**
4. Res 资源混淆（`resources.arsc` 字符串池重写）
5. SO 查看 / 编辑 / 对比（字符串常量扫描、节区视图、Hex 级差异）
6. 十六进制对比（文件级差异 + 字节级跳转）
7. 高兼容性省略库（.so 依赖裁剪）编辑

**P2 — 保护与增强**
8. Dex / Jar 混淆、字符串加密
9. 控制流混淆 8.0、VM 保护
10. 布局查看器（运行时 View 层级 / Layout Inspector 简版）

**P3 — 媒体与网络**
11. PDF 合并 / 加密 / 分割 / 提取 / 解除加密限制 / 删页（pdfbox-android）
12. FFmpeg：转码、剪切、提取视频流 / 音频流、自定义命令（ffmpeg-kit 或自编译 so）
13. 网络存储：FTP / SFTP / SMB / WebDAV
14. HTTP 服务、APK MCP 服务（由 App 进程对外提供接口）

> 全量清单与逐项状态见应用内「设置 → 功能清单」，数据源为 `tool/ToolRegistry.kt`
> （`READY` / `PARTIAL` / `PLANNED` 三态，不做包装）。

## 三、工程结构

```
app/src/main/java/com/zhiwei/filemanager/
├── MainActivity.kt              入口（主题模式、启动一次性权限申请）
├── core/
│   ├── fs/       FileRepo（列举/排序/路径）、FileOps（增删改查/复制/移动）、StorageVolumes（存储卷枚举）
│   ├── model/    FileItem、SortMode/SortOrder
│   ├── prefs/    AppPrefs（Compose State 包装的 SharedPreferences）
│   ├── shell/    ShellRunner（sh/su）、ShizukuHelper（Shizuku 通道）
│   ├── task/     TaskCenter（任务看板）、AppScope
│   ├── update/   UpdateChecker（Release 检查 + 镜像加速 + 断点续传）
│   └── util/     Format（尺寸/时间/类型判定）、AppInfo、ApkInspector（APK 解析/签名/加固）、
│                 CompanionApps（联动应用探测）、Permissions（权限门面）
├── tool/
│   ├── ToolRegistry.kt           全量功能清单与真实进度
│   ├── archive/ArchiveTool.kt    zip/tar/gz/xz/lz4/zstd/rar
│   ├── codec/    DexReader（纯 Kotlin Dex 解析）、SmaliEngine（反射兼容层）
│   ├── diff/     DiffTool（统一 diff）
│   ├── hex/      HexTool（分页浏览 + 搜索）
│   └── text/     EncodingTool（GBK/UTF-8 嗅探与批量转码）、StringCodec
└── ui/
    ├── AppRoot.kt               底部四页 + 二级路由（工具页 / 预览 / 终端）
    ├── AppDrawer.kt             左侧折叠栏（存储卷 / 常用目录 / 应用联动 / 工具集合）
    ├── files/     FileBrowserScreen（多窗格/列表/批量/菜单/属性）、FileBrowserState、BookmarkSearchSheets
    ├── preview/   PreviewScreen（九类文件预览）
    ├── settings/  SettingsScreen（外观/浏览/提权/功能清单/关于与更新）、UpdateNotice（新版弹窗）
    ├── tasks/     TaskBoardScreen
    ├── terminal/  TerminalScreen（三通道 shell）
    ├── tools/     ToolboxScreen 与各工具页（含 ScriptScreen、ApkToolScreen）
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
