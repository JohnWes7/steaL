package io.github.johnwes7.steal.views.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// MARK: - Material 之外的颜色 / Colors outside Material

/**
 * Material 配色之外的颜色：品牌字标和额外的语义色，值用 iOS 原色，不进 Material 色阶。
 * Colors outside the Material scheme: the brand wordmark and extra semantic colors, using the iOS values as-is
 * rather than Material tonal palettes.
 *
 * 界面里经 [ExtendedTheme.colors] 读，不直接用 `Color.kt` 的常量；不跟 [ColorPalette] 和动态配色变。
 * Read through [ExtendedTheme.colors] in UI code rather than using the `Color.kt` constants directly; these don't
 * change with the [ColorPalette] or dynamic color.
 */
@Immutable
data class ExtendedColors(
    /**
     * 品牌绿：只给品牌字标和标语用。
     * Brand green, only for the wordmark and tagline.
     */
    val brandGreen: Color,
    /**
     * 品牌蓝：只给品牌字标用。
     * Brand blue, only for the wordmark.
     */
    val brandBlue: Color,
    /**
     * 「热门」的圆底，如 Hot deals 标题前的火焰。
     * The container behind the "hot" accent, such as the flame before the Hot deals title.
     */
    val hotContainer: Color,
    /**
     * [hotContainer] 上的图标。
     * Icons on [hotContainer].
     */
    val onHotContainer: Color,
)

private val LightExtendedColors = ExtendedColors(
    brandGreen = BrandGreenLight,
    brandBlue = BrandBlueLight,
    hotContainer = OrangeContainerLight,
    onHotContainer = OnOrangeContainerLight,
)

private val DarkExtendedColors = ExtendedColors(
    brandGreen = BrandGreenDark,
    brandBlue = BrandBlueDark,
    hotContainer = OrangeContainerDark,
    onHotContainer = OnOrangeContainerDark,
)

/**
 * 当前主题的 [ExtendedColors]，由 [SteaLTheme] 提供。
 * The current theme's [ExtendedColors], provided by [SteaLTheme].
 */
val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        brandGreen = Color.Unspecified,
        brandBlue = Color.Unspecified,
        hotContainer = Color.Unspecified,
        onHotContainer = Color.Unspecified,
    )
}

/**
 * Material 之外的主题值的入口，用法同 `MaterialTheme.colorScheme`。
 * Entry point for theme values outside Material, used like `MaterialTheme.colorScheme`.
 */
object ExtendedTheme {
    /**
     * 当前主题的额外颜色。
     * The current theme's extra colors.
     */
    val colors: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}

// MARK: - 主题 / Theme

/**
 * App 的 Material 3 主题。
 * The app's Material 3 theme.
 *
 * [palette] 选用哪套配色，默认 [ColorPalette.ThemeBuilder]。[dynamicColor] 为 `true` 且系统是 Android 12 以上时改用
 * 按壁纸生成的动态配色，[palette] 不起作用；默认关掉，保持品牌色。[darkTheme] 默认跟随系统。
 * [palette] picks the color palette, [ColorPalette.ThemeBuilder] by default. When [dynamicColor] is `true` on
 * Android 12 and later, the wallpaper-based dynamic scheme replaces [palette]; it is off by default to keep the brand
 * colors. [darkTheme] follows the system by default.
 */
@Composable
fun SteaLTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ColorPalette = ColorPalette.ThemeBuilder,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // 1. 选 Material 配色：动态配色，或 palette 的那一套
    // 1. Pick the Material scheme: dynamic, or the one from palette
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamicColor = dynamicColor && supportsDynamicColor

    val colorScheme: ColorScheme
    if (useDynamicColor) {
        val context = LocalContext.current
        colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        colorScheme = palette.colorScheme(darkTheme = darkTheme)
    }

    // 2. Material 之外的颜色只分浅色、深色
    // 2. The colors outside Material only vary with light and dark
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    // 3. 提供两套颜色：Expressive 主题另外带弹簧动效（MotionScheme.expressive）
    // 3. Provide both color sets; the Expressive theme also brings spring motion (MotionScheme.expressive)
    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
