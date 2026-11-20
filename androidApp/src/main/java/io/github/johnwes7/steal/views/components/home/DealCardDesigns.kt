package io.github.johnwes7.steal.views.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.previews.previewDeals
import io.github.johnwes7.steal.shared.models.itad.ItadDeal
import io.github.johnwes7.steal.shared.models.itad.ItadDealInfo
import io.github.johnwes7.steal.views.components.shared.ArtworkImage
import io.github.johnwes7.steal.views.components.shared.DealCurrentPrice
import io.github.johnwes7.steal.views.components.shared.DealRegularPrice
import io.github.johnwes7.steal.views.theme.SteaLTheme

// 临时文件：首页 deal 卡片的几种设计，用户选定一种后，把它并回 DealCardView.kt / DealGridCardView.kt，删掉这个文件。
// Temporary file: candidate designs for the home deal cards; once the user picks one, it moves back into
// DealCardView.kt / DealGridCardView.kt and this file goes away.

/**
 * 首页 deal 卡片的候选设计。
 * The candidate designs for the home deal cards.
 */
enum class DealCardDesign(
    /**
     * 调试区按钮上的字。
     * The label on the debug chip.
     */
    val label: String,
) {
    /**
     * 封面优先、贴近 iOS：标题压在封面渐变上，蓝色胶囊折扣，小卡底部是磨砂价格条。
     * Cover first, close to iOS: the title on the cover's gradient, a blue pill discount, a frosted price band.
     */
    CoverFirst(label = "A"),

    /**
     * Material 3 卡片：标题压在封面上，价格和折扣在封面下的信息栏。
     * Material 3 cards: the title on the cover, with prices and discount in an info bar underneath.
     */
    InfoBar(label = "B"),

    /**
     * 全部压在封面上：折扣和价格合成一个价签块。
     * Everything on the cover: the discount and prices joined into one price tag.
     */
    PriceTag(label = "C"),
}

// MARK: - 对外入口 / Entry points

/**
 * 首页最上面的大卡，样子由 [design] 决定。
 * The home page's large lead card, styled by [design].
 */
@Composable
fun DealHeroCard(
    deal: ItadDeal,
    design: DealCardDesign,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (design) {
        DealCardDesign.CoverFirst -> CoverFirstHero(deal = deal, onClick = onClick, modifier = modifier)
        DealCardDesign.InfoBar -> InfoBarHero(deal = deal, onClick = onClick, modifier = modifier)
        DealCardDesign.PriceTag -> PriceTagHero(deal = deal, onClick = onClick, modifier = modifier)
    }
}

/**
 * 首页网格里的小卡，样子由 [design] 决定；宽度由父布局给。
 * A small card in the home grid, styled by [design]; the parent sets the width.
 */
@Composable
fun DealGridCard(
    deal: ItadDeal,
    design: DealCardDesign,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (design) {
        DealCardDesign.CoverFirst -> CoverFirstGrid(deal = deal, onClick = onClick, modifier = modifier)
        DealCardDesign.InfoBar -> InfoBarGrid(deal = deal, onClick = onClick, modifier = modifier)
        DealCardDesign.PriceTag -> PriceTagGrid(deal = deal, onClick = onClick, modifier = modifier)
    }
}

// MARK: - 共用 / Shared pieces

/**
 * 封面下半部压暗：白字标题在任何封面上都看得清，上半部的画面不受影响。
 * Darkens the lower part of the cover so the white title stays readable, leaving the upper artwork untouched.
 */
private val coverScrim = Brush.verticalGradient(
    0.3f to Color.Transparent,
    0.6f to Color.Black.copy(alpha = 0.4f),
    1f to Color.Black.copy(alpha = 0.88f),
)

/**
 * 更重的压暗：方案 C 在封面上放的东西多，暗区要更大。
 * A heavier scrim: design C puts more on the cover, so the dark area is larger.
 */
private val strongCoverScrim = Brush.verticalGradient(
    0.3f to Color.Transparent,
    0.65f to Color.Black.copy(alpha = 0.45f),
    1f to Color.Black.copy(alpha = 0.92f),
)

