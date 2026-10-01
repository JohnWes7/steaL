//
//  HotDealsViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/10/1.
//

import Foundation

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
    
    

    private func requestBody(region: AppRegion) -> [String: Any] {
        [
            "country": region.apiRegionCode,
            "offset": 0,
            "limit": 20,
            "sort": "-hot",
            "nondeals": false,
            "filter": [
                "type": [1],
                "drm": [61],
            ],
        ]
    }
    
    func fetchHotDeals(region: AppRegion) async {
        loadState = .loading
        let body = requestBody(region: region)
        
        do {
            let response: ITADDealsResponse = try await ITADAPI.fetchDeals(body: body)
            let items = response.list.map { HomeDealItem(deal: $0) }
            loadState = .loaded(items)


        } catch {
            
        }
    }
}

