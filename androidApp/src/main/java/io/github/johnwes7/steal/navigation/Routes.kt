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
 * Profile tab 的根页面。
 * The root screen of the Profile tab.
 */
@Serializable
data object ProfileRoute : NavKey
