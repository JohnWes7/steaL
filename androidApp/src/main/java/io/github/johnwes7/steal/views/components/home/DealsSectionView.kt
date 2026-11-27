package io.github.johnwes7.steal.views.components.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.previews.previewDealItems
import io.github.johnwes7.steal.shared.models.app.HomeDealItem
import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.viewmodels.GamesViewModel.DealLoadState
import io.github.johnwes7.steal.views.components.shared.ContentUnavailableView
import io.github.johnwes7.steal.views.components.shared.LoadErrorView
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * Hot deals 区块的内容：按 [state] 显示加载占位、失败、空列表，或一张大卡加一个网格。
 * The Hot deals section content: a loading placeholder, a failure, an empty list, or one large card plus a grid,
 * depending on [state].
 *
 * 失败和空列表的按钮都调 [onRetry]；点卡片调 [onSelect]，去哪里由页面决定。卡片上的心形按 [savedAppIDs] 显示实心或空心，
 * 点了调 [onToggleWishlist]；还没有 Steam appID 的卡心形变暗。
 * The failure and empty-list buttons both call [onRetry]; tapping a card calls [onSelect] and the page decides
 * where it leads. Each card's heart is filled or outlined from [savedAppIDs] and calls [onToggleWishlist] when
 * tapped; cards without a Steam appID dim it.
 *
 * 只有状态的种类变了（如加载中 → 有数据）才淡入淡出；同一种状态的更新（补上 appID 后的新 [DealLoadState.Loaded]）就地刷新，
 * 卡片不重建。
 * Only a change in the kind of state (such as loading to loaded) cross-fades; updates within the same kind (a new
 * [DealLoadState.Loaded] once appIDs resolve) refresh in place without rebuilding the cards.
 */
@Composable
fun DealsSectionView(
    state: DealLoadState,
    savedAppIDs: Set<Int>,
    onSelect: (HomeDealItem) -> Unit,
    onToggleWishlist: (HomeDealItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        label = "dealsSection",
        contentKey = { targetState -> targetState::class },
    ) { targetState ->
        // 读 targetState 而不是 state：淡出中的旧内容要继续显示旧状态。
        // Reads targetState rather than state, so the outgoing content keeps showing the old state while it fades.
        when (targetState) {
            DealLoadState.Loading -> DealsLoadingPlaceholder()
            is DealLoadState.Failed -> LoadErrorView(error = targetState.error, onRetry = onRetry)
            is DealLoadState.Loaded -> {
                if (targetState.items.isEmpty()) {
                    ContentUnavailableView(
                        iconRes = R.drawable.money_off_24px,
                        title = stringResource(R.string.deals_empty_title),
                        message = stringResource(R.string.deals_empty_message),
                        actionLabel = stringResource(R.string.action_refresh),
                        onAction = onRetry,
                    )
                } else {
                    DealsContent(
                        items = targetState.items,
                        savedAppIDs = savedAppIDs,
                        onSelect = onSelect,
                        onToggleWishlist = onToggleWishlist,
                    )
                }
            }
        }
    }
}

private val GRID_SPACING = 12.dp

/**
 * 网格每列的最小宽度：所有手机竖屏都是两列，横屏放得下更多列。
 * The minimum width of a grid column: two columns on every phone in portrait, more in landscape.
 */
private val MIN_GRID_CARD_WIDTH = 160.dp

@Composable
private fun DealsContent(
    items: List<HomeDealItem>,
    savedAppIDs: Set<Int>,
    onSelect: (HomeDealItem) -> Unit,
    onToggleWishlist: (HomeDealItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstItem = items.first()
    val otherItems = items.drop(1)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
    ) {
        DealCardView(
            deal = firstItem.deal,
            isSaved = isSaved(item = firstItem, savedAppIDs = savedAppIDs),
            isWishlistEnabled = firstItem.steamAppID != null,
            onClick = { onSelect(firstItem) },
            onToggleWishlist = { onToggleWishlist(firstItem) },
        )
        DealsGrid(
            items = otherItems,
            savedAppIDs = savedAppIDs,
            onSelect = onSelect,
            onToggleWishlist = onToggleWishlist,
        )
    }
}

