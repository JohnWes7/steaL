package io.github.johnwes7.steal.views.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.johnwes7.steal.R

/**
 * App 的字体样式，目前全用 Material 3 默认值（字体是手机的系统字体）。
 * The app's text styles, currently all Material 3 defaults (in the phone's system font).
 *
 * 默认样式以 sp 为单位，会跟着系统字体大小缩放；要改某个样式时在这里覆盖，不在页面里写死字号。
 * The defaults are in sp and scale with the system font size; override a style here when needed
 * rather than hard-coding sizes in screens.
 */
internal val AppTypography = Typography()

/**
 * 品牌字标用的字体：打包在 App 里的 Roboto Bold，不跟手机的系统字体变。
 * The brand wordmark font: Roboto Bold bundled with the app, unaffected by the phone's system font.
 *
 * 只打包了 Bold 一个字重，要别的粗细也会显示成 Bold。
 * Only the Bold weight is bundled, so other weights also render as Bold.
 */
internal val BrandFontFamily = FontFamily(Font(R.font.roboto_bold, FontWeight.Bold))
