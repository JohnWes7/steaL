package io.github.johnwes7.steal.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnwes7.steal.LocalAppContainer
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.navigation.GameDetailRoute
import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.steam.SteamAppDetails
import io.github.johnwes7.steal.shared.viewmodels.GameDetailViewModel
import io.github.johnwes7.steal.shared.viewmodels.GameDetailViewModel.DetailLoadState
import io.github.johnwes7.steal.shared.viewmodels.GameDetailViewModel.InfoLoadState
import io.github.johnwes7.steal.shared.viewmodels.GameDetailViewModel.PricesLoadState
import io.github.johnwes7.steal.views.components.shared.CenteredPageItem
import io.github.johnwes7.steal.views.components.shared.ContentUnavailableView
import io.github.johnwes7.steal.views.components.shared.LoadErrorView
import io.github.johnwes7.steal.views.components.shared.PageBackButton
import io.github.johnwes7.steal.views.components.shared.PageScaffold
import io.github.johnwes7.steal.views.components.shared.PageTopBarStyle
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 游戏详情页：Steam 详情是硬门槛，ITAD 的比价和游戏信息随后补上。
 * The game detail screen: the Steam details are the hard requirement, with the ITAD prices and game info filling
 * in afterwards.
 *
 * [appID]、[uuid]、[title] 是导航参数，原样交给 ViewModel 的工厂。
 * [appID], [uuid] and [title] are the navigation arguments, handed to the view model's factory as they are.
 */
@Composable
fun GameDetailView(
    appID: Int,
    uuid: String?,
    title: String,
    onBack: () -> Unit,
    onOpenGameDetail: (GameDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 1. 取这一页的 ViewModel：导航参数交给工厂，每次推入各有一个
    // 1. Get this page's view model: the navigation arguments go to the factory, one per push
    val appContainer = LocalAppContainer.current
    val viewModelFactory = appContainer.gameDetailViewModelFactory(appID = appID, uuid = uuid, title = title)
    val viewModel: GameDetailViewModel = viewModel(factory = viewModelFactory)
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val pricesState by viewModel.pricesState.collectAsStateWithLifecycle()
    val infoState by viewModel.infoState.collectAsStateWithLifecycle()

    // 2. 交进界面实际显示的那份翻译里的 Steam 语言代码：界面语言变了会再交一次，ViewModel 据此重新加载
    // 2. Hand in the Steam language code from the translation the UI actually shows: a UI language change hands it
    //    in again and the view model reloads
    val steamLanguage = stringResource(R.string.steam_api_language)
    LaunchedEffect(steamLanguage) {
        viewModel.loadIfNeeded(steamLanguage = steamLanguage)
    }

    // 3. 状态和操作交给内容函数显示
    // 3. Hand the state and actions to the content function
    GameDetailContent(
        title = detailState.title(fallback = viewModel.initialTitle),
        detailState = detailState,
        pricesState = pricesState,
        infoState = infoState,
        onBack = onBack,
        onRetry = { viewModel.retry() },
        onRetryPrices = { viewModel.retryPrices() },
        onRetryInfo = { viewModel.retryInfo() },
        onOpenGameDetail = onOpenGameDetail,
        modifier = modifier,
    )
}

/**
 * 页面内容：只接状态和回调，预览直接用它。
 * The screen content, taking only state and callbacks, so previews can use it directly.
 */
@Composable
private fun GameDetailContent(
    title: String,
    detailState: DetailLoadState,
    pricesState: PricesLoadState,
    infoState: InfoLoadState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryPrices: () -> Unit,
    onRetryInfo: () -> Unit,
    onOpenGameDetail: (GameDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 顶栏在列表滚离顶部时换色、两行收成一行，所以列表的滚动状态要交给骨架。
    // The top bar changes color and collapses as the list scrolls, so the scaffold gets the list's scroll state.
    val listState = rememberLazyListState()

    PageScaffold(
        header = { Text(text = title) },
        scrollState = listState,
        modifier = modifier,
        navigationIcon = { PageBackButton(onClick = onBack) },
        topBarStyle = PageTopBarStyle.Collapsing,
    ) { contentPadding ->
        // 骨架给的留白加在列表里面，内容才会滚到两条栏后面；再加上列表自己的边距。
        // The scaffold's padding goes inside the list so content scrolls behind both bars, plus the list's own margins.
        val listPadding = contentPadding + PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp)

        // 列表一直在，加载中、没找到、失败都是列表里的一项（理由同 Sales 页：旧滚动位置、出场动画）。
        // The list is always there, with loading, not found and failure as items in it (as on the Sales page: the
        // restored scroll position and the appearance animation).
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = listPadding,
        ) {
            when (detailState) {
                DetailLoadState.Loading -> item(key = "detailLoading") {
                    CenteredPageItem {
                        GameDetailLoading()
                    }
                }
                DetailLoadState.NotFound -> item(key = "detailNotFound") {
                    CenteredPageItem {
                        ContentUnavailableView(
                            iconRes = R.drawable.help_24px,
                            title = stringResource(R.string.game_detail_not_found_title),
                            message = stringResource(R.string.game_detail_not_found_message),
                        )
                    }
                }
                is DetailLoadState.Failed -> item(key = "detailError") {
                    CenteredPageItem {
                        LoadErrorView(error = detailState.error, onRetry = onRetry)
                    }
                }
                // 详情的各区块第二部分再做；第一部分先放临时显示区。
                // The detail sections come in part two; part one shows a temporary display for now.
                is DetailLoadState.Loaded -> temporaryDetailItems(
                    details = detailState.details,
                    pricesState = pricesState,
                    infoState = infoState,
                    onRetryPrices = onRetryPrices,
                    onRetryInfo = onRetryInfo,
                    onOpenGameDetail = onOpenGameDetail,
                )
            }
        }
    }
}

