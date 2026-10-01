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

    private(set) var dealState: DealLoadState = .loading
    
    private(set) var nextOffset : Int? = 0

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
    
    func loadHotDeals(region: AppRegion) async {
        dealState = .loading

        let body = requestBody(region: region, offset: 0)
        
        do {
            let response: ITADDealsResponse = try await ITADAPI.fetchDeals(body: body)
            if response.hasMore {
                nextOffset = response.nextOffset
            } else {
                nextOffset = nil
            }

            let items = response.list.map { deal in HomeDealItem(deal: deal) }
            dealState = .loaded(items)

            // fetch steam appIDs for the deals
            let uuids = items.map(\.id)
            do {
                let appIDByUUID = try await SteamAppIDResolver.resolveAppIDs(
                    uuids: uuids, region: region
                    )
                guard !Task.isCancelled else { return }

                // applyAppIDs：只更新 uuids 里的这些条目
                applyAppIDs(appIDByUUID)
            } catch {
                if Task.isCancelled { return }
                // 把这一页还在 .pending 的标成 .failed，loadState 不变
                settlePendingAsFailed(uuids)
                Logger.steam.error("resolveAppIDs failed: \(String(describing: error))")
            }

        } catch {
            if Task.isCancelled { return }
            Logger.games.error("loadHotDeals failed: \(String(describing: error))")
            dealState = .failed(LoadError.from(error))
        }
    }

    private func applyAppIDs(_ appIDByUUID: [String: Int]) {
        guard case .loaded(let items) = dealState else { return }

        dealState = .loaded(items.map { item in
            // 只更新 uuids 里的这些条目 / Only update the items whose uuids are in the dictionary
            guard appIDByUUID.keys.contains(item.id) else { return item }

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

