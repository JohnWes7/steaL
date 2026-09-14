//
//  ITADPriceGame+Preview.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import Foundation

#if DEBUG

extension ITADPriceGame {

    /// 预览用示例数据，字段结构对齐 ITAD 真实响应 /
    /// Sample data for previews, shaped like a real response
    ///
    /// 商店 ID 只有 `61`（Steam）是实测确认的，另外两个是示意值；三条 deal 覆盖
    /// 「Steam 直购」「第三方但 Steam 激活」「不激活 Steam」三种情况 /
    /// Only shop id 61 (Steam) is verified; the other two ids are placeholders. The three deals
    /// cover Steam direct, a third-party Steam key, and a key that does not activate on Steam
    static let previewSample = ITADPriceGame(
        id: "018d937f-0f5c-72f6-9b1e-2e5b3c4d5e6f",
        historyLow: ITADPriceHistory(
            all: ITADPrice(amount: 45.53, amountInt: 4553, currency: "AUD"),
            y1: ITADPrice(amount: 48.38, amountInt: 4838, currency: "AUD"),
            m3: ITADPrice(amount: 73.20, amountInt: 7320, currency: "AUD")
        ),
        deals: [
            ITADPriceDeal(
                shop: ITADShopRef(id: 15, name: "Fanatical"),
                price: ITADPrice(amount: 69.95, amountInt: 6995, currency: "AUD"),
                regular: ITADPrice(amount: 99.95, amountInt: 9995, currency: "AUD"),
                cut: 30,
                voucher: nil,
                storeLow: ITADPrice(amount: 69.95, amountInt: 6995, currency: "AUD"),
                flag: nil,
                drm: [ITADPlatformRef(id: 61, name: "Steam")],
                platforms: [ITADPlatformRef(id: 1, name: "Windows")],
                timestamp: "2026-09-14T02:10:00Z",
                expiry: nil,
                url: "https://www.fanatical.com"
            ),
            ITADPriceDeal(
                shop: ITADShopRef(id: 61, name: "Steam"),
                price: ITADPrice(amount: 71.93, amountInt: 7193, currency: "AUD"),
                regular: ITADPrice(amount: 99.95, amountInt: 9995, currency: "AUD"),
                cut: 28,
                voucher: nil,
                storeLow: ITADPrice(amount: 71.93, amountInt: 7193, currency: "AUD"),
                flag: nil,
                drm: [ITADPlatformRef(id: 61, name: "Steam")],
                platforms: [
                    ITADPlatformRef(id: 1, name: "Windows"),
                    ITADPlatformRef(id: 2, name: "Mac"),
                ],
                timestamp: "2026-09-14T02:10:00Z",
                expiry: nil,
                url: "https://store.steampowered.com"
            ),
            ITADPriceDeal(
                shop: ITADShopRef(id: 5, name: "GOG"),
                price: ITADPrice(amount: 79.99, amountInt: 7999, currency: "AUD"),
                regular: ITADPrice(amount: 79.99, amountInt: 7999, currency: "AUD"),
                cut: 0,
                voucher: nil,
                storeLow: nil,
                flag: nil,
                drm: [],
                platforms: [ITADPlatformRef(id: 1, name: "Windows")],
                timestamp: "2026-09-14T02:10:00Z",
                expiry: nil,
                url: "https://www.gog.com"
            ),
        ]
    )
}

#endif
