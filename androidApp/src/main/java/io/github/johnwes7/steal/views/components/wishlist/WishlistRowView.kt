package io.github.johnwes7.steal.views.components.wishlist

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.previews.previewWishlistItems
import io.github.johnwes7.steal.previews.previewWishlistPrices
import io.github.johnwes7.steal.shared.models.app.WishlistItem
import io.github.johnwes7.steal.shared.models.itad.ItadPriceDeal
import io.github.johnwes7.steal.shared.viewmodels.WishlistViewModel.PriceLoadState
import io.github.johnwes7.steal.views.components.home.DiscountBadge
import io.github.johnwes7.steal.views.components.shared.ArtworkImage
import io.github.johnwes7.steal.views.components.shared.DealCurrentPrice
import io.github.johnwes7.steal.views.components.shared.DealRegularPrice
import io.github.johnwes7.steal.views.theme.SteaLTheme

// MARK: - 行 / Row

/**
 * 愿望单的一行：左边一条竖线（打折是主色、其余是淡色）、完整显示的封面、名称和价格（打折时前面是折扣）；整行可点。
 * One wishlist row: a leading bar (primary on sale, muted otherwise), the uncropped artwork, the name and the price
 * (led by the discount on sale); the whole row is clickable.
 *
 * [price] 由 `WishlistViewModel` 算好。底色是不透明的页面背景，左滑删除时下面的红色不会透出来。
 * [price] comes ready from `WishlistViewModel`. The background is the opaque page color, so the red underneath
 * doesn't show through while swiping to delete.
 */
@Composable
fun WishlistRowView(
    item: WishlistItem,
    price: PriceLoadState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barColor = if (isOnSale(price = price)) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                // 竖线按内容高度撑满，不是无限高。
                // The bar fills the content's height rather than growing without bound.
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(color = barColor, shape = CircleShape),
            )
            WishlistArtwork(
                item = item,
                modifier = Modifier.size(width = ARTWORK_WIDTH, height = ARTWORK_HEIGHT),
            )
            NameAndPrice(item = item, price = price, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * 封面的宽高比，同 Steam header 和 ITAD `banner400` 的 460:215。
 * The artwork's aspect ratio, matching the Steam header and ITAD `banner400` at 460:215.
 */
private const val ARTWORK_ASPECT_RATIO = 460f / 215f

/**
 * 封面宽度。
 * The artwork width.
 */
private val ARTWORK_WIDTH: Dp = 120.dp

/**
 * 封面按原比例完整显示时的高度。
 * The artwork's height when shown whole at its own ratio.
 */
private val ARTWORK_HEIGHT: Dp = ARTWORK_WIDTH / ARTWORK_ASPECT_RATIO

// MARK: - 组成部分 / Parts

/**
 * 封面：圆角、完整显示不裁图；尺寸由 [modifier] 决定。
 * The artwork, rounded and shown whole without cropping; [modifier] decides the size.
 *
 * 比例不同的旧图两侧会露出占位底色，同 iOS。
 * Older art in another ratio leaves the placeholder color at its sides, as on iOS.
 */
@Composable
private fun WishlistArtwork(
    item: WishlistItem,
    modifier: Modifier = Modifier,
) {
    ArtworkImage(
        url = item.headerImageURL,
        contentScale = ContentScale.Fit,
        modifier = modifier.clip(MaterialTheme.shapes.small),
    )
}

/**
 * 名称（最多两行）在上、价格在下。
 * The name (up to two lines) above the price.
 */
@Composable
private fun NameAndPrice(item: WishlistItem, price: PriceLoadState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.CenterVertically),
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        PriceLine(price = price)
    }
}

/**
 * 按价格状态显示：占位、价格、没价格、失败。
 * Shows the price state: a placeholder, the price, no price or a failure.
 */
