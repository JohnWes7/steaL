package io.github.johnwes7.steal.previews

import io.github.johnwes7.steal.shared.models.saleevents.SaleEvent
import io.github.johnwes7.steal.shared.models.saleevents.SaleEventList
import io.github.johnwes7.steal.shared.models.saleevents.SaleEventStore
import kotlin.time.Instant

// 要写在 previewSaleEventList 前面：同一文件的顶层属性按书写顺序初始化。
// Must come before previewSaleEventList: top-level properties in a file initialize in order.
private const val EPIC_SOURCE = "https://forums.unrealengine.com/t/2026-q4-promotional-guide-egs-promotions/2746185"
private const val STEAM_SOURCE = "https://partner.steamgames.com/doc/marketing/upcoming_events"

/**
 * 预览用的活动列表，按 [previewNow]（2026-11-24）看：两条进行中（第二条名字长、折两行），两条 12 月开始，一条跨年到
 * 2027 年 1 月，一条是不认识的商店。时间取自 2026-11-25 的真实响应。
 * The event list for previews, as seen from [previewNow] (2026-11-24): two happening now (the second with a long,
 * two-line name), two starting in December, one in January 2027 across the year end, and one from an unknown store.
 * The times come from a real response on 2026-11-25.
 */
internal val previewSaleEventList: SaleEventList = SaleEventList(
    refreshedAt = Instant.parse("2026-11-16T01:00:06Z"),
    events = listOf(
        SaleEvent(
            id = "epic-2026-11-12-epic-savings-nov",
            store = SaleEventStore.Epic,
            kind = "sale",
            name = "Epic Savings: Nov",
            start = Instant.parse("2026-11-12T16:00:00Z"),
            end = Instant.parse("2026-11-26T16:00:00Z"),
            sourceUrl = EPIC_SOURCE,
        ),
        SaleEvent(
            id = "epic-2026-11-17-black-friday-premium-editions-sale",
            store = SaleEventStore.Epic,
            kind = "sale",
            name = "Black Friday: Premium Editions Sale",
            start = Instant.parse("2026-11-17T16:00:00Z"),
            end = Instant.parse("2026-12-01T16:00:00Z"),
            sourceUrl = EPIC_SOURCE,
        ),
        SaleEvent(
            id = "epic-2026-12-10-holiday-sale",
            store = SaleEventStore.Epic,
            kind = "sale",
            name = "Holiday Sale",
            start = Instant.parse("2026-12-10T16:00:00Z"),
            end = Instant.parse("2027-01-07T16:00:00Z"),
            sourceUrl = EPIC_SOURCE,
        ),
        SaleEvent(
            id = "steam-2026-12-17-winter-sale-2026",
            store = SaleEventStore.Steam,
            kind = "seasonalSale",
            name = "Winter Sale 2026",
            start = Instant.parse("2026-12-17T18:00:00Z"),
            end = Instant.parse("2027-01-04T18:00:00Z"),
            sourceUrl = STEAM_SOURCE,
        ),
        SaleEvent(
            id = "steam-2027-01-14-desktop-companion-fest",
            store = SaleEventStore.Steam,
            kind = "themedFest",
            name = "Desktop Companion Fest",
            start = Instant.parse("2027-01-14T18:00:00Z"),
            end = Instant.parse("2027-01-18T18:00:00Z"),
            sourceUrl = STEAM_SOURCE,
        ),
        SaleEvent(
            id = "humble-2027-01-20-winter-bundle",
            store = SaleEventStore.Unknown(rawValue = "humble"),
            kind = "bundleSale",
            name = "Winter Bundle",
            start = Instant.parse("2027-01-20T18:00:00Z"),
            end = Instant.parse("2027-02-03T18:00:00Z"),
            sourceUrl = "https://example.com/humble",
        ),
    ),
)
