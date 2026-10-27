//
//  ITADGiveaway+Preview.swift
//  steaL
//
//  Created by yijiang on 2026/9/11.
//

import Foundation

#if DEBUG

extension ITADGiveaway {

    /// 预览用示例：带商店与一周内截止时间的典型条目 /
    /// Preview sample: a typical entry with a shop and a deadline within a week
    static let previewSample = ITADGiveaway(
        id: 16551,
        title: "Astral Ascent - FREE on Epic Games",
        shop: ITADShopRef(id: 16, name: "Epic Game Store"),
        url: "https://store.epicgames.com/p/astral-ascent-b33bc2",
        details: "https://isthereanydeal.com/giveaways/16551/",
        isMature: false,
        publish: "2026-09-10T05:42:36+02:00",
        expiry: "2026-09-17T17:00:00+02:00",
        note: nil,
        games: [
            ITADGiveawayGame(
                id: "018d937f-4261-7276-8cfb-b10a2e6e569c",
                slug: "astral-ascent",
                title: "Astral Ascent",
                type: "game",
                mature: false,
                assets: ITADAssets(
                    boxart: "https://assets.isthereanydeal.com/018d937f-4261-7276-8cfb-b10a2e6e569c/boxart.jpg?t=1788204063",
                    banner145: "https://assets.isthereanydeal.com/018d937f-4261-7276-8cfb-b10a2e6e569c/banner145.jpg?t=1788204063",
                    banner300: "https://assets.isthereanydeal.com/018d937f-4261-7276-8cfb-b10a2e6e569c/banner300.jpg?t=1788204063",
                    banner400: "https://assets.isthereanydeal.com/018d937f-4261-7276-8cfb-b10a2e6e569c/banner400.jpg?t=1788204064",
                    banner600: "https://assets.isthereanydeal.com/018d937f-4261-7276-8cfb-b10a2e6e569c/banner600.jpg?t=1788204064"
                ),
                drmFree: false,
                keys: [ITADShopRef(id: 16, name: "Epic Game Store")],
                platforms: [ITADPlatformRef(id: 1, name: "Windows")]
            )
        ]
    )

    /// 预览用示例：无商店、无图也无截止时间，用来检查回退文案与占位图 /
    /// Preview sample: no shop, no artwork and no deadline, to check the fallback copy and the placeholder
    static let previewSampleFallback = ITADGiveaway(
        id: 16512,
        title: "Once Human - Free In-Game Item Pack",
        shop: nil,
        url: "https://g.oncehuman.game/c/inxzeh",
        details: "https://isthereanydeal.com/giveaways/16512/",
        isMature: false,
        publish: "2026-08-26T12:58:30+02:00",
        expiry: "2026-09-30T21:59:00+02:00",
        note: "Redeem this code for a free in-game item pack.",
        games: [
            ITADGiveawayGame(
                id: "018d937f-6f0d-72b5-b774-b8daa809805d",
                slug: "once-human",
                title: "Once Human",
                type: "game",
                mature: false,
                assets: ITADAssets(
                    boxart: nil,
                    banner145: nil,
                    banner300: nil,
                    banner400: nil,
                    banner600: nil
                ),
                drmFree: false,
                keys: [],
                platforms: [ITADPlatformRef(id: 1, name: "Windows")]
            )
        ]
    )
}

#endif
