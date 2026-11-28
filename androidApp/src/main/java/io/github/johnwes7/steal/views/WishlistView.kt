package io.github.johnwes7.steal.views

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnwes7.steal.LocalAppContainer
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.navigation.GameDetailRoute
import io.github.johnwes7.steal.previews.previewWishlistItems
import io.github.johnwes7.steal.previews.previewWishlistPrices
import io.github.johnwes7.steal.shared.models.app.WishlistFilter
import io.github.johnwes7.steal.shared.models.app.WishlistItem
import io.github.johnwes7.steal.shared.viewmodels.WishlistViewModel
import io.github.johnwes7.steal.shared.viewmodels.WishlistViewModel.Row
import io.github.johnwes7.steal.views.components.shared.CenteredPageItem
import io.github.johnwes7.steal.views.components.shared.ContentUnavailableView
import io.github.johnwes7.steal.views.components.shared.PageHeaderView
import io.github.johnwes7.steal.views.components.shared.PageScaffold
import io.github.johnwes7.steal.views.components.wishlist.WishlistFilterBar
import io.github.johnwes7.steal.views.components.wishlist.WishlistRowView
import io.github.johnwes7.steal.views.theme.ExtendedTheme
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * Wishlist tab 的根页面：本机愿望单，顶上筛选，每行带价格；点一行调 [onOpenGameDetail] 打开详情页，左滑删除，下拉刷新。
 * The Wishlist tab's root screen: the on-device wishlist with a filter on top and a price on each row; tapping a row
 * calls [onOpenGameDetail] to open the detail screen, swiping it away removes it, and pulling down refreshes.
 */
@Composable
fun WishlistView(
    onOpenGameDetail: (GameDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 1. 取这一页的 ViewModel，收集它的状态
    // 1. Get this page's view model and collect its state
    val viewModel: WishlistViewModel = viewModel(factory = LocalAppContainer.current.wishlistViewModelFactory)
    val items by viewModel.items.collectAsStateWithLifecycle()
    val rows by viewModel.visibleRows.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val isFilterPending by viewModel.isFilterPending.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    // 2. 交进界面实际显示的那份翻译里的 Steam 语言代码，让 ViewModel 开始加载
    // 2. Hand in the Steam language code from the translation the UI actually shows, so the view model starts loading
    val steamLanguage = stringResource(R.string.steam_api_language)
    LaunchedEffect(steamLanguage) {
        viewModel.loadIfNeeded(steamLanguage = steamLanguage)
    }

    // 3. 状态和操作交给内容函数显示
    // 3. Hand the state and actions to the content function
    WishlistContent(
        items = items,
        rows = rows,
        selectedFilter = selectedFilter,
        isFilterPending = isFilterPending,
        isRefreshing = isRefreshing,
        onSelectFilter = { filter -> viewModel.selectFilter(filter = filter) },
        onRefresh = { viewModel.refresh() },
        onOpenItem = { item ->
            val route = GameDetailRoute(appID = item.appID, uuid = item.itadID, title = item.name)
            onOpenGameDetail(route)
        },
        onRemove = { item -> viewModel.remove(item = item) },
        modifier = modifier,
    )
}

/**
 * 页面内容：只接状态和回调，预览直接用它。
 * The screen content, taking only state and callbacks, so previews can use it directly.
 *
 * [items] 是整张愿望单、`null` 表示还没读到数据库；[rows] 是筛选后的行。筛选栏只在有收藏时显示。
 * [items] is the whole wishlist, `null` until the database is read; [rows] are the filtered rows. The filter bar
 * only shows when something is saved.
 */
@Composable
private fun WishlistContent(
    items: List<WishlistItem>?,
    rows: List<Row>?,
    selectedFilter: WishlistFilter,
    isFilterPending: Boolean,
    isRefreshing: Boolean,
    onSelectFilter: (WishlistFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpenItem: (WishlistItem) -> Unit,
    onRemove: (WishlistItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 顶栏在列表滚离顶部时换色，所以列表的滚动状态要交给骨架。
    // The top bar changes color once the list leaves its top, so the scaffold gets the list's scroll state.
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val showsFilter = !items.isNullOrEmpty()

    PageScaffold(
        header = {
            Column {
                PageHeaderView(title = stringResource(R.string.tab_wishlist))
                if (showsFilter) {
                    WishlistFilterBar(
                        selected = selectedFilter,
                        onSelect = onSelectFilter,
                        // 标题位左边有顶栏的缩进、右边没有，补上让两边对称。
                        // The title slot is inset on the start but not the end; pad the end to match.
                        modifier = Modifier.padding(end = 16.dp, bottom = 8.dp),
                    )
                }
            }
        },
        scrollState = listState,
        modifier = modifier,
    ) { contentPadding ->
        // 骨架给的留白加在列表里面，内容才会滚到两条栏后面。
        // The scaffold's padding goes inside the list so content scrolls behind both bars.
        val listPadding = contentPadding + PaddingValues(top = 4.dp, bottom = 24.dp)

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            state = pullToRefreshState,
            indicator = {
                // 刷新指示器从顶栏下沿出现，不被顶栏挡住。
                // The refresh indicator appears from the top bar's bottom edge instead of behind the bar.
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = contentPadding.calculateTopPadding()),
                )
            },
        ) {
            // 列表一直在，读取中、空列表、筛选为空都是列表里的一项（理由同 Sales 页）。
            // The list is always there, with loading, empty and an empty filter as items in it (as on the Sales page).
            LazyColumn(
                state = listState,
                contentPadding = listPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                wishlistItems(
                    items = items,
                    rows = rows,
                    selectedFilter = selectedFilter,
                    isFilterPending = isFilterPending,
                    onOpenItem = onOpenItem,
                    onRemove = onRemove,
                )
            }
        }
    }
}

