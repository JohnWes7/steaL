//
//  WishlistItem+Preview.swift
//  steaL
//
//  Created by yijiang on 2026/9/23.
//

import Foundation

#if DEBUG

extension WishlistItem {

    /// 预览用示例：今天、昨天、今年更早、去年各一条，外加一条没有封面的未发售游戏 /
    /// Preview samples: one each from today, yesterday, earlier this year and last year, plus one unreleased without artwork
    static let previewSamples: [WishlistItem] = [
        WishlistItem(
            appID: 1245620,
            name: "ELDEN RING",
            headerImageURL: "https://cdn.akamai.steamstatic.com/steam/apps/1245620/header.jpg",
            itadID: nil,
            isComingSoon: false,
            addedAt: .now
        ),
        WishlistItem(
            appID: 1145360,
            name: "Hades",
            headerImageURL: "https://cdn.akamai.steamstatic.com/steam/apps/1145360/header.jpg",
            itadID: nil,
            isComingSoon: false,
            addedAt: .now.addingTimeInterval(-86_400)
        ),
        WishlistItem(
            appID: 413150,
            name: "Stardew Valley",
            headerImageURL: "https://cdn.akamai.steamstatic.com/steam/apps/413150/header.jpg",
            itadID: nil,
            isComingSoon: false,
            addedAt: .now.addingTimeInterval(-86_400 * 12)
        ),
        WishlistItem(
            appID: 1086940,
            name: "Baldur's Gate 3",
            headerImageURL: "https://cdn.akamai.steamstatic.com/steam/apps/1086940/header.jpg",
            itadID: nil,
            isComingSoon: false,
            addedAt: .now.addingTimeInterval(-86_400 * 400)
        ),
        WishlistItem(
            appID: 0,
            name: "A Game Without Artwork And With A Rather Long Title",
            headerImageURL: nil,
            itadID: nil,
            isComingSoon: true,
            addedAt: nil
        ),
    ]
}

#endif