/**
 * 加载中的指示器。
 * The loading indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GameDetailLoading() {
    LoadingIndicator()
}

// MARK: - 临时显示区 / Temporary display
// 临时：第一部分只为在模拟器上确认数据对，第二部分换成正式组件时整节删掉。文字直接写英文、不进 strings.xml。
// Temporary: part one only checks the data on the emulator; the whole section goes when part two adds the real
// components. The text is plain English and stays out of strings.xml.

/**
 * 临时：用纯文字列出 Steam 详情、比价和游戏信息，失败的区块给重试，试玩版给推入本体的按钮。
 * Temporary: lists the Steam details, prices and game info as plain text, with retries for failed sections and a
 * button that pushes a demo's full game.
 */
private fun LazyListScope.temporaryDetailItems(
    details: SteamAppDetails,
    pricesState: PricesLoadState,
    infoState: InfoLoadState,
    onRetryPrices: () -> Unit,
    onRetryInfo: () -> Unit,
    onOpenGameDetail: (GameDetailRoute) -> Unit,
) {
    item(key = "temporarySteam") {
        TemporarySection(heading = "Steam", lines = steamLines(details = details))
    }

    val fullGameRoute = fullGameRoute(details = details)
    if (fullGameRoute != null) {
        item(key = "temporaryFullGame") {
            Button(onClick = { onOpenGameDetail(fullGameRoute) }) {
                Text(text = "Open full game: ${fullGameRoute.title}")
            }
        }
    }

    item(key = "temporaryPrices") {
        Column {
            val pricesHeading = "Prices: ${pricesState::class.simpleName}"
            TemporarySection(heading = pricesHeading, lines = pricesLines(state = pricesState))
            if (pricesState == PricesLoadState.Failed) {
                Button(onClick = onRetryPrices) {
                    Text(text = "Retry prices")
                }
            }
        }
    }

    item(key = "temporaryInfo") {
        Column {
            val infoHeading = "Info: ${infoState::class.simpleName}"
            TemporarySection(heading = infoHeading, lines = infoLines(state = infoState))
            if (infoState == InfoLoadState.Failed) {
                Button(onClick = onRetryInfo) {
                    Text(text = "Retry info")
                }
            }
        }
    }
}

/**
 * 临时：一段标题加几行文字。
 * Temporary: a heading followed by lines of text.
 */
