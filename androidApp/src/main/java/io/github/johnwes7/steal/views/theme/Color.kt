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

// 容器色 / Container colors
// 深色是我们自己定的色阶（用户 2026-11-21 定）：容器 = 品牌色 16–18% 叠在深色表面上（约 Material 色阶第 18–24 档、
// 低彩度），容器上的字 = 品牌色本身（约第 57–77 档）。比 Material 默认（容器第 30 档、字第 90 档）的字更亮、品牌色更突出。
// 浅色先沿用 Material 默认（容器第 90 档、字第 10 档），浅色还没设计。
// Dark mode uses our own tones (the user's call): a container is the brand color at 16–18% over the dark surface
// (about Material tone 18–24, low chroma) and its content is the brand color itself (about tone 57–77), brighter and
// more on-brand than Material's default (container tone 30, content tone 90). Light mode keeps Material's default
// for now (container tone 90, content tone 10).

/**
 * 品牌绿的容器（深色）：绿 16% 叠在导航栏色上；品质标签、Games 选中的胶囊。
 * The brand-green container (dark): green at 16% over the navigation bar color.
 */
internal val GreenContainerDark = Color(0xFF2D3926)

/**
 * 品牌绿容器上的文字与图标（深色）：品牌绿本身。
 * Text and icons on the brand-green container (dark): the brand green itself.
 */
internal val OnGreenContainerDark = BrandGreenDark

/**
 * 品牌蓝的容器（深色）：蓝 18% 叠在卡片色上；折扣标签、其他 tab 选中的胶囊。
 * The brand-blue container (dark): blue at 18% over the card color.
 */
internal val BlueContainerDark = Color(0xFF2B3951)

/**
 * 品牌蓝容器上的文字与图标（深色）：品牌蓝本身。
 * Text and icons on the brand-blue container (dark): the brand blue itself.
 */
internal val OnBlueContainerDark = BrandBlueDark

/**
 * 橙色的容器（深色）：橙 16% 叠在页面背景上；火焰圆底。
 * The orange container (dark): orange at 16% over the page background.
 */
internal val OrangeContainerDark = Color(0xFF362912)

/**
 * 橙色容器上的文字与图标（深色）：系统橙本身。
 * Text and icons on the orange container (dark): the system orange itself.
 */
internal val OnOrangeContainerDark = SystemOrangeDark

/**
 * 品牌绿的容器（浅色，第 90 档）。
 * The brand-green container (light, tone 90).
 */
internal val GreenContainerLight = Color(0xFFB1F66C)

/**
 * 品牌绿容器上的文字与图标（浅色，第 10 档）。
 * Text and icons on the brand-green container (light, tone 10).
 */
internal val OnGreenContainerLight = Color(0xFF0E2000)

/**
 * 品牌蓝的容器（浅色，第 90 档）。
 * The brand-blue container (light, tone 90).
 */
internal val BlueContainerLight = Color(0xFFD9E2FF)

/**
 * 品牌蓝容器上的文字与图标（浅色，第 10 档）。
 * Text and icons on the brand-blue container (light, tone 10).
 */
internal val OnBlueContainerLight = Color(0xFF001A43)

/**
 * 橙色的容器（浅色，第 90 档）。
 * The orange container (light, tone 90).
 */
internal val OrangeContainerLight = Color(0xFFFFDDBB)

/**
 * 橙色容器上的文字与图标（浅色，第 10 档）。
 * Text and icons on the orange container (light, tone 10).
 */
internal val OnOrangeContainerLight = Color(0xFF2B1700)
