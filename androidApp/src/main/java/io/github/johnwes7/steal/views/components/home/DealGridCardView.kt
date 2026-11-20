package io.github.johnwes7.steal.views.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.previews.previewDeals
import io.github.johnwes7.steal.shared.models.itad.ItadDeal
import io.github.johnwes7.steal.views.components.shared.ArtworkImage
import io.github.johnwes7.steal.views.components.shared.DealCurrentPrice
import io.github.johnwes7.steal.views.components.shared.DealRegularPrice
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 首页网格里的小卡：竖版封面配折扣贴纸，下面是标题、价格和商店；整张卡可点。
 * A small card in the home grid: portrait box art with the discount sticker, then the title, prices and shop
 * underneath; the whole card is clickable.
 *
 * 宽度由父布局给（网格里平分），高度跟着宽度走，同一行的卡一样高。
 * The parent sets the width (an equal share of the grid row) and the height follows it, so cards in a row match.
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box {
            // 2:3 和 Steam 库封面、ITAD boxart 一致，裁切时不丢内容。
            // 2:3 matches Steam library capsules and ITAD box art, so cropping loses nothing.
            ArtworkImage(
                url = deal.assets.portraitArtworkUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            )
            if (info.cut > 0) {
                DiscountSticker(
                    cut = info.cut,
                    diameter = 48.dp,
                    textStyle = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 固定占两行：短标题的卡和长标题的卡一样高，同一行的价格对齐。
            // Always two lines tall, so short and long titles give equal cards and the prices in a row line up.
            Text(
                text = deal.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // 大字号下现价和原价放不下一行，原价换到下一行。
            // At large font sizes the regular price wraps under the current one.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DealCurrentPrice(
                    price = info.price,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.alignByBaseline(),
                )
                DealRegularPrice(
                    regular = info.regular,
                    price = info.price,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Text(
                text = info.shop.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview
@Composable
private fun DealGridCardViewPreview() {
    SteaLTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            DealGridCardView(deal = previewDeals[3], onClick = {}, modifier = Modifier.width(180.dp))
        }
    }
}