/**
 * 按状态往列表里放：读取中、没有收藏、筛选为空（还在查就转圈），或者一行行收藏。
 * Fills the list by state: loading, nothing saved, an empty filter (a spinner while still checking), or the rows.
 */
private fun LazyListScope.wishlistItems(
    items: List<WishlistItem>?,
    rows: List<Row>?,
    selectedFilter: WishlistFilter,
    isFilterPending: Boolean,
    onOpenItem: (WishlistItem) -> Unit,
    onRemove: (WishlistItem) -> Unit,
) {
    // 1. 还没读到数据库（两个状态各自到达，任一个还没到都算）
    // 1. The database isn't read yet (the two states arrive separately; either one missing counts)
    if (items == null || rows == null) {
        item(key = "loading") {
            CenteredPageItem {
                WishlistLoading()
            }
        }
        return
    }

    // 2. 没有收藏
    // 2. Nothing saved
    if (items.isEmpty()) {
        item(key = "empty") {
            CenteredPageItem {
                ContentUnavailableView(
                    iconRes = R.drawable.favorite_24px,
                    title = stringResource(R.string.wishlist_empty_title),
                    message = stringResource(R.string.wishlist_empty_message),
                )
            }
        }
        return
    }

    // 3. 有收藏但这个筛选下一行都没有：数据还在查就转圈，查完了才说没有
    // 3. Saved games, but none pass this filter: a spinner while its data loads, the empty message once it's in
    if (rows.isEmpty()) {
        item(key = "filterEmpty") {
            CenteredPageItem {
                if (isFilterPending) {
                    WishlistLoading()
                } else {
                    FilterEmptyState(filter = selectedFilter)
                }
            }
        }
        return
    }

    // 4. 一行行收藏
    // 4. The rows
    items(items = rows, key = { row -> row.item.appID }) { row ->
        SwipeToRemoveRow(
            row = row,
            onOpen = { onOpenItem(row.item) },
            onRemove = { onRemove(row.item) },
            modifier = Modifier.animateItem(),
        )
    }
}

/**
 * 能左滑删除的一行：只能从右往左滑，露出红底和垃圾桶，滑过一半松手就删。
 * A row that swipes away to remove: only from end to start, revealing red with a bin, and removed once released
 * past halfway.
 */
