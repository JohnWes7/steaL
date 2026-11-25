package io.github.johnwes7.steal.views.components.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.previews.previewNow
import io.github.johnwes7.steal.previews.previewSaleEventList
import io.github.johnwes7.steal.shared.models.saleevents.SaleEvent
import io.github.johnwes7.steal.shared.models.saleevents.SaleEventSection
import io.github.johnwes7.steal.views.theme.SteaLTheme
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// MARK: - 一段 / One section

/**
 * 把 Sales 列表的一段加进 `LazyColumn`：段标题，加上进行中的大卡片，或某个月开始的分段行。
 * Adds one section of the Sales list to a `LazyColumn`: the section title, then big cards for happening now or
 * segmented rows for a month.
 *
 * 每个活动是列表的一个 item，key 是活动 ID（一个活动只会在一段里）；段标题的 key 以 `section-` 开头。点一行调
 * [onSelect]，去哪里由页面决定；还剩几天和进度按 [now] 算，天数是 [timeZone] 里的日历日。
 * Each event is its own list item keyed by its ID (an event appears in only one section); section title keys start
 * with `section-`. Tapping an event calls [onSelect] and the page decides where it leads; days left and progress
 * are measured from [now], with days counted as calendar days in [timeZone].
 */
fun LazyListScope.saleEventSectionItems(
    section: SaleEventSection,
    onSelect: (SaleEvent) -> Unit,
    now: Instant,
    timeZone: TimeZone,
) {
    when (section) {
        is SaleEventSection.HappeningNow -> {
            item(key = "section-happening-now") {
                SectionTitle(section = section, timeZone = timeZone, modifier = Modifier.animateItem())
            }
            itemsIndexed(
                items = section.events,
                key = { _, event -> event.id },
                contentType = { _, _ -> "saleEventCard" },
            ) { index, event ->
                val gap = gapBelow(index = index, count = section.events.size, gap = CARD_GAP)
                SaleEventCardView(
                    event = event,
                    onClick = { onSelect(event) },
                    modifier = Modifier
                        .animateItem()
                        .padding(bottom = gap),
                    now = now,
                    timeZone = timeZone,
                )
            }
        }
        is SaleEventSection.Month -> {
            item(key = "section-${section.month}") {
                SectionTitle(section = section, timeZone = timeZone, modifier = Modifier.animateItem())
            }
            itemsIndexed(
                items = section.events,
                key = { _, event -> event.id },
                contentType = { _, _ -> "saleEventRow" },
            ) { index, event ->
                val count = section.events.size
                val gap = gapBelow(index = index, count = count, gap = ListItemDefaults.SegmentedGap)
                SaleEventRowView(
                    event = event,
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
                    onClick = { onSelect(event) },
                    modifier = Modifier
                        .animateItem()
                        .padding(bottom = gap),
                    timeZone = timeZone,
                )
            }
        }
    }
}

/**
 * 进行中的大卡片之间的间距。
 * The space between the big happening-now cards.
 */
private val CARD_GAP: Dp = 12.dp

/**
 * 段里第 [index] 个（共 [count] 个）下面留多少：最后一个不留，段与段之间由标题的上边距隔开。
 * The space below item [index] of [count] in a section: none after the last, since section titles carry the
 * space between sections.
 */
private fun gapBelow(index: Int, count: Int, gap: Dp): Dp {
    val isLast = index == count - 1
    return if (isLast) 0.dp else gap
}

/**
 * 段标题：主色小字，同设置页的分组标题；进行中写「Happening now」，其余写月份和年份。
 * The section title: small primary text like settings group headings; "Happening now" for ongoing events,
 * otherwise the month and year.
 */
@Composable
private fun SectionTitle(section: SaleEventSection, timeZone: TimeZone, modifier: Modifier = Modifier) {
    Text(
        text = saleEventSectionTitle(section = section, timeZone = timeZone),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .semantics { heading() }
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

// MARK: - 预览 / Previews

@Preview(heightDp = 1200)
@Composable
private fun SaleEventSectionItemsPreview() {
    SteaLTheme(darkTheme = true) {
        val timeZone = TimeZone.currentSystemDefault()
        val sections = previewSaleEventList.sections(now = previewNow, timeZone = timeZone)
        LazyColumn(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            for (section in sections) {
                saleEventSectionItems(section = section, onSelect = { _ -> }, now = previewNow, timeZone = timeZone)
            }
        }
    }
}
