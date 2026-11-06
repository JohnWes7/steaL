//
//  SaleEvent+Preview.swift
//  steaL
//
//  Created by yijiang on 2026/11/6.
//

import Foundation

#if DEBUG

extension SaleEvent {

    /// 预览用示例：Epic 的两周促销（2026-11-12 16:00Z – 11-26 16:00Z）。
    /// Preview sample: a two-week Epic sale (2026-11-12 16:00Z – 11-26 16:00Z).
    static let previewEpicSavings = SaleEvent(
        id: "epic-2026-11-12-epic-savings-nov",
        store: .epic,
        kind: .sale,
        name: "Epic Savings: Nov",
        start: Date(timeIntervalSince1970: 1_794_499_200),
        end: Date(timeIntervalSince1970: 1_795_708_800),
        sourceURL: URL(string: "https://forums.unrealengine.com/t/2026-q4-promotional-guide-egs-promotions/2746185")!
    )

    /// 预览用示例：Steam 主题 Fest（2026-11-16 18:00Z – 11-23 18:00Z）。
    /// Preview sample: a Steam themed fest (2026-11-16 18:00Z – 11-23 18:00Z).
    static let previewAutoBattlerFest = SaleEvent(
        id: "steam-2026-11-16-auto-battler-rpg-fest",
        store: .steam,
        kind: .themedFest,
        name: "Auto-Battler RPG Fest",
        start: Date(timeIntervalSince1970: 1_794_852_000),
        end: Date(timeIntervalSince1970: 1_795_456_800),
        sourceURL: URL(string: "https://partner.steamgames.com/doc/marketing/upcoming_events")!
    )

    /// 预览用示例：Steam 冬促（2026-12-17 18:00Z – 2027-01-04 18:00Z），跨年。
    /// Preview sample: the Steam Winter Sale (2026-12-17 18:00Z – 2027-01-04 18:00Z), spanning the new year.
    static let previewWinterSale = SaleEvent(
        id: "steam-2026-12-17-winter-sale-2026",
        store: .steam,
        kind: .seasonalSale,
        name: "Winter Sale 2026",
        start: Date(timeIntervalSince1970: 1_797_530_400),
        end: Date(timeIntervalSince1970: 1_799_085_600),
        sourceURL: URL(string: "https://partner.steamgames.com/doc/marketing/upcoming_events")!
    )

    /// 预览用示例：这个版本还不认识的商店与类型，看兜底显示。
    /// Preview sample: a store and kind this version does not know, to check the fallback display.
    static let previewUnknownStore = SaleEvent(
        id: "humble-2027-02-22-spring-bundle",
        store: .unknown("humble"),
        kind: .unknown("bundle"),
        name: "Spring Bundle",
        start: Date(timeIntervalSince1970: 1_803_319_200),
        end: Date(timeIntervalSince1970: 1_803_924_000),
        sourceURL: URL(string: "https://www.humblebundle.com")!
    )
}

#endif
