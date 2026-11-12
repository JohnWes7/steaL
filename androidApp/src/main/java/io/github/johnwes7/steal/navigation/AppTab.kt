package io.github.johnwes7.steal.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import io.github.johnwes7.steal.R

/**
 * 底部导航栏的五个 tab，顺序即显示顺序。
 * The five bottom navigation tabs, in display order.
 *
 * 对应 iOS `MainTabView.AppTab`。图标是 Material Symbols：未选中用线框版，选中用填充版，让选中项更醒目。
 * Mirrors iOS `MainTabView.AppTab`. Icons are Material Symbols: outlined when unselected and filled
 * when selected, so the selected tab stands out.
 */
enum class AppTab(
    val route: NavKey,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    @param:DrawableRes val selectedIconRes: Int,
) {
    Games(
        route = GamesRoute,
        labelRes = R.string.tab_games,
        iconRes = R.drawable.sports_esports_24px,
        selectedIconRes = R.drawable.sports_esports_fill1_24px,
    ),
    Search(
        route = SearchRoute,
        labelRes = R.string.tab_search,
        iconRes = R.drawable.search_24px,
        selectedIconRes = R.drawable.search_24px,
    ),
    Wishlist(
        route = WishlistRoute,
        labelRes = R.string.tab_wishlist,
        iconRes = R.drawable.favorite_24px,
        selectedIconRes = R.drawable.favorite_fill1_24px,
    ),
    Sales(
        route = SalesRoute,
        labelRes = R.string.tab_sales,
        iconRes = R.drawable.calendar_month_24px,
        selectedIconRes = R.drawable.calendar_month_fill1_24px,
    ),
    Profile(
        route = ProfileRoute,
        labelRes = R.string.tab_profile,
        iconRes = R.drawable.person_24px,
        selectedIconRes = R.drawable.person_fill1_24px,
    ),
}
