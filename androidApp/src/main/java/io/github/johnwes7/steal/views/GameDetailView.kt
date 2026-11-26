package io.github.johnwes7.steal.views

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
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
import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.viewmodels.GameDetailViewModel
import io.github.johnwes7.steal.shared.viewmodels.GameDetailViewModel.DetailLoadState
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
    modifier: Modifier = Modifier,
) {
    // 1. 取这一页的 ViewModel：导航参数交给工厂，每次推入各有一个
    // 1. Get this page's view model: the navigation arguments go to the factory, one per push
    val appContainer = LocalAppContainer.current
    val viewModelFactory = appContainer.gameDetailViewModelFactory(appID = appID, uuid = uuid, title = title)
    val viewModel: GameDetailViewModel = viewModel(factory = viewModelFactory)
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()

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
        onBack = onBack,
        onRetry = { viewModel.retry() },
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
    onBack: () -> Unit,
    onRetry: () -> Unit,
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
                // 详情的各区块第二部分再做；第一部分 4d 先在这里放临时显示区。
                // The detail sections come in part two; step 4d of part one puts a temporary display here first.
                is DetailLoadState.Loaded -> {}
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

// MARK: - 预览 / Previews

@Preview
@Composable
private fun GameDetailContentLoadingPreview() {
    SteaLTheme(darkTheme = true) {
        GameDetailContent(
            title = "ELDEN RING",
            detailState = DetailLoadState.Loading,
            onBack = {},
            onRetry = {},
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
            onBack = {},
            onRetry = {},
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
            onBack = {},
            onRetry = {},
        )
    }
}
