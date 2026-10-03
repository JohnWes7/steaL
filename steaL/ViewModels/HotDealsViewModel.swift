//
//  HotDealsViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/10/1.
//

import Foundation
import OSLog

@MainActor
@Observable
final class HotDealsViewModel {
    enum DealLoadState: Equatable {
        /// 首次加载或重试中 / First load, or a retry in progress
        case loading
        /// 请求成功，列表可能为空；装 `HomeDealItem` 是因为渲染只要「deal + appID」/
        /// Request succeeded, the list may still be empty; carries `HomeDealItem` because rendering
        /// only needs deal plus appID
        case loaded([HomeDealItem])
        /// 请求失败，携带归一化后的错误 / Request failed, carrying the normalised error
        case failed(LoadError)
    }

    enum NextPageState { 
        case idle, loading, failed 
    }

    private(set) var dealState: DealLoadState = .loading
    private(set) var nextPageState: NextPageState = .idle

    private(set) var nextOffset : Int? = 0

    /// 记录上一次的 region，避免重复请求 / Records the last region to avoid duplicate requests
    private var loadedRegion: AppRegion?

    private func requestBody(region: AppRegion, offset: Int) -> [String: Any] {
        [
            "country": region.apiRegionCode,
            "offset": offset,
            "limit": 20,
            "sort": "-hot",
            "nondeals": false,
            "filter": [
                "type": [1],
                "drm": [61],
            ],
        ]
    }

    // MARK: - loadIfNeeded
    
    /// 同一地区已经加载过就跳过，否则加载第一页。
    /// Skips when this region is already loaded; otherwise loads the first page.
    ///
    /// `.task` 在页面每次出现时都会跑（从详情页返回也算），重新加载会把列表打回 `.loading`、丢掉已翻的页。
    /// `.task` runs on every appearance, including coming back from a detail page, and reloading would reset the list
    /// to `.loading` and lose the loaded pages.
    ///
    /// - Parameter region: 商店区域 / The store region.
    func loadIfNeeded(region: AppRegion) async {
        guard loadedRegion != region else { return }

        await loadHotDeals(region: region)

        // 中途被取消（页面消失）的不算，下次出现再加载 / A cancelled load does not count; the next appearance loads again
        guard !Task.isCancelled else { return }
        loadedRegion = region
    }

    /// 加载第一页，失败后的重试也走这里。
    /// Loads the first page; a retry after a failure comes here too.
    ///
    /// - Parameter region: 商店区域 / The store region.
    func loadHotDeals(region: AppRegion) async {
        dealState = .loading
        nextPageState = .idle

        let body = requestBody(region: region, offset: 0)
        
        do {
            let response: ITADDealsResponse = try await ITADAPI.fetchDeals(body: body)
            // 换了地区或页面消失，旧结果不写回 / A cancelled load (region changed or page gone) does not write back
            guard !Task.isCancelled else { return }

            if response.hasMore {
                nextOffset = response.nextOffset
            } else {
                nextOffset = nil
            }

            let items = response.list.map { deal in HomeDealItem(deal: deal) }
            dealState = .loaded(items)

            let uuids = items.map(\.id)
            Task {
                await resolveAppIDs(uuids: uuids, region: region)
            }
        } catch {
            if Task.isCancelled { return }
            Logger.games.error("loadHotDeals failed: \(String(describing: error))")
            dealState = .failed(LoadError.from(error))
        }
    }

    // MARK: - loadNextPage

