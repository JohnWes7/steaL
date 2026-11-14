package io.github.johnwes7.steal.views

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.github.johnwes7.steal.navigation.AppTab
import io.github.johnwes7.steal.navigation.GamesRoute
import io.github.johnwes7.steal.navigation.Navigator
import io.github.johnwes7.steal.navigation.ProfileRoute
import io.github.johnwes7.steal.navigation.SalesRoute
import io.github.johnwes7.steal.navigation.SearchRoute
import io.github.johnwes7.steal.navigation.WishlistRoute
import io.github.johnwes7.steal.navigation.rememberNavigationState
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * App 的主框架：底部导航栏 + 每个 tab 自己的返回栈。
 * The app's main frame: a bottom navigation bar with a back stack per tab.
 *
 * 对应 iOS `MainTabView`（`TabView` 里每个 tab 一个 `NavigationStack`）。用 Navigation 3，
 * 写法照官方 multiple back stacks 示例：切 tab 保留各自的页面和状态，在别的 tab 按返回先回 Games 再退出。
 * The counterpart of iOS `MainTabView` (a `NavigationStack` per tab inside `TabView`). Uses Navigation 3
 * following the official multiple back stacks recipe: each tab keeps its pages and state when switching,
 * and back from another tab returns to Games before leaving.
 */
@Composable
fun MainTabView() {
    val tabs = AppTab.entries // 所有 tab
    val topLevelRoutes = remember { tabs.map { tab -> tab.route } } // 所有tab的navkey
    
    // state 保存三个东西: start页面, 当前tab, 每个tab的返回栈(字典)
    val navigationState = rememberNavigationState(
        startRoute = GamesRoute,
        topLevelRoutes = topLevelRoutes,
    )
    
    // 只有navigationState改变时才会创建navigator, 避免重复创建
    val navigator = remember(navigationState) { Navigator(navigationState) }

    // 把返回栈里的 key 换成页面：每种 key 一个分支，新页面在这里加一行。
    // Turns a back stack key into a screen: one branch per key type; add a line here for each new screen.
    val entryProvider: (NavKey) -> NavEntry<NavKey> = { key ->
        when (key) {
            is GamesRoute -> NavEntry(key) { GamesView() }
            is SearchRoute -> NavEntry(key) { SearchView() }
            is WishlistRoute -> NavEntry(key) { WishlistView() }
            is SalesRoute -> NavEntry(key) { SaleEventsView() }
            is ProfileRoute -> NavEntry(key) { ProfileView() }
            // 漏写页面时直接崩溃，开发时立刻发现（同官方 entryProvider DSL 的默认 fallback）。
            // Crash on a missing screen so it shows up during development (same as the DSL's default fallback).
            else -> error("Unknown route: $key")
        }
    }

    // 页面自己处理顶部系统栏的留白，所以 Scaffold 不再加内容边距，只让出底部导航栏的高度。
    // Screens handle the top system bar themselves, so Scaffold adds no content insets beyond the navigation bar.
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            MainNavigationBar(
                tabs = tabs,
                selectedRoute = navigationState.topLevelRoute,
                onSelect = { route -> navigator.navigate(route) },
            )
        },
    ) { innerPadding ->
        val entries = navigationState.toDecoratedEntries(entryProvider)
        NavDisplay(
            entries = entries,
            onBack = { navigator.goBack() },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * 底部导航栏：显示 [tabs]，高亮 [selectedRoute]，点击时调 [onSelect]。
 * The bottom navigation bar: shows [tabs], highlights [selectedRoute], and calls [onSelect] on tap.
 *
 * 选中色同 iOS：在 Games 用品牌绿（主色），其他 tab 用品牌蓝（次色）。
 * The selected color matches iOS: brand green (primary) on Games, brand blue (secondary) on other tabs.
 */
@Composable
private fun MainNavigationBar(
    tabs: List<AppTab>,
    selectedRoute: NavKey,
    onSelect: (NavKey) -> Unit,
) {
    val isOnGames = selectedRoute == GamesRoute
    val tint = if (isOnGames) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    // 选中项背后的胶囊用选中色的浅色版，代替 Material 默认的 secondaryContainer。
    // The pill behind the selected item uses a light version of the tint instead of Material's default secondaryContainer.
    val indicatorColor = tint.copy(alpha = 0.16f)
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = tint,
        selectedTextColor = tint,
        indicatorColor = indicatorColor,
    )

    NavigationBar {
        for (tab in tabs) {
            val isSelected = tab.route == selectedRoute
            val iconRes = if (isSelected) tab.selectedIconRes else tab.iconRes
            val label = stringResource(tab.labelRes) // 获取本地字符串

            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab.route) },
                icon = {
                    // 文字标签已经说明了 tab，图标不再重复朗读。
                    // The text label already names the tab, so the icon isn't read out again.
                    Icon(painter = painterResource(iconRes), contentDescription = null)
                },
                label = { Text(label) },
                colors = itemColors,
            )
        }
    }
}

@Preview
@Composable
private fun MainTabViewPreview() {
    SteaLTheme {
        MainTabView()
    }
}

@Preview
@Composable
private fun MainNavigationBarPreview() {
    SteaLTheme {
        MainNavigationBar(
            tabs = AppTab.entries,
            selectedRoute = WishlistRoute,
            onSelect = { _ -> },
        )
    }
}
