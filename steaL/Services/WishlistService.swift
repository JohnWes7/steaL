//
//  WishlistService.swift
//  steaL
//
//  Created by yijiang on 2026/9/23.
//

import Foundation
import FirebaseFirestore
import Observation
import OSLog

/// 当前账号在 Firestore 里的 wishlist：监听、单次读取与写入。
/// The current account's wishlist in Firestore: listening, one-off reads and writes.
@MainActor
@Observable
final class WishlistService {

    /// 当前账号的 wishlist，按加入时间倒序。
    /// The current account's wishlist, newest first.
    private(set) var items: [WishlistItem] = []

    /// 监听状态，Wishlist tab 用来区分「加载中」和「空列表」。
    /// Listening state; lets the Wishlist tab tell loading from empty.
    private(set) var state: WishlistState = .idle

    /// 由 `items` 派生，给心形按钮做 O(1) 判断。
    /// Derived from `items` for O(1) heart-button checks.
    private var appIDs: Set<Int> = []

    /// Firestore 实例。
    /// The Firestore instance.
    @ObservationIgnored private let database: Firestore

    /// 创建 Service。
    /// Creates the service.
    ///
    /// - Parameter database: 要用的 Firestore 实例 / The Firestore instance to use.
    init(database: Firestore = .firestore()) {
        self.database = database
    }

    /// 是否已收藏。
    /// Whether the game is on the wishlist.
    ///
    /// - Parameter appID: Steam App ID / Steam App ID.
    /// - Returns: 在当前列表里返回 `true` / `true` when it is in the current list.
    func contains(appID: Int) -> Bool {
        appIDs.contains(appID)
    }

    // MARK: - 监听 / Listening

    /// 监听某个账号的 wishlist，直到所在 Task 被取消。
    /// Listens to an account's wishlist until the enclosing task is cancelled.
    ///
    /// 由 `RootView` 的 `.task(id: uid)` 调用。
    /// Called from `RootView`'s `.task(id: uid)`.
    ///
    /// - Parameter uid: 当前账号，nil 表示未登录 / The current account; nil when signed out.
    func listen(uid: String?) async {
        // 换账号时先清掉上一个账号的数据
        // Drop the previous account's data before switching
        update(items: [])
        guard let uid else {
            state = .idle
            return
        }
        state = .loading

        let query = collection(for: uid).order(by: "addedAt", descending: true)
        do {
            // 这个是类似于callback flow的冷流 看一次拿一次数据
            // Task 取消时序列结束，会自动移除监听
            for try await snapshot in query.snapshots {
                // 已经轮到下一个账号时，丢掉旧账号迟到的快照
                // Drop a late snapshot from the old account once the next one has started
                guard !Task.isCancelled else { return }
                update(items: snapshot.documents.compactMap { try? $0.data(as: WishlistItem.self) })
                state = .loaded
            }
        } catch {
            guard !Task.isCancelled else { return }
            Logger.wishlist.error("listen failed / failed: \(String(describing: error))")
            state = .failed(error)
        }
    }

    // MARK: - 单次读取 / One-off reads

    /// 读一次某个账号的 wishlist，不监听。
    /// Reads an account's wishlist once, without listening.
    ///
    /// 给提醒检查用：后台唤醒时没有界面，`RootView` 里正在监听的那个实例不在，拿不到 `items`。
    /// 离线时 Firestore 会退回本地缓存。
    /// For the alert check: a background launch has no UI, so the listening instance held by `RootView` isn't there to provide `items`.
    /// Offline, Firestore falls back to its local cache.
    ///
    /// - Parameter uid: 当前账号 / The current account.
    /// - Returns: 解码成功的收藏，解码失败的文档跳过（同监听）/ The decoded items; documents that fail to decode are skipped, as when listening.
    /// - Throws: Firestore 读取失败时抛出 / Throws when the Firestore read fails.
    func fetchItems(uid: String) async throws -> [WishlistItem] {
        let snapshot = try await collection(for: uid).getDocuments()
        let items = snapshot.documents.compactMap { document in
            try? document.data(as: WishlistItem.self)
        }
        return items
    }

    // MARK: - 写入 / Writes

    /// 加入 wishlist，已存在则覆盖。
    /// Adds to the wishlist, overwriting an existing entry.
    ///
    /// 写入都用同步版本：async 版本要等服务器确认，离线时会一直挂起；同步版本先进本地队列，监听器立刻推送。
    /// Writes use the synchronous overloads: the async ones wait for the server and hang offline, while these queue locally
    /// and the listener fires at once.
    ///
    /// - Parameters:
    ///   - item: 要保存的快照 / The snapshot to save.
    ///   - uid: 当前账号 / The current account.
    /// - Throws: 编码失败时抛出 / Throws on an encoding failure.
    func add(_ item: WishlistItem, uid: String) throws {
        try collection(for: uid).document(String(item.appID)).setData(from: item)
    }

    /// 移出 wishlist，不存在也不报错。
    /// Removes from the wishlist; a missing entry is not an error.
    ///
    /// - Parameters:
    ///   - appID: Steam App ID / Steam App ID.
    ///   - uid: 当前账号 / The current account.
    func remove(appID: Int, uid: String) {
        collection(for: uid).document(String(appID)).delete()
    }

