//
//  WishlistItem+MockPrice.swift
//  steaL
//
//  Created by yijiang on 2026/9/24.
//

import Foundation

// 临时占位：价格 API 还没接，Wishlist 行先用这些假价格搭骨架，接上后整个文件删掉 /
// Temporary placeholder: the price API isn't wired up yet, so wishlist rows use these fake prices; delete this file once it is
extension WishlistItem {

    /// 占位原价：按 appID 从几档常见价位里挑一个，同一个游戏每次都一样 /
    /// Placeholder regular price: picked from a few common tiers by appID, so a game always gets the same one
    var mockRegular: ITADPrice {
        let tiers = [19.95, 29.95, 49.95, 89.95]
        let amount = tiers[appID % tiers.count]
        let amountInt = Int((amount * 100).rounded())
        return ITADPrice(amount: amount, amountInt: amountInt, currency: "AUD")
    }

    /// 占位现价：大约三分之二的游戏打六折，其余等于原价，好让两种样式都看得到 /
    /// Placeholder current price: about two thirds are 40% off and the rest full price, so both styles show up
    var mockPrice: ITADPrice {
        let regular = mockRegular
        let isOnSale = appID % 3 != 0
        if !isOnSale {
            return regular
        }

        let amountInt = Int((Double(regular.amountInt) * 0.6).rounded())
        let amount = Double(amountInt) / 100
        return ITADPrice(amount: amount, amountInt: amountInt, currency: "AUD")
    }
}