@Composable
private fun SwipeToRemoveRow(
    row: Row,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val isSwiping = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            DeleteBackground(isVisible = isSwiping)
        },
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { _ -> onRemove() },
    ) {
        WishlistRowView(item = row.item, price = row.price, onClick = onOpen)
    }
}

/**
 * 左滑时露出的删除底：红底、右边一个垃圾桶；没在滑时透明。
 * What swiping reveals: red with a bin at the end; transparent while not swiping.
 */
@Composable
private fun DeleteBackground(isVisible: Boolean) {
    val color = if (isVisible) ExtendedTheme.colors.destructive else Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = color)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (isVisible) {
            Icon(
                painter = painterResource(R.drawable.delete_24px),
                contentDescription = null,
                tint = ExtendedTheme.colors.onDestructive,
            )
        }
    }
}

/**
 * 筛选为空时的说明；All 不会为空（有收藏才显示列表），什么也不放。
 * The message for an empty filter; All is never empty (the list only shows with saved games), so it shows nothing.
 */
@Composable
private fun FilterEmptyState(filter: WishlistFilter) {
    when (filter) {
        WishlistFilter.All -> Unit
        WishlistFilter.OnSale -> FilterEmptyMessage(
            iconRes = R.drawable.sell_24px,
            title = stringResource(R.string.wishlist_on_sale_empty_title),
            message = stringResource(R.string.wishlist_on_sale_empty_message),
        )
        WishlistFilter.AllTimeLow -> FilterEmptyMessage(
            iconRes = R.drawable.trending_down_24px,
            title = stringResource(R.string.wishlist_all_time_low_empty_title),
            message = stringResource(R.string.wishlist_all_time_low_empty_message),
        )
        WishlistFilter.Upcoming -> FilterEmptyMessage(
            iconRes = R.drawable.calendar_month_24px,
            title = stringResource(R.string.wishlist_upcoming_empty_title),
            message = stringResource(R.string.wishlist_upcoming_empty_message),
        )
    }
}

@Composable
private fun FilterEmptyMessage(@DrawableRes iconRes: Int, title: String, message: String) {
    ContentUnavailableView(iconRes = iconRes, title = title, message = message)
}

/**
 * 读取中的指示器。
 * The loading indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WishlistLoading() {
    LoadingIndicator()
}

// MARK: - 预览 / Previews

/**
 * 预览用的行：样例愿望单和每种价格状态按顺序配对。
 * Rows for previews: the sample wishlist paired in order with each price state.
 */
private fun previewRows(): List<Row> {
    val pairs = previewWishlistItems.zip(previewWishlistPrices)
    val rows = pairs.map { pair -> Row(item = pair.first, price = pair.second) }
    return rows
}

@Preview
@Composable
private fun WishlistContentPreview() {
    SteaLTheme(darkTheme = true) {
        WishlistContent(
            items = previewWishlistItems,
            rows = previewRows(),
            selectedFilter = WishlistFilter.All,
            isFilterPending = false,
            isRefreshing = false,
            onSelectFilter = { _ -> },
            onRefresh = {},
            onOpenItem = { _ -> },
            onRemove = { _ -> },
        )
    }
}

@Preview
@Composable
private fun WishlistContentFilterEmptyPreview() {
    SteaLTheme(darkTheme = true) {
        WishlistContent(
            items = previewWishlistItems,
            rows = emptyList(),
            selectedFilter = WishlistFilter.Upcoming,
            isFilterPending = false,
            isRefreshing = false,
            onSelectFilter = { _ -> },
            onRefresh = {},
            onOpenItem = { _ -> },
            onRemove = { _ -> },
        )
    }
}

@Preview
@Composable
private fun WishlistContentEmptyPreview() {
    SteaLTheme(darkTheme = true) {
        WishlistContent(
            items = emptyList(),
            rows = emptyList(),
            selectedFilter = WishlistFilter.All,
            isFilterPending = false,
            isRefreshing = false,
            onSelectFilter = { _ -> },
            onRefresh = {},
            onOpenItem = { _ -> },
            onRemove = { _ -> },
        )
    }
}
