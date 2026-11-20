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
 * 首页最上面的大卡：整张是横幅封面，折扣、品质标签、标题、商店压在渐变上，价格在绿色容器的胶囊里；整张卡可点。
 * The home page's large lead card: the whole card is the wide cover, with the discount, quality tag, title and shop
 * on its gradient and the prices in a green container pill; the whole card is clickable.
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
        shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val artworkHeight = heroArtworkHeight(availableWidth = maxWidth)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(artworkHeight),
            ) {
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
                            .padding(14.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QualityTag(info = info)
                    CoverTitle(title = deal.title, style = MaterialTheme.typography.titleLarge)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = info.shop.name,
                            style = MaterialTheme.typography.labelLarge.copy(shadow = coverTextShadow),
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        PricePill(info = info)
                    }
                }
            }
        }
    }
}

/**
 * 大卡（也就是封面）的高度：宽度的 0.66 倍，比横幅原比例高一点给压在上面的字留地方；横屏太宽时封顶，免得一张图占满整屏。
 * The lead card's (that is, the cover's) height: 0.66 of the width, a little taller than the banner's own ratio to
 * make room for the text on it; capped on wide landscape screens so one image doesn't fill the screen.
 */
internal fun heroArtworkHeight(availableWidth: Dp): Dp {
    val coverHeight = availableWidth * HERO_HEIGHT_TO_WIDTH
    return minOf(coverHeight, MAX_HERO_ARTWORK_HEIGHT)
}

private const val HERO_HEIGHT_TO_WIDTH = 0.66f

private val MAX_HERO_ARTWORK_HEIGHT = 250.dp

/**
 * 绿色容器色的价格胶囊：现价在前、划线原价在后，都用配对的文字色。
 * The price pill on the green container: current price then struck regular price, both in the paired content color.
 */
@Composable
private fun PricePill(info: ItadDealInfo) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DealCurrentPrice(
                price = info.price,
                style = MaterialTheme.typography.titleLarge,
                color = onContainer,
                modifier = Modifier.alignByBaseline(),
            )
            DealRegularPrice(
                regular = info.regular,
                price = info.price,
                style = MaterialTheme.typography.labelMedium,
                color = onContainer.copy(alpha = 0.7f),
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/**
 * 品质标签：绿色容器配绿色文字（Material 的 tonal 样式）；没有标签时什么都不画。
 * The quality tag: green text on a green container (Material's tonal style); draws nothing when there is no tag.
 */
@Composable
private fun QualityTag(info: ItadDealInfo) {
    val labelRes = qualityLabelRes(info = info)
    if (labelRes == null) {
        return
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
 * 品质标签的文字，依次看史低、近三个月最低、Steam 激活；都不是时为 `null`。
 * The quality tag's text, checking all-time low, three-month low and Steam activation in turn; `null` when none
 * applies.
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