    /// 加载下一页并拼到列表后面，由列表底部的「Load More」/ 重试按钮触发。
    /// Loads the next page and appends it, triggered by the list footer's Load More or retry button.
    ///
    /// 被取消（底部视图离开屏幕）回到 `.idle`，底部视图再出现会自动重新加载；只有请求失败才是 `.failed`。
    /// A cancellation (the footer left the screen) goes back to `.idle`, so the footer reloads when it reappears;
    /// only a failed request becomes `.failed`.
    ///
    /// - Parameter region: 商店区域 / The store region.
    func loadNextPage(region: AppRegion) async {
        // 没有下一页、或者已经在加载，就不重复请求
        guard let offset = nextOffset else { return }
        guard nextPageState != .loading else { return }
        nextPageState = .loading

        do {
            let body = requestBody(region: region, offset: offset)
            let response = try await ITADAPI.fetchDeals(body: body)
            guard !Task.isCancelled else { nextPageState = .idle; return }

            // 1. 更新 nextOffset（同第一页的写法）
            if response.hasMore {
                nextOffset = response.nextOffset
            } else {
                nextOffset = nil
            }

            // 2. 在 await 之后重新读列表：请求期间上一页补 appID 的结果可能已经写回 /
            //    Re-read the list after the await: the previous page's appIDs may have landed meanwhile
            guard case .loaded(let currentItems) = dealState else {
                nextPageState = .idle
                return
            }

            // 按 ITAD UUID 去重：`-hot` 排序在两次请求之间会变，新一页可能带着已有的游戏 /
            // Dedupe by ITAD UUID: the `-hot` order shifts between requests, so a new page may repeat games
            let existingIDs = Set(currentItems.map(\.id))
            let newDeals = response.list.filter { deal in
                !existingIDs.contains(deal.id)
            }
            let newItems = newDeals.map { deal in
                HomeDealItem(deal: deal)
            }

            dealState = .loaded(currentItems + newItems)

            // 3. 这一页已经拼进列表 / This page is in the list now
            nextPageState = .idle

            // 4. 补 appID 另开一个 Task：改了 `nextOffset` 后底部视图的 `.task(id:)` 会被取消，跟着它走的话每一页都查不完 /
            //    Resolve appIDs in a separate Task: changing `nextOffset` cancels the footer's `.task(id:)`,
            //    so riding along with it would cut off every page's lookup
            let uuids = newItems.map(\.id)
            Task {
                await resolveAppIDs(uuids: uuids, region: region)
            }
        } catch {
            if Task.isCancelled { nextPageState = .idle; return }
            Logger.games.error("loadNextPage failed: \(String(describing: error))")
            nextPageState = .failed  // 列表保留，只在底部显示 Retry
        }
    } 

    // MARK: - 私有 / Private helpers

    /// 给一页条目补 Steam appID 并写回列表。
    /// Resolves Steam appIDs for one page of items and writes them back into the list.
    ///
    /// 第一页与下一页共用；请求失败时把这一页还在 `.pending` 的落定为 `.failed`，列表本身不变。
    /// Shared by the first and later pages; on failure this page's `.pending` items settle as `.failed`
    /// while the list itself stays.
    ///
    /// - Parameters:
    ///   - uuids: 这一页的 ITAD UUID / This page's ITAD UUIDs.
    ///   - region: 商店区域，校验多个候选时用 / The store region, used when checking several candidates.
    private func resolveAppIDs(uuids: [String], region: AppRegion) async {
        do {
            let appIDByUUID = try await SteamAppIDResolver.resolveAppIDs(uuids: uuids, region: region)
            guard !Task.isCancelled else { return }

            applyAppIDs(appIDByUUID, to: uuids)
        } catch {
            if Task.isCancelled { return }

            Logger.games.error("resolveAppIDs failed: \(String(describing: error))")
            settlePendingAsFailed(uuids)
        }
    }

    private func applyAppIDs(_ appIDByUUID: [String: Int], to uuids: [String]) {
        guard case .loaded(let items) = dealState else { return }

        dealState = .loaded(items.map { item in
            // 只更新 uuids 里的这些条目 / Only update the items whose uuids are in the dictionary
            guard uuids.contains(item.id) else { return item }

            var updatedItem = item

            if let appID = appIDByUUID[item.id] {
                updatedItem.steam = .resolved(appID)
            } else {
                updatedItem.steam = .notFound
            }

            return updatedItem
        })
    }

    /// 补 appID 的请求失败时，把这一页还在等待中的条目落定为 `.failed`
    private func settlePendingAsFailed(_ uuids: [String]) {
        guard case .loaded(let items) = dealState else { return }

        dealState = .loaded(
            items.map { item in
                guard uuids.contains(item.id) else { return item }
                guard item.steam == .pending else { return item }

                var updatedItem = item
                updatedItem.steam = .failed
                return updatedItem
            })
    }
}

