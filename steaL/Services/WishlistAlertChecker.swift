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
        if !notifyPriceDrops && !notifyReleases { return }

        // TODO: 第 2 步：没有通知权限就 return（调 isAuthorized()）
        let isAllowed = await isAuthorized()
        guard isAllowed else { return }

        // TODO: 第 3 步：wishlistService.fetchItems(uid:) 读一次 wishlist，失败记日志后 return
        var items: [WishlistItem] = []
        do {
            items = try await wishlistService.fetchItems(uid: uid)
        } catch {
            Logger.wishlist.error("Failed to fetch wishlist items for uid \(uid, privacy: .private): \(error.localizedDescription, privacy: .public)")
            return
        }
        
        // TODO: 第 4 步：开了降价通知就 await checkPriceDrops(...)
        if notifyPriceDrops {
            await checkPriceDrops(items: items, uid: uid, region: region, wishlistService: wishlistService)
        }

        // TODO: 第 5 步：开了发售通知就 await checkReleases(...)
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
        // TODO: 4a. 只留 itadID 不为空的行
        // TODO: 4b. ITADAPI.fetchPrices(uuids:region:) 查价，失败记日志后 return
        // TODO: 4c. 每一行：response.entry(for:) → bestSteamDeal → priceDropAction(...) → 按结果发通知 / 回写
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
        // TODO: 第 4 步的核心判断，见讲解
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
            trigger: nil // 立即发 / Send right away
        )

        do {
            try await UNUserNotificationCenter.current().add(request)
        } catch {
            Logger.wishlist.error("Failed to send notification \(identifier, privacy: .public): \(error.localizedDescription, privacy: .public)")
        }
    }
}
