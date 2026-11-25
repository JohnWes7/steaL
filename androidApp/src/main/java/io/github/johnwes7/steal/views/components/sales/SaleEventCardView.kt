package io.github.johnwes7.steal.views.components.sales

import android.icu.text.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.previews.previewNow
import io.github.johnwes7.steal.previews.previewSaleEventList
import io.github.johnwes7.steal.shared.models.saleevents.SaleEvent
import io.github.johnwes7.steal.views.theme.SteaLTheme
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * 进行中活动的大卡片：商店、名字、还剩几天，波浪进度条是活动已经过去了多少，下面是起止日期；整张卡可点。
 * The big card for an ongoing event: store, name and days left, a wavy bar for how much of the event has passed,
 * and the start and end dates below it; the whole card is clickable.
 *
 * 还剩几天和进度都按 [now] 算，天数是 [timeZone] 里的日历日；预览传固定时间。
 * Days left and progress are measured from [now], with days counted as calendar days in [timeZone]; previews pin
 * the time.
 */
@Composable
fun SaleEventCardView(
    event: SaleEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val colorScheme = MaterialTheme.colorScheme
    val progress = event.elapsedFraction(now = now)
    val daysLeft = event.daysUntilEnd(now = now, timeZone = timeZone)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // 商店 + 外链图标：离开 App 的提示放在右上角。
            // Store and external-link icon: the hint that it leaves the app sits top right.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = saleEventStoreName(store = event.store),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.open_in_new_24px),
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = event.name,
                style = MaterialTheme.typography.titleLarge,
                color = colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = saleEventEndsText(daysLeft = daysLeft),
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Text(
                    text = formattedDate(instant = event.start, skeleton = DateFormat.ABBR_MONTH_DAY),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formattedDate(instant = event.end, skeleton = DateFormat.ABBR_MONTH_DAY),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SaleEventCardViewPreview() {
    SteaLTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 前两条在 previewNow 时进行中，第二条名字长、折两行。
            // The first two are ongoing at previewNow; the second has a long, two-line name.
            SaleEventCardView(event = previewSaleEventList.events[0], onClick = {}, now = previewNow)
            SaleEventCardView(event = previewSaleEventList.events[1], onClick = {}, now = previewNow)
        }
    }
}
