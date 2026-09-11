//
//  GamesViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/10.
//

import Foundation

/// 面向 UI 的错误类型：把网络层的各种失败（`URLError`、`ITADAPIError`、`DecodingError`）
/// 归一成少数几种用户能理解的状况，每种都自带固定英文文案与 SF Symbol /
/// UI-facing error type. Normalises every network failure (`URLError`, `ITADAPIError`,
/// `DecodingError`) into the few situations a user can act on, each carrying fixed English copy
/// and an SF Symbol
///
/// 刻意不透传 `localizedDescription`：系统文案会跟随设备语言，在全英文界面里会中英混排，
/// 而且 `HTTP 403` 这类信息对用户没有意义 —— 状态码只留在日志里 /
/// Deliberately does not forward `localizedDescription`: the system copy follows the device
/// language and would mix Chinese into an all-English UI, and strings such as `HTTP 403` mean
/// nothing to a user — status codes stay in the logs
enum DealsError: Error, Equatable {
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
        case .server: "The deal service is having trouble right now. Please try again in a moment."
        case .decoding: "We received data we couldn't read. Please try again later."
        case .unknown: "The deals couldn't be loaded. Please try again."
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
    static func from(_ error: Error) -> DealsError {
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

    /// 页面状态机。原先由 `isLoading` + `errorMessage` + `itadDealsData` 三个独立变量描述状态，
    /// 三者的组合可以表示出「正在加载却同时有错误」这类非法状态；换成枚举后它们在类型层面
    /// 就不存在了，界面也只需要一个 `switch` /
    /// Single state machine. The previous `isLoading` + `errorMessage` + `itadDealsData` trio could
    /// express illegal combinations such as "loading and failed at once"; an enum makes them
    /// unrepresentable and lets the view use one `switch`
    enum LoadState: Equatable {
        /// 首次加载或重试中 / First load, or a retry in progress
        case loading
        /// 请求成功，注意列表本身可能为空 / Request succeeded; the list itself may still be empty
        case loaded(ITADDealsData)
        /// 请求失败，携带归一化后的错误 / Request failed, carrying the normalised error
        case failed(DealsError)
    }

    /// 界面唯一需要观察的状态 / The only state the view needs to observe
    private(set) var state: LoadState = .loading

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
    }

    /// 拉取优惠列表并推进状态机 / Fetches the deal list and advances the state machine
    func fetchDeals(body: [String: Any]) async {
        state = .loading

        do {
            let data = try await ITADAPI.fetchDeals(body: body)
            state = .loaded(data)

        } catch {
            // 完整错误（含服务端 body）只进日志；界面只看到归一化后的 `DealsError` /
            // The full error (including the server body) only goes to the log; the UI only ever sees
            // the normalised `DealsError`
            print("[GamesViewModel] fetchDeals failed / failed: \(error)")
            if let itadError = error as? ITADAPIError {
                print("[GamesViewModel] detailed error / detail: \(itadError.debugDescription)")
            }

            state = .failed(DealsError.from(error))
        }
    }

    /// 用当前请求体重试，供失败态与空态的操作按钮调用 /
    /// Retries with the current request body, used by the failure and empty state actions
    func retry() async {
        await fetchDeals(body: requestBody)
    }

    /// 切换单个游戏的收藏状态 / Toggles the favourite state of a single game
    func toggleFavorite(_ id: String) {

    }
    
    func isFavorite(_ id: String) -> Bool {
        return false
    }
}
