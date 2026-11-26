package io.github.johnwes7.steal.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// 导航用的路由 key：每个 key 代表一个页面，返回栈就是这些 key 的列表。
// Navigation route keys: each key stands for a screen, and a back stack is a list of them.
// 要 @Serializable，返回栈才能在旋转屏幕、进程被杀后恢复。
// They must be @Serializable so back stacks survive configuration changes and process death.

/**
 * Games tab 的根页面。
 * The root screen of the Games tab.
 */
@Serializable
data object GamesRoute : NavKey

/**
 * 游戏详情页，从首页的卡片推入；试玩版的详情页也会推入它本体的详情页。
 * The game detail screen, pushed from a home page card; a demo's detail screen also pushes its full game's.
 */
@Serializable
data class GameDetailRoute(
    /**
     * Steam appID。
     * The Steam appID.
     */
    val appID: Int,

    /**
     * ITAD 游戏 UUID；首页带着，从别处进来可能没有。
     * The ITAD game UUID; the home page brings it, other entry points may not.
     */
    val uuid: String?,

    /**
     * 列表上的名字，Steam 详情回来之前当标题。
     * The name shown in the list, used as the title until the Steam details arrive.
     */
    val title: String,
) : NavKey

/**
 * Search tab 的根页面。
 * The root screen of the Search tab.
 */
@Serializable
data object SearchRoute : NavKey

/**
 * Wishlist tab 的根页面。
 * The root screen of the Wishlist tab.
 */
@Serializable
data object WishlistRoute : NavKey

/**
 * Sales tab 的根页面。
 * The root screen of the Sales tab.
 */
@Serializable
data object SalesRoute : NavKey

/**
 * Settings tab 的根页面。
 * The root screen of the Settings tab.
 */
@Serializable
data object SettingsRoute : NavKey

/**
 * 「地区与货币」单选页，从 Settings 根页面推入。
 * The "Region & currency" single-choice screen, pushed from the Settings root screen.
 */
@Serializable
data object RegionSettingsRoute : NavKey

/**
 * 「主题与色彩」设置页，从 Settings 根页面推入。
 * The "Theme & color" settings screen, pushed from the Settings root screen.
 */
@Serializable
data object ThemeSettingsRoute : NavKey

/**
 * 「语言」单选页，从 Settings 根页面推入。
 * The "Language" single-choice screen, pushed from the Settings root screen.
 */
@Serializable
data object LanguageSettingsRoute : NavKey
