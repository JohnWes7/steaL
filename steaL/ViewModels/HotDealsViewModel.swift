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

    private(set) var loadState: DealLoadState = .loading
    
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
        loadState = .loading

        let body = requestBody(region: region, offset: 0)
        
        do {
            let response: ITADDealsResponse = try await ITADAPI.fetchDeals(body: body)
            if response.hasMore {
                nextOffset = response.nextOffset
            } else {
                nextOffset = nil
            }

            let items = response.list.map { deal in HomeDealItem(deal: deal) }
            loadState = .loaded(items)

            // fetch steam appIDs for the deals
        } catch {
            if Task.isCancelled { return }
            Logger.games.error("loadHotDeals failed: \(String(describing: error))")
            loadState = .failed(LoadError.from(error))
        }
    }
}

