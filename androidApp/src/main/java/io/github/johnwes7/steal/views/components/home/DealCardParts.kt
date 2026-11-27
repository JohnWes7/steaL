package io.github.johnwes7.steal.views.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.views.theme.ExtendedTheme
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 封面下半部压暗：白字标题压在任何封面上都看得清，上半部的画面不受影响。
 * Darkens the lower part of a cover so a white title stays readable, leaving the upper artwork untouched.
 */
internal val coverScrim: Brush = Brush.verticalGradient(
    0.3f to Color.Transparent,
    0.6f to Color.Black.copy(alpha = 0.4f),
    1f to Color.Black.copy(alpha = 0.88f),
)

/**
 * 封面顶部压暗：右上角没有底的白色心形在亮封面上也看得清，往下很快变透明。
 * Darkens the top of a cover so the white heart at the top right, which has no container, stays visible on bright
 * artwork, fading out quickly below.
 */
internal val coverTopScrim: Brush = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.45f),
    0.28f to Color.Transparent,
)

/**
 * 压在封面上的文字的阴影：亮色封面上也看得清。
 * The shadow for text on covers, keeping it legible on bright artwork.
 */
internal val coverTextShadow: Shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 1f), blurRadius = 8f)

/**
 * 压在封面渐变上的白色标题，最多两行。
 * A white title on the cover's gradient, at most two lines.
 */
@Composable
internal fun CoverTitle(
    title: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
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
 * 封面左上角的折扣：蓝色容器配蓝色文字的小圆角标签（Material 的 tonal 样式），写着「-55%」。
 * The discount at a cover's top left: a small rounded tonal label, blue text on a blue container, such as "-55%".
 *
 * [cut] 直接用 ITAD 给的值，不按价格重算，免得和服务端的舍入对不上。
 * [cut] is ITAD's own value rather than one recomputed from the prices, which could round differently.
 */
@Composable
internal fun DiscountBadge(
    cut: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = stringResource(R.string.deal_discount_badge, cut),
            style = style,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * 封面右上角的心形：已收藏是粉色实心，没收藏是白色空心；[isEnabled] 为 `false`（还没有 Steam appID）时变暗、点了没反应。
 * The heart at a cover's top right: solid pink when saved, a white outline otherwise; dimmed and inert when
 * [isEnabled] is `false` (no Steam appID yet).
 *
 * 变暗而不是隐藏：appID 一般几百毫秒就查到，隐藏的话心形会突然冒出来。
 * Dimmed rather than hidden: the appID usually arrives within a few hundred milliseconds, and a hidden heart would
 * pop in.
 */
@Composable
internal fun CoverWishlistButton(
    isSaved: Boolean,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 1. 图标和朗读文字
    // 1. The icon and the spoken label
    val iconRes = if (isSaved) R.drawable.favorite_fill1_24px else R.drawable.favorite_24px
    val descriptionRes = if (isSaved) R.string.action_remove_from_wishlist else R.string.action_add_to_wishlist

    // 2. 颜色：没有底，封面上一律用白色（不跟主题），已收藏用收藏粉；靠卡片画的 coverTopScrim 衬着
    // 2. Colors: no container, white on covers regardless of theme, the favorite pink when saved; the card's
    //    coverTopScrim sits behind it
    val colors = IconButtonDefaults.iconToggleButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = 0.4f),
        checkedContentColor = ExtendedTheme.colors.favorite,
    )

    IconToggleButton(
        checked = isSaved,
        onCheckedChange = { _ -> onToggle() },
        modifier = modifier,
        enabled = isEnabled,
        colors = colors,
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = stringResource(descriptionRes))
    }
}

@Preview
@Composable
private fun DealCardPartsPreview() {
    SteaLTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .size(width = 220.dp, height = 160.dp)
                .background(coverScrim),
        ) {
            DiscountBadge(
                cut = 67,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            )
            Row(modifier = Modifier.align(Alignment.TopEnd)) {
                CoverWishlistButton(isSaved = false, isEnabled = false, onToggle = {})
                CoverWishlistButton(isSaved = false, isEnabled = true, onToggle = {})
                CoverWishlistButton(isSaved = true, isEnabled = true, onToggle = {})
            }
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                CoverTitle(title = "No Man's Sky", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
