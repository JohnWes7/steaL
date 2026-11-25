package io.github.johnwes7.steal.shared.models.saleevents

import io.github.johnwes7.steal.shared.services.appNetworkJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth

/**
 * sale-events 接口响应的解码、日历日计算与分段测试。
 * Tests for the sale-events response: decoding, calendar-day counts and sections.
 */
class SaleEventListTest {

    // MARK: - 解码 / Decoding

    /**
     * 真实响应能完整解码：时刻解成 Instant，`sourceURL` 落到 `sourceUrl`，商店认得出。
     * A real response decodes in full: times become instants, `sourceURL` lands in `sourceUrl`, and stores are
     * recognised.
     */
    @Test
    fun decodesRealResponse() {
        val eventList = realEventList()

        assertEquals(Instant.parse("2026-11-16T01:00:06Z"), eventList.refreshedAt)
        assertEquals(5, eventList.events.size)

        val epicSavings = eventList.events[0]
        assertEquals("epic-2026-11-12-epic-savings-nov", epicSavings.id)
        assertEquals(SaleEventStore.Epic, epicSavings.store)
        assertEquals("sale", epicSavings.kind)
        assertEquals("Epic Savings: Nov", epicSavings.name)
        assertEquals(Instant.parse("2026-11-12T16:00:00Z"), epicSavings.start)
        assertEquals(Instant.parse("2026-11-26T16:00:00Z"), epicSavings.end)
        assertEquals(
            "https://forums.unrealengine.com/t/2026-q4-promotional-guide-egs-promotions/2746185",
            epicSavings.sourceUrl,
        )

        val winterSale = eventList.events[3]
        assertEquals(SaleEventStore.Steam, winterSale.store)
        assertEquals("seasonalSale", winterSale.kind)
    }

    /**
     * 不认识的商店和类型不让解码失败：商店落到 Unknown 并保留原始值，类型原样保留。
     * An unknown store or kind does not fail decoding: the store becomes Unknown with its raw value, and the kind
     * is kept as is.
     */
    @Test
    fun decodesUnknownStoreAndKind() {
        val eventList = appNetworkJson.decodeFromString<SaleEventList>(UNKNOWN_STORE_RESPONSE)

        val event = eventList.events[0]
        assertEquals(SaleEventStore.Unknown("humble"), event.store)
        assertEquals("humble", event.store.rawValue)
        assertEquals("bundleSale", event.kind)
    }

    /**
     * 四个已知商店都认得出，原始值能换回去。
     * All four known stores are recognised, and their raw values round-trip.
     */
    @Test
    fun recognisesKnownStores() {
        assertEquals(SaleEventStore.Steam, SaleEventStore.from("steam"))
        assertEquals(SaleEventStore.Epic, SaleEventStore.from("epic"))
        assertEquals(SaleEventStore.Itchio, SaleEventStore.from("itchio"))
        assertEquals(SaleEventStore.Gog, SaleEventStore.from("gog"))
        assertEquals("itchio", SaleEventStore.Itchio.rawValue)
    }

    /**
     * 空列表是「没有活动」，不是错误。
     * An empty list means "no events", not an error.
     */
    @Test
    fun decodesEmptyList() {
        val eventList = appNetworkJson.decodeFromString<SaleEventList>(EMPTY_RESPONSE)

        assertTrue(eventList.events.isEmpty())
        assertTrue(eventList.sections(now = NOW, timeZone = MELBOURNE).isEmpty())
    }

    /**
     * 时刻解析不了时直接用 Json 解码抛 IllegalArgumentException（标准库的 InstantFormatException），不是
     * SerializationException；经过 Ktor 的 `body()` 会被包成 JsonConvertException，那一层由 `SaleEventsApiTest` 测。
     * An unparsable time makes a direct Json decode throw IllegalArgumentException (the standard library's
     * InstantFormatException), not SerializationException; through Ktor's `body()` it is wrapped in
     * JsonConvertException, which `SaleEventsApiTest` covers.
     */
    @Test
    fun badTimeFailsDecoding() {
        assertFailsWith<IllegalArgumentException> {
            appNetworkJson.decodeFromString<SaleEventList>(BAD_TIME_RESPONSE)
        }
    }

    // MARK: - 进行中 / Happening now

    /**
     * 开始那一刻算进行中，结束那一刻不算（结束时刻不含）。
     * The start instant counts as happening now and the end instant does not (the end is exclusive).
     */
    @Test
    fun happeningNowIncludesStartExcludesEnd() {
        val epicSavings = realEventList().events[0]
        val oneSecondBeforeStart = Instant.parse("2026-11-12T15:59:59Z")

        assertFalse(epicSavings.isHappeningNow(oneSecondBeforeStart))
        assertTrue(epicSavings.isHappeningNow(epicSavings.start))
        assertFalse(epicSavings.isHappeningNow(epicSavings.end))
    }

