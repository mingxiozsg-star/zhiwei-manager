# ---- 通用 ----
-dontwarn org.slf4j.**
-dontwarn org.apache.**
-dontwarn javax.**
-dontwarn java.awt.**
-dontwarn sun.misc.**
-dontwarn org.tukaani.**
-dontwarn com.github.luben.**
-dontwarn net.jpountz.**

# ---- Kotlin / Compose ----
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }

# ---- Dex / Smali 工具链（反射 + ServiceLoader 依赖）----
-keep class org.jf.** { *; }
-keep class com.android.tools.smali.** { *; }
-keep class org.antlr.** { *; }
-keep class com.google.common.** { *; }
-keepnames class com.google.common.collect.**
-keep class javax.annotation.** { *; }
-dontwarn org.antlr.**
-dontwarn com.google.common.**

# ---- Shizuku ----
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# ---- Commons Compress 可选压缩器 ----
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class com.github.junrar.** { *; }