package io.github.johnwes7.steal.views.components.home

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
 * 失败和空列表的按钮都调 [onRetry]；点卡片调 [onSelect]，去哪里由页面决定。
 * The failure and empty-list buttons both call [onRetry]; tapping a card calls [onSelect] and the page decides
 * where it leads.
 */
@Composable
fun DealsSectionView(
    state: DealLoadState,
    onSelect: (HomeDealItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    design: DealCardDesign = DealCardDesign.CoverFirst,
) {
    when (state) {
        DealLoadState.Loading -> DealsLoadingPlaceholder(modifier = modifier)
        is DealLoadState.Failed -> LoadErrorView(error = state.error, onRetry = onRetry, modifier = modifier)
        is DealLoadState.Loaded -> {
            if (state.items.isEmpty()) {
                ContentUnavailableView(
                    iconRes = R.drawable.money_off_24px,
                    title = stringResource(R.string.deals_empty_title),
                    message = stringResource(R.string.deals_empty_message),
                    actionLabel = stringResource(R.string.action_refresh),
                    onAction = onRetry,
                    modifier = modifier,
                )
            } else {
                DealsContent(items = state.items, design = design, onSelect = onSelect, modifier = modifier)
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
    design: DealCardDesign,
    onSelect: (HomeDealItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstItem = items.first()
    val otherItems = items.drop(1)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
    ) {
        DealHeroCard(deal = firstItem.deal, design = design, onClick = { onSelect(firstItem) })
        DealsGrid(items = otherItems, design = design, onSelect = onSelect)
    }
}

@Composable
private fun DealsGrid(
    items: List<HomeDealItem>,
    design: DealCardDesign,
    onSelect: (HomeDealItem) -> Unit,
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
                            DealGridCard(
                                deal = item.deal,
                                design = design,
                                onClick = { onSelect(item) },
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
            // 大卡 = 图片区 + 标题与价格（约 130dp）。
            // The large card is the artwork plus the title and prices, about 130dp.
            val heroHeight = heroArtworkHeight(availableWidth = maxWidth) + 130.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(blockColor),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GRID_SPACING)) {
            repeat(2) { _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(2f / 3f)
                        .clip(MaterialTheme.shapes.large)
                        .background(blockColor),
                )
            }
        }
    }
}

@Preview
@Composable
private fun DealsSectionViewLoadedPreview() {
    SteaLTheme {
        DealsSectionView(
            state = DealLoadState.Loaded(items = previewDealItems),
            onSelect = { _ -> },
            onRetry = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun DealsSectionViewOtherStatesPreview() {
    SteaLTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            DealsSectionView(state = DealLoadState.Loading, onSelect = { _ -> }, onRetry = {})
            DealsSectionView(state = DealLoadState.Failed(error = LoadError.Offline), onSelect = { _ -> }, onRetry = {})
            DealsSectionView(state = DealLoadState.Loaded(items = emptyList()), onSelect = { _ -> }, onRetry = {})
        }
    }
}