    // MARK: - 已过去的比例 / Elapsed fraction

    /**
     * Epic Savings 共 14 天：开始前和开始那一刻是 0，第 7 天是一半，结束那一刻和之后是 1。
     * Epic Savings lasts 14 days: 0 before and at the start, half on day 7, and 1 at and after the end.
     */
    @Test
    fun elapsedFractionRunsFromZeroToOne() {
        val epicSavings = realEventList().events[0]
        val dayBefore = Instant.parse("2026-11-11T16:00:00Z")
        val halfway = Instant.parse("2026-11-19T16:00:00Z")
        val dayAfter = Instant.parse("2026-11-27T16:00:00Z")

        assertEquals(0f, epicSavings.elapsedFraction(now = dayBefore))
        assertEquals(0f, epicSavings.elapsedFraction(now = epicSavings.start))
        assertEquals(0.5f, epicSavings.elapsedFraction(now = halfway))
        assertEquals(1f, epicSavings.elapsedFraction(now = epicSavings.end))
        assertEquals(1f, epicSavings.elapsedFraction(now = dayAfter))
    }

    /**
     * 结束不晚于开始的坏数据当作已经过完，不出 NaN。
     * Bad data that ends no later than it starts counts as over, never NaN.
     */
    @Test
    fun elapsedFractionOfEmptyEventIsOne() {
        val instantEvent = event(id = "instant", start = "2026-12-01T00:00:00Z", end = "2026-12-01T00:00:00Z")

        assertEquals(1f, instantEvent.elapsedFraction(now = NOW))
    }

    // MARK: - 天数 / Day counts

    /**
     * 还有几天结束按用户时区的日历日：墨尔本 25 号 22:00，27 号 03:00 结束，只差 29 小时但是后天，算 2 天；
     * 同一时刻在 UTC 是 25 号到 26 号，算 1 天。
     * Days until the end count calendar days in the user's time zone: at 22:00 on the 25th in Melbourne, an end at
     * 03:00 on the 27th is only 29 hours away but is the day after tomorrow, so 2; the same instant in UTC runs from
     * the 25th to the 26th, so 1.
     */
    @Test
    fun daysUntilEndCountsCalendarDays() {
        val epicSavings = realEventList().events[0]

        assertEquals(2, epicSavings.daysUntilEnd(now = NOW, timeZone = MELBOURNE))
        assertEquals(1, epicSavings.daysUntilEnd(now = NOW, timeZone = TimeZone.UTC))
    }

    /**
     * 结束当天是 0（今天），前一天是 1（明天）。
     * The end day is 0 (today) and the day before is 1 (tomorrow).
     */
    @Test
    fun daysUntilEndIsZeroOnTheEndDay() {
        val epicSavings = realEventList().events[0]
        val endDayMorning = Instant.parse("2026-11-26T14:00:00Z")
        val dayBeforeEnd = Instant.parse("2026-11-25T14:00:00Z")

        assertEquals(0, epicSavings.daysUntilEnd(now = endDayMorning, timeZone = MELBOURNE))
        assertEquals(1, epicSavings.daysUntilEnd(now = dayBeforeEnd, timeZone = MELBOURNE))
    }

    /**
     * 跨夏令时的活动不少算一天：Rhythm Fest 在洛杉矶从 3 月 8 日 10:00（PST）到 15 日 10:00（PDT），只有 6 天 23 小时，
     * 按日历日是 7 天；墨尔本同样是 7 天。
     * An event across a DST change does not lose a day: Rhythm Fest runs from 10:00 on 8 March (PST) to 10:00 on
     * 15 March (PDT) in Los Angeles, only 6 days 23 hours, which is 7 calendar days; Melbourne also gets 7.
     */
    @Test
    fun durationCountsCalendarDaysAcrossDst() {
        val rhythmFest = realEventList().events[4]
        val losAngeles = TimeZone.of("America/Los_Angeles")

        assertEquals("Rhythm Fest", rhythmFest.name)
        assertEquals(7, rhythmFest.durationDays(timeZone = losAngeles))
        assertEquals(7, rhythmFest.durationDays(timeZone = MELBOURNE))
    }

    // MARK: - 分段 / Sections

