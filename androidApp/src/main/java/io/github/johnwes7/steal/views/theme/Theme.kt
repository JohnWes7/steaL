package io.github.johnwes7.steal.views.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 浅色配色：iOS 的品牌色对应到 Material 3 的颜色角色。
 * The light scheme, mapping the iOS brand colors onto Material 3 color roles.
 *
 * 品牌绿是主色，品牌蓝是次色（同 iOS tab 栏：Games 用绿、其他用蓝）；占位底色放在 surfaceVariant，
 * 所有 surfaceContainer 都用 iOS 的浮起表面色，让卡片和底部导航栏是白底、页面是浅灰底。
 * Brand green is primary and brand blue is secondary (as in the iOS tab bar: green for Games, blue
 * elsewhere); the image placeholder goes in surfaceVariant, and every surfaceContainer uses the iOS
 * elevated surface so cards and the navigation bar sit white on a light gray page.
 */
private val LightColors = lightColorScheme(
    primary = BrandGreenLight,
    onPrimary = OnBrandGreenLight,
    secondary = BrandBlueLight,
    onSecondary = OnBrandGreenLight,
    background = AppBackgroundLight,
    onBackground = PrimaryTextLight,
    surface = AppBackgroundLight,
    onSurface = PrimaryTextLight,
    onSurfaceVariant = SecondaryTextLight,
    surfaceVariant = ImagePlaceholderLight,
    surfaceContainerLowest = SurfaceElevatedLight,
    surfaceContainerLow = SurfaceElevatedLight,
    surfaceContainer = SurfaceElevatedLight,
    surfaceContainerHigh = SurfaceElevatedLight,
    surfaceContainerHighest = SurfaceElevatedLight,
)

/**
 * 深色配色，角色对应同 [LightColors]。
 * The dark scheme, with the same role mapping as [LightColors].
 */
private val DarkColors = darkColorScheme(
    primary = BrandGreenDark,
    onPrimary = OnBrandGreenDark,
    secondary = BrandBlueDark,
    onSecondary = OnBrandGreenDark,
    background = AppBackgroundDark,
    onBackground = PrimaryTextDark,
    surface = AppBackgroundDark,
    onSurface = PrimaryTextDark,
    onSurfaceVariant = SecondaryTextDark,
    surfaceVariant = ImagePlaceholderDark,
    surfaceContainerLowest = SurfaceElevatedDark,
    surfaceContainerLow = SurfaceElevatedDark,
    surfaceContainer = SurfaceElevatedDark,
    surfaceContainerHigh = SurfaceElevatedDark,
    surfaceContainerHighest = SurfaceElevatedDark,
)

/**
 * App 的 Material 3 主题，默认用品牌色。
 * The app's Material 3 theme, using the brand colors by default.
 *
 * [dynamicColor] 为 `true` 且系统是 Android 12 以上时改用按壁纸生成的动态配色；默认关掉，
 * 保持和 iOS 一样的品牌观感，以后做成设置项时由调用方传进来。
 * When [dynamicColor] is `true` on Android 12 and later, the wallpaper-based dynamic scheme is used
 * instead; it is off by default to keep the same brand look as iOS, and a future setting will pass it in.
 *
 * [darkTheme] 默认跟随系统。
 * [darkTheme] follows the system by default.
 */
@Composable
fun SteaLTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamicColor = dynamicColor && supportsDynamicColor

    val colorScheme: ColorScheme
    if (useDynamicColor) {
        val context = LocalContext.current
        colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        colorScheme = if (darkTheme) DarkColors else LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
