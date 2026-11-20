package io.github.johnwes7.steal.views

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnwes7.steal.LocalAppContainer
import io.github.johnwes7.steal.shared.models.app.HomeDealItem
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.viewmodels.GamesViewModel
import io.github.johnwes7.steal.shared.viewmodels.GamesViewModel.DealLoadState
import io.github.johnwes7.steal.shared.viewmodels.GamesViewModel.GiveawaysLoadState
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * Games tab 的根页面，目前是调试界面：用纯文字列出 [GamesViewModel] 的状态。
 * The Games tab's root screen, currently a debug screen listing [GamesViewModel]'s state as plain text.
 */
@Composable
fun GamesView(modifier: Modifier = Modifier) {
    // 1. 取这一页的 ViewModel，收集它的状态
    // 1. Get this page's view model and collect its state
    val viewModel: GamesViewModel = viewModel(factory = LocalAppContainer.current.gamesViewModelFactory)
    val dealState by viewModel.dealState.collectAsStateWithLifecycle()
    val giveawaysState by viewModel.giveawaysState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    // 2. 调试用的地区，转屏不丢
    // 2. The debug region, surviving rotation
    var region by rememberSaveable { mutableStateOf(AppRegion.Australia) }

    // 3. 页面出现、地区变了都调 loadIfNeeded，由它决定要不要请求
    // 3. Call loadIfNeeded whenever the page appears or the region changes; it decides whether to fetch
    LaunchedEffect(region) {
        viewModel.loadIfNeeded(region = region)
    }

    // 4. 状态和操作交给内容函数显示
    // 4. Hand the state and actions to the content function
    GamesDebugContent(
        dealState = dealState,
        giveawaysState = giveawaysState,
        isRefreshing = isRefreshing,
        region = region,
        onSelectRegion = { newRegion -> region = newRegion },
        onRefresh = { viewModel.refresh(region = region) },
        onRetryDeals = { viewModel.retryDeals(region = region) },
        onRetryGiveaways = { viewModel.retryGiveaways() },
        modifier = modifier,
    )
}

/**
 * 调试界面本身：只接状态和回调，预览直接用它。
 * The debug screen itself, taking only state and callbacks, so previews can use it directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamesDebugContent(
    dealState: DealLoadState,
    giveawaysState: GiveawaysLoadState,
    isRefreshing: Boolean,
    region: AppRegion,
    onSelectRegion: (AppRegion) -> Unit,
    onRefresh: () -> Unit,
    onRetryDeals: () -> Unit,
    onRetryGiveaways: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 底部由 MainTabView 让出导航栏，这里只让出顶部状态栏和横屏时的刘海。
    // MainTabView already clears the navigation bar; this clears only the status bar and landscape cutouts.
    val topInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(topInsets),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DebugRegionPicker(selected = region, onSelect = onSelectRegion)
            DebugDealsSection(state = dealState, onRetry = onRetryDeals)
            DebugGiveawaysSection(state = giveawaysState, onRetry = onRetryGiveaways)
        }
    }
}

@Composable
private fun DebugRegionPicker(selected: AppRegion, onSelect: (AppRegion) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (region in AppRegion.entries) {
            FilterChip(
                selected = region == selected,
                onClick = { onSelect(region) },
                label = { Text(region.apiRegionCode) },
            )
        }
    }
}

@Composable
private fun DebugDealsSection(state: DealLoadState, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Deals", style = MaterialTheme.typography.titleMedium)
        when (state) {
            DealLoadState.Loading -> Text(text = "Loading…")
            is DealLoadState.Failed -> {
                Text(text = "Failed: ${state.error}")
                Button(onClick = onRetry) { Text(text = "Retry") }
            }
            is DealLoadState.Loaded -> {
                if (state.items.isEmpty()) {
                    Text(text = "(empty)")
                }
                for (item in state.items) {
                    DebugDealRow(item = item)
                }
            }
        }
    }
}

@Composable
private fun DebugDealRow(item: HomeDealItem) {
    val deal = item.deal.deal
    val price = "${deal.price.amount} ${deal.price.currency}"
    val regular = "${deal.regular.amount} ${deal.regular.currency}"
    val steam = steamLinkText(steam = item.steam)
    Text(text = "${item.deal.title}\n  $price (was $regular, -${deal.cut}%) · $steam")
}

private fun steamLinkText(steam: HomeDealItem.SteamLink): String {
    return when (steam) {
        HomeDealItem.SteamLink.Pending -> "appID …"
        is HomeDealItem.SteamLink.Resolved -> "appID ${steam.appID}"
        HomeDealItem.SteamLink.NotFound -> "appID not found"
        HomeDealItem.SteamLink.Failed -> "appID failed"
    }
}

@Composable
private fun DebugGiveawaysSection(state: GiveawaysLoadState, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Giveaways", style = MaterialTheme.typography.titleMedium)
        when (state) {
            GiveawaysLoadState.Loading -> Text(text = "Loading…")
            is GiveawaysLoadState.Failed -> {
                Text(text = "Failed: ${state.error}")
                Button(onClick = onRetry) { Text(text = "Retry") }
            }
            is GiveawaysLoadState.Loaded -> {
                if (state.giveaways.isEmpty()) {
                    Text(text = "(empty)")
                }
                for (giveaway in state.giveaways) {
                    val expiry = giveaway.expiry ?: "no expiry"
                    Text(text = "${giveaway.title}\n  until $expiry")
                }
            }
        }
    }
}

@Preview
@Composable
private fun GamesDebugContentPreview() {
    SteaLTheme {
        GamesDebugContent(
            dealState = DealLoadState.Loaded(items = emptyList()),
            giveawaysState = GiveawaysLoadState.Loading,
            isRefreshing = false,
            region = AppRegion.Australia,
            onSelectRegion = { _ -> },
            onRefresh = {},
            onRetryDeals = {},
            onRetryGiveaways = {},
        )
    }
}
