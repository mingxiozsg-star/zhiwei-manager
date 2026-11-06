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

// 签名策略：**确定性优先**。
// 旧实现的坑：无 Secrets 时在 CI 里用 keytool 现生成 keystore，
// 于是每一轮构建的签名都不同，用户覆盖安装必然失败（“应用未安装”），只能卸载重装、丢数据。
// 现在统一回落到 AGP 内置的 Android 标准 debug keystore —— 它跨机器、跨构建完全一致，
// 因此「同包名 + 同签名」永远成立，升级可以直接覆盖安装。
// 若日后要上架，再通过 keystore.properties 注入正式 keystore（届时签名会变，需要一次性迁移）。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
android {
    namespace = "com.zhiwei.filemanager"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.zhiwei.filemanager"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "1.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    signingConfigs {
        if (!keystoreProps.getProperty("storeFile").isNullOrBlank()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
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
            // 默认走 debug 签名：稳定、可覆盖安装。有 keystore.properties 时才用正式签名。
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
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