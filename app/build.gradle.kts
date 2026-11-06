import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // 只补 Compose 编译器子插件。Kotlin 本体由 AGP 9 内建（android.builtInKotlin 默认开启），
    // 再应用 org.jetbrains.kotlin.android 会与内建注册的 `kotlin` 扩展重名 → 构建直接失败。
    // 但 Compose 编译器 **必须** 显式应用：AGP 的 VariantTaskManager 在
    // buildFeatures.compose=true 且未检测到该插件时，会以 MISSING_COMPOSE_COMPILER_GRADLE_PLUGIN 报错终止。
    alias(libs.plugins.kotlin.compose)
}

// 签名策略：**必须固定**，否则升级必然失败。
// 踩过的坑（2026-11-06 实测坐实）：「AGP 内置的 Android 标准 debug keystore 跨构建一致」是错的。
// CI 每次都是全新 runner，~/.android/debug.keystore 不存在 → AGP 现场用 keytool 随机生成一对密钥，
// 于是 v1.3.0 与 v1.4.0 的签名证书完全不同（SHA-256 分别 453f59af… / 575915a7…），
// 覆盖安装报「应用未安装 / 软件包与现有软件包存在冲突」。
// 现在 release 一律要求 keystore.properties（本地 / CI 的 Secrets 都会生成它），签名恒定。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val hasFixedKeystore = !keystoreProps.getProperty("storeFile").isNullOrBlank()
android {
    namespace = "com.zhiwei.filemanager"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.zhiwei.filemanager"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "1.4.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    signingConfigs {
        if (hasFixedKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // 三档签名全开：v1(JAR) + v2 + v3。v1 让部分第三方安装器/老 ROM 的
                // 「签名校验」也能正常识别，v3 供 Android 9+ 直接读取。
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 固定签名优先。缺 keystore.properties 时本地退回 debug 签名以便调试；
            // 但 CI 上由 workflow 直接 fail，不会静默产出「签名不固定」的正式包。
            if (hasFixedKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "⚠ 未找到 keystore.properties：本次 release 使用 debug 签名，" +
                        "签名不固定，禁止分发。CI 会拒绝这种情况。"
                )
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        // 打开 BuildConfig，让 AppInfo 直接从构建产物读版本号，杜绝“关于页版本号写死不同步”。
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.md",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
            pickFirsts += setOf(
                "META-INF/services/*",
                "META-INF/*.kotlin_module",
                "META-INF/versions/9/module-info.class"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// AGP 内建 Kotlin 会把 jvmTarget 与 Java 目标对齐，这里显式再声明一次作为兜底，
// 避免出现 "Inconsistent JVM-target" 这类硬失败（Kotlin DSL 由 AGP 传递引入）。
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// aapt2 分发按宿主架构选择：本地 proot(aarch64) 用 linux-aarch64，GitHub 云端(x86_64) 保持 AGP 默认
val hostArch = System.getProperty("os.arch").orEmpty()
val aapt2Classifier = when {
    hostArch.contains("aarch64") || hostArch.contains("arm64") -> "linux-aarch64"
    else -> null
}
if (aapt2Classifier != null) {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.android.tools.build" && requested.name == "aapt2") {
                useTarget("com.android.tools.build:aapt2:${requested.version}:$aapt2Classifier")
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // AGP 内建 Kotlin 不保证自动注入 stdlib，显式声明保证 Kotlin 源码可编译
    implementation(libs.kotlin.stdlib)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // AnimatedContent / 转场动画（foundation 传递依赖里虽带 animation，但显式声明避免版本漂移）
    implementation(libs.androidx.animation)

    // 文件 / 归档
    implementation(libs.commons.io)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.zstd.jni)
    implementation(libs.lz4.java)
    implementation(libs.junrar)

    // 提权（Shizuku）
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // 对比
    implementation(libs.java.diff.utils)
    // APK 签名 / 校验（v1+v2+v3）
    implementation(libs.apksig)

    // Dex / Smali / Jar 核心工具链
    implementation(libs.smali)
    implementation(libs.baksmali)
    implementation(libs.smali.dexlib2)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}