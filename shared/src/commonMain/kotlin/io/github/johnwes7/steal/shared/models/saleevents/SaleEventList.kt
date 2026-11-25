package io.github.johnwes7.steal.shared.models.saleevents

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// MARK: - 根对象 / Root

/**
 * 自建后端 `GET /v1/sale-events` 的响应体：所有商店还没结束的活动。
 * The response of our backend's `GET /v1/sale-events`: every store's events that have not ended.
 *
 * 字段同 `api/openapi.yaml` 的 `SaleEventList`；时刻都是整秒 UTC，直接解成 [Instant]。
 * The fields match `SaleEventList` in `api/openapi.yaml`; times are whole-second UTC and decode straight into
 * [Instant].
 */
@Serializable
data class SaleEventList(
    /**
     * 各商店最后一次成功刷新时间里最早的那个，表示最旧的那部分数据有多旧。
     * The earliest of the stores' last successful refresh times: how old the stalest part of the data is.
     */
    val refreshedAt: Instant,

    /**
     * 进行中与将来的活动，可能为空。
     * Ongoing and upcoming events, possibly empty.
     */
    val events: List<SaleEvent>,
) {

    /**
     * 按 [now] 把活动分段：先是进行中，再按 [timeZone] 里的开始月份；没有活动时是空列表。
     * Splits the events into sections as of [now]: happening now first, then by start month in [timeZone]; empty
     * when there are no events.
     *
     * 页面每次重组都传当前时间调用、不缓存结果：接口返回之后还会有活动开始或结束。
     * Pages call this with the current time on every recomposition and never cache the result: events keep starting
     * and ending after the response arrives.
     */
    fun sections(now: Instant, timeZone: TimeZone): List<SaleEventSection> {
        // 1. 去掉已结束的，按开始时间排序
        // 1. Drop ended events and sort by start
        val currentEvents = events.filter { event -> event.end > now }
        val sortedEvents = currentEvents.sortedBy { event -> event.start }

        // 2. 分成进行中与未开始
        // 2. Split into happening now and upcoming
        val happeningNowEvents = sortedEvents.filter { event -> event.isHappeningNow(now) }
        val upcomingEvents = sortedEvents.filter { event -> event.start > now }

        // 3. 未开始的按开始月份分段
        // 3. Group upcoming events by start month
        // groupBy 保留原顺序：月份按时间先后出现，每个月里仍按开始时间排好。
        // groupBy keeps the original order: months come out chronologically and each month stays sorted by start.
        val upcomingByMonth = upcomingEvents.groupBy { event -> event.startMonth(timeZone) }
        val monthSections = upcomingByMonth.map { (month, monthEvents) ->
            SaleEventSection.Month(month = month, events = monthEvents)
        }

        // 4. 有进行中的才把它放在最前面
        // 4. Put happening now first, only when there is any
        if (happeningNowEvents.isEmpty()) {
            return monthSections
        }
        val happeningNowSection = SaleEventSection.HappeningNow(events = happeningNowEvents)
        val sections: List<SaleEventSection> = listOf(happeningNowSection) + monthSections
        return sections
    }
}

// MARK: - 活动 / Event

/**
 * 一场商店大促或 Fest。
 * One store sale or fest.
 *
 * 天数都按 [TimeZone] 里的日历日数、不按 24 小时：跨夏令时的活动少一小时，按小时数会少一天。
 * Days are counted as calendar days in a [TimeZone], not 24-hour spans: an event across a DST change is an hour
 * short and would lose a day.
 */
@Serializable
data class SaleEvent(
    /**
     * 在一份响应里唯一，可以当列表的 key；跨刷新不保证不变。
     * Unique within one response and usable as a list key; not guaranteed stable across refreshes.
     */
    val id: String,

    /**
     * 哪个商店的活动。
     * The store the event belongs to.
     */
    val store: SaleEventStore,

    /**
     * 活动类型的原始值，如 `seasonalSale` / `themedFest` / `nextFest` / `sale`；以后会加新值，界面暂不显示。
     * The event type's raw value, such as `seasonalSale` / `themedFest` / `nextFest` / `sale`; new values will be
     * added, and the UI does not show it yet.
     */
    val kind: String,

    /**
     * 源页面上的原文名字（英文，不翻译）。
     * The name exactly as on the source page (English, untranslated).
     */
    val name: String,

    /**
     * 开始时刻。
     * The start time.
     */
    val start: Instant,

    /**
     * 结束时刻，不含这一刻。
     * The end time, exclusive.
     */
    val end: Instant,

    /**
     * 这条数据提取自哪个网页。
     * The web page this event was extracted from.
     */
    @SerialName("sourceURL")
    val sourceUrl: String,
) {

    /**
     * 在 [now] 是否已经开始、还没结束。
     * Whether the event has started and not yet ended at [now].
     */
    fun isHappeningNow(now: Instant): Boolean {
        val hasStarted = start <= now
        val hasEnded = end <= now
        return hasStarted && !hasEnded
    }

    /**
     * 持续几天：[timeZone] 里开始那天到结束那天隔几个日历日。
     * The length in days: the calendar days from the start date to the end date in [timeZone].
     */
    fun durationDays(timeZone: TimeZone): Int {
        val startDate = start.dateIn(timeZone)
        val endDate = end.dateIn(timeZone)
        return startDate.daysUntil(endDate)
    }

    /**
     * 还有几天结束：[timeZone] 里 [now] 那天到结束那天隔几个日历日，0 是今天、1 是明天。
     * How many days until the event ends: the calendar days from the date of [now] to the end date in [timeZone];
     * 0 is today and 1 is tomorrow.
     */
    fun daysUntilEnd(now: Instant, timeZone: TimeZone): Int {
        val today = now.dateIn(timeZone)
        val endDate = end.dateIn(timeZone)
        return today.daysUntil(endDate)
    }
}

/**
 * 开始时刻在 [timeZone] 里是哪一年哪个月，给 [SaleEventList.sections] 分段用。
 * The year and month of the start time in [timeZone], for [SaleEventList.sections].
 */
private fun SaleEvent.startMonth(timeZone: TimeZone): YearMonth {
    val startDate = start.dateIn(timeZone)
    return startDate.yearMonth
}

/**
 * 这个时刻在 [timeZone] 里是哪一天。
 * The date of this instant in [timeZone].
 */
private fun Instant.dateIn(timeZone: TimeZone): LocalDate {
    val localDateTime = toLocalDateTime(timeZone)
    return localDateTime.date
}

// MARK: - 商店 / Store

/**
 * 活动所属的商店。
 * The store an event belongs to.
 *
 * 后端以后会加新商店，不认识的值落到 [Unknown]，不让整份响应解码失败。
 * The backend will add stores later; unknown values land in [Unknown] instead of failing the whole response.
 */
@Serializable(with = SaleEventStoreSerializer::class)
sealed interface SaleEventStore {

    /**
     * 接口里的字符串。
     * The string used in the API.
     */
    val rawValue: String

    /**
     * Steam。
     * Steam.
     */
    data object Steam : SaleEventStore {
        override val rawValue: String = "steam"
    }

    /**
     * Epic Games Store。
     * Epic Games Store.
     */
    data object Epic : SaleEventStore {
        override val rawValue: String = "epic"
    }

    /**
     * itch.io。
     * itch.io.
     */
    data object Itchio : SaleEventStore {
        override val rawValue: String = "itchio"
    }

    /**
     * GOG。
     * GOG.
     */
    data object Gog : SaleEventStore {
        override val rawValue: String = "gog"
    }

    /**
     * 这个版本还不认识的商店，带原始值 [rawValue]。
     * A store this version does not know yet, with its raw value [rawValue].
     */
    data class Unknown(override val rawValue: String) : SaleEventStore

    companion object {

        /**
         * 按接口里的字符串 [rawValue] 取商店，不认识的是 [Unknown]。
         * Returns the store for the API string [rawValue]; unknown strings become [Unknown].
         */
        fun from(rawValue: String): SaleEventStore {
            val store = when (rawValue) {
                Steam.rawValue -> Steam
                Epic.rawValue -> Epic
                Itchio.rawValue -> Itchio
                Gog.rawValue -> Gog
                else -> Unknown(rawValue)
            }
            return store
        }
    }
}

/**
 * 把 [SaleEventStore] 编解码成接口里的字符串。
 * Encodes and decodes [SaleEventStore] as the API string.
 *
 * 和 [SaleEventStore] 一样是 public：序列化插件要求自定义序列化器至少和类型一样可见，否则用到的地方可能找不到它。
 * Public like [SaleEventStore]: the serialization plugin wants a custom serializer at least as visible as its type,
 * or use sites may fail to find it.
 */
object SaleEventStoreSerializer : KSerializer<SaleEventStore> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.github.johnwes7.steal.shared.models.saleevents.SaleEventStore", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): SaleEventStore {
        val rawValue = decoder.decodeString()
        return SaleEventStore.from(rawValue)
    }

    override fun serialize(encoder: Encoder, value: SaleEventStore) {
        encoder.encodeString(value.rawValue)
    }
}

// MARK: - 分段 / Sections

/**
 * Sales 列表的一段：进行中，或某个月开始的活动。
 * One section of the Sales list: happening now, or the events starting in one month.
 */
sealed interface SaleEventSection {

    /**
     * 这一段的活动，按开始时间升序。
     * The section's events, sorted by start ascending.
     */
    val events: List<SaleEvent>

    /**
     * 已经开始、还没结束的活动。
     * Events that have started and not yet ended.
     */
    data class HappeningNow(override val events: List<SaleEvent>) : SaleEventSection

    /**
     * 在 [month] 开始的活动（用户时区里的年月）。
     * Events starting in [month] (the year and month in the user's time zone).
     */
    data class Month(val month: YearMonth, override val events: List<SaleEvent>) : SaleEventSection
}
