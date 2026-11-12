// 改写自 android/nav3-recipes 的 multiplestacks 示例（Apache License 2.0）。
// Adapted from the multiplestacks recipe in android/nav3-recipes (Apache License 2.0).

package io.github.johnwes7.steal.navigation

import androidx.navigation3.runtime.NavKey

/**
 * 处理前进和返回，改的是 [state]。
 * Handles forward and back navigation by updating [state].
 */
class Navigator(val state: NavigationState) {

    /**
     * 前往 [route]：是 tab 的根页面就切到那个 tab，否则推入当前 tab 的返回栈。
     * Goes to [route]: switches tabs if it is a tab's root screen, otherwise pushes it onto the current tab's back stack.
     */
    fun navigate(route: NavKey) {
        val isTopLevelRoute = route in state.backStacks.keys
        if (isTopLevelRoute) {
            state.topLevelRoute = route
            return
        }

        val currentStack = state.backStacks.getValue(state.topLevelRoute)
        currentStack.add(route)
    }

    /**
     * 返回一步：在 tab 里面就退一页，已经在 tab 根页面就回到起始 tab。
     * Goes back one step: pops a page inside the tab, or returns to the start tab when already at the tab's root.
     *
     * 起始 tab 的根页面按返回不会走到这里，`NavDisplay` 会把返回交给系统退出 App。
     * Back on the start tab's root never reaches here; `NavDisplay` hands it to the system, which leaves the app.
     */
    fun goBack() {
        val currentStack = state.backStacks.getValue(state.topLevelRoute)
        val currentRoute = currentStack.last()
        val isAtTabRoot = currentRoute == state.topLevelRoute

        if (isAtTabRoot) {
            state.topLevelRoute = state.startRoute
        } else {
            currentStack.removeLastOrNull()
        }
    }
}
