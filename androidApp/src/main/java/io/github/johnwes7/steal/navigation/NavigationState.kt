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
    // 当前 tab 与每个 tab 的返回栈都存进系统保管的 Bundle：进程被杀后重新执行到这里时，从 Bundle 取回原值，
    // 不用初始值。
    // The selected tab and every back stack are saved in the system-kept Bundle; when this runs again after
    // process death, the saved values come back instead of the initial ones.
    val topLevelRoute = rememberNavKey(startRoute)
    // associateWith：每个 tab 根页面当 key，它自己的返回栈当 value。
    // associateWith: each tab's root screen becomes a key, with its own back stack as the value.
    val backStacks = topLevelRoutes.associateWith { route -> rememberNavBackStack(route) }

    // 外壳对象本身不用存：它只是把上面两样（已能恢复的）包起来。用普通 remember 是为了重组时保持同一个实例，
    // 只有 tab 配置变了才重建。
    // The wrapper itself needs no saving; it only wraps the two restorable pieces above. Plain remember keeps
    // the same instance across recompositions, rebuilding it only when the tab setup changes.
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
        // 1. 5 个 tab 的返回栈全都转换，不只是正在显示的：装饰器按「页面还在不在列表里」决定清不清状态，
        //    漏掉的 tab 会被当成已移除，切回来时页面状态和 ViewModel 就没了。
        // 1. Convert every tab's back stack, not just the visible ones: decorators clear state for pages that
        //    drop out of the list, so a skipped tab would lose its page state and ViewModels.
        val decoratedEntries = backStacks.mapValues { (_, backStack) ->
            // 2. 两个装饰器给每一页各自一份 rememberSaveable 存储和 ViewModel，页面还在栈里就保留，弹出栈就清掉。
            // 2. The two decorators give each page its own rememberSaveable storage and ViewModels, kept while the
            //    page is on the stack and cleared when it is popped.
            val decorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>(),
            )
            // 3. 栈里每个 key 经 entryProvider 换成页面（NavEntry），再套上装饰器；顺序和栈相同。
            // 3. Each key on the stack becomes a page (NavEntry) via entryProvider, wrapped by the decorators,
            //    in stack order.
            rememberDecoratedNavEntries(
                backStack = backStack,
                entryDecorators = decorators,
                entryProvider = entryProvider,
            )
        }

        // 4. 这次用到的 tab：在起始 tab 时只有它，否则是「起始 tab + 当前 tab」。
        // 4. The tabs in use: just the start tab when on it, otherwise the start tab plus the selected tab.
        val routesInUse = topLevelRoutesInUse()
        // 5. 把这几个 tab 的页面首尾相接成一条列表，NavDisplay 显示最后一页，按返回就从尾部往前退。
        //    例：在 Wishlist 打开了详情 → [Games, Wishlist, 详情]。
        // 5. Join those tabs' pages into one list; NavDisplay shows the last page and back walks from the end.
        //    Example: a detail page opened in Wishlist gives [Games, Wishlist, detail].
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
