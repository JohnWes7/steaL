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

    /// 当前列表属于哪个请求，第一页开始加载时就换成新请求。
    /// The request the current list belongs to, switched as soon as a first page starts loading.
    ///
    /// 下一页跑在不跟视图绑定的 `Task` 里，换了地区或大类也不会被取消；回来时请求对不上就丢掉，不拼进新列表。
    /// The next page runs in a `Task` not tied to the view, so a new region or genre does not cancel it;
    /// a result whose request no longer matches is dropped instead of being appended to the new list.
    private var listRequest: HotDealsRequest?

    /// 组 `deals/v2` 的请求体，选了大类就加上 `filter.tags`。
    /// Builds the `deals/v2` request body, adding `filter.tags` when a genre is chosen.
    ///
    /// - Parameters:
    ///   - request: 地区与大类 / The region and genre.
    ///   - offset: 分页偏移 / The page offset.
    /// - Returns: 请求体 / The request body.
    private func requestBody(request: HotDealsRequest, offset: Int) -> [String: Any] {
        var filter: [String: Any] = [
            "type": [1],
            "drm": [61],
        ]

        // `tags` 是「同时有这些标签」，只选一个大类时和 `tagsUnion` 一样 /
        // `tags` means "has all these tags", the same as `tagsUnion` for a single genre
        if let genre = request.genre {
            filter["tags"] = [genre.rawValue]
        }

        return [
            "country": request.region.apiRegionCode,
            "offset": offset,
            "limit": 20,
            "sort": "-hot",
            "nondeals": false,
            "filter": filter,
        ]
    }

    // MARK: - loadIfNeeded
    
    /// 同样的地区与大类已经加载过就跳过，否则加载第一页。
    /// Skips when this region and genre are already loaded; otherwise loads the first page.
    ///
    /// `.task` 在页面每次出现时都会跑（从详情页返回也算），重新加载会把列表打回 `.loading`、丢掉已翻的页。
    /// `.task` runs on every appearance, including coming back from a detail page, and reloading would reset the list
    /// to `.loading` and lose the loaded pages.
    ///
    /// - Parameter request: 地区与大类 / The region and genre.
    func loadIfNeeded(request: HotDealsRequest) async {
        // 被取消的加载（页面消失）停在 `.loading`，下次出现会重新加载 /
        // A cancelled load (page gone) stays `.loading`, so the next appearance loads again
        let isSameRequest = listRequest == request
        let isLoading = dealState == .loading
        if isSameRequest && !isLoading { return }

        await loadHotDeals(request: request)
    }

    /// 加载第一页，失败后的重试也走这里。
    /// Loads the first page; a retry after a failure comes here too.
    ///
    /// - Parameter request: 地区与大类 / The region and genre.
    func loadHotDeals(request: HotDealsRequest) async {
        dealState = .loading
        nextPageState = .idle
        listRequest = request

        let body = requestBody(request: request, offset: 0)
        
        do {
            let response: ITADDealsResponse = try await ITADAPI.fetchDeals(body: body)
            // 换了条件或页面消失，旧结果不写回 / A cancelled load (conditions changed or page gone) does not write back
            guard !Task.isCancelled else { return }
            // 重试按钮的 `Task` 不会被取消，靠请求对不上来挡 / The retry button's `Task` is never cancelled, so a mismatched request stops it
            guard listRequest == request else { return }

            if response.hasMore {
                nextOffset = response.nextOffset
            } else {
                nextOffset = nil
            }

            let items = response.list.map { deal in HomeDealItem(deal: deal) }
            dealState = .loaded(items)

            let uuids = items.map(\.id)
            Task {
                await resolveAppIDs(uuids: uuids, region: request.region)
            }
        } catch {
            if Task.isCancelled { return }
            guard listRequest == request else { return }
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
    /// - Parameter request: 地区与大类，要和当前列表的一致 / The region and genre, matching the current list's.
    func loadNextPage(request: HotDealsRequest) async {
        // 没有下一页、或者已经在加载，就不重复请求
        guard listRequest == request else { return }
        guard let offset = nextOffset else { return }
        guard nextPageState != .loading else { return }
        nextPageState = .loading

        do {
            let body = requestBody(request: request, offset: offset)
            let response = try await ITADAPI.fetchDeals(body: body)

            // 请求期间换了地区或大类：新列表已经重置了分页状态，这一页直接丢掉 /
            // The region or genre changed meanwhile: the new list has reset its paging state, so drop this page
            guard listRequest == request else { return }
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
                await resolveAppIDs(uuids: uuids, region: request.region)
            }
        } catch {
            guard listRequest == request else { return }
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