@Composable
private fun PriceLine(price: PriceLoadState) {
    when (price) {
        PriceLoadState.Loading -> PricePlaceholder()
        is PriceLoadState.Loaded -> DealPrice(deal = price.deal)
        PriceLoadState.NoPrice -> Text(
            text = stringResource(R.string.wishlist_no_price),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PriceLoadState.Failed -> PriceFailed()
    }
}

/**
 * 打折时折扣 + 主色现价 + 划线原价；不打折只显示一个价格，用正文色（主色只留给打折）。
 * On sale: the discount, the price in primary and the struck-through regular price; otherwise a single price in the
 * body color (primary is kept for sales).
 *
 * 折扣不压在封面上，免得挡图。
 * The discount stays off the artwork so it doesn't cover the art.
 */
@Composable
private fun DealPrice(deal: ItadPriceDeal) {
    val isOnSale = deal.regular.amountInt > deal.price.amountInt
    val priceColor = if (isOnSale) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val cut = deal.cut

    // 字号不同的几样按竖直中线对齐。
    // Items of different sizes line up on their vertical centers.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (cut > 0) {
            DiscountBadge(cut = cut, style = MaterialTheme.typography.labelSmall)
        }
        DealCurrentPrice(price = deal.price, style = MaterialTheme.typography.titleSmall, color = priceColor)
        DealRegularPrice(regular = deal.regular, price = deal.price, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * 价格还没到时的占位：和价格同样高的色块轻轻呼吸，价格回来时行高不跳（同首页的骨架）。
 * The placeholder before the price arrives: a block as tall as the price, gently pulsing, so the row height doesn't
 * jump when the price lands (as the home page's skeleton).
 */
@Composable
private fun PricePlaceholder() {
    val transition = rememberInfiniteTransition(label = "wishlistPricePlaceholder")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 900), repeatMode = RepeatMode.Reverse),
        label = "wishlistPricePlaceholderPulse",
    )

    // 透明文字只用来占出和价格一样的高度。
    // The transparent text only takes up the price's height.
    Text(
        text = PLACEHOLDER_PRICE_TEXT,
        style = MaterialTheme.typography.titleSmall,
        color = Color.Transparent,
        modifier = Modifier
            // 在绘制阶段读透明度：呼吸时只重画，不重组。
            // Reads the alpha in the draw phase, so pulsing only redraws instead of recomposing.
            .graphicsLayer { alpha = pulse }
            .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraSmall),
    )
}

/**
 * 占位色块的宽度来自这串字，大约是一个价格的长度。
 * The placeholder block takes its width from this text, about as long as a price.
 */
private const val PLACEHOLDER_PRICE_TEXT = "A$ 00.00"

/**
 * 查价失败：警告图标 + 「Couldn't load price」，下拉刷新重试。
 * The price failed: a warning icon plus "Couldn't load price"; pull to refresh retries.
 */
@Composable
private fun PriceFailed() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.warning_24px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.wishlist_price_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// MARK: - 判断 / Checks

/**
 * 打折时的折扣百分比，其余情况是 `null`；直接用 ITAD 的 `cut`，同首页。
 * The discount percentage when on sale, otherwise `null`; ITAD's own `cut`, as on the home page.
 */
private fun discountCut(price: PriceLoadState): Int? {
    if (price !is PriceLoadState.Loaded) {
        return null
    }
    val cut = price.deal.cut
    if (cut <= 0) {
        return null
    }
    return cut
}

private fun isOnSale(price: PriceLoadState): Boolean {
    val cut = discountCut(price = price)
    return cut != null
}

// MARK: - 预览 / Previews

/**
 * 每种价格状态一行：样例愿望单和 [previewWishlistPrices] 按顺序配对。
 * One row per price state: the sample wishlist paired in order with [previewWishlistPrices].
 */
@Preview(heightDp = 560)
@Composable
private fun WishlistRowPreview() {
    val rows = previewWishlistItems.zip(previewWishlistPrices)

    SteaLTheme(darkTheme = true) {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            for ((item, price) in rows) {
                WishlistRowView(item = item, price = price, onClick = {})
            }
        }
    }
}
