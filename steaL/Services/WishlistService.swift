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

@MainActor
@Observable
final class WishlistService {

    /// 当前账号的 wishlist，按加入时间倒序 / The current account's wishlist, newest first
    private(set) var items: [WishlistItem] = []

    /// 监听状态，Wishlist tab 用来区分「加载中」和「空列表」/ Listening state; lets the Wishlist tab tell loading from empty
    private(set) var state: WishlistState = .idle

    /// 由 `items` 派生，给心形按钮做 O(1) 判断 / Derived from `items` for O(1) heart-button checks
    private var appIDs: Set<Int> = []

    /// Firestore 实例 / Firestore instance
    @ObservationIgnored private let database: Firestore

    init(database: Firestore = .firestore()) {
        self.database = database
    }

    /// 是否已收藏 / Whether the game is on the wishlist
    /// - Parameter appID: Steam App ID
    func contains(appID: Int) -> Bool {
        appIDs.contains(appID)
    }

    // MARK: - 监听 / Listening

    /// 监听某个账号的 wishlist，直到所在 Task 被取消；由 `.task(id: uid)` 调用 /
    /// Listens to an account's wishlist until the enclosing task is cancelled; called from `.task(id: uid)`
    /// - Parameter uid: 当前账号，nil 表示未登录 / The current account; nil when signed out
    func listen(uid: String?) async {
        // 换账号时先清掉上一个账号的数据 / Drop the previous account's data before switching
        update(items: [])
        guard let uid else {
            state = .idle
            return
        }
        state = .loading

        let query = collection(for: uid).order(by: "addedAt", descending: true)
        do {
            // Task 取消时序列结束，SDK 在 onTermination 里移除监听器 / Cancelling the task ends the sequence; the SDK removes the listener on termination
            for try await snapshot in query.snapshots {
                // 已经轮到下一个账号时，丢掉旧账号迟到的快照 / Drop a late snapshot from the old account once the next one has started
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

    // MARK: - 写入 / Writes

    // 不用 async 版本：它要等服务器确认，离线时会一直挂起；同步版本先进本地队列，监听器立刻推送 /
    // Not the async overloads: they wait for the server and hang offline; these queue locally and the listener fires at once
    /// 加入 wishlist，已存在则覆盖 / Adds to the wishlist, overwriting an existing entry
    /// - Parameter item: 要保存的快照 / The snapshot to save
    /// - Parameter uid: 当前账号 / The current account
    func add(_ item: WishlistItem, uid: String) throws {
        try collection(for: uid).document(String(item.appID)).setData(from: item)
    }

    /// 移出 wishlist，不存在也不报错 / Removes from the wishlist; a missing entry is not an error
    /// - Parameter appID: Steam App ID
    /// - Parameter uid: 当前账号 / The current account
    func remove(appID: Int, uid: String) {
        collection(for: uid).document(String(appID)).delete()
    }

    // `updateData` 只改给出的字段；文档已被删掉时会失败而不是重新建出来（`setData(merge:)` 会建）/
    // `updateData` touches only the given fields and fails on a removed document instead of recreating it (as `setData(merge:)` would)
    /// 写入 Steam 核对后的发售状态，并记下核对时间 / Writes the release status checked with Steam, along with when it was checked
    /// - Parameter isComingSoon: 核对结果 / The checked result
    /// - Parameter appID: Steam App ID
    /// - Parameter uid: 当前账号 / The current account
    func updateReleaseStatus(isComingSoon: Bool, appID: Int, uid: String) {
        let fields: [String: Any] = [
            "isComingSoon": isComingSoon,
            "releaseCheckedAt": Date.now,
        ]
        collection(for: uid).document(String(appID)).updateData(fields)
    }

    /// 写入 lookup 到的 ITAD UUID，之后查价不用再 lookup / Writes a looked-up ITAD UUID so later price loads skip the lookup
    /// - Parameter itadID: ITAD 游戏 UUID / ITAD game UUID
    /// - Parameter appID: Steam App ID
    /// - Parameter uid: 当前账号 / The current account
    func updateITADID(_ itadID: String, appID: Int, uid: String) {
        collection(for: uid).document(String(appID)).updateData(["itadID": itadID])
    }

    // 删账号前调用：账号删了以后 Rules 就不再放行，数据会删不掉。客户端没有「删整个集合」的接口，只能取出来逐条删；
    // 用 async 版本，因为要等服务器确认删完才能接着删账号。一个 batch 上限 500 条，wishlist 用不到 /
    // Called before deleting the account: afterwards Rules no longer allow it. Clients can't delete a whole collection, so fetch and
    // delete each document; the async overloads wait for the server so the account is deleted only after. A batch holds 500 writes, plenty here
    /// 删掉某个账号的整个 wishlist / Deletes an account's whole wishlist
    /// - Parameter uid: 当前账号 / The current account
    func deleteAll(uid: String) async throws {
        let snapshot = try await collection(for: uid).getDocuments()
        let batch = database.batch()
        for document in snapshot.documents {
            batch.deleteDocument(document.reference)
        }
        try await batch.commit()
    }

    // MARK: - 私有 / Private

    /// `users/{uid}/wishlist` 集合 / The `users/{uid}/wishlist` collection
    private func collection(for uid: String) -> CollectionReference {
        database.collection("users").document(uid).collection("wishlist")
    }

    /// 同时更新列表和派生的 appID 集合 / Updates the list and its derived appID set together
    private func update(items: [WishlistItem]) {
        appIDs = Set(items.map(\.appID))
        self.items = items
    }
}

/// Wishlist 监听状态 / Wishlist listening state
enum WishlistState {
    /// 未登录，不监听 / Signed out, not listening
    case idle
    /// 等第一份快照 / Waiting for the first snapshot
    case loading
    /// 已收到快照 / A snapshot has arrived
    case loaded
    /// 监听被拒绝或中断（如 Rules 不允许）/ Listening was rejected or interrupted (e.g. by Rules)
    case failed(Error)
}
