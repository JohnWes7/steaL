package io.github.johnwes7.steal.views.components.home

import android.icu.text.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.previews.previewGiveaways
import io.github.johnwes7.steal.previews.previewNow
import io.github.johnwes7.steal.shared.models.itad.ItadGiveaway
import io.github.johnwes7.steal.views.components.shared.ArtworkImage
import io.github.johnwes7.steal.views.theme.SteaLTheme
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Instant

// MARK: - 行 / Row

/**
 * Giveaways 区块的一行卡片：左边缩略图（左上角 FREE），中间标题与商店，右边截止倒计时；整张卡可点。
 * One card row of the Giveaways section: the thumbnail on the left (FREE at its top left), the title and shop in the
 * middle, and the deadline countdown on the right; the whole card is clickable.
 *
 * 倒计时按 [now] 算，预览传固定时间。
 * The countdown is measured from [now], which previews pin to a fixed time.
 */
@Composable
fun GiveawayRowView(
    giveaway: ItadGiveaway,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now(),
) {
    val deadline = giveaway.deadline(now = now)
    val isExpiringSoon = giveaway.isExpiringSoon(now = now)
    val cardShape = MaterialTheme.shapes.large

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_ROW_HEIGHT)
                    .padding(start = THUMBNAIL_WIDTH + 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GiveawayTitleAndShop(
                    giveaway = giveaway,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                )
                if (deadline != null) {
                    DeadlineLabel(deadline = deadline, isExpiringSoon = isExpiringSoon)
                }
            }
            // 缩略图不参与决定行高：matchParentSize 取文字撑出来的高度，图片加载完行高不会跳；
            // wrapContentWidth 放开宽度后再定成固定宽、靠左。
            // The thumbnail never decides the row height: matchParentSize takes the height the text sets, so the
            // row doesn't jump when the image loads; wrapContentWidth releases the width so it can be fixed and
            // aligned to the start.
            GiveawayThumbnail(
                giveaway = giveaway,
                shape = cardShape,
                modifier = Modifier
                    .matchParentSize()
                    .wrapContentWidth(align = Alignment.Start)
                    .width(THUMBNAIL_WIDTH),
            )
        }
    }
}

/**
 * 缩略图的宽高比，同 ITAD `banner300` / `banner400` 与 Steam header 的 460:215。
 * The thumbnail's aspect ratio, matching ITAD `banner300` / `banner400` and the Steam header at 460:215.
 */
private const val THUMBNAIL_ASPECT_RATIO = 460f / 215f

/**
 * 缩略图宽度。
 * The thumbnail width.
 */
private val THUMBNAIL_WIDTH: Dp = 120.dp

/**
 * 最小行高：缩略图按原比例完整显示的高度；标题折两行时行更高，缩略图跟着拉高、左右裁掉一点。
 * The minimum row height: the thumbnail's height when shown whole at its own ratio; a two-line title makes the row
 * taller and the thumbnail stretches with it, cropping its sides a little.
 */
private val MIN_ROW_HEIGHT: Dp = THUMBNAIL_WIDTH / THUMBNAIL_ASPECT_RATIO

// MARK: - 组成部分 / Parts

/**
 * 缩略图：按 [shape] 裁切，左上角压 FREE 徽章；尺寸由 [modifier] 决定。
 * The thumbnail, clipped to [shape], with the FREE badge at its top left; [modifier] decides the size.
 *
 * 传卡片的形状：左边两个角和卡片的圆角重合，右边两个角也是同样的圆角。
 * Takes the card's shape, so its left corners coincide with the card's and its right corners match them.
 */
@Composable
private fun GiveawayThumbnail(
    giveaway: ItadGiveaway,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clip(shape)) {
        ArtworkImage(url = giveaway.thumbnailUrl, modifier = Modifier.matchParentSize())
        FreeBadge(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp),
        )
    }
}

/**
 * FREE 徽章：绿色容器的小圆角标签，和首页的价格胶囊同一组颜色。
 * The FREE badge: a small rounded label on the green container, the same color pair as the home price capsule.
 */
@Composable
private fun FreeBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = stringResource(R.string.giveaway_free_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * 标题（最多两行）与商店名。
 * The title (up to two lines) and shop name.
 */
@Composable
private fun GiveawayTitleAndShop(
    giveaway: ItadGiveaway,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = giveaway.displayTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = shopName(giveaway = giveaway),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 截止倒计时的胶囊：平时是浅底绿字，48 小时内换成实心的绿色容器。
 * The deadline countdown capsule: green text on a light container normally, the solid green container within
 * 48 hours.
 */
@Composable
private fun DeadlineLabel(
    deadline: ItadGiveaway.Deadline,
    isExpiringSoon: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = if (isExpiringSoon) colorScheme.primaryContainer else colorScheme.surfaceContainerHighest
    val contentColor = if (isExpiringSoon) colorScheme.onPrimaryContainer else colorScheme.primary

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = deadlineText(deadline = deadline),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

// MARK: - 文字 / Text

/**
 * 商店名；没有商店（如游戏内兑换码）时是「Redeem code」。
 * The shop name, or "Redeem code" when there is no shop (such as an in-game code).
 */
@Composable
private fun shopName(giveaway: ItadGiveaway): String {
    val shop = giveaway.shop ?: return stringResource(R.string.giveaway_redeem_code)
    return shop.name
}

/**
 * 倒计时的文字：「Today」「3d left」，或按当前语言写的月日（`Dec 2` / `12月2日`）。
 * The countdown text: "Today", "3d left", or the month and day in the current language (`Dec 2` / `12月2日`).
 */
@Composable
private fun deadlineText(deadline: ItadGiveaway.Deadline): String {
    return when (deadline) {
        ItadGiveaway.Deadline.Today -> stringResource(R.string.giveaway_ends_today)
        is ItadGiveaway.Deadline.DaysLeft -> stringResource(R.string.giveaway_days_left, deadline.days)
        is ItadGiveaway.Deadline.OnDate -> formattedMonthDay(instant = deadline.expiry)
    }
}

/**
 * 把 [instant] 按当前语言和手机时区写成「月 日」。
 * Formats [instant] as month and day in the current language and the phone's time zone.
 */
@Composable
private fun formattedMonthDay(instant: Instant): String {
    // 1. 取当前语言：语言设置变了会跟着重组
    // 1. Read the current locale: a language change recomposes
    val locale = LocalConfiguration.current.locales[0]

    // 2. 每种语言建一次格式器
    // 2. Build one formatter per locale
    val formatter = remember(locale) {
        DateFormat.getInstanceForSkeleton(DateFormat.ABBR_MONTH_DAY, locale)
    }

    // 3. 格式化
    // 3. Format
    val date = Date(instant.toEpochMilliseconds())
    return formatter.format(date)
}

// MARK: - 预览 / Previews

@Preview(heightDp = 420)
@Composable
private fun GiveawayRowViewPreview() {
    SteaLTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (giveaway in previewGiveaways) {
                GiveawayRowView(giveaway = giveaway, onClick = {}, now = previewNow)
            }
        }
    }
}
