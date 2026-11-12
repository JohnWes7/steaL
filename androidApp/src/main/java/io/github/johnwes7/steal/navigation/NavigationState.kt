// 改写自 android/nav3-recipes 的 multiplestacks 示例（Apache License 2.0）。
// Adapted from the multiplestacks recipe in android/nav3-recipes (Apache License 2.0).

package io.github.johnwes7.steal.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * 建立导航状态：每个 tab 一个返回栈，外加当前选中的 tab，旋转屏幕和进程被杀后都能恢复。
 * Creates the navigation state: one back stack per tab plus the selected tab, surviving configuration
 * changes and process death.
 *
 * [startRoute] 是起始 tab（也必须在 [topLevelRoutes] 里），[topLevelRoutes] 是全部 tab 的根页面。
 * [startRoute] is the starting tab (it must also be in [topLevelRoutes]); [topLevelRoutes] are the root
 * screens of all tabs.
 */
@Composable
fun rememberNavigationState(
    startRoute: NavKey,
    topLevelRoutes: List<NavKey>,
): NavigationState {
    val topLevelRoute = rememberNavKey(startRoute)
    val backStacks = topLevelRoutes.associateWith { route -> rememberNavBackStack(route) }

    return remember(startRoute, topLevelRoutes) {
        NavigationState(
            startRoute = startRoute,
            topLevelRoute = topLevelRoute,
            backStacks = backStacks,
        )
    }
}

/**
 * 导航状态：当前选中的 tab 与每个 tab 自己的返回栈。
 * Navigation state: the selected tab and each tab's own back stack.
 *
 * 对应 iOS 每个 tab 一个 `NavigationStack`。这个类只存状态，改动都经过 [Navigator]。
 * The counterpart of one `NavigationStack` per tab on iOS. This class only holds state; changes go
 * through [Navigator].
 */
class NavigationState(
    val startRoute: NavKey,
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    /**
     * 当前选中的 tab（它的根页面 key）。
     * The selected tab (its root screen key).
     */
    var topLevelRoute: NavKey by topLevelRoute

    /**
     * 把要显示的返回栈转成 `NavDisplay` 用的页面列表，用 [entryProvider] 把 key 转成页面。
     * Turns the back stacks in use into entries for `NavDisplay`, using [entryProvider] to turn keys into screens.
     *
     * 每个返回栈各自保存页面状态（滚动位置等）和 ViewModel，所以切走再切回来，tab 里的内容还在。
     * Each back stack keeps its own saved UI state (scroll position and so on) and ViewModels, so a tab's
     * content is still there after switching away and back.
     */
    @Composable
    fun toDecoratedEntries(
        entryProvider: (NavKey) -> NavEntry<NavKey>,
    ): List<NavEntry<NavKey>> {
        val decoratedEntries = backStacks.mapValues { (_, backStack) ->
            val decorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>(),
            )
            rememberDecoratedNavEntries(
                backStack = backStack,
                entryDecorators = decorators,
                entryProvider = entryProvider,
            )
        }

        val routesInUse = topLevelRoutesInUse()
        return routesInUse.flatMap { route -> decoratedEntries.getValue(route) }
    }

    /**
     * 返回正在用的 tab：起始 tab 永远在最前面，选中别的 tab 时再加上它。
     * Returns the tabs in use: the start tab always comes first, followed by the selected tab if it differs.
     *
     * 起始 tab 垫在最底下，所以在别的 tab 一路按返回，会先回到起始 tab 再退出 App
     * （Android 导航原则的「固定起始页」）。没显示的 tab 状态照样保留。
     * Keeping the start tab at the bottom means pressing back from another tab returns to the start tab
     * before leaving the app (the "fixed start destination" navigation principle). Tabs not shown still
     * keep their state.
     */
    private fun topLevelRoutesInUse(): List<NavKey> {
        val isOnStartTab = topLevelRoute == startRoute
        if (isOnStartTab) {
            return listOf(startRoute)
        }
        return listOf(startRoute, topLevelRoute)
    }
}
