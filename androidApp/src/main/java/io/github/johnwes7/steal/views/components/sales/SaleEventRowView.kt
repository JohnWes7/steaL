package io.github.johnwes7.steal.views.components.sales

import android.icu.text.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.previews.previewSaleEventList
import io.github.johnwes7.steal.shared.models.saleevents.SaleEvent
import io.github.johnwes7.steal.views.theme.SteaLTheme
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// MARK: - 行 / Row

/**
 * 未开始活动的一行：日期块 + 名字 + 「商店 · 几天」+ 外链图标；整行可点。
 * One row for an upcoming event: date tile, name, "store · days" and the external-link icon; the whole row is
 * clickable.
 *
 * 放在同一段的分段卡片里，[shapes] 由调用方按行在段里的位置给（`ListItemDefaults.segmentedShapes`）。
 * It sits in its section's segmented card; the caller passes [shapes] for the row's position in the section
 * (`ListItemDefaults.segmentedShapes`).
 */
@Composable
fun SaleEventRowView(
    event: SaleEvent,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val storeName = saleEventStoreName(store = event.store)
    val duration = saleEventDurationText(event = event, timeZone = timeZone)

    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { SaleEventDateTile(date = event.start) },
        supportingContent = {
            Text(text = stringResource(R.string.sale_event_store_and_time, storeName, duration))
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.open_in_new_24px),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
    ) {
        Text(text = event.name)
    }
}

// MARK: - 日期块 / Date tile

/**
 * 日历页样式的日期块：月份缩写（`secondary`）+ 日期数字，底色比卡片亮一级。
 * A calendar-page date tile: the abbreviated month (`secondary`) over the day number, on a container one step
 * brighter than the card.
 *
 * 只给最小尺寸、不定死：系统字体放大时块跟着变大，字不被裁掉。
 * Only a minimum size, not a fixed one: with larger system text the tile grows instead of clipping the text.
 */
@Composable
private fun SaleEventDateTile(date: Instant) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(colorScheme.surfaceContainerHighest)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = formattedDate(instant = date, skeleton = DateFormat.ABBR_MONTH),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.secondary,
        )
        Text(
            text = formattedDayNumber(instant = date),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
        )
    }
}

// MARK: - 预览 / Previews

@Preview
@Composable
private fun SaleEventRowViewPreview() {
    SteaLTheme(darkTheme = true) {
        // 后四条在 previewNow 时都还没开始，最后一条是不认识的商店。
        // The last four are upcoming at previewNow; the last one is from an unknown store.
        val upcomingEvents = previewSaleEventList.events.drop(2)
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            upcomingEvents.forEachIndexed { index, event ->
                SaleEventRowView(
                    event = event,
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = upcomingEvents.size),
                    onClick = {},
                )
            }
        }
    }
}
