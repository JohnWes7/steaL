package io.github.johnwes7.steal.views.components.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * 首页最上面的大卡：横幅封面上压着折扣、品质标签和标题，封面下一行是价格与商店；整张卡可点。
 * The home page's large lead card: the discount, quality tag and title sit on the wide cover, with the prices and
 * shop in a row underneath; the whole card is clickable.
 */
@Composable
fun DealCardView(
    deal: ItadDeal,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = deal.deal

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val artworkHeight = heroArtworkHeight(availableWidth = maxWidth)
            HeroCover(
                deal = deal,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(artworkHeight),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                DealCurrentPrice(price = info.price, style = MaterialTheme.typography.headlineSmall)
                DealRegularPrice(
                    regular = info.regular,
                    price = info.price,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = info.shop.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 180.dp),
            )
        }
    }
}

/**
 * 大卡封面的高度：按 `banner600` 的原比例（600 × 344）不裁切，横屏太宽时封顶，免得一张图占满整屏。
 * The lead card's cover height: `banner600`'s own ratio (600 × 344) so nothing is cropped, capped on wide
 * landscape screens so one image doesn't fill the screen.
 */
internal fun heroArtworkHeight(availableWidth: Dp): Dp {
    val bannerHeight = availableWidth * BANNER_HEIGHT_TO_WIDTH
    return minOf(bannerHeight, MAX_HERO_ARTWORK_HEIGHT)
}

private const val BANNER_HEIGHT_TO_WIDTH = 344f / 600f

private val MAX_HERO_ARTWORK_HEIGHT = 220.dp

@Composable
private fun HeroCover(deal: ItadDeal, modifier: Modifier = Modifier) {
    val info = deal.deal

    Box(modifier = modifier) {
        ArtworkImage(url = deal.assets.wideArtworkUrl, modifier = Modifier.matchParentSize())
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(coverScrim),
        )
        if (info.cut > 0) {
            DiscountBadge(
                cut = info.cut,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            QualityTag(info = info)
            CoverTitle(title = deal.title, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/**
 * 品质标签：半透明黑底、品牌绿字，压在封面上也看得清；没有标签时什么都不画。
 * The quality tag: brand-green text on translucent black, legible on any cover; draws nothing when there is no tag.
 */
@Composable
private fun QualityTag(info: ItadDealInfo) {
    val labelRes = qualityLabelRes(info = info)
    if (labelRes == null) {
        return
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * 封面上的品质标签，依次看史低、近三个月最低、Steam 激活；都不是时为 `null`。
 * The quality tag on the cover, checking all-time low, three-month low and Steam activation in turn; `null` when
 * none applies.
 */
@StringRes
private fun qualityLabelRes(info: ItadDealInfo): Int? {
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
    SteaLTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            DealCardView(deal = previewDeals[1], onClick = {})
        }
    }
}
