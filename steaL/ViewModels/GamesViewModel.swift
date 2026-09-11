//
//  GamesViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/10.
//

import Foundation

/// 面向 UI 的错误类型：把网络层的各种失败归一成几种用户能理解的状况，
/// 每种自带固定英文文案与 SF Symbol /
/// UI-facing error: normalises network failures into a few actionable cases, each with fixed
/// English copy and an SF Symbol
enum LoadError: Error, Equatable {
    /// 无网络，或连不上主机 / No connection, or the host cannot be reached
    case offline
    /// 服务端返回非 2xx / The server answered with a non-2xx status
    case server(statusCode: Int)
    /// 响应体与 DTO 对不上 / The response body does not match the DTOs
    case decoding
    /// 其余未归类失败 / Anything left over
    case unknown

    /// 面向用户的标题 / User-facing title
    var title: String {
        switch self {
        case .offline: "You're Offline"
        case .server: "Server Unavailable"
        case .decoding: "Unexpected Response"
        case .unknown: "Something Went Wrong"
        }
    }

    /// 面向用户的说明 / User-facing message
    var message: String {
        switch self {
        case .offline: "Check your internet connection and try again."
        case .server: "The service is having trouble right now. Please try again in a moment."
        case .decoding: "We received data we couldn't read. Please try again later."
        case .unknown: "We couldn't load this right now. Please try again."
        }
    }

    /// 交给 `ContentUnavailableView` 使用的 SF Symbol / SF Symbol handed to `ContentUnavailableView`
    var symbolName: String {
        switch self {
        case .offline: "wifi.slash"
        case .server: "exclamationmark.icloud"
        case .decoding: "questionmark.circle"
        case .unknown: "exclamationmark.triangle"
        }
    }

    /// 把底层抛出的任意错误归一化 / Normalises whatever the lower layers threw
    static func from(_ error: Error) -> LoadError {
        switch error {
        case let urlError as URLError:
            switch urlError.code {
            case .notConnectedToInternet, .networkConnectionLost, .cannotFindHost,
                 .cannotConnectToHost, .timedOut, .dataNotAllowed:
                return .offline
            default:
                return .unknown
            }

        case let apiError as ITADAPIError:
            switch apiError {
            case .invalidResponse:
                return .unknown
            case .requestFailed(let statusCode, _):
                return .server(statusCode: statusCode)
            }

        case is DecodingError:
            return .decoding

        default:
            return .unknown
        }
    }
}

@MainActor
@Observable
final class GamesViewModel {

    /// Deals 列表状态机 / State machine for the deal list
    enum DealLoadState: Equatable {
        /// 首次加载或重试中 / First load, or a retry in progress
        case loading
        /// 请求成功，注意列表本身可能为空 / Request succeeded; the list itself may still be empty
        case loaded(ITADDealsData)
        /// 请求失败，携带归一化后的错误 / Request failed, carrying the normalised error
        case failed(LoadError)
    }

    /// Giveaways 区块的独立状态机：与 deals 互不影响 /
    /// Independent state machine for the giveaways section; it never affects the deals state
    enum GiveawaysLoadState: Equatable {
        /// 首次加载或重试中 / First load, or a retry in progress
        case loading
        /// 请求成功，列表可能为空 / Request succeeded; the list itself may still be empty
        case loaded(ITADGiveawaysData)
        /// 请求失败，携带归一化后的错误 / Request failed, carrying the normalised error
        case failed(LoadError)
    }

    /// Deals 列表当前状态 / Current state of the deal list
    private(set) var dealState: DealLoadState = .loading

    /// Giveaways 区块当前状态 / Current state of the giveaways section
    private(set) var giveawaysState: GiveawaysLoadState = .loading

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
        Task {
            await fetchDeals(body: requestBody)
        }
        Task {
            await fetchGiveaways()
        }
    }

    /// 拉取优惠列表 / Fetches the deal list
    func fetchDeals(body: [String: Any]) async {
        dealState = .loading

        do {
            let data = try await ITADAPI.fetchDeals(body: body)
            dealState = .loaded(data)

        } catch {
            // 完整错误只进日志，界面只看到 `LoadError` / Log the full error; the UI only sees `LoadError`
            print("[GamesViewModel] fetchDeals failed / failed: \(error)")
            if let itadError = error as? ITADAPIError {
                print("[GamesViewModel] detailed error / detail: \(itadError.debugDescription)")
            }

            dealState = .failed(LoadError.from(error))
        }
    }

    /// 重试 deals，只影响第一个区块 / Retries deals; only the first section is affected
    func retryDeals() async {
        await fetchDeals(body: requestBody)
    }

    /// 拉取限时免费领取列表 / Fetches the giveaways list
    func fetchGiveaways() async {
        giveawaysState = .loading

        do {
            let giveaways = try await ITADAPI.fetchGiveaways()
            giveawaysState = .loaded(giveaways)

        } catch {
            // 完整错误只进日志，界面只看到 `LoadError` / Log the full error; the UI only sees `LoadError`
            print("[GamesViewModel] fetchGiveaways failed / failed: \(error)")
            if let itadError = error as? ITADAPIError {
                print("[GamesViewModel] detailed error / detail: \(itadError.debugDescription)")
            }

            giveawaysState = .failed(LoadError.from(error))
        }
    }

    /// 重试 giveaways，只影响第二个区块 / Retries giveaways; only the second section is affected
    func retryGiveaways() async {
        await fetchGiveaways()
    }

    /// 切换单个游戏的收藏状态 / Toggles the favourite state of a single game
    func toggleFavorite(_ id: String) {

    }
    
    func isFavorite(_ id: String) -> Bool {
        return false
    }
}