@Composable
private fun TemporarySection(heading: String, lines: List<String>) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = heading, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        for (line in lines) {
            Text(text = line, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * 临时：Steam 详情的几行文字。
 * Temporary: the Steam details as lines of text.
 */
private fun steamLines(details: SteamAppDetails): List<String> {
    val price = details.priceOverview
    val priceText = if (price == null) {
        "none"
    } else {
        "${price.currency} ${price.final} (regular ${price.initial}, -${price.discountPercent}%)"
    }
    val genreNames = details.genres.orEmpty().map { genre -> genre.description }
    val demoAppIDs = details.demos.orEmpty().map { demo -> demo.appID }
    return listOf(
        "appID: ${details.steamAppID}",
        "name: ${details.name}",
        "type: ${details.type} / kind: ${details.gameKind}",
        "release: ${details.releaseDate?.date} (coming soon: ${details.isComingSoon})",
        "required age: ${details.requiredAge}",
        "price: $priceText",
        "genres: $genreNames",
        "movies: ${details.movies?.size} / screenshots: ${details.screenshots?.size}",
        "demos: $demoAppIDs",
        "full game: ${details.fullGame?.appID} ${details.fullGame?.name}",
    )
}

/**
 * 临时：比价的几行文字；没到手时是空的。
 * Temporary: the prices as lines of text; empty until they are in.
 */
private fun pricesLines(state: PricesLoadState): List<String> {
    val prices = when (state) {
        is PricesLoadState.Loaded -> state.prices
        is PricesLoadState.LoadedWithoutHistory -> state.prices
        else -> null
    }
    if (prices == null) {
        return emptyList()
    }
    val history = prices.historyLow
    val historyLine =
        "lows: all ${history.all?.amount} / 1y ${history.y1?.amount} / 3m ${history.m3?.amount} ${history.all?.currency}"
    val bestSteamLine = "best Steam: ${prices.bestSteamDeal?.shop?.name}"
    val dealLines = prices.deals.map { deal ->
        val drmNames = deal.drm.map { drm -> drm.name }
        "${deal.shop.name}: ${deal.price.amount} ${deal.price.currency} (-${deal.cut}%) drm $drmNames"
    }
    return listOf(historyLine, bestSteamLine) + dealLines
}

/**
 * 临时：游戏信息的几行文字；没到手时是空的。
 * Temporary: the game info as lines of text; empty until it is in.
 */
private fun infoLines(state: InfoLoadState): List<String> {
    if (state !is InfoLoadState.Loaded) {
        return emptyList()
    }
    val info = state.info
    val reviewLines = info.reviews.orEmpty().map { review -> "${review.source}: ${review.score} (${review.count})" }
    val summaryLines = listOf(
        "players: recent ${info.players?.recent} / peak ${info.players?.peak}",
        "rank: ${info.stats?.rank}",
        "tags: ${info.tags}",
    )
    return summaryLines + reviewLines
}

/**
 * 临时：试玩版的本体路由；不是试玩版，或 `fullgame.appid`（字符串）转不成数字时是 `null`。
 * Temporary: the route to a demo's full game; `null` when the entry isn't a demo or `fullgame.appid` (a string)
 * isn't a number.
 */
private fun fullGameRoute(details: SteamAppDetails): GameDetailRoute? {
    val fullGame = details.fullGame
    if (fullGame == null) {
        return null
    }
    val appID = fullGame.appID?.toIntOrNull()
    if (appID == null) {
        return null
    }
    val title = fullGame.name ?: "Full game"
    return GameDetailRoute(appID = appID, uuid = null, title = title)
}

// MARK: - 预览 / Previews

@Preview
@Composable
private fun GameDetailContentLoadingPreview() {
    SteaLTheme(darkTheme = true) {
        GameDetailContent(
            title = "ELDEN RING",
            detailState = DetailLoadState.Loading,
            pricesState = PricesLoadState.Hidden,
            infoState = InfoLoadState.Hidden,
            onBack = {},
            onRetry = {},
            onRetryPrices = {},
            onRetryInfo = {},
            onOpenGameDetail = { _ -> },
        )
    }
}

@Preview
@Composable
private fun GameDetailContentNotFoundPreview() {
    SteaLTheme(darkTheme = true) {
        GameDetailContent(
            title = "ELDEN RING",
            detailState = DetailLoadState.NotFound,
            pricesState = PricesLoadState.Hidden,
            infoState = InfoLoadState.Hidden,
            onBack = {},
            onRetry = {},
            onRetryPrices = {},
            onRetryInfo = {},
            onOpenGameDetail = { _ -> },
        )
    }
}

@Preview
@Composable
private fun GameDetailContentFailedPreview() {
    SteaLTheme(darkTheme = true) {
        GameDetailContent(
            title = "ELDEN RING",
            detailState = DetailLoadState.Failed(error = LoadError.Offline),
            pricesState = PricesLoadState.Hidden,
            infoState = InfoLoadState.Hidden,
            onBack = {},
            onRetry = {},
            onRetryPrices = {},
            onRetryInfo = {},
            onOpenGameDetail = { _ -> },
        )
    }
}
