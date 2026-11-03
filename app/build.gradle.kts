import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI / 本地可通过 keystore.properties（不入库）注入正式签名；缺失时回落到 debug 签名
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
        versionCode = 1
        versionName = "0.1.0"

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

// Kotlin 编译目标必须与上方 compileOptions(Java 17) 一致，
// 否则 AGP 的 JVM-target 一致性校验会直接判定构建失败。
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