// Top-level build file where you can add configuration options common to all sub-projects/modules.
// AGP 9 起 Kotlin 支持内建：不再应用 org.jetbrains.kotlin.android（会与内建 `kotlin` 扩展重名）。
// Compose 编译器插件只在 :app 模块应用（AGP 不会自动注入，缺失即构建失败）。
plugins {
    alias(libs.plugins.android.application) apply false
}
