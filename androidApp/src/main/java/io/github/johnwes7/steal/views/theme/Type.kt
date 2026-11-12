package io.github.johnwes7.steal.views.theme

import androidx.compose.material3.Typography

/**
 * App 的字体样式，目前全用 Material 3 默认值。
 * The app's text styles, currently all Material 3 defaults.
 *
 * 默认样式以 sp 为单位，会跟着系统字体大小缩放；要改某个样式时在这里覆盖，不在页面里写死字号。
 * The defaults are in sp and scale with the system font size; override a style here when needed
 * rather than hard-coding sizes in screens.
 */
internal val AppTypography = Typography()
