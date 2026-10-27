//
//  ITADDeal+Preview.swift
//  steaL
//
//  Created by yijiang on 2026/9/11.
//

import Foundation

#if DEBUG

extension ITADDeal {

    /// 预览用示例数据，结构对齐 ITAD 真实响应；独立成文件是为了两个卡片视图共用一份 /
    /// Sample data for previews, shaped like a real response, shared by both card views
    static let previewSample = ITADDeal(
        id: "01973dd3-e247-712c-aaee-acff181a3175",
        slug: "cyberpunk-2077",
        title: "Cyberpunk 2077",
        type: "game",
        mature: true,
        assets: ITADAssets(
            boxart: "https://assets.isthereanydeal.com/018d937f-3a1d-720b-aafa-2c1f21404d3f/boxart.jpg?t=1788299733",
            banner145: "https://assets.isthereanydeal.com/018d937f-3a1d-720b-aafa-2c1f21404d3f/banner145.jpg?t=1788299733",
            banner300: "https://assets.isthereanydeal.com/018d937f-3a1d-720b-aafa-2c1f21404d3f/banner300.jpg?t=1788299733",
            banner400: "https://assets.isthereanydeal.com/018d937f-3a1d-720b-aafa-2c1f21404d3f/banner400.jpg?t=1788299733",
            banner600: "https://assets.isthereanydeal.com/018d937f-3a1d-720b-aafa-2c1f21404d3f/banner600.jpg?t=1788299733"
        ),
        deal: ITADDealInfo(
            shop: ITADShopRef(id: 61, name: "Steam"),
            price: ITADPrice(amount: 23.99, amountInt: 2399, currency: "AUD"),
            regular: ITADPrice(amount: 59.99, amountInt: 5999, currency: "AUD"),
            cut: 60,
            voucher: nil,
            storeLow: ITADPrice(amount: 23.99, amountInt: 2399, currency: "AUD"),
            historyLow: ITADPrice(amount: 27.99, amountInt: 2799, currency: "AUD"),
            historyLow1y: ITADPrice(amount: 29.99, amountInt: 2999, currency: "AUD"),
            historyLow3m: ITADPrice(amount: 29.99, amountInt: 2999, currency: "AUD"),
            flag: "H",
            drm: [ITADPlatformRef(id: 61, name: "Steam")],
            platforms: [ITADPlatformRef(id: 1, name: "Windows")],
            timestamp: "2026-09-01T19:15:45+02:00",
            expiry: nil,
            url: "https://isthereanydeal.com/"
        )
    )
}

#endif
