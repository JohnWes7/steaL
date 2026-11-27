package io.github.johnwes7.steal.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnwes7.steal.LocalAppContainer
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.navigation.GameDetailRoute
import io.github.johnwes7.steal.previews.previewSearchItems
import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.steam.SteamSearchItem
import io.github.johnwes7.steal.shared.viewmodels.SearchViewModel
import io.github.johnwes7.steal.shared.viewmodels.SearchViewModel.SearchState
import io.github.johnwes7.steal.views.components.shared.ArtworkImage
import io.github.johnwes7.steal.views.components.shared.CenteredPageItem
import io.github.johnwes7.steal.views.components.shared.ContentUnavailableView
import io.github.johnwes7.steal.views.components.shared.LoadErrorView
import io.github.johnwes7.steal.views.components.shared.PageScaffold
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * Search tab 的根页面：顶栏是搜索框，下面是 Steam 商店的搜索结果；点一条推入详情页。
 * The Search tab's root screen: the top bar is the search field, with Steam store results below; tapping one pushes
 * the detail page.
 *
 * 这一页本身就是搜索页，不是点了再展开的搜索栏。输入框的文字由这一层持有（可保存、进程被杀后也恢复），每次变化交给
 * ViewModel，由它防抖后再搜。
 * The page itself is the search screen rather than a bar that expands when tapped. This layer holds the input text
 * (saveable, restored after process death too) and hands every change to the view model, which debounces before
 * searching.
 */
