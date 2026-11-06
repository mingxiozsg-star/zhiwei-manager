package com.zhiwei.filemanager.core.util

import com.zhiwei.filemanager.BuildConfig

/**
 * 应用元信息。
 *
 * 版本号**必须**取自构建产物（BuildConfig）。
 * 旧实现把 VERSION 写死成 "0.1.0"，导致「关于页」永远显示首发版本，与实际包版本脱节。
 */
object AppInfo {
    const val NAME = "知微管理器"
    const val EN_NAME = "ZhiweiManager"
    const val PKG = "com.zhiwei.filemanager"

    val VERSION: String = runCatching { BuildConfig.VERSION_NAME }.getOrDefault("unknown")
    val VERSION_CODE: Int = runCatching { BuildConfig.VERSION_CODE }.getOrDefault(0)

    /** 形如 v1.3.0(4)：一眼看出包序，避免“版本号没变”的错觉。 */
    val VERSION_FULL: String get() = "v$VERSION($VERSION_CODE)"

    // —— 团队与联系方式（关于我们）——
    const val TEAM = "始源创想科技IT"
    const val EMAIL = "ailixiya@qycxkjit.wecom.work"
    const val QQ_GROUP = "1131325287"
    const val GITHUB_USER = "mingxiozsg-star"
    const val REPO_URL = "https://github.com/mingxiozsg-star/zhiwei-manager"
    const val RELEASE_API = "https://api.github.com/repos/mingxiozsg-star/zhiwei-manager/releases/latest"
    const val RELEASES_PAGE = "https://github.com/mingxiozsg-star/zhiwei-manager/releases"

    const val DESIGN_NOTE =
        "双窗口左右并排（对齐 MT / NP 管理器），删除进回收站，APK / Dex / 归档 / 文本工具链内置。"
}