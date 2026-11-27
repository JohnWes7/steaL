package io.github.johnwes7.steal.views

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnwes7.steal.LocalAppContainer
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.previews.previewWishlistItems
import io.github.johnwes7.steal.shared.models.app.WishlistItem
import io.github.johnwes7.steal.shared.viewmodels.WishlistViewModel
import io.github.johnwes7.steal.views.components.shared.CenteredPageItem
import io.github.johnwes7.steal.views.components.shared.ContentUnavailableView
import io.github.johnwes7.steal.views.components.shared.PageHeaderView
import io.github.johnwes7.steal.views.components.shared.PageScaffold
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * Wishlist tab 的根页面，对应 iOS `WishlistView`；现在是调试用的列表，原样列出本机数据库里的每一行。
 * The Wishlist tab's root screen, mirroring iOS `WishlistView`; for now a debug list showing every row of the
 * on-device database as it is.
 */
@Composable
fun WishlistView(modifier: Modifier = Modifier) {
    val appContainer = LocalAppContainer.current
    val viewModel: WishlistViewModel = viewModel(factory = appContainer.wishlistViewModelFactory)
    val items by viewModel.items.collectAsStateWithLifecycle()

    WishlistContent(items = items, modifier = modifier)
}

/**
 * 页面内容：只接状态，预览直接用它。[items] 是 `null` 时还没读到数据库。
 * The screen content, taking only state, so previews can use it directly. [items] is `null` until the database has
 * been read.
 */
@Composable
private fun WishlistContent(items: List<WishlistItem>?, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    PageScaffold(
        header = { PageHeaderView(title = stringResource(R.string.tab_wishlist)) },
        scrollState = listState,
        modifier = modifier,
    ) { contentPadding ->
        // 列表一直在，读取中、空列表都是列表里的一项（理由同 Sales 页）。
        // The list is always there, with loading and empty as items in it (as on the Sales page).
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (items == null) {
                item(key = "loading") {
                    CenteredPageItem {
                        WishlistLoading()
                    }
                }
            } else if (items.isEmpty()) {
                item(key = "empty") {
                    CenteredPageItem {
                        ContentUnavailableView(
                            iconRes = R.drawable.favorite_24px,
                            title = stringResource(R.string.wishlist_empty_title),
                            message = stringResource(R.string.wishlist_empty_message),
                        )
                    }
                }
            } else {
                items(items = items, key = { item -> item.appID }) { item ->
                    WishlistDebugRow(item = item, modifier = Modifier.animateItem())
                }
            }
        }
    }
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

/**
 * 调试用的一行：名字，下面原样列出各个字段。
 * A debug row: the name, with every field listed as it is below it.
 *
 * 只给开发时看数据库存了什么，做正式的行时整个换掉，所以字段名直接写在代码里、不进 `strings.xml`。
 * Only for checking what the database holds during development and replaced whole by the real row, so the field
 * names are written in code rather than `strings.xml`.
 */
@Composable
private fun WishlistDebugRow(item: WishlistItem, modifier: Modifier = Modifier) {
    val debugText = debugDescription(item = item)
    ListItem(
        headlineContent = { Text(text = item.name) },
        supportingContent = { Text(text = debugText) },
        modifier = modifier,
    )
}

/**
 * 一行除名字以外的所有字段，每个字段一行。
 * Every field of a row except the name, one field per line.
 */
private fun debugDescription(item: WishlistItem): String {
    val knownLow = item.knownLow
    val knownLowText = if (knownLow == null) {
        "null"
    } else {
        "${knownLow.amount} ${knownLow.region.name}"
    }
    val lines = listOf(
        "appID: ${item.appID}",
        "itadID: ${item.itadID}",
        "isComingSoon: ${item.isComingSoon}",
        "releaseCheckedAt: ${item.releaseCheckedAt}",
        "knownLow: $knownLowText",
        "addedAt: ${item.addedAt}",
        "headerImageURL: ${item.headerImageURL}",
    )
    return lines.joinToString(separator = "\n")
}

// MARK: - 预览 / Previews

@Preview
@Composable
private fun WishlistContentPreview() {
    SteaLTheme(darkTheme = true) {
        WishlistContent(items = previewWishlistItems)
    }
}

@Preview
@Composable
private fun WishlistContentEmptyPreview() {
    SteaLTheme(darkTheme = true) {
        WishlistContent(items = emptyList())
    }
}
