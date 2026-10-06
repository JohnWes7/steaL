//
//  WishlistAlertChecker.swift
//  steaL
//
//  Created by yijiang on 2026/10/6.
//

import Foundation
import OSLog
import UserNotifications

/// 检查 wishlist 的降价与发售，需要时发本地通知。
/// Checks the wishlist for price drops and releases, sending local notifications when needed.
///
/// 前台（回到 App）和后台刷新共用同一个入口，不依赖界面。开关、账号、地区由调用方传入，这里不读 SwiftData 和 Auth。
/// The foreground (returning to the app) and background refresh share this entry point, with no UI dependency.
/// The caller passes in the toggles, account and region; nothing here reads SwiftData or Auth.
enum WishlistAlertChecker {

    // MARK: - 入口 / Entry point

    /// 跑一次完整检查。
    /// Runs one full check.
    ///
    /// - Parameters:
    ///   - uid: 当前账号 / The current account.
    ///   - region: 当前地区，决定价格的货币 / The current region, which sets the price currency.
    ///   - notifyPriceDrops: 降价通知开关 / The price-drop notification toggle.
    ///   - notifyReleases: 发售通知开关 / The release notification toggle.
    ///   - wishlistService: 用来读写 Firestore 的 Service / The service that reads and writes Firestore.
    static func run(
        uid: String,
        region: AppRegion,
        notifyPriceDrops: Bool,
        notifyReleases: Bool,
        wishlistService: WishlistService
    ) async {
        // 第 1 步：两个开关都关着就 return
        guard notifyPriceDrops || notifyReleases else { return }

        // 第 2 步：没有通知权限就 return
        let isAllowed = await isAuthorized()
        guard isAllowed else { return }

        // 第 3 步：读一次 wishlist，失败记日志后 return
        let items: [WishlistItem]
        do {
            items = try await wishlistService.fetchItems(uid: uid)
        } catch {
            Logger.wishlist.error("alert check: fetch wishlist for \(uid, privacy: .private) failed / failed: \(String(describing: error))")
            return
        }

        // 第 4 步：开了降价通知就查降价
        if notifyPriceDrops {
            await checkPriceDrops(items: items, uid: uid, region: region, wishlistService: wishlistService)
        }

        // 第 5 步：开了发售通知就查发售
        if notifyReleases {
            await checkReleases(items: items, uid: uid, region: region, wishlistService: wishlistService)
        }
    }

    // MARK: - 降价 / Price drops

    /// 降价判断的结果。
    /// The outcome of a price-drop check.
    enum PriceDropAction: Equatable {
        /// 比已知低价更低：发通知，并把这个价格记成新的已知低价。
        /// Lower than the known low: notify, and record this price as the new known low.
        case notify(WishlistItem.KnownPrice)
        /// 回到原价且有已知低价：清掉，下次打折还能提醒。
        /// Back to full price with a known low: clear it so the next sale can alert again.
        case clear
        /// 什么都不做。
        /// Do nothing.
        case keep
    }

    /// 查一批游戏的价格，按结果发降价通知并回写 `knownLow`。
    /// Fetches prices for the items, sending price-drop notifications and writing back `knownLow`.
    ///
    /// `itadID` 为空的行跳过：Wishlist 页打开时会补上。
    /// Items without an `itadID` are skipped; the wishlist page fills it in when opened.
    ///
    /// - Parameters:
    ///   - items: 当前账号的收藏 / The account's wishlist items.
    ///   - uid: 当前账号 / The current account.
    ///   - region: 当前地区 / The current region.
    ///   - wishlistService: 用来回写的 Service / The service used for write-backs.
    private static func checkPriceDrops(
        items: [WishlistItem],
        uid: String,
        region: AppRegion,
        wishlistService: WishlistService
    ) async {
        // 4a. 只留 itadID 不为空的行，没有就不发请求
        // 4a. Keep only items with an itadID; send no request when there are none
        let itemsWithItadID = items.filter { item in item.itadID != nil }
        let uuids = itemsWithItadID.compactMap { item in item.itadID }
        guard !uuids.isEmpty else { return }

        // 4b. 一次查完；`prices/v3` 一批最多 200 个，wishlist 用不到，不分批
        // 4b. One request; `prices/v3` takes up to 200 per batch, more than a wishlist needs, so no batching
        let prices: ITADPricesResponse
        do {
            prices = try await ITADAPI.fetchPrices(uuids: uuids, region: region)
        } catch {
            Logger.wishlist.error("alert check: prices failed / failed: \(String(describing: error))")
            return
        }

        // 4c. 逐行判断；一行出问题只跳过这一行
        // 4c. Check each item; a problem with one item only skips that item
        for item in itemsWithItadID {
            guard let itadID = item.itadID else { continue }
            // 稀疏数组：没有条目或没有 Steam 能激活的报价就跳过
            // Sparse response: skip when there is no entry or no Steam-activating offer
            guard let game = prices.entry(for: itadID) else { continue }
            guard let deal = game.bestSteamDeal else { continue }

            let action = priceDropAction(item: item, deal: deal, region: region)
            switch action {
            case .notify(let knownLow):
                await sendPriceDropNotification(item: item, deal: deal)
                do {
                    try wishlistService.updateKnownLow(knownLow, appID: item.appID, uid: uid)
                } catch {
                    Logger.wishlist.error("alert check: save knownLow failed / failed: \(String(describing: error))")
                }
            case .clear:
                wishlistService.clearKnownLow(appID: item.appID, uid: uid)
            case .keep:
                break
            }
        }
    }