@Composable
fun SearchView(
    onOpenGameDetail: (GameDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 1. 取这一页的 ViewModel，收集它的状态
    // 1. Get this page's view model and collect its state
    val viewModel: SearchViewModel = viewModel(factory = LocalAppContainer.current.searchViewModelFactory)
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()

    // 2. 交进界面实际显示的那份翻译里的 Steam 语言代码：界面语言变了会再交一次，ViewModel 据此重搜
    // 2. Hand in the Steam language code from the translation the UI actually shows: a UI language change hands it
    //    in again and the view model searches again
    val steamLanguage = stringResource(R.string.steam_api_language)
    LaunchedEffect(steamLanguage) {
        viewModel.loadIfNeeded(steamLanguage = steamLanguage)
    }

    // 3. 输入框的文字：每次变化都交给 ViewModel
    // 3. The input text: every change goes to the view model
    val queryState = rememberTextFieldState()
    LaunchedEffect(queryState) {
        val texts = snapshotFlow { queryState.text.toString() }
        texts.collect { text -> viewModel.applyQuery(query = text) }
    }

    // 4. 状态和操作交给内容函数显示
    // 4. Hand the state and actions to the content function
    SearchContent(
        searchState = searchState,
        queryState = queryState,
        onOpenResult = { item ->
            val route = GameDetailRoute(appID = item.id, uuid = null, title = item.name)
            onOpenGameDetail(route)
        },
        modifier = modifier,
    )
}

/**
 * 页面内容：只接状态和回调，预览直接用它。
 * The screen content, taking only state and callbacks, so previews can use it directly.
 *
 * [focusesWhenEmpty] 为 `true` 时，输入框是空的就在出现时自动聚焦、弹出键盘；预览传 `false`。
 * When [focusesWhenEmpty] is `true`, an empty input field takes focus and raises the keyboard when it appears;
 * previews pass `false`.
 */
@Composable
private fun SearchContent(
    searchState: SearchState,
    queryState: TextFieldState,
    onOpenResult: (SteamSearchItem) -> Unit,
    modifier: Modifier = Modifier,
    focusesWhenEmpty: Boolean = true,
) {
    // 顶栏和搜索框都在列表滚离顶部时换色，所以列表的滚动状态要交给骨架和搜索框。
    // Both the top bar and the search field change color once the list leaves its top, so they get its scroll state.
    val listState = rememberLazyListState()
    val isScrolled = listState.canScrollBackward

    PageScaffold(
        header = {
            SearchField(
                queryState = queryState,
                isScrolled = isScrolled,
                focusesWhenEmpty = focusesWhenEmpty,
            )
        },
        scrollState = listState,
        modifier = modifier,
    ) { contentPadding ->
        // 骨架给的留白加在列表里面，内容才会滚到两条栏后面；再加上列表自己的边距。
        // The scaffold's padding goes inside the list so content scrolls behind both bars, plus the list's own margins.
        val ownPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp)

        // 键盘盖住 tab 栏、比它高：键盘弹出时底部再让出多出来的那段，被挡住的结果才滑得出来，空状态也居中在键盘上面。
        // The keyboard covers the tab bar and is taller: while it shows, the bottom clears the extra height so hidden
        // results can scroll into view and empty states center above the keyboard.
        val keyboardHeight = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val tabBarClearance = contentPadding.calculateBottomPadding()
        val keyboardExtra = (keyboardHeight - tabBarClearance).coerceAtLeast(0.dp)
        val keyboardPadding = PaddingValues(bottom = keyboardExtra)

        val listPadding = contentPadding + ownPadding + keyboardPadding

        // 列表一直在，按状态放 item（同 Sales 页），结果行才有淡入淡出。
        // The list is always there with items chosen by state (as on the Sales page), so result rows fade in and out.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (searchState) {
                SearchState.Idle -> item(key = "searchIdle") {
                    CenteredPageItem {
                        ContentUnavailableView(
                            iconRes = R.drawable.search_24px,
                            title = stringResource(R.string.search_idle_title),
                            message = stringResource(R.string.search_idle_message),
                        )
                    }
                }
                SearchState.Loading -> item(key = "searchLoading") {
                    CenteredPageItem {
                        SearchLoading()
                    }
                }
                is SearchState.NoResults -> item(key = "searchNoResults") {
                    CenteredPageItem {
                        ContentUnavailableView(
                            iconRes = R.drawable.search_24px,
                            title = stringResource(R.string.search_no_results_title, searchState.query),
                            message = stringResource(R.string.search_no_results_message),
                        )
                    }
                }
                // 不给重试：改一下搜索词就会再搜。
                // No retry: editing the query searches again.
                is SearchState.Failed -> item(key = "searchError") {
                    CenteredPageItem {
                        LoadErrorView(error = searchState.error, onRetry = null)
                    }
                }
                is SearchState.Loaded -> items(
                    items = searchState.items,
                    key = { item -> item.id },
                ) { item ->
                    SearchResultRow(
                        item = item,
                        onClick = { onOpenResult(item) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

// MARK: - 搜索框 / Search field

/**
 * 顶栏里的搜索框：M3 搜索栏的输入框，左边放大镜、有字时右边清除键，键盘上是搜索键。
 * The search field in the top bar: the M3 search bar's input field, with a magnifier at the start, a clear button
 * at the end when there is text, and a search key on the keyboard.
 *
 * 不画 `SearchBar` 本体：这一页一直是搜索状态，所以交给输入框的 `SearchBarState` 一开始就是展开的，获得焦点时也不会
 * 再展开出别的东西。底色同 M3 的 `AppBarWithSearch`：平时 `surfaceContainerHigh`，[isScrolled] 时
 * `surfaceContainerHighest`，和滚动后的顶栏分得开。[focusesWhenEmpty] 见 [SearchContent]。
 * The `SearchBar` itself is not drawn: this page is always in search mode, so the `SearchBarState` given to the
 * input field starts expanded, and focusing it expands nothing more. The container follows M3's `AppBarWithSearch`:
 * `surfaceContainerHigh` normally and `surfaceContainerHighest` when [isScrolled], so it stands apart from the
 * scrolled top bar. See [SearchContent] for [focusesWhenEmpty].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    queryState: TextFieldState,
    isScrolled: Boolean,
    focusesWhenEmpty: Boolean,
    modifier: Modifier = Modifier,
) {
    // 1. 一直展开的搜索栏状态
    // 1. A search bar state that stays expanded
    val searchBarState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)

    // 2. 出现时输入框是空的才自动聚焦：从详情页返回、切 tab 回来时框里有字，不弹键盘挡住结果
    // 2. Focus on appearing only when the field is empty: coming back from the detail page or another tab with text
    //    in it doesn't raise the keyboard over the results
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        val isEmpty = queryState.text.isEmpty()
        if (focusesWhenEmpty && isEmpty) {
            focusRequester.requestFocus()
        }
    }

    // 3. 底色：滚动后换成更亮一档
    // 3. Container color: one step lighter once scrolled
    val containerColor = searchFieldContainerColor(isScrolled = isScrolled)
    val colors = SearchBarDefaults.inputFieldColors(
        focusedContainerColor = containerColor,
        unfocusedContainerColor = containerColor,
    )

    // 4. 按键盘上的搜索键只收起键盘：边打字边出结果，不用另外提交
    // 4. The keyboard's search key only hides the keyboard: results arrive while typing, so there is nothing to submit
    val focusManager = LocalFocusManager.current
    val hasText = queryState.text.isNotEmpty()

    SearchBarDefaults.InputField(
        textFieldState = queryState,
        searchBarState = searchBarState,
        onSearch = { _ -> focusManager.clearFocus() },
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 16.dp)
            .focusRequester(focusRequester),
        placeholder = { Text(text = stringResource(R.string.search_placeholder)) },
        leadingIcon = {
            Icon(painter = painterResource(R.drawable.search_24px), contentDescription = null)
        },
        trailingIcon = if (hasText) {
            { SearchClearButton(queryState = queryState) }
        } else {
            null
        },
        colors = colors,
    )
}

/**
 * 搜索框的底色：平时是 M3 搜索栏的默认底色，滚动后用 `surfaceContainerHighest`（M3 `AppBarWithSearch` 的默认值）。
 * The search field's container: the M3 search bar's default normally, and `surfaceContainerHighest` once scrolled
 * (the default of M3's `AppBarWithSearch`).
 */
@Composable
private fun searchFieldContainerColor(isScrolled: Boolean): Color {
    if (isScrolled) {
        return MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val searchBarColors = SearchBarDefaults.colors()
    return searchBarColors.containerColor
}

/**
 * 清除输入的按钮，只在有字时出现。
 * The button that clears the input, shown only when there is text.
 */
@Composable
private fun SearchClearButton(queryState: TextFieldState) {
    IconButton(onClick = { queryState.clearText() }) {
        Icon(painter = painterResource(R.drawable.close_24px), contentDescription = null)
    }
}

// MARK: - 结果 / Results

/**
 * 一条搜索结果：左边 Steam 的横图，右边游戏名（最多两行）；整张卡可点。
 * One search result: Steam's capsule image on the start side and the game name (up to two lines) beside it; the
 * whole card is clickable.
 *
 * 版式同首页 Giveaways 的行：卡片底色 `surfaceContainerHigh`，横图四个角都用卡片的圆角，不参与决定行高。圆角比
 * Giveaways 小（`small`）：Steam 横图左上角有 DLC / Demo 的角标，大圆角会切掉它的一角。
 * Laid out like the home page's Giveaways rows: a `surfaceContainerHigh` card, a capsule image with the card's
 * rounded corners on all four sides that never decides the row height. The corners are smaller than the
 * Giveaways' (`small`): Steam capsules carry a DLC / Demo badge at the top left, and large corners cut into it.
 */
@Composable
private fun SearchResultRow(
    item: SteamSearchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = MaterialTheme.shapes.small

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_ROW_HEIGHT)
                    .padding(start = CAPSULE_WIDTH + 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                )
            }
            // 横图不参与决定行高：matchParentSize 取文字撑出来的高度，图片加载完行高不会跳（同 GiveawayRowView）。
            // 名字折两行时横图被拉高、左右要裁掉一截：靠左对齐裁右边，左上角的 DLC / Demo 角标才留得住。
            // The image never decides the row height: matchParentSize takes the height the text sets, so the row
            // doesn't jump when the image loads (as in GiveawayRowView). A two-line name stretches the image and its
            // sides must be cropped: aligning to the start crops the right side, keeping the DLC / Demo badge at the
            // top left.
            ArtworkImage(
                url = item.tinyImage,
                alignment = Alignment.TopStart,
                modifier = Modifier
                    .matchParentSize()
                    .wrapContentWidth(align = Alignment.Start)
                    .width(CAPSULE_WIDTH)
                    .clip(cardShape),
            )
        }
    }
}

/**
 * 加载中的指示器。
 * The loading indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchLoading() {
    LoadingIndicator()
}

/**
 * Steam 搜索横图（capsule）的宽高比，231:87。
 * The aspect ratio of Steam's search capsule image, 231:87.
 */
private const val CAPSULE_ASPECT_RATIO = 231f / 87f

/**
 * 横图宽度，同首页 Giveaways 的缩略图。
 * The capsule image width, the same as the home page's Giveaways thumbnails.
 */
private val CAPSULE_WIDTH: Dp = 120.dp

/**
 * 最小行高：横图按原比例完整显示的高度；名字折两行时行更高，横图跟着拉高、左右裁掉一点。
 * The minimum row height: the image's height when shown whole at its own ratio; a two-line name makes the row
 * taller and the image stretches with it, cropping its sides a little.
 */
private val MIN_ROW_HEIGHT: Dp = CAPSULE_WIDTH / CAPSULE_ASPECT_RATIO

// MARK: - 预览 / Previews

@Preview
@Composable
private fun SearchContentLoadedPreview() {
    SteaLTheme(darkTheme = true) {
        SearchContent(
            searchState = SearchState.Loaded(query = "counter-strike", items = previewSearchItems),
            queryState = TextFieldState(initialText = "counter-strike"),
            onOpenResult = { _ -> },
            focusesWhenEmpty = false,
        )
    }
}

@Preview
@Composable
private fun SearchContentIdlePreview() {
    SteaLTheme(darkTheme = true) {
        SearchContent(
            searchState = SearchState.Idle,
            queryState = TextFieldState(),
            onOpenResult = { _ -> },
            focusesWhenEmpty = false,
        )
    }
}

@Preview
@Composable
private fun SearchContentNoResultsPreview() {
    SteaLTheme(darkTheme = true) {
        SearchContent(
            searchState = SearchState.NoResults(query = "zzqqxx"),
            queryState = TextFieldState(initialText = "zzqqxx"),
            onOpenResult = { _ -> },
            focusesWhenEmpty = false,
        )
    }
}

@Preview
@Composable
private fun SearchContentFailedPreview() {
    SteaLTheme(darkTheme = true) {
        SearchContent(
            searchState = SearchState.Failed(error = LoadError.Offline),
            queryState = TextFieldState(initialText = "elden ring"),
            onOpenResult = { _ -> },
            focusesWhenEmpty = false,
        )
    }
}
