//
//  GamesViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/10.
//

import Foundation

@MainActor
@Observable
final class GamesViewModel {
    
    var itadDealsData: ITADDealsData?
    var isLoading: Bool = false
    
    var errorMessage: String?  = nil
    
    var requestBody: [String: Any] = [
        "country": "AU",
        "offset": 0,
        "limit": 20,
//        "sort": "-cut",
        "nondeals": false,
        "filter": [
            "type": [1],
            "wasSpotlight": true,
            "drm": [61],
        ],
    ]
    
    init() {
        isLoading = true

        Task {
            await fetchDeals(body: requestBody)
        }
    }

    func fetchDeals(body: [String: Any]) async {
        isLoading = true
        defer { isLoading = false }

        do {
            let data = try await ITADAPI.fetchDeals(body: body)
            itadDealsData = data
            errorMessage = nil

        } catch {
            // 打印完整错误（含服务端 body）/ Log the full error including the server body
            print("[GamesViewModel] fetchDeals failed / failed: \(error)")
            if let itadError = error as? ITADAPIError {
                print("[GamesViewModel] detailed error / detail: \(itadError.debugDescription)")
            }

            errorMessage = error.localizedDescription
        }
    }

    /// 切换单个游戏的收藏状态 / Toggles the favourite state of a single game
    func toggleFavorite(_ id: String) {

    }
    
    func isFavorite(_ id: String) -> Bool {
        return false
    }
}
