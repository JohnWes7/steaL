package io.github.johnwes7.steal.views.theme

import androidx.compose.ui.graphics.Color

// Material 配色之外的颜色（[ExtendedColors] 用）。Material 的颜色角色在 ColorPalettes.kt。
// Colors outside the Material scheme (used by [ExtendedColors]); the Material color roles are in ColorPalettes.kt.

// MARK: - 品牌色 / Brand colors
// 值照抄 iOS Asset Catalog（iosApp/steaL/Assets.xcassets），改了要两边一起改。
// Copied from the iOS Asset Catalog; change both platforms together.

/**
 * 品牌绿（浅色）。
 * Brand green (light).
 */
internal val BrandGreenLight = Color(0xFF248A3D)

/**
 * 品牌绿（深色）。
 * Brand green (dark).
 */
internal val BrandGreenDark = Color(0xFF8DCF4A)

/**
 * 品牌蓝（浅色）。
 * Brand blue (light).
 */
internal val BrandBlueLight = Color(0xFF3D84FF)

/**
 * 品牌蓝（深色，与浅色相同）。
 * Brand blue (dark, same as light).
 */
internal val BrandBlueDark = Color(0xFF3D84FF)

// MARK: - 「热门」的橙色 / The "hot" orange

/**
 * 「热门」橙色的圆底（深色）：iOS 系统橙 16% 叠在深色页面背景上。
 * The "hot" orange container (dark): the iOS system orange at 16% over the dark page background.
 */
internal val OrangeContainerDark = Color(0xFF362912)

/**
 * 圆底上的图标（深色）：iOS 系统橙本身。
 * Icons on the container (dark): the iOS system orange itself.
 */
internal val OnOrangeContainerDark = Color(0xFFFF9F0A)

/**
 * 「热门」橙色的圆底（浅色）。
 * The "hot" orange container (light).
 *
 * 浅色还没设计，先用 Material 色阶第 90 档。
 * Light mode isn't designed yet; this is Material tone 90 for now.
 */
internal val OrangeContainerLight = Color(0xFFFFDDBB)

/**
 * 圆底上的图标（浅色，Material 色阶第 10 档）。
 * Icons on the container (light, Material tone 10).
 */
internal val OnOrangeContainerLight = Color(0xFF2B1700)

// MARK: - 「免费领取」的绿色 / The "gift" green

/**
 * 「免费领取」绿色的圆底（深色）：品牌绿 16% 叠在深色页面背景（iOS `appBackground` #101214）上，算法同橙色的圆底。
 * The "gift" green container (dark): the brand green at 16% over the dark page background (iOS `appBackground`
 * #101214), worked out the same way as the orange container.
 */
internal val GreenContainerDark = Color(0xFF24301D)

/**
 * 圆底上的图标（深色）：品牌绿本身。
 * Icons on the container (dark): the brand green itself.
 */
internal val OnGreenContainerDark = BrandGreenDark

/**
 * 「免费领取」绿色的圆底（浅色）。
 * The "gift" green container (light).
 *
 * 浅色还没设计，先用品牌绿色阶（Theme Builder 导出的 primary）第 90 档。
 * Light mode isn't designed yet; this is tone 90 of the brand green palette (Theme Builder's primary) for now.
 */
internal val GreenContainerLight = Color(0xFFB1F66C)

/**
 * 圆底上的图标（浅色，同一色阶第 10 档）。
 * Icons on the container (light, tone 10 of the same palette).
 */
internal val OnGreenContainerLight = Color(0xFF0E2000)

// MARK: - 收藏的粉色 / The favorite pink

/**
 * 已收藏的心形（深色）：iOS 系统粉（`systemPink` 深色）。
 * A saved heart (dark): the iOS system pink (`systemPink`, dark).
 */
internal val FavoritePinkDark = Color(0xFFFF375F)

/**
 * 已收藏的心形（浅色）：iOS 系统粉（`systemPink` 浅色）。
 * A saved heart (light): the iOS system pink (`systemPink`, light).
 */
internal val FavoritePinkLight = Color(0xFFFF2D55)

/**
 * 删除（深色）：iOS 系统红（`systemRed` 深色），如左滑删除露出的底色。
 * Delete (dark): the iOS system red (`systemRed`, dark), such as the background revealed by swipe to delete.
 */
internal val DestructiveRedDark = Color(0xFFFF453A)

/**
 * 删除（浅色）：iOS 系统红（`systemRed` 浅色）。
 * Delete (light): the iOS system red (`systemRed`, light).
 */
internal val DestructiveRedLight = Color(0xFFFF3B30)
