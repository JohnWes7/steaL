package io.github.johnwes7.steal.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnwes7.steal.LocalAppContainer
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.previews.previewDealItems
import io.github.johnwes7.steal.shared.models.app.HomeDealItem
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.viewmodels.GamesViewModel
import io.github.johnwes7.steal.shared.viewmodels.GamesViewModel.DealLoadState
import io.github.johnwes7.steal.shared.viewmodels.GamesViewModel.GiveawaysLoadState
import io.github.johnwes7.steal.views.components.home.DealsSectionView
import io.github.johnwes7.steal.views.components.home.GamesHeaderView
import io.github.johnwes7.steal.views.theme.ExtendedTheme
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * Games tab 的根页面：Hot deals 区块，下面暂时是调试用的地区切换与 giveaways 文字。
 * The Games tab's root screen: the Hot deals section, followed for now by a debug region switch and giveaways text.
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

    // 4. 状态和操作交给内容函数显示；Hot deals 列表页和详情页还没做，点了先不做事
    // 4. Hand the state and actions to the content function; the Hot deals list and detail pages don't exist yet
    GamesContent(
        dealState = dealState,
        giveawaysState = giveawaysState,
        isRefreshing = isRefreshing,
        region = region,
        onSelectRegion = { newRegion -> region = newRegion },
        onRefresh = { viewModel.refresh(region = region) },
        onOpenHotDeals = {},
        onSelectDeal = { _ -> },
        onRetryDeals = { viewModel.retryDeals(region = region) },
        onRetryGiveaways = { viewModel.retryGiveaways() },
        modifier = modifier,
    )
}

/**
 * 页面内容：只接状态和回调，预览直接用它。
 * The screen content, taking only state and callbacks, so previews can use it directly.
 */
@Composable
private fun GamesContent(
    dealState: DealLoadState,
    giveawaysState: GiveawaysLoadState,
    isRefreshing: Boolean,
    region: AppRegion,
    onSelectRegion: (AppRegion) -> Unit,
    onRefresh: () -> Unit,
    onOpenHotDeals: () -> Unit,
    onSelectDeal: (HomeDealItem) -> Unit,
    onRetryDeals: () -> Unit,
    onRetryGiveaways: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 顶栏固定在上面；内容滚到它下面时，顶栏底色换成导航栏那一层的灰。
    // The top bar stays pinned; once content scrolls under it, it switches to the navigation bar's gray.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // 底部由 MainTabView 让出导航栏，顶栏自己让出状态栏，内容只需让出横屏时左右的刘海。
    // MainTabView clears the navigation bar and the top bar clears the status bar, so content only clears side cutouts.
    val sideInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        TopAppBar(
            title = { GamesHeaderView() },
            expandedHeight = 80.dp,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            scrollBehavior = scrollBehavior,
        )
        GamesList(
            dealState = dealState,
            giveawaysState = giveawaysState,
            isRefreshing = isRefreshing,
            region = region,
            onSelectRegion = onSelectRegion,
            onRefresh = onRefresh,
            onOpenHotDeals = onOpenHotDeals,
            onSelectDeal = onSelectDeal,
            onRetryDeals = onRetryDeals,
            onRetryGiveaways = onRetryGiveaways,
            modifier = Modifier.windowInsetsPadding(sideInsets),
        )
    }
}

@Composable
private fun GamesList(
    dealState: DealLoadState,
    giveawaysState: GiveawaysLoadState,
    isRefreshing: Boolean,
    region: AppRegion,
    onSelectRegion: (AppRegion) -> Unit,
    onRefresh: () -> Unit,
    onOpenHotDeals: () -> Unit,
    onSelectDeal: (HomeDealItem) -> Unit,
    onRetryDeals: () -> Unit,
    onRetryGiveaways: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "hotDealsHeader") {
                HotDealsHeader(onClick = onOpenHotDeals)
            }
            item(key = "deals") {
                DealsSectionView(state = dealState, onSelect = onSelectDeal, onRetry = onRetryDeals)
            }
            item(key = "debug") {
                DebugSection(
                    region = region,
                    onSelectRegion = onSelectRegion,
                    giveawaysState = giveawaysState,
                    onRetryGiveaways = onRetryGiveaways,
                )
            }
        }
    }
}

/**
 * 「Hot deals」区块标题：火焰图标 + 标题 + 箭头，整行可点，打开完整的 Hot deals 列表。
 * The Hot deals section title: a flame, the title and a chevron; the whole row is clickable and opens the full
 * Hot deals list.
 */
@Composable
private fun HotDealsHeader(onClick: () -> Unit) {
    val openLabel = stringResource(R.string.hot_deals_open_label)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
            .semantics { heading() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 图标只是装饰，朗读只读标题。
        // The icons are decorative; only the title is read out.
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color = ExtendedTheme.colors.hotContainer, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.local_fire_department_fill1_24px),
                contentDescription = null,
                tint = ExtendedTheme.colors.onHotContainer,
            )
        }
        Text(
            text = stringResource(R.string.hot_deals_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.chevron_right_24px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 调试区：切地区（看各种货币的格式）与 giveaways 的纯文字状态。
 * The debug area: a region switch (to check each currency's formatting) and the giveaways state as plain text.
 */
@Composable
private fun DebugSection(
    region: AppRegion,
    onSelectRegion: (AppRegion) -> Unit,
    giveawaysState: GiveawaysLoadState,
    onRetryGiveaways: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()
        Text(text = "Debug", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (option in AppRegion.entries) {
                FilterChip(
                    selected = option == region,
                    onClick = { onSelectRegion(option) },
                    label = { Text(option.apiRegionCode) },
                )
            }
        }
        Text(text = "Giveaways", style = MaterialTheme.typography.titleSmall)
        when (giveawaysState) {
            GiveawaysLoadState.Loading -> Text(text = "Loading…")
            is GiveawaysLoadState.Failed -> {
                Text(text = "Failed: ${giveawaysState.error}")
                Button(onClick = onRetryGiveaways) { Text(text = "Retry") }
            }
            is GiveawaysLoadState.Loaded -> {
                for (giveaway in giveawaysState.giveaways) {
                    val expiry = giveaway.expiry ?: "no expiry"
                    Text(text = "${giveaway.title}\n  until $expiry")
                }
            }
        }
    }
}

@Preview
@Composable
private fun GamesContentPreview() {
    SteaLTheme(darkTheme = true) {
        GamesContent(
            dealState = DealLoadState.Loaded(items = previewDealItems),
            giveawaysState = GiveawaysLoadState.Loading,
            isRefreshing = false,
            region = AppRegion.Australia,
            onSelectRegion = { _ -> },
            onRefresh = {},
            onOpenHotDeals = {},
            onSelectDeal = { _ -> },
            onRetryDeals = {},
            onRetryGiveaways = {},
        )
    }
}
