package io.github.johnwes7.steal.views

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.views.components.shared.PageHeaderView
import io.github.johnwes7.steal.views.components.shared.PageScaffold
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * Wishlist tab 的根页面，对应 iOS `WishlistView`；目前只有顶栏的大头部，内容还没做。
 * The Wishlist tab's root screen, mirroring iOS `WishlistView`; only the large header in the top bar for now.
 */
@Composable
fun WishlistView(modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    PageScaffold(
        header = { PageHeaderView(title = stringResource(R.string.tab_wishlist)) },
        scrollState = listState,
        modifier = modifier,
    ) { contentPadding ->
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
        }
    }
}

@Preview
@Composable
private fun WishlistViewPreview() {
    SteaLTheme(darkTheme = true) {
        WishlistView()
    }
}