    /**
     * 真实样例在墨尔本 11-25 22:00 分成三段：进行中两条，12 月两条，2027 年 3 月一条。
     * The real sample at 22:00 on 25 November in Melbourne splits into three sections: two happening now, two in
     * December and one in March 2027.
     */
    @Test
    fun splitsRealResponseIntoSections() {
        val sections = realEventList().sections(now = NOW, timeZone = MELBOURNE)

        assertEquals(3, sections.size)

        val happeningNow = assertIs<SaleEventSection.HappeningNow>(sections[0])
        assertEquals(
            listOf("epic-2026-11-12-epic-savings-nov", "epic-2026-11-17-black-friday-premium-editions-sale"),
            happeningNow.events.map { event -> event.id },
        )

        val december = assertIs<SaleEventSection.Month>(sections[1])
        assertEquals(YearMonth(2026, 12), december.month)
        assertEquals(
            listOf("epic-2026-12-10-holiday-sale", "steam-2026-12-17-winter-sale-2026"),
            december.events.map { event -> event.id },
        )

        val march = assertIs<SaleEventSection.Month>(sections[2])
        assertEquals(YearMonth(2027, 3), march.month)
        assertEquals(listOf("steam-2027-03-08-rhythm-fest"), march.events.map { event -> event.id })
    }

    /**
     * 月份按用户时区切：11-30 20:00Z 开始的活动在墨尔本是 12 月 1 日，在 UTC 还是 11 月。
     * Months follow the user's time zone: an event starting at 20:00Z on 30 November is 1 December in Melbourne
     * but still November in UTC.
     */
    @Test
    fun monthFollowsTimeZone() {
        val lateNovember = event(id = "late-november", start = "2026-11-30T20:00:00Z", end = "2026-12-07T20:00:00Z")
        val eventList = eventListOf(lateNovember)

        val melbourneSection = assertIs<SaleEventSection.Month>(eventList.sections(now = NOW, timeZone = MELBOURNE)[0])
        val utcSection = assertIs<SaleEventSection.Month>(eventList.sections(now = NOW, timeZone = TimeZone.UTC)[0])

        assertEquals(YearMonth(2026, 12), melbourneSection.month)
        assertEquals(YearMonth(2026, 11), utcSection.month)
    }

    /**
     * 接口返回之后才结束的活动也去掉：Epic Savings 结束那一刻起不再出现。
     * Events that end after the response are dropped too: Epic Savings disappears from its end instant on.
     */
    @Test
    fun dropsEndedEvents() {
        val epicSavingsEnd = Instant.parse("2026-11-26T16:00:00Z")

        val sections = realEventList().sections(now = epicSavingsEnd, timeZone = MELBOURNE)

        val happeningNow = assertIs<SaleEventSection.HappeningNow>(sections[0])
        assertEquals(
            listOf("epic-2026-11-17-black-friday-premium-editions-sale"),
            happeningNow.events.map { event -> event.id },
        )
    }

    /**
     * 没有进行中的就不出现那一段，第一段直接是月份。
     * Without ongoing events there is no happening-now section; the first section is a month.
     */
    @Test
    fun omitsHappeningNowWhenNothingIsOngoing() {
        val beforeEverything = Instant.parse("2026-11-01T00:00:00Z")

        val sections = realEventList().sections(now = beforeEverything, timeZone = MELBOURNE)

        val november = assertIs<SaleEventSection.Month>(sections[0])
        assertEquals(YearMonth(2026, 11), november.month)
        assertEquals(2, november.events.size)
    }

    /**
     * 不依赖接口的顺序：倒过来给也分出同样的段。
     * Does not rely on the API's order: the reversed list gives the same sections.
     */
    @Test
    fun sortsEventsItself() {
        val eventList = realEventList()
        val reversedEvents = eventList.events.reversed()
        val reversedList = eventList.copy(events = reversedEvents)

        val expected = eventList.sections(now = NOW, timeZone = MELBOURNE)
        val actual = reversedList.sections(now = NOW, timeZone = MELBOURNE)

        assertEquals(expected, actual)
    }

    /**
     * 全都结束了就是空列表。
     * Everything ended gives an empty list.
     */
    @Test
    fun allEndedGivesNoSections() {
        val afterEverything = Instant.parse("2027-12-31T00:00:00Z")

        val sections = realEventList().sections(now = afterEverything, timeZone = MELBOURNE)

        assertTrue(sections.isEmpty())
    }

    // MARK: - 辅助 / Helpers

    /**
     * 解码真实样例。
     * Decodes the real sample.
     */
    private fun realEventList(): SaleEventList {
        return appNetworkJson.decodeFromString<SaleEventList>(REAL_RESPONSE)
    }

    /**
     * 一条 Steam 活动，[start] 和 [end] 是 ISO 8601 字符串。
     * A Steam event, with [start] and [end] as ISO 8601 strings.
     */
    private fun event(id: String, start: String, end: String): SaleEvent {
        return SaleEvent(
            id = id,
            store = SaleEventStore.Steam,
            kind = "themedFest",
            name = id,
            start = Instant.parse(start),
            end = Instant.parse(end),
            sourceUrl = "https://example.com/$id",
        )
    }

