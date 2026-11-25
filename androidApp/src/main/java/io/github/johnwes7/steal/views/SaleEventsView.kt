package io.github.johnwes7.steal.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnwes7.steal.LocalAppContainer
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.previews.previewNow
import io.github.johnwes7.steal.previews.previewSaleEventList
import io.github.johnwes7.steal.shared.config.AppLog
import io.github.johnwes7.steal.shared.models.saleevents.SaleEvent
import io.github.johnwes7.steal.shared.viewmodels.SaleEventsViewModel
import io.github.johnwes7.steal.shared.viewmodels.SaleEventsViewModel.SaleEventsLoadState
import io.github.johnwes7.steal.views.components.sales.saleEventSectionItems
import io.github.johnwes7.steal.views.components.shared.ContentUnavailableView
import io.github.johnwes7.steal.views.components.shared.LoadErrorView
import io.github.johnwes7.steal.views.components.shared.PageHeaderView
import io.github.johnwes7.steal.views.components.shared.PageScaffold
import io.github.johnwes7.steal.views.theme.SteaLTheme
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * Sales tab 的根页面：各商店的促销活动，进行中的在最上面，其余按开始月份分段；点一行在 App 外打开来源网页。
 * The Sales tab's root screen: store sale events, ongoing ones first and the rest by start month; tapping a row
 * opens its source page outside the app.
 */
@Composable
fun SaleEventsView(modifier: Modifier = Modifier) {
    // 1. 取这一页的 ViewModel，收集它的状态
    // 1. Get this page's view model and collect its state
    val viewModel: SaleEventsViewModel = viewModel(factory = LocalAppContainer.current.saleEventsViewModelFactory)
    val saleState by viewModel.saleState.collectAsStateWithLifecycle()

    // 2. 页面出现时让 ViewModel 开始加载，加载过就不再请求
    // 2. Ask the view model to start loading when the page appears; it won't refetch once loaded
    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
    }

    // 3. 取打开外部链接用的 UriHandler：来源网页在 App 外面打开
    // 3. Get the UriHandler for external links: source pages open outside the app
    val uriHandler = LocalUriHandler.current

    // 4. 状态和操作交给内容函数显示
    // 4. Hand the state and actions to the content function
    SaleEventsContent(
        saleState = saleState,
        onOpenEvent = { event -> openSourcePage(uriHandler = uriHandler, url = event.sourceUrl) },
        onRetry = { viewModel.retry() },
        modifier = modifier,
    )
}

/**
 * 在 App 外打开活动的来源网页；手机上没有 App 能打开时只记日志。
 * Opens an event's source page outside the app; only logs when nothing on the phone can open it.
 */
private fun openSourcePage(uriHandler: UriHandler, url: String) {
    try {
        uriHandler.openUri(url)
    } catch (error: IllegalArgumentException) {
        AppLog.saleEvents.e(error) { "打不开来源网页 / Can't open the source page: $url" }
    }
}

/**
 * 页面内容：只接状态和回调，预览直接用它。
 * The screen content, taking only state and callbacks, so previews can use it directly.
 *
 * 分段在这里用 [now] 和 [timeZone] 现场算，不缓存：每次重组都是当前时间。
 * Sections are computed here from [now] and [timeZone] without caching, so every recomposition uses the current
 * time.
 */
@Composable
private fun SaleEventsContent(
    saleState: SaleEventsLoadState,
    onOpenEvent: (SaleEvent) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    // 顶栏在列表滚离顶部时换色，所以列表的滚动状态要交给骨架。
    // The top bar changes color once the list leaves its top, so the scaffold gets the list's scroll state.
    val listState = rememberLazyListState()

    PageScaffold(
        header = { PageHeaderView(title = stringResource(R.string.tab_sales)) },
        scrollState = listState,
        modifier = modifier,
    ) { contentPadding ->
        // 骨架给的留白加在列表里面，内容才会滚到两条栏后面；再加上列表自己的边距。
        // The scaffold's padding goes inside the list so content scrolls behind both bars, plus the list's own margins.
        val listPadding = contentPadding + PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp)

        // 列表一直在，按状态放 item：进程被杀后恢复的旧滚动位置在加载中只能落到唯一的那一项，新数据从顶部开始；
        // 活动行是加进已布局过的列表，animateItem 才会让它们淡入。
        // The list is always there, with items chosen by state: a scroll position restored after process death can
        // only land on the single loading item, so fresh data starts at the top; event rows join a list that has
        // already been laid out, which is what lets animateItem fade them in.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = listPadding,
        ) {
            when (saleState) {
                SaleEventsLoadState.Loading -> item(key = "saleEventsLoading") {
                    CenteredPageItem {
                        SaleEventsLoading()
                    }
                }
                is SaleEventsLoadState.Failed -> item(key = "saleEventsError") {
                    CenteredPageItem {
                        LoadErrorView(error = saleState.error, onRetry = onRetry)
                    }
                }
                is SaleEventsLoadState.Loaded -> {
                    val sections = saleState.eventList.sections(now = now, timeZone = timeZone)
                    if (sections.isEmpty()) {
                        item(key = "saleEventsEmpty") {
                            CenteredPageItem {
                                ContentUnavailableView(
                                    iconRes = R.drawable.calendar_month_24px,
                                    title = stringResource(R.string.sale_events_empty_title),
                                    message = stringResource(R.string.sale_events_empty_message),
                                )
                            }
                        }
                    } else {
                        for (section in sections) {
                            saleEventSectionItems(
                                section = section,
                                onSelect = onOpenEvent,
                                now = now,
                                timeZone = timeZone,
                            )
                        }
                        item(key = "disclaimer") {
                            SaleEventsDisclaimer(modifier = Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }
}

/**
 * 列表末尾的免责说明：日期是 AI 整理的，可能有误。
 * The disclaimer at the end of the list: the dates are gathered by AI and may be wrong.
 */
@Composable
private fun SaleEventsDisclaimer(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.sale_events_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
    )
}

/**
 * 加载中的指示器。
 * The loading indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SaleEventsLoading() {
    LoadingIndicator()
}

/**
 * 列表里占满一屏、内容居中的一项，放加载中、失败和空列表；出现和消失时淡入淡出。
 * A list item that fills one screen with its content centered, for loading, failure and the empty list; it fades
 * in and out.
 *
 * 一屏是列表的可见区域减去 contentPadding，所以内容居中在两条栏之间。
 * One screen is the list's viewport minus its contentPadding, so the content centers between the two bars.
 */
@Composable
private fun LazyItemScope.CenteredPageItem(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .animateItem()
            .fillParentMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// MARK: - 预览 / Previews

@Preview
@Composable
private fun SaleEventsContentLoadedPreview() {
    SteaLTheme(darkTheme = true) {
        SaleEventsContent(
            saleState = SaleEventsLoadState.Loaded(eventList = previewSaleEventList),
            onOpenEvent = { _ -> },
            onRetry = {},
            now = previewNow,
        )
    }
}

@Preview
@Composable
private fun SaleEventsContentLoadingPreview() {
    SteaLTheme(darkTheme = true) {
        SaleEventsContent(
            saleState = SaleEventsLoadState.Loading,
            onOpenEvent = { _ -> },
            onRetry = {},
            now = previewNow,
        )
    }
}