@Composable
private fun cardColors() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

/**
 * 压在封面上的文字的阴影：亮色封面上白字、绿字也看得清。
 * The shadow for text on covers, keeping white and green text legible on bright artwork.
 */
private val coverTextShadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 1f), blurRadius = 8f)

@Composable
private fun CoverTitle(title: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = style.copy(shadow = coverTextShadow),
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * 封面左上角的品质标签：半透明黑底、品牌绿字。
 * The quality chip at the cover's top left: brand-green text on translucent black.
 */
@Composable
private fun QualityChip(info: ItadDealInfo, modifier: Modifier = Modifier) {
    val labelRes = qualityLabelRes(info = info)
    if (labelRes == null) {
        return
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
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

// MARK: - A 封面优先 / A: cover first

@Composable
private fun CoverFirstHero(deal: ItadDeal, onClick: () -> Unit, modifier: Modifier) {
    val info = deal.deal
    val qualityLabelRes = qualityLabelRes(info = info)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = cardColors(),
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
                    DiscountPill(
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
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (qualityLabelRes != null) {
                        val qualityLabel = stringResource(qualityLabelRes).uppercase()
                        Text(
                            text = qualityLabel,
                            style = MaterialTheme.typography.labelMedium.copy(shadow = coverTextShadow),
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    CoverTitle(title = deal.title, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                DealCurrentPrice(
                    price = info.price,
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                )
                DealRegularPrice(
                    regular = info.regular,
                    price = info.price,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = info.shop.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CoverFirstGrid(deal: ItadDeal, onClick: () -> Unit, modifier: Modifier) {
    val info = deal.deal
    val coverUrl = deal.assets.portraitArtworkUrl

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = cardColors(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            val coverHeight = maxHeight
            ArtworkImage(url = coverUrl, modifier = Modifier.matchParentSize())
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(coverScrim),
            )
            if (info.cut > 0) {
                DiscountPill(
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
                FrostedBand(coverUrl = coverUrl, coverHeight = coverHeight) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DealCurrentPrice(
                            price = info.price,
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        )
                        Text(
                            text = info.shop.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 蓝色胶囊折扣（同 iOS）。
 * The blue pill discount, as on iOS.
 */
@Composable
private fun DiscountPill(cut: Int, style: TextStyle, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
    ) {
        Text(
            text = stringResource(R.string.deal_discount_badge, cut),
            style = style,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/**
 * 磨砂价格条：同一张封面再画一次并模糊，只露出这一截，像压在封面上的毛玻璃。
 * The frosted price band: the same cover drawn again and blurred, showing only this strip, like frosted glass.
 *
 * 模糊要 Android 12 以上，更早的系统只剩半透明黑底。
 * Blur needs Android 12 or later; earlier versions only get the translucent black.
 */
@Composable
private fun FrostedBand(coverUrl: String?, coverHeight: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds(),
    ) {
        // 模糊的封面和上面的封面一样大、底边对齐，画面才接得上。
        // The blurred cover matches the real one in size and is bottom-aligned, so the artwork lines up.
        ArtworkImage(
            url = coverUrl,
            modifier = Modifier
                .matchParentSize()
                .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                .height(coverHeight)
                .blur(radius = 20.dp),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.3f)),
        )
        // 顶边一条细亮线，像玻璃的边缘。
        // A thin light line along the top edge, like the rim of the glass.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.12f)),
        )
        content()
    }
}

// MARK: - B 信息栏 / B: info bar

@Composable
private fun InfoBarHero(deal: ItadDeal, onClick: () -> Unit, modifier: Modifier) {
    val info = deal.deal

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = cardColors(),
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
                QualityChip(
                    info = info,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                )
                CoverTitle(
                    title = deal.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DealCurrentPrice(price = info.price, style = MaterialTheme.typography.headlineSmall)
                    if (info.cut > 0) {
                        DiscountTonalBadge(cut = info.cut)
                    }
                }
                DealRegularPrice(
                    regular = info.regular,
                    price = info.price,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
            ) {
                Text(
                    text = info.shop.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoBarGrid(deal: ItadDeal, onClick: () -> Unit, modifier: Modifier) {
    val info = deal.deal

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = cardColors(),
    ) {
        // 3:4 比整张 2:3 封面短一截，加上信息栏后整张卡和 iOS 的小卡差不多高。
        // 3:4 is shorter than the full 2:3 cover, so with the info bar the card ends up about as tall as on iOS.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
        ) {
            ArtworkImage(url = deal.assets.portraitArtworkUrl, modifier = Modifier.matchParentSize())
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(coverScrim),
            )
            CoverTitle(
                title = deal.title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DealCurrentPrice(
                price = info.price,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (info.cut > 0) {
                DiscountTonalBadge(cut = info.cut)
            }
        }
    }
}

/**
 * 浅蓝底、蓝字的折扣标签（Material 的 tonal 样式）。
 * The discount as a tonal label: blue text on a light blue fill.
 */
@Composable
private fun DiscountTonalBadge(cut: Int) {
    val brandBlue = MaterialTheme.colorScheme.secondary
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = brandBlue.copy(alpha = 0.18f),
        contentColor = brandBlue,
    ) {
        Text(
            text = stringResource(R.string.deal_discount_badge, cut),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

// MARK: - C 价签块 / C: price tag

@Composable
private fun PriceTagHero(deal: ItadDeal, onClick: () -> Unit, modifier: Modifier) {
    val info = deal.deal

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = cardColors(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 字都压在图上，图比 A、B 高一点，给标题和价签留地方。
            // Everything sits on the image, so it is a little taller than in A and B to make room.
            val artworkHeight = minOf(maxWidth * 0.62f, 240.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(artworkHeight),
            ) {
                ArtworkImage(url = deal.assets.wideArtworkUrl, modifier = Modifier.matchParentSize())
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(strongCoverScrim),
                )
                QualityChip(
                    info = info,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CoverTitle(title = deal.title, style = MaterialTheme.typography.titleLarge)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = info.shop.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        PriceTagBlock(info = info, isLarge = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceTagGrid(deal: ItadDeal, onClick: () -> Unit, modifier: Modifier) {
    val info = deal.deal

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = cardColors(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            ArtworkImage(url = deal.assets.portraitArtworkUrl, modifier = Modifier.matchParentSize())
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(strongCoverScrim),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoverTitle(title = deal.title, style = MaterialTheme.typography.titleSmall)
                PriceTagBlock(info = info, isLarge = false)
            }
        }
    }
}

/**
 * 价签块：左边蓝底的折扣，右边深色底的原价（划线）和现价。
 * The price tag: the discount on blue at left, the struck regular price and current price on dark at right.
 */
@Composable
private fun PriceTagBlock(info: ItadDealInfo, isLarge: Boolean) {
    val typography = MaterialTheme.typography
    val discountStyle = if (isLarge) typography.titleLarge else typography.titleSmall
    val priceStyle = if (isLarge) typography.titleLarge else typography.titleSmall
    val regularStyle = if (isLarge) typography.labelMedium else typography.labelSmall
    val horizontalPadding = if (isLarge) 12.dp else 8.dp

    // 两段一样高：折扣段撑满价格段的高度。
    // Both segments share one height: the discount stretches to the price segment's height.
    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        if (info.cut > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.secondary)
                    .padding(horizontal = horizontalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.deal_discount_badge, info.cut),
                    style = discountStyle,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondary,
                )
            }
        }
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = horizontalPadding, vertical = 4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            DealRegularPrice(regular = info.regular, price = info.price, style = regularStyle)
            DealCurrentPrice(price = info.price, style = priceStyle)
        }
    }
}

// MARK: - 预览 / Previews

@Preview(widthDp = 760)
@Composable
private fun DealCardDesignsPreview() {
    SteaLTheme(darkTheme = true) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            for (design in DealCardDesign.entries) {
                Column(
                    modifier = Modifier.width(230.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DealHeroCard(deal = previewDeals[1], design = design, onClick = {})
                    DealGridCard(deal = previewDeals[3], design = design, onClick = {})
                }
            }
        }
    }
}
