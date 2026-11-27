package io.github.johnwes7.steal.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.johnwes7.steal.HTTP_CACHE_FOLDER
import io.github.johnwes7.steal.LocalAppContainer
import io.github.johnwes7.steal.config.Config
import io.github.johnwes7.steal.navigation.AppTab
import io.github.johnwes7.steal.navigation.GameDetailRoute
import io.github.johnwes7.steal.navigation.GamesRoute
import io.github.johnwes7.steal.navigation.Navigator
import io.github.johnwes7.steal.navigation.SalesRoute
import io.github.johnwes7.steal.navigation.SearchRoute
import io.github.johnwes7.steal.navigation.LanguageSettingsRoute
import io.github.johnwes7.steal.navigation.RegionSettingsRoute
import io.github.johnwes7.steal.navigation.SettingsRoute
import io.github.johnwes7.steal.navigation.ThemeSettingsRoute
import io.github.johnwes7.steal.navigation.WishlistRoute
import io.github.johnwes7.steal.navigation.rememberNavigationState
import io.github.johnwes7.steal.shared.di.AppContainer
import io.github.johnwes7.steal.shared.services.createSettingsDataStore
import io.github.johnwes7.steal.shared.services.database.createAppDatabase
import io.github.johnwes7.steal.views.components.shared.LocalBarBlurSettings
import io.github.johnwes7.steal.views.components.shared.LocalTabBarHeight
import io.github.johnwes7.steal.views.components.shared.barBackground
import io.github.johnwes7.steal.views.components.shared.rememberBarBlurStyle
import io.github.johnwes7.steal.views.theme.SteaLTheme
import java.io.File

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
            is GamesRoute -> NavEntry(key) {
                GamesView(onOpenGameDetail = { route -> navigator.navigate(route) })
            }
            is GameDetailRoute -> NavEntry(key) {
                GameDetailView(
                    appID = key.appID,
                    uuid = key.uuid,
                    title = key.title,
                    onBack = { navigator.goBack() },
                    onOpenGameDetail = { route -> navigator.navigate(route) },
                )
            }
            is SearchRoute -> NavEntry(key) {
                SearchView(onOpenGameDetail = { route -> navigator.navigate(route) })
            }
            is WishlistRoute -> NavEntry(key) { WishlistView() }
            is SalesRoute -> NavEntry(key) { SaleEventsView() }
            is SettingsRoute -> NavEntry(key) {
                SettingsView(
                    onOpenRegionSettings = { navigator.navigate(RegionSettingsRoute) },
                    onOpenThemeSettings = { navigator.navigate(ThemeSettingsRoute) },
                    onOpenLanguageSettings = { navigator.navigate(LanguageSettingsRoute) },
                )
            }
            is RegionSettingsRoute -> NavEntry(key) { RegionSettingsView(onBack = { navigator.goBack() }) }
            is ThemeSettingsRoute -> NavEntry(key) { ThemeSettingsView(onBack = { navigator.goBack() }) }
            is LanguageSettingsRoute -> NavEntry(key) { LanguageSettingsView(onBack = { navigator.goBack() }) }
            // 漏写页面时直接崩溃，开发时立刻发现（同官方 entryProvider DSL 的默认 fallback）。
            // Crash on a missing screen so it shows up during development (same as the DSL's default fallback).
            else -> error("Unknown route: $key")
        }
    }

    // tab 栏是毛玻璃：页面铺满到 tab 栏后面，tab 栏模糊它后面的页面；用户关掉毛玻璃时是实心的叠色。
    // The tab bar is frosted: pages extend behind it and the bar blurs the page underneath; a solid tint when the
    // user turns frosting off.
    val hazeState = rememberHazeState()
    val barBlurSettings = LocalBarBlurSettings.current
    val tabBarTint = rememberUpdatedState(MaterialTheme.colorScheme.surfaceContainer)
    val tabBarBlurStyle = rememberBarBlurStyle(tint = tabBarTint, settings = barBlurSettings)

    // 页面自己处理顶部系统栏的留白，所以 Scaffold 不加系统栏的内容边距。
    // Screens handle the top system bar themselves, so Scaffold adds no system bar insets to the content.
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            MainNavigationBar(
                tabs = tabs,
                selectedRoute = navigationState.topLevelRoute,
                onSelect = { route -> navigator.navigate(route) },
                modifier = Modifier.barBackground(
                    isBlurEnabled = barBlurSettings.isEnabled,
                    hazeState = hazeState,
                    blurStyle = tabBarBlurStyle,
                    tint = tabBarTint,
                ),
            )
        },
    ) { innerPadding ->
        val entries = navigationState.toDecoratedEntries(entryProvider)
        // 页面不垫高，由 PageScaffold 按 LocalTabBarHeight 在内容底部让出 tab 栏。
        // Pages aren't padded here; PageScaffold clears the tab bar at the bottom of the content using
        // LocalTabBarHeight.
        CompositionLocalProvider(LocalTabBarHeight provides innerPadding.calculateBottomPadding()) {
            NavDisplay(
                entries = entries,
                onBack = { navigator.goBack() },
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            )
        }
    }
}