    /**
     * 只含 [events] 的列表，刷新时间随便填一个。
     * A list holding only [events], with any refresh time.
     */
    private fun eventListOf(vararg events: SaleEvent): SaleEventList {
        return SaleEventList(refreshedAt = Instant.parse("2026-11-16T01:00:06Z"), events = events.toList())
    }

    private companion object {

        /**
         * 测试里的「现在」：墨尔本 2026-11-25 22:00（AEDT），UTC 是同一天 11:00。
         * The tests' "now": 22:00 on 2026-11-25 in Melbourne (AEDT), 11:00 the same day in UTC.
         */
        val NOW: Instant = Instant.parse("2026-11-25T11:00:00Z")

        /**
         * 开发者所在的时区，11 月到 3 月是夏令时 UTC+11。
         * The developer's time zone, on daylight time (UTC+11) from November to March.
         */
        val MELBOURNE: TimeZone = TimeZone.of("Australia/Melbourne")

        /**
         * 2026-11-25 请求 `GET /v1/sale-events` 的真实响应，留第 1–4 条和 Rhythm Fest（Steam 的开始、结束跨了美国夏令时）。
         * A real response to `GET /v1/sale-events` on 2026-11-25, keeping entries 1–4 and Rhythm Fest (whose Steam
         * start and end straddle the US DST change).
         */
        const val REAL_RESPONSE = """
        {
          "refreshedAt": "2026-11-16T01:00:06Z",
          "events": [
            {
              "id": "epic-2026-11-12-epic-savings-nov",
              "store": "epic",
              "kind": "sale",
              "name": "Epic Savings: Nov",
              "start": "2026-11-12T16:00:00Z",
              "end": "2026-11-26T16:00:00Z",
              "sourceURL": "https://forums.unrealengine.com/t/2026-q4-promotional-guide-egs-promotions/2746185"
            },
            {
              "id": "epic-2026-11-17-black-friday-premium-editions-sale",
              "store": "epic",
              "kind": "sale",
              "name": "Black Friday: Premium Editions Sale",
              "start": "2026-11-17T16:00:00Z",
              "end": "2026-12-01T16:00:00Z",
              "sourceURL": "https://forums.unrealengine.com/t/2026-q4-promotional-guide-egs-promotions/2746185"
            },
            {
              "id": "epic-2026-12-10-holiday-sale",
              "store": "epic",
              "kind": "sale",
              "name": "Holiday Sale",
              "start": "2026-12-10T16:00:00Z",
              "end": "2027-01-07T16:00:00Z",
              "sourceURL": "https://forums.unrealengine.com/t/2026-q4-promotional-guide-egs-promotions/2746185"
            },
            {
              "id": "steam-2026-12-17-winter-sale-2026",
              "store": "steam",
              "kind": "seasonalSale",
              "name": "Winter Sale 2026",
              "start": "2026-12-17T18:00:00Z",
              "end": "2027-01-04T18:00:00Z",
              "sourceURL": "https://partner.steamgames.com/doc/marketing/upcoming_events"
            },
            {
              "id": "steam-2027-03-08-rhythm-fest",
              "store": "steam",
              "kind": "themedFest",
              "name": "Rhythm Fest",
              "start": "2027-03-08T18:00:00Z",
              "end": "2027-03-15T17:00:00Z",
              "sourceURL": "https://partner.steamgames.com/doc/marketing/upcoming_events"
            }
          ]
        }
        """

        /**
         * 商店和类型都是这个版本不认识的值。
         * A store and kind this version does not know.
         */
        const val UNKNOWN_STORE_RESPONSE = """
        {
          "refreshedAt": "2026-11-16T01:00:06Z",
          "events": [
            {
              "id": "humble-2026-12-01-winter-bundle",
              "store": "humble",
              "kind": "bundleSale",
              "name": "Winter Bundle",
              "start": "2026-12-01T18:00:00Z",
              "end": "2026-12-15T18:00:00Z",
              "sourceURL": "https://example.com/humble"
            }
          ]
        }
        """

        /**
         * 没有活动。
         * No events.
         */
        const val EMPTY_RESPONSE = """
        { "refreshedAt": "2026-11-16T01:00:06Z", "events": [] }
        """

        /**
         * 开始时刻不是 ISO 8601。
         * A start time that is not ISO 8601.
         */
        const val BAD_TIME_RESPONSE = """
        {
          "refreshedAt": "2026-11-16T01:00:06Z",
          "events": [
            {
              "id": "steam-bad",
              "store": "steam",
              "kind": "themedFest",
              "name": "Bad Fest",
              "start": "next Tuesday",
              "end": "2026-12-15T18:00:00Z",
              "sourceURL": "https://example.com/bad"
            }
          ]
        }
        """
    }
}
