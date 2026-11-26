package io.github.johnwes7.steal.views

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnwes7.steal.LocalAppContainer
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.navigation.GameDetailRoute
import io.github.johnwes7.steal.previews.previewAppDetails
import io.github.johnwes7.steal.previews.previewGameInfo
import io.github.johnwes7.steal.previews.previewPriceGame
import io.github.johnwes7.steal.shared.config.AppLog
import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.steam.SteamAppDetails
import io.github.johnwes7.steal.shared.viewmodels.GameDetailViewModel
import io.github.johnwes7.steal.shared.viewmodels.GameDetailViewModel.DetailLoadState
import io.github.johnwes7.steal.shared.viewmodels.GameDetailViewModel.InfoLoadState
import io.github.johnwes7.steal.shared.viewmodels.GameDetailViewModel.PricesLoadState
import io.github.johnwes7.steal.views.components.detail.DemoSectionView
import io.github.johnwes7.steal.views.components.detail.DetailFooterView
import io.github.johnwes7.steal.views.components.detail.DetailTag
import io.github.johnwes7.steal.views.components.detail.FullGameSectionView
import io.github.johnwes7.steal.views.components.detail.ItadInfoSectionView
import io.github.johnwes7.steal.views.components.detail.MediaGalleryView
import io.github.johnwes7.steal.views.components.detail.OfferFilter
import io.github.johnwes7.steal.views.components.detail.PriceSectionView
import io.github.johnwes7.steal.views.components.detail.SteamDetailSectionView
import io.github.johnwes7.steal.views.components.shared.CenteredPageItem
import io.github.johnwes7.steal.views.components.shared.ContentUnavailableView
import io.github.johnwes7.steal.views.components.shared.LoadErrorView
import io.github.johnwes7.steal.views.components.shared.PageBackButton
import io.github.johnwes7.steal.views.components.shared.PageScaffold
import io.github.johnwes7.steal.views.components.shared.PageTopBarStyle
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 页面内容左右的边距；画廊不用，贴到屏幕两边。
 * The side margin of the page content; the gallery skips it and runs edge to edge.
 */
private val DETAIL_SIDE_MARGIN = 16.dp

/**
 * 画廊下面各区块之间的间距。
 * The spacing between the sections below the gallery.
 */
private val DETAIL_SECTION_SPACING = 28.dp

/**
 * 画廊下面一个区块的外边距：左右是页面边距，上面是区块间距。
 * The outer padding of a section below the gallery: the page margin on the sides, the section spacing on top.
 */
private val SECTION_PADDING = PaddingValues(start = DETAIL_SIDE_MARGIN, end = DETAIL_SIDE_MARGIN, top = DETAIL_SECTION_SPACING)

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
    val steamDetailState by viewModel.steamDetailState.collectAsStateWithLifecycle()
    val itadPricesState by viewModel.itadPricesState.collectAsStateWithLifecycle()
    val itadInfoState by viewModel.itadInfoState.collectAsStateWithLifecycle()

    // 2. 交进界面实际显示的那份翻译里的 Steam 语言代码：界面语言变了会再交一次，ViewModel 据此重新加载
    // 2. Hand in the Steam language code from the translation the UI actually shows: a UI language change hands it
    //    in again and the view model reloads
    val steamLanguage = stringResource(R.string.steam_api_language)
    LaunchedEffect(steamLanguage) {
        viewModel.loadIfNeeded(steamLanguage = steamLanguage)
    }

    // 3. 状态和操作交给内容函数显示；离开 App 的网址在这一层打开
    // 3. Hand the state and actions to the content function; URLs that leave the app are opened at this level
    val uriHandler = LocalUriHandler.current
    GameDetailContent(
        appID = appID,
        title = steamDetailState.title(fallback = viewModel.initialTitle),
        steamDetailState = steamDetailState,
        pricesState = itadPricesState,
        infoState = itadInfoState,
        onBack = onBack,
        onRetry = { viewModel.retry() },
        onRetryPrices = { viewModel.retryPrices() },
        onRetryInfo = { viewModel.retryInfo() },
        onOpenGameDetail = onOpenGameDetail,
        onOpenDemoStorePage = { demoAppID ->
            val url = "https://store.steampowered.com/app/$demoAppID/"
            openExternalPage(uriHandler = uriHandler, url = url)
        },
        onOpenOffer = { url -> openExternalPage(uriHandler = uriHandler, url = url) },
        modifier = modifier,
    )
}

/**
 * 用外部 App（浏览器或商店 App）打开 [url]；没有 App 能打开时只记日志。
 * Opens [url] in another app (a browser or the store's app); only logs when no app can open it.
 */