/**
 * 这张卡的游戏在不在愿望单里；还没有 Steam appID 的一定不在。
 * Whether this card's game is on the wishlist; one without a Steam appID never is.
 */
private fun isSaved(item: HomeDealItem, savedAppIDs: Set<Int>): Boolean {
    val appID = item.steamAppID
    if (appID == null) {
        return false
    }
    return appID in savedAppIDs
}

@Composable
private fun DealsGrid(
    items: List<HomeDealItem>,
    savedAppIDs: Set<Int>,
    onSelect: (HomeDealItem) -> Unit,
    onToggleWishlist: (HomeDealItem) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnCount = gridColumnCount(availableWidth = maxWidth)
        val rows = items.chunked(columnCount)

        Column(verticalArrangement = Arrangement.spacedBy(GRID_SPACING)) {
            for (row in rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(GRID_SPACING)) {
                    for (item in row) {
                        // 按 UUID 认卡片：刷新后顺序变了，图片和点按状态跟着游戏走。
                        // Cards are keyed by UUID, so after a refresh reorders them, images and press state follow the game.
                        key(item.itadUUID) {
                            DealGridCardView(
                                deal = item.deal,
                                isSaved = isSaved(item = item, savedAppIDs = savedAppIDs),
                                isWishlistEnabled = item.steamAppID != null,
                                onClick = { onSelect(item) },
                                onToggleWishlist = { onToggleWishlist(item) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    // 最后一行不满时补空位，卡片宽度和上面几行一致。
                    // Pad a short last row with empty slots so its cards keep the same width as the rows above.
                    val emptySlots = columnCount - row.size
                    repeat(emptySlots) { _ ->
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun gridColumnCount(availableWidth: Dp): Int {
    val fittingColumns = ((availableWidth + GRID_SPACING) / (MIN_GRID_CARD_WIDTH + GRID_SPACING)).toInt()
    return maxOf(2, fittingColumns)
}

/**
 * 加载中的骨架：和真内容同样形状的色块轻轻明暗呼吸，数据回来时版面不跳。
 * The loading skeleton: blocks shaped like the real content, gently pulsing, so the layout doesn't jump when the
 * data arrives.
 */
@Composable
private fun DealsLoadingPlaceholder(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.deals_loading)
    val transition = rememberInfiniteTransition(label = "dealsPlaceholder")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 900), repeatMode = RepeatMode.Reverse),
        label = "dealsPlaceholderPulse",
    )
    val blockColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 在绘制阶段读透明度：呼吸时只重画，不重组。
            // Reads the alpha in the draw phase, so pulsing only redraws instead of recomposing.
            .graphicsLayer { alpha = pulse }
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 大卡整张就是封面。
            // The large card is all cover.
            val heroHeight = heroArtworkHeight(availableWidth = maxWidth)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .clip(MaterialTheme.shapes.extraLargeIncreased)
                    .background(blockColor),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GRID_SPACING)) {
            repeat(2) { _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(2f / 3f)
                        .clip(MaterialTheme.shapes.largeIncreased)
                        .background(blockColor),
                )
            }
        }
    }
}

@Preview
@Composable
private fun DealsSectionViewLoadedPreview() {
    SteaLTheme(darkTheme = true) {
        DealsSectionView(
            state = DealLoadState.Loaded(items = previewDealItems),
            savedAppIDs = emptySet(),
            onSelect = { _ -> },
            onToggleWishlist = { _ -> },
            onRetry = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun DealsSectionViewOtherStatesPreview() {
    SteaLTheme(darkTheme = true) {
        Column(modifier = Modifier.padding(16.dp)) {
            DealsSectionView(
                state = DealLoadState.Loading,
                savedAppIDs = emptySet(),
                onSelect = { _ -> },
                onToggleWishlist = { _ -> },
                onRetry = {},
            )
            DealsSectionView(
                state = DealLoadState.Failed(error = LoadError.Offline),
                savedAppIDs = emptySet(),
                onSelect = { _ -> },
                onToggleWishlist = { _ -> },
                onRetry = {},
            )
            DealsSectionView(
                state = DealLoadState.Loaded(items = emptyList()),
                savedAppIDs = emptySet(),
                onSelect = { _ -> },
                onToggleWishlist = { _ -> },
                onRetry = {},
            )
        }
    }
}