    /// 发一条降价通知，如「ELDEN RING is now A$39.95 (-50%)」。
    /// Sends a price-drop notification, such as "ELDEN RING is now A$39.95 (-50%)".
    ///
    /// identifier 固定是 `price-<appID>`，同一个游戏的新通知替换旧的。
    /// The identifier is always `price-<appID>`, so a newer notification for the same game replaces the old one.
    ///
    /// - Parameters:
    ///   - item: 降价的收藏 / The wishlist item on sale.
    ///   - deal: 这次查到的报价 / The offer just fetched.
    private static func sendPriceDropNotification(item: WishlistItem, deal: ITADPriceDeal) async {
        let formattedPrice = deal.price.amount.formatted(.currency(code: deal.price.currency))
        let body = "\(item.name) is now \(formattedPrice) (-\(deal.cut)%)"
        await sendNotification(identifier: "price-\(item.appID)", title: "Price Drop", body: body)
    }

    /// 只看数据，决定这一行要不要发降价通知。
    /// Decides from the data alone whether this item needs a price-drop notification.
    ///
    /// - Parameters:
    ///   - item: 这一行收藏 / The wishlist item.
    ///   - deal: 这次查到的 Steam 能激活的最低报价 / The cheapest Steam-activating offer just fetched.
    ///   - region: 当前地区 / The current region.
    /// - Returns: 要做的事 / What to do.
    static func priceDropAction(
        item: WishlistItem,
        deal: ITADPriceDeal,
        region: AppRegion
    ) -> PriceDropAction {
        // 没打折：有旧记录就清掉（包括别的地区留下的），下次打折还能提醒
        // Not on sale: clear any known low (including one from another region) so the next sale can alert again
        if deal.cut == 0 {
            if item.knownLow != nil {
                return .clear
            }
            return .keep
        }

        // 已知低价只在同一个地区才能比，别的地区当作没有
        // A known low is comparable only within the same region; another region's counts as none
        var lastAmount: Int?
        if let knownLow = item.knownLow, knownLow.region == region {
            lastAmount = knownLow.amount
        }

        let currentAmount = deal.price.amountInt
        let newKnownLow = WishlistItem.KnownPrice(amount: currentAmount, region: region)

        // 在打折、又没有已知低价：用户还不知道这次打折
        // On sale with no known low: the user hasn't heard about this sale
        guard let lastAmount else {
            return .notify(newKnownLow)
        }

        // 比已知低价更低才提醒，持平或回涨都不打扰
        // Alert only below the known low; equal or higher stays quiet
        if currentAmount < lastAmount {
            return .notify(newKnownLow)
        }
        return .keep
    }

    // MARK: - 发售 / Releases

    /// 向 Steam 核对还没发售的游戏，发售了就发通知并回写 `isComingSoon = false`。
    /// Checks unreleased games with Steam, notifying and writing back `isComingSoon = false` once released.
    ///
    /// 只看 `isComingSoon == true` 的行：Wishlist 页先核对到的已经是 `false`，不再提醒。
    /// Only items with `isComingSoon == true`; ones the wishlist page already found released are `false` and get no alert.
    ///
    /// - Parameters:
    ///   - items: 当前账号的收藏 / The account's wishlist items.
    ///   - uid: 当前账号 / The current account.
    ///   - region: 当前地区 / The current region.
    ///   - wishlistService: 用来回写的 Service / The service used for write-backs.
    private static func checkReleases(
        items: [WishlistItem],
        uid: String,
        region: AppRegion,
        wishlistService: WishlistService
    ) async {
        // TODO: 5a. 只留 isComingSoon == true 的行
        // TODO: 5b. 每一行 SteamAPI.fetchAppDetails(appID:region:)，失败或查不到就跳过这一行
        // TODO: 5c. details.isComingSoon == false → 发通知 + wishlistService.updateReleaseStatus(...)
    }

    // MARK: - 通知 / Notifications

    /// 是否有发通知的权限。
    /// Whether the app may send notifications.
    ///
    /// - Returns: 用户允许了返回 `true` / `true` when the user has allowed them.
    private static func isAuthorized() async -> Bool {
        let settings = await UNUserNotificationCenter.current().notificationSettings()

        switch settings.authorizationStatus {
        case .authorized, .provisional:
            return true
        case .denied, .notDetermined, .ephemeral:
            return false
        @unknown default:
            return false
        }
    }

    /// 立即发一条本地通知；同一个 identifier 会替换旧的。
    /// Sends a local notification right away; the same identifier replaces an earlier one.
    ///
    /// - Parameters:
    ///   - identifier: 通知 id，如 `"price-\(appID)"` / The notification id, e.g. `"price-\(appID)"`.
    ///   - title: 标题 / The title.
    ///   - body: 正文 / The body text.
    private static func sendNotification(identifier: String, title: String, body: String) async {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: identifier,
            content: content,
            // 立即发
            // Send right away
            trigger: nil
        )

        do {
            try await UNUserNotificationCenter.current().add(request)
        } catch {
            Logger.wishlist.error("send notification \(identifier, privacy: .public) failed / failed: \(String(describing: error))")
        }
    }
}
