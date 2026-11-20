package io.github.johnwes7.steal.views.components.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.previews.previewDeals
import io.github.johnwes7.steal.shared.models.itad.ItadDeal
import io.github.johnwes7.steal.shared.models.itad.ItadDealInfo
import io.github.johnwes7.steal.views.components.shared.ArtworkImage
import io.github.johnwes7.steal.views.components.shared.DealCurrentPrice
import io.github.johnwes7.steal.views.components.shared.DealRegularPrice
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 首页最上面的大卡：横幅大图上只放折扣贴纸和品质标签，标题、价格与商店在图下面；整张卡可点。
 * The home page's large lead card: the wide banner carries only the discount sticker and quality tag, with the
 * title, prices and shop underneath; the whole card is clickable.
 *
 * 标题不压在图上：横幅里本来就有游戏自己的 logo，再叠一行字会撞在一起，大字号下也排不开。
 * The title stays off the artwork: banners already carry the game's own logo, so an overlaid title would clash
 * with it and run out of room at large font sizes.
 */
@Composable
fun DealCardView(
    deal: ItadDeal,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val artworkHeight = heroArtworkHeight(availableWidth = maxWidth)
            HeroArtwork(deal = deal, modifier = Modifier.height(artworkHeight))
        }
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = deal.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            HeroPriceRow(info = deal.deal)
        }
    }
}

/**
 * 大卡图片区的高度：按 `banner600` 的原比例（600 × 344）不裁切，横屏太宽时封顶，免得一张图占满整屏。
 * The lead card's artwork height: `banner600`'s own ratio (600 × 344) so nothing is cropped, capped on wide
 * landscape screens so one image doesn't fill the screen.
 */
internal fun heroArtworkHeight(availableWidth: Dp): Dp {
    val bannerHeight = availableWidth * BANNER_HEIGHT_TO_WIDTH
    return minOf(bannerHeight, MAX_HERO_ARTWORK_HEIGHT)
}

private const val BANNER_HEIGHT_TO_WIDTH = 344f / 600f

private val MAX_HERO_ARTWORK_HEIGHT = 220.dp

@Composable
private fun HeroArtwork(deal: ItadDeal, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth()) {
        ArtworkImage(
            url = deal.assets.wideArtworkUrl,
            modifier = Modifier.matchParentSize(),
        )
        if (deal.deal.cut > 0) {
            DiscountSticker(
                cut = deal.deal.cut,
                diameter = 64.dp,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            )
        }
        val qualityLabelRes = qualityLabelRes(info = deal.deal)
        if (qualityLabelRes != null) {
            QualityTag(
                text = stringResource(qualityLabelRes),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            )
        }
    }
}

/**
 * 品质标签：品牌绿的小胶囊，深浅模式下压在图片上都看得清。
 * The quality tag: a small brand-green pill that stays legible on artwork in light and dark mode.
 */
@Composable
private fun QualityTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun HeroPriceRow(info: ItadDealInfo) {
    // 放得下时价格靠左、商店靠右；大字号下放不下，商店换到下一行。
    // Prices left and shop right when they fit; at large font sizes the shop wraps to the next line.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            DealCurrentPrice(price = info.price, style = MaterialTheme.typography.headlineSmall)
            DealRegularPrice(
                regular = info.regular,
                price = info.price,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ShopPill(name = info.shop.name)
    }
}

/**
 * 卖这个价的商店：`deals/v2` 每个游戏只给一个最低价，所以只有一家。
 * The shop selling at this price: `deals/v2` gives one best price per game, so there is only ever one.
 */
@Composable
private fun ShopPill(name: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/**
 * 图片上的品质标签，依次看史低、近三个月最低、Steam 激活；都不是时为 `null`。
 * The quality tag on the artwork, checking all-time low, three-month low and Steam activation in turn; `null`
 * when none applies.
 */
@StringRes
internal fun qualityLabelRes(info: ItadDealInfo): Int? {
    // ITAD 的 flag：H 是史低，N 是新史低。
    // ITAD's flag: H is an all-time low and N a new all-time low.
    if (info.flag == "H" || info.flag == "N" || info.isHistoricalLow) {
        return R.string.deal_quality_all_time_low
    }

    val threeMonthLow = info.historyLow3m
    if (threeMonthLow != null && info.price.amountInt <= threeMonthLow.amountInt) {
        return R.string.deal_quality_three_month_low
    }

    if (info.isSteamActivation) {
        return R.string.deal_quality_steam
    }
    return null
}

@Preview
@Composable
private fun DealCardViewPreview() {
    SteaLTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            DealCardView(deal = previewDeals[1], onClick = {})
        }
    }
}
