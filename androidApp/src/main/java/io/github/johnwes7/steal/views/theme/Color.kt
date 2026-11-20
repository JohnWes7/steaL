package io.github.johnwes7.steal.views.theme

import androidx.compose.ui.graphics.Color

// 品牌色，值照抄 iOS Asset Catalog（iosApp/steaL/Assets.xcassets），改了要两边一起改。
// Brand colors copied from the iOS Asset Catalog; change both platforms together.

// 浅色 / Light

/**
 * 品牌绿（浅色）。
 * Brand green (light).
 */
internal val BrandGreenLight = Color(0xFF248A3D)

/**
 * 品牌绿上的文字与图标（浅色）。
 * Text and icons on brand green (light).
 */
internal val OnBrandGreenLight = Color(0xFFFFFFFF)

/**
 * 品牌蓝（浅色）。
 * Brand blue (light).
 */
internal val BrandBlueLight = Color(0xFF3D84FF)

/**
 * 品牌蓝上的文字与图标（浅色、深色相同，同 iOS 蓝底白字）。
 * Text and icons on brand blue (the same in light and dark, white on blue as on iOS).
 */
internal val OnBrandBlue = Color(0xFFFFFFFF)

/**
 * 强调用的橙色（浅色），值是 iOS 的系统橙。
 * The accent orange (light), using the iOS system orange.
 */
internal val SystemOrangeLight = Color(0xFFFF9500)

/**
 * 橙色上的文字与图标（浅色、深色相同）。
 * Text and icons on the orange (the same in light and dark).
 */
internal val OnSystemOrange = Color(0xFF101214)

/**
 * 页面背景（浅色）。
 * Page background (light).
 */
internal val AppBackgroundLight = Color(0xFFF2F2F7)

/**
 * 卡片等浮起表面（浅色）。
 * Elevated surfaces such as cards (light).
 */
internal val SurfaceElevatedLight = Color(0xFFFFFFFF)

/**
 * 主要文字（浅色）。
 * Primary text (light).
 */
internal val PrimaryTextLight = Color(0xFF12161A)

/**
 * 次要文字（浅色）。
 * Secondary text (light).
 */
internal val SecondaryTextLight = Color(0xFF5C6468)

/**
 * 图片加载前的占位底色（浅色）。
 * Placeholder fill before an image loads (light).
 */
internal val ImagePlaceholderLight = Color(0xFFE5E5EA)

// 深色 / Dark

/**
 * 品牌绿（深色）。
 * Brand green (dark).
 */
internal val BrandGreenDark = Color(0xFF8DCF4A)

/**
 * 品牌绿上的文字与图标（深色）。
 * Text and icons on brand green (dark).
 */
internal val OnBrandGreenDark = Color(0xFF101214)

/**
 * 品牌蓝（深色，与浅色相同）。
 * Brand blue (dark, same as light).
 */
internal val BrandBlueDark = Color(0xFF3D84FF)

/**
 * 强调用的橙色（深色），值是 iOS 的系统橙。
 * The accent orange (dark), using the iOS system orange.
 */
internal val SystemOrangeDark = Color(0xFFFF9F0A)

/**
 * 页面背景（深色）。
 * Page background (dark).
 */
internal val AppBackgroundDark = Color(0xFF101214)

/**
 * 卡片等浮起表面（深色）。
 * Elevated surfaces such as cards (dark).
 */
internal val SurfaceElevatedDark = Color(0xFF27292B)

// 深色的表面层级 / Dark surface tiers
// iOS 只有一个浮起表面色；Android 的导航栏、顶栏和卡片要分得开，所以补一组由暗到亮的灰（只有 Android 用）。
// iOS has a single elevated surface; Android needs its navigation bar, top bar and cards to read apart, so this
// adds a dark-to-light ramp of grays used only on Android.

/**
 * 最暗的表面层级（深色），比页面背景还暗。
 * The darkest surface tier (dark), below the page background.
 */
internal val SurfaceContainerLowestDark = Color(0xFF0B0C0E)

/**
 * 较暗的表面层级（深色）。
 * A low surface tier (dark).
 */
internal val SurfaceContainerLowDark = Color(0xFF16181A)

/**
 * 导航栏、滚动后的顶栏（深色）：比页面背景亮一点，比卡片暗。
 * The navigation bar and the scrolled top bar (dark): a little lighter than the page, darker than cards.
 */
internal val SurfaceContainerDark = Color(0xFF1B1D1F)

/**
 * 卡片上的胶囊、标签（深色）：比卡片再亮一层。
 * Pills and tags on cards (dark): one step lighter than the card.
 */
internal val SurfaceContainerHighestDark = Color(0xFF34373A)

/**
 * 主要文字（深色）。
 * Primary text (dark).
 */
internal val PrimaryTextDark = Color(0xFFEDF1F4)

/**
 * 次要文字（深色）。
 * Secondary text (dark).
 */
internal val SecondaryTextDark = Color(0xFF8E969A)

/**
 * 图片加载前的占位底色（深色）。
 * Placeholder fill before an image loads (dark).
 */
internal val ImagePlaceholderDark = Color(0xFF192022)