    /// 写入 Steam 核对后的发售状态，并记下核对时间。
    /// Writes the release status checked with Steam, along with when it was checked.
    ///
    /// 回写都用 `updateData`：只改给出的字段，文档已被删掉时会失败，而不是重新建出来（`setData(merge:)` 会建）。
    /// Write-backs use `updateData`: it touches only the given fields and fails on a removed document instead of recreating it,
    /// as `setData(merge:)` would.
    ///
    /// - Parameters:
    ///   - isComingSoon: 核对结果 / The checked result.
    ///   - appID: Steam App ID / Steam App ID.
    ///   - uid: 当前账号 / The current account.
    func updateReleaseStatus(isComingSoon: Bool, appID: Int, uid: String) {
        let fields: [String: Any] = [
            "isComingSoon": isComingSoon,
            "releaseCheckedAt": Date.now,
        ]
        collection(for: uid).document(String(appID)).updateData(fields)
    }

    /// 写入 lookup 到的 ITAD UUID，之后查价不用再 lookup。
    /// Writes a looked-up ITAD UUID so later price loads skip the lookup.
    ///
    /// - Parameters:
    ///   - itadID: ITAD 游戏 UUID / ITAD game UUID.
    ///   - appID: Steam App ID / Steam App ID.
    ///   - uid: 当前账号 / The current account.
    func updateITADID(_ itadID: String, appID: Int, uid: String) {
        collection(for: uid).document(String(appID)).updateData(["itadID": itadID])
    }

    /// 记下这次发降价通知时的价格。
    /// Records the price this price-drop notification was sent at.
    ///
    /// 用 `Firestore.Encoder` 编码，map 里的键和 `NotifiedPrice` 的 Codable 键保持一致，不手写。
    /// Encoded with `Firestore.Encoder` so the map keys match `NotifiedPrice`'s Codable keys instead of being written by hand.
    ///
    /// - Parameters:
    ///   - notifiedPrice: 通知时的价格与地区 / The price and region at notification time.
    ///   - appID: Steam App ID / Steam App ID.
    ///   - uid: 当前账号 / The current account.
    /// - Throws: 编码失败时抛出，固定结构的模型不会发生 / Throws on an encoding failure, which this fixed model never hits.
    func updateLastNotifiedLow(_ notifiedPrice: WishlistItem.NotifiedPrice, appID: Int, uid: String) throws {
        let encodedPrice = try Firestore.Encoder().encode(notifiedPrice)
        let fields: [String: Any] = ["lastNotifiedLow": encodedPrice]
        collection(for: uid).document(String(appID)).updateData(fields)
    }

    /// 清掉上次通知的价格，下次打折还能再提醒。
    /// Clears the last notified price so the next sale can alert again.
    ///
    /// 删掉字段而不是写 `null`，解码时同样是 nil。
    /// Deletes the field rather than writing `null`; it decodes as nil either way.
    ///
    /// - Parameters:
    ///   - appID: Steam App ID / Steam App ID.
    ///   - uid: 当前账号 / The current account.
    func clearLastNotifiedLow(appID: Int, uid: String) {
        let fields: [String: Any] = ["lastNotifiedLow": FieldValue.delete()]
        collection(for: uid).document(String(appID)).updateData(fields)
    }

    /// 删掉某个账号的整个 wishlist。
    /// Deletes an account's whole wishlist.
    ///
    /// 删账号前调用：账号删了以后 Rules 就不再放行，数据会删不掉。客户端没有「删整个集合」的接口，只能取出来逐条删；
    /// 用 async 版本，因为要等服务器确认删完才能接着删账号。一个 batch 上限 500 条，wishlist 用不到。
    /// Called before deleting the account, since afterwards Rules no longer allow it. Clients can't delete a whole collection,
    /// so fetch and delete each document; the async overloads wait for the server so the account is deleted only after.
    /// A batch holds 500 writes, plenty here.
    ///
    /// - Parameter uid: 当前账号 / The current account.
    /// - Throws: 读取或提交失败时抛出 / Throws when the read or the commit fails.
    func deleteAll(uid: String) async throws {
        let snapshot = try await collection(for: uid).getDocuments()
        let batch = database.batch()
        for document in snapshot.documents {
            batch.deleteDocument(document.reference)
        }
        try await batch.commit()
    }

    // MARK: - 私有 / Private

    /// `users/{uid}/wishlist` 集合。
    /// The `users/{uid}/wishlist` collection.
    ///
    /// - Parameter uid: 账号 / The account.
    /// - Returns: 这个账号的 wishlist 集合 / That account's wishlist collection.
    private func collection(for uid: String) -> CollectionReference {
        database.collection("users").document(uid).collection("wishlist")
    }

    /// 同时更新列表和派生的 appID 集合。
    /// Updates the list and its derived appID set together.
    ///
    /// - Parameter items: 新的列表 / The new list.
    private func update(items: [WishlistItem]) {
        appIDs = Set(items.map(\.appID))
        self.items = items
    }
}

/// Wishlist 监听状态。
/// Wishlist listening state.
enum WishlistState {
    /// 未登录，不监听。
    /// Signed out, not listening.
    case idle
    /// 等第一份快照。
    /// Waiting for the first snapshot.
    case loading
    /// 已收到快照。
    /// A snapshot has arrived.
    case loaded
    /// 监听被拒绝或中断（如 Rules 不允许）。
    /// Listening was rejected or interrupted (e.g. by Rules).
    case failed(Error)
}