/**
 * 底部导航栏：显示 [tabs]，高亮 [selectedRoute]，点击时调 [onSelect]。
 * The bottom navigation bar: shows [tabs], highlights [selectedRoute], and calls [onSelect] on tap.
 *
 * 选中色同 iOS：在 Games 用品牌绿（主色），其他 tab 用品牌蓝（次色）。栏本身透明，背景由调用方经 [modifier] 加的模糊层画。
 * The selected color matches iOS: brand green (primary) on Games, brand blue (secondary) on other tabs. The bar
 * itself is transparent; the caller draws its background with a blur layer through [modifier].
 */
@Composable
private fun MainNavigationBar(
    tabs: List<AppTab>,
    selectedRoute: NavKey,
    onSelect: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOnGames = selectedRoute == GamesRoute
    val colorScheme = MaterialTheme.colorScheme
    // 选中项：胶囊用容器色、图标用配对的文字色、标签用主色本身（Material 导航栏的配色方式）。
    // The selected item: container color for the pill, its paired content color for the icon, the base color for
    // the label (Material's navigation bar pairing).
    val tint = if (isOnGames) colorScheme.primary else colorScheme.secondary
    val indicatorColor = if (isOnGames) colorScheme.primaryContainer else colorScheme.secondaryContainer
    val selectedIconColor = if (isOnGames) colorScheme.onPrimaryContainer else colorScheme.onSecondaryContainer
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = selectedIconColor,
        selectedTextColor = tint,
        indicatorColor = indicatorColor,
    )

    NavigationBar(modifier = modifier, containerColor = Color.Transparent) {
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
    // 预览不经过 SteaLApplication，要自己建一个容器；首页会真的发请求，预览环境连不上网就显示失败。
    // Previews skip SteaLApplication, so they build their own container; the home page really fetches, and shows
    // a failure when the preview environment has no network.
    val context = LocalContext.current
    val appContainer = remember {
        val httpCacheDirectory = File(context.cacheDir, HTTP_CACHE_FOLDER)
        AppContainer(
            logsRequests = false,
            httpCacheDirectory = httpCacheDirectory.path,
            itadApiKey = Config.ITAD_API_KEY,
            backendBaseUrl = Config.BACKEND_BASE_URL,
            settingsDataStore = createSettingsDataStore(context = context),
            appDatabase = createAppDatabase(context = context),
        )
    }
    CompositionLocalProvider(LocalAppContainer provides appContainer) {
        SteaLTheme {
            MainTabView()
        }
    }
}

@Preview
@Composable
private fun MainNavigationBarPreview() {
    SteaLTheme {
        // 预览里没有模糊层，用导航栏的底色代替。
        // Previews have no blur layer, so the navigation bar's color stands in.
        MainNavigationBar(
            tabs = AppTab.entries,
            selectedRoute = WishlistRoute,
            onSelect = { _ -> },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        )
    }
}
