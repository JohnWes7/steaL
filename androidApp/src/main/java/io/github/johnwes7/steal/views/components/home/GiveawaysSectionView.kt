package io.github.johnwes7.steal.views.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.previews.previewGiveaways
import io.github.johnwes7.steal.previews.previewNow
import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.itad.ItadGiveaway
import io.github.johnwes7.steal.shared.viewmodels.GamesViewModel.GiveawaysLoadState
import io.github.johnwes7.steal.views.components.shared.LoadErrorView
import io.github.johnwes7.steal.views.theme.SteaLTheme
import kotlin.time.Clock
import kotlin.time.Instant

// MARK: - 区块 / Section

/**
 * Giveaways 区块的内容：按 [state] 往列表里放加载中、失败、空列表的提示，或每条 giveaway 一行。
 * The Giveaways section content: adds a loading, failure or empty-list item to the list for [state], or one row per
 * giveaway.
 *
 * 在 `LazyColumn` 的内容里调用，每条 giveaway 是列表的一个 item，只建出屏幕上的那几行。点一行调 [onSelect]，去哪里由页面
 * 决定；失败的 Retry 调 [onRetry]；倒计时按 [now] 算，预览传固定时间。item 的 key 都以 `giveaway` 开头，页面别用同样的 key。
 * Call it inside a `LazyColumn`'s content; each giveaway is its own list item, so only the rows on screen get built.
 * Tapping a row calls [onSelect] and the page decides where it leads; the failure's Retry calls [onRetry]; countdowns
 * are measured from [now], which previews pin to a fixed time. Every item key starts with `giveaway`, so the page
 * must not reuse such keys.
 */
fun LazyListScope.giveawaysSectionItems(
    state: GiveawaysLoadState,
    onSelect: (ItadGiveaway) -> Unit,
    onRetry: () -> Unit,
    now: Instant = Clock.System.now(),
) {
    when (state) {
        GiveawaysLoadState.Loading -> item(key = "giveawaysLoading") {
            GiveawaysLoading()
        }
        is GiveawaysLoadState.Failed -> item(key = "giveawaysError") {
            LoadErrorView(error = state.error, onRetry = onRetry)
        }
        is GiveawaysLoadState.Loaded -> {
            val giveaways = state.giveaways
            if (giveaways.isEmpty()) {
                item(key = "giveawaysEmpty") {
                    GiveawaysEmptyNote()
                }
            } else {
                items(
                    items = giveaways,
                    key = { giveaway -> "giveaway-${giveaway.id}" },
                    contentType = { _ -> "giveaway" },
                ) { giveaway ->
                    GiveawayRowView(
                        giveaway = giveaway,
                        onClick = { onSelect(giveaway) },
                        modifier = Modifier.animateItem(),
                        now = now,
                    )
                }
            }
        }
    }
}

// MARK: - 状态 / States

/**
 * 加载中：区块里居中一个加载指示器，不影响同页的其他区块。
 * Loading: a loading indicator centered in the section, leaving the other sections on the page untouched.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GiveawaysLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}

/**
 * 现在没有 giveaway 时的一行浅色提示，不给按钮：下拉就能刷新。
 * A single muted line when there are no giveaways right now, with no button since pull to refresh is available.
 */
@Composable
private fun GiveawaysEmptyNote() {
    Text(
        text = stringResource(R.string.giveaways_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

// MARK: - 预览 / Previews

/**
 * 把一种状态的区块放进和首页一样间距的列表里预览。
 * Previews the section for one state in a list spaced like the home page.
 */
@Composable
private fun GiveawaysSectionPreview(state: GiveawaysLoadState) {
    SteaLTheme(darkTheme = true) {
        LazyColumn(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            giveawaysSectionItems(state = state, onSelect = { _ -> }, onRetry = {}, now = previewNow)
        }
    }
}

@Preview(name = "Loaded", heightDp = 420)
@Composable
private fun GiveawaysSectionLoadedPreview() {
    GiveawaysSectionPreview(state = GiveawaysLoadState.Loaded(giveaways = previewGiveaways))
}

@Preview(name = "Loading")
@Composable
private fun GiveawaysSectionLoadingPreview() {
    GiveawaysSectionPreview(state = GiveawaysLoadState.Loading)
}

@Preview(name = "Failed")
@Composable
private fun GiveawaysSectionFailedPreview() {
    GiveawaysSectionPreview(state = GiveawaysLoadState.Failed(error = LoadError.Offline))
}

@Preview(name = "Empty")
@Composable
private fun GiveawaysSectionEmptyPreview() {
    GiveawaysSectionPreview(state = GiveawaysLoadState.Loaded(giveaways = emptyList()))
}
