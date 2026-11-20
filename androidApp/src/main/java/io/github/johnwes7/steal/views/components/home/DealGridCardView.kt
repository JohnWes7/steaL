package io.github.johnwes7.steal.views.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.previews.previewDeals
import io.github.johnwes7.steal.shared.models.itad.ItadDeal
import io.github.johnwes7.steal.views.components.shared.ArtworkImage
import io.github.johnwes7.steal.views.components.shared.DealCurrentPrice
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 首页网格里的小卡：整张是 2:3 竖版封面，折扣、标题和一条半透明的价格栏都压在封面上；整张卡可点。
 * A small card in the home grid: the whole card is a 2:3 portrait cover, with the discount, the title and a
 * translucent price bar on top of it; the whole card is clickable.
 *
 * 宽度由父布局给（网格里平分），高度是宽度的 1.5 倍，同一行的卡一样高。
 * The parent sets the width (an equal share of the grid row) and the height is 1.5 times that, so cards in a row
 * match.
 */
@Composable
fun DealGridCardView(
    deal: ItadDeal,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = deal.deal

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        // 2:3 和 Steam 库封面、ITAD boxart 一致，裁切时不丢内容。
        // 2:3 matches Steam library capsules and ITAD box art, so cropping loses nothing.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            ArtworkImage(url = deal.assets.portraitArtworkUrl, modifier = Modifier.matchParentSize())
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(coverScrim),
            )
            if (info.cut > 0) {
                DiscountBadge(
                    cut = info.cut,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
            ) {
                CoverTitle(
                    title = deal.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                )
                PriceBar(deal = deal)
            }
        }
    }
}

/**
 * 封面底部的价格栏：卡片色 85% 不透明，盖住封面又透出一点画面；左边现价、右边商店。
 * The price bar at the cover's bottom: the card color at 85% opacity, covering the art while letting a little show
 * through; current price at left, shop at right.
 */
@Composable
private fun PriceBar(deal: ItadDeal) {
    val info = deal.deal
    val barColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DealCurrentPrice(price = info.price, style = MaterialTheme.typography.titleMedium)
        Text(
            text = info.shop.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview
@Composable
private fun DealGridCardViewPreview() {
    SteaLTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            DealGridCardView(deal = previewDeals[3], onClick = {}, modifier = Modifier.width(180.dp))
        }
    }
}
