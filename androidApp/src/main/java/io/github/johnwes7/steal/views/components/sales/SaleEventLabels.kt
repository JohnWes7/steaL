package io.github.johnwes7.steal.views.components.sales

import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.shared.models.saleevents.SaleEvent
import io.github.johnwes7.steal.shared.models.saleevents.SaleEventSection
import io.github.johnwes7.steal.shared.models.saleevents.SaleEventStore
import java.util.Date
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

// MARK: - 文字 / Text

/**
 * 商店的显示名；不认识的商店直接显示接口里的原始值。
 * The store's display name; an unknown store shows its raw API value.
 */
@Composable
fun saleEventStoreName(store: SaleEventStore): String {
    return when (store) {
        SaleEventStore.Steam -> stringResource(R.string.sale_event_store_steam)
        SaleEventStore.Epic -> stringResource(R.string.sale_event_store_epic)
        SaleEventStore.Itchio -> stringResource(R.string.sale_event_store_itchio)
        SaleEventStore.Gog -> stringResource(R.string.sale_event_store_gog)
        is SaleEventStore.Unknown -> store.rawValue
    }
}

/**
 * 持续几天（「7 days」/「7 天」），按 [timeZone] 里的日历日算。
 * The length in days ("7 days" / "7 天"), counted as calendar days in [timeZone].
 */
@Composable
fun saleEventDurationText(event: SaleEvent, timeZone: TimeZone): String {
    val days = event.durationDays(timeZone = timeZone)
    return pluralStringResource(R.plurals.sale_event_duration_days, days, days)
}

/**
 * 「Ends today / tomorrow / in 3 days」：相对日期用 ICU，按当前语言写（中文「今天 / 明天 / 3天后」）。
 * "Ends today / tomorrow / in 3 days": the relative date comes from ICU in the current language (Chinese
 * "今天 / 明天 / 3天后").
 */
@Composable
fun saleEventEndsText(daysLeft: Int): String {
    // 1. 每种语言建一次格式器：小写开头，好放进句子中间
    // 1. Build one formatter per locale, lowercase so it fits mid-sentence
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        RelativeDateTimeFormatter.getInstance(
            ULocale.forLocale(locale),
            null,
            RelativeDateTimeFormatter.Style.LONG,
            DisplayContext.CAPITALIZATION_NONE,
        )
    }

    // 2. 今天、明天用说法，更远的用「n 天后」
    // 2. Today and tomorrow get their words; later days get "in n days"
    val relativeDay = when {
        daysLeft <= 0 -> formatter.format(
            RelativeDateTimeFormatter.Direction.THIS,
            RelativeDateTimeFormatter.AbsoluteUnit.DAY,
        )
        daysLeft == 1 -> formatter.format(
            RelativeDateTimeFormatter.Direction.NEXT,
            RelativeDateTimeFormatter.AbsoluteUnit.DAY,
        )
        else -> formatter.format(
            daysLeft.toDouble(),
            RelativeDateTimeFormatter.Direction.NEXT,
            RelativeDateTimeFormatter.RelativeUnit.DAYS,
        )
    }

    // 3. 套进「Ends …」
    // 3. Put it into "Ends …"
    return stringResource(R.string.sale_event_ends, relativeDay)
}

/**
 * 段标题：进行中写「Happening now」，其余写月份和年份（「December 2026」/「2026年12月」）。
 * The section title: "Happening now" for ongoing events, otherwise the month and year ("December 2026" /
 * "2026年12月").
 */
@Composable
fun saleEventSectionTitle(section: SaleEventSection, timeZone: TimeZone): String {
    return when (section) {
        is SaleEventSection.HappeningNow -> stringResource(R.string.sale_events_happening_now)
        is SaleEventSection.Month -> {
            val firstDay = section.month.firstDay
            val monthStart = firstDay.atStartOfDayIn(timeZone)
            formattedDate(instant = monthStart, skeleton = DateFormat.YEAR_MONTH)
        }
    }
}

// MARK: - 日期 / Dates

/**
 * 把 [instant] 按当前语言、手机时区和 ICU 的 [skeleton]（如 `DateFormat.ABBR_MONTH`）格式化。
 * Formats [instant] in the current language and the phone's time zone with an ICU [skeleton] (such as
 * `DateFormat.ABBR_MONTH`).
 */
@Composable
fun formattedDate(instant: Instant, skeleton: String): String {
    // 1. 取当前语言：语言设置变了会跟着重组
    // 1. Read the current locale: a language change recomposes
    val locale = LocalConfiguration.current.locales[0]

    // 2. 每种语言、每种格式建一次格式器
    // 2. Build one formatter per locale and skeleton
    val formatter = remember(locale, skeleton) {
        DateFormat.getInstanceForSkeleton(skeleton, locale)
    }

    // 3. 格式化
    // 3. Format
    val date = Date(instant.toEpochMilliseconds())
    return formatter.format(date)
}

/**
 * [instant] 在手机时区里是几号，只有数字（日历块用）。
 * The day of the month of [instant] in the phone's time zone, digits only (for calendar tiles).
 *
 * 用固定的 pattern `d` 而不是 skeleton：中文的 skeleton `d` 会输出「27日」。
 * Uses the fixed pattern `d` rather than a skeleton: in Chinese the `d` skeleton gives "27日".
 */
@Composable
fun formattedDayNumber(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        SimpleDateFormat("d", locale)
    }
    val date = Date(instant.toEpochMilliseconds())
    return formatter.format(date)
}