private fun openExternalPage(uriHandler: UriHandler, url: String) {
    try {
        uriHandler.openUri(url)
    } catch (error: IllegalArgumentException) {
        AppLog.gameDetail.e(error) { "打不开外部页面 / Can't open the external page: $url" }
    }
}

/**
 * 页面内容：只接状态和回调，预览直接用它。
 * The screen content, taking only state and callbacks, so previews can use it directly.
 *
 * 简介展开、报价筛选和展开这三个界面状态放在这里：区块切到加载中再回来（重试）也不会丢。
 * The description's expansion and the offer filter and expansion live here, so they survive a section going back
 * to loading and returning (a retry).
 */
@Composable
private fun GameDetailContent(
    appID: Int,
    title: String,
    steamDetailState: DetailLoadState,
    pricesState: PricesLoadState,
    infoState: InfoLoadState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryPrices: () -> Unit,
    onRetryInfo: () -> Unit,
    onOpenGameDetail: (GameDetailRoute) -> Unit,
    onOpenDemoStorePage: (Int) -> Unit,
    onOpenOffer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 1. 界面状态
    // 1. The UI state
    var isSteamDetailExpanded by rememberSaveable { mutableStateOf(false) }
    var offerFilter by rememberSaveable { mutableStateOf(OfferFilter.Steam) }
    var isShowingAllOffers by rememberSaveable { mutableStateOf(false) }

    // 2. 列表里用到的文字先取好：列表的构建函数不是 Composable，读不了资源
    // 2. Read the text the list needs up front: the list builder isn't composable, so it can't read resources
    val demoFallbackName = stringResource(R.string.game_detail_demo_fallback_name, title)
    val demoOwnFallbackName = stringResource(R.string.game_detail_section_demo)
    val fullGameFallbackName = stringResource(R.string.game_detail_section_full_game)

    // 3. 顶栏在列表滚离顶部时换色，所以列表的滚动状态要交给骨架
    // 3. The top bar changes color once the list scrolls, so the scaffold gets the list's scroll state
    val listState = rememberLazyListState()

    PageScaffold(
        header = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        scrollState = listState,
        modifier = modifier,
        navigationIcon = { PageBackButton(onClick = onBack) },
        topBarStyle = PageTopBarStyle.Small,
    ) { contentPadding ->
        // 骨架给的留白加在列表里面，内容才会滚到两条栏后面；再加上列表自己的上下边距。左右边距由需要的区块自己加，画廊才能
        // 贴到屏幕两边。
        // The scaffold's padding goes inside the list so content scrolls behind both bars, plus the list's own top and
        // bottom margins. Sections that need side margins add their own, so the gallery can run edge to edge.
        val listPadding = contentPadding + PaddingValues(top = 4.dp, bottom = 24.dp)

        // 列表一直在，加载中、没找到、失败都是列表里的一项（理由同 Sales 页：旧滚动位置、出场动画）。
        // The list is always there, with loading, not found and failure as items in it (as on the Sales page: the
        // restored scroll position and the appearance animation).
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = listPadding,
        ) {
            when (steamDetailState) {
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
                        LoadErrorView(error = steamDetailState.error, onRetry = onRetry)
                    }
                }
                is DetailLoadState.Loaded -> {
                    val details = steamDetailState.details
                    val demoLink = demoLink(
                        details = details,
                        fallbackName = demoFallbackName,
                        ownFallbackName = demoOwnFallbackName,
                    )
                    val fullGameRoute = fullGameRoute(details = details, fallbackName = fullGameFallbackName)

                    item(key = "gallery") {
                        MediaGalleryView(
                            headerImageUrl = details.headerImage,
                            screenshotUrls = screenshotUrls(details = details),
                        )
                    }
                    item(key = "genresAndRelease") {
                        GenresAndReleaseDate(details = details)
                    }
                    item(key = "about") {
                        SteamDetailSectionView(
                            details = details,
                            isExpanded = isSteamDetailExpanded,
                            onToggleExpanded = { isSteamDetailExpanded = !isSteamDetailExpanded },
                            modifier = Modifier.padding(SECTION_PADDING),
                        )
                    }
                    if (demoLink != null) {
                        item(key = "demo") {
                            DemoSectionView(
                                name = demoLink.name,
                                onClick = { onOpenDemoStorePage(demoLink.appID) },
                                modifier = Modifier.padding(SECTION_PADDING),
                            )
                        }
                    }
                    if (fullGameRoute != null) {
                        item(key = "fullGame") {
                            FullGameSectionView(
                                name = fullGameRoute.title,
                                onClick = { onOpenGameDetail(fullGameRoute) },
                                modifier = Modifier.padding(SECTION_PADDING),
                            )
                        }
                    }
                    // 该不该出现由 ViewModel 定成 Hidden（试玩版、未开预购），Hidden 时不占位置。
                    // The view model decides via Hidden (demos, no pre-order yet), and Hidden takes no space.
                    if (pricesState != PricesLoadState.Hidden) {
                        item(key = "prices") {
                            PriceSectionView(
                                state = pricesState,
                                offerFilter = offerFilter,
                                onSelectOfferFilter = { newFilter -> offerFilter = newFilter },
                                isShowingAllOffers = isShowingAllOffers,
                                onToggleOffers = { isShowingAllOffers = !isShowingAllOffers },
                                onRetry = onRetryPrices,
                                onOfferClick = { deal -> onOpenOffer(deal.url) },
                                modifier = Modifier.padding(SECTION_PADDING),
                            )
                        }
                    }
                    if (infoState != InfoLoadState.Hidden) {
                        item(key = "info") {
                            ItadInfoSectionView(
                                state = infoState,
                                onRetry = onRetryInfo,
                                modifier = Modifier.padding(SECTION_PADDING),
                            )
                        }
                    }
                    item(key = "footer") {
                        DetailFooterView(
                            appID = appID,
                            itadID = itadID(infoState = infoState),
                            modifier = Modifier.padding(SECTION_PADDING),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 截图的完整尺寸地址；没有地址的截图跳过。
 * The full-size screenshot URLs, skipping screenshots without one.
 */
private fun screenshotUrls(details: SteamAppDetails): List<String> {
    val screenshots = details.screenshots.orEmpty()
    return screenshots.mapNotNull { screenshot -> screenshot.full }
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

// MARK: - 类型标签、发售日 / Genres and release date

/**
 * 画廊下面的 Steam 类型标签（横向滑动）和发售日；只显示 Steam 的事实，不和 ITAD 的数据混在一起。
 * The Steam genre tags below the gallery (scrolling sideways) and the release date; Steam's facts only, kept apart
 * from the ITAD data.
 */
@Composable
private fun GenresAndReleaseDate(details: SteamAppDetails) {
    val genres = details.genres.orEmpty()
    val genreNames = genres.mapNotNull { genre -> genre.description }
    val nonEmptyGenreNames = genreNames.filter { name -> name.isNotEmpty() }
    val releaseDate = details.releaseDate?.date

    Column(modifier = Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (nonEmptyGenreNames.isNotEmpty()) {
            // 左右边距放在滑动里面，滑到头时标签才贴边。
            // The margins sit inside the scroll, so the tags only reach the edge when scrolled.
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = DETAIL_SIDE_MARGIN),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (name in nonEmptyGenreNames) {
                    DetailTag(text = name)
                }
            }
        }
        if (releaseDate != null) {
            ReleaseDateRow(date = releaseDate, isComingSoon = details.isComingSoon)
        }
    }
}

/**
 * 发售日一行：「已发售 · 日期」或「即将推出 · 日期」；日期是 Steam 按请求语言给的文字，原样显示。
 * The release date line: "Released · date" or "Coming soon · date"; the date is Steam's text in the requested
 * language, shown as it is.
 */
@Composable
private fun ReleaseDateRow(date: String, isComingSoon: Boolean) {
    val textRes = if (isComingSoon) R.string.game_detail_coming_soon else R.string.game_detail_released
    Row(
        modifier = Modifier.padding(horizontal = DETAIL_SIDE_MARGIN),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.calendar_month_24px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(textRes, date),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// MARK: - 试玩版 / 本体入口 / Demo and full game entries

/**
 * 试玩版入口要打开的 Steam appID 和显示的名字。
 * The Steam appID the demo entry opens and the name it shows.
 */
private class DemoLink(val appID: Int, val name: String)

/**
 * 试玩版入口：本体页面上是 Steam 列的第一个试玩版，试玩版自己的页面上（`demos` 是空的）是它自己；都没有时是 `null`。
 * The demo entry: the first demo Steam lists on a base game, or the demo itself on a demo's own page (where `demos`
 * is empty); `null` when neither applies.
 *
 * Steam 很少填试玩版的名字，空的时候用 [fallbackName]（「本体名 + 试玩版」）；试玩版自己没有名字时用 [ownFallbackName]。
 * Steam rarely fills in a demo's name, so an empty one becomes [fallbackName] ("base name + Demo"); a demo page
 * without a name uses [ownFallbackName].
 */
private fun demoLink(details: SteamAppDetails, fallbackName: String, ownFallbackName: String): DemoLink? {
    // 1. 本体页面：Steam 列的第一个试玩版
    // 1. A base game's page: the first demo Steam lists
    val demos = details.demos.orEmpty()
    val firstDemo = demos.firstOrNull()
    val firstDemoAppID = firstDemo?.appID
    if (firstDemoAppID != null) {
        val description = firstDemo.description
        val name = if (description.isNullOrEmpty()) fallbackName else description
        return DemoLink(appID = firstDemoAppID, name = name)
    }

    // 2. 试玩版自己的页面：它自己
    // 2. A demo's own page: the demo itself
    if (!details.isDemo) {
        return null
    }
    val ownAppID = details.steamAppID
    if (ownAppID == null) {
        return null
    }
    val ownName = details.name
    val name = if (ownName.isNullOrEmpty()) ownFallbackName else ownName
    return DemoLink(appID = ownAppID, name = name)
}

/**
 * 试玩版的本体路由；不是试玩版，或 `fullgame.appid`（字符串）转不成数字时是 `null`。名字缺了用 [fallbackName]。
 * The route to a demo's full game; `null` when the entry isn't a demo or `fullgame.appid` (a string) isn't a
 * number. A missing name becomes [fallbackName].
 */
private fun fullGameRoute(details: SteamAppDetails, fallbackName: String): GameDetailRoute? {
    val fullGame = details.fullGame
    if (fullGame == null) {
        return null
    }
    val appID = fullGame.appID?.toIntOrNull()
    if (appID == null) {
        return null
    }
    val name = fullGame.name
    val title = if (name.isNullOrEmpty()) fallbackName else name
    return GameDetailRoute(appID = appID, uuid = null, title = title)
}

/**
 * 底部显示的 ITAD ID：从信息区块的数据读，只有一个数据源；信息没到手（或没有）时是 `null`。
 * The ITAD ID shown at the bottom, read from the info section's data so there's one source; `null` until the info
 * is in (or when there is none).
 */
private fun itadID(infoState: InfoLoadState): String? {
    if (infoState is InfoLoadState.Loaded) {
        return infoState.info.id
    }
    return null
}

// MARK: - 预览 / Previews

@Preview(heightDp = 2400)
@Composable
private fun GameDetailContentLoadedPreview() {
    SteaLTheme(darkTheme = true) {
        GameDetailContent(
            appID = 3357650,
            title = "PRAGMATA",
            steamDetailState = DetailLoadState.Loaded(details = previewAppDetails),
            pricesState = PricesLoadState.Loaded(prices = previewPriceGame),
            infoState = InfoLoadState.Loaded(info = previewGameInfo),
            onBack = {},
            onRetry = {},
            onRetryPrices = {},
            onRetryInfo = {},
            onOpenGameDetail = { _ -> },
            onOpenDemoStorePage = { _ -> },
            onOpenOffer = { _ -> },
        )
    }
}

@Preview
@Composable
private fun GameDetailContentLoadingPreview() {
    SteaLTheme(darkTheme = true) {
        GameDetailContent(
            appID = 1245620,
            title = "ELDEN RING",
            steamDetailState = DetailLoadState.Loading,
            pricesState = PricesLoadState.Hidden,
            infoState = InfoLoadState.Hidden,
            onBack = {},
            onRetry = {},
            onRetryPrices = {},
            onRetryInfo = {},
            onOpenGameDetail = { _ -> },
            onOpenDemoStorePage = { _ -> },
            onOpenOffer = { _ -> },
        )
    }
}

@Preview
@Composable
private fun GameDetailContentNotFoundPreview() {
    SteaLTheme(darkTheme = true) {
        GameDetailContent(
            appID = 1245620,
            title = "ELDEN RING",
            steamDetailState = DetailLoadState.NotFound,
            pricesState = PricesLoadState.Hidden,
            infoState = InfoLoadState.Hidden,
            onBack = {},
            onRetry = {},
            onRetryPrices = {},
            onRetryInfo = {},
            onOpenGameDetail = { _ -> },
            onOpenDemoStorePage = { _ -> },
            onOpenOffer = { _ -> },
        )
    }
}

@Preview
@Composable
private fun GameDetailContentFailedPreview() {
    SteaLTheme(darkTheme = true) {
        GameDetailContent(
            appID = 1245620,
            title = "ELDEN RING",
            steamDetailState = DetailLoadState.Failed(error = LoadError.Offline),
            pricesState = PricesLoadState.Hidden,
            infoState = InfoLoadState.Hidden,
            onBack = {},
            onRetry = {},
            onRetryPrices = {},
            onRetryInfo = {},
            onOpenGameDetail = { _ -> },
            onOpenDemoStorePage = { _ -> },
            onOpenOffer = { _ -> },
        )
    }
}
