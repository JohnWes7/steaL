//
//  GamesViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/10.
//

import Foundation

/// 面向 UI 的错误类型：把网络层的失败归一成几种状况，每种自带固定英文文案与 SF Symbol /
/// UI-facing error: normalises network failures into a few cases, each with fixed copy and an
/// SF Symbol
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

        // Steam 的 `gameNotFound` 归一成通用失败，不当作离线 / `gameNotFound` becomes a generic failure, not offline
        case let apiError as SteamAPIError:
            switch apiError {
            case .invalidResponse, .gameNotFound:
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
        /// 请求成功，列表可能为空；装 `HomeDealItem` 是因为渲染只要「deal + appID」/
        /// Request succeeded, the list may still be empty; carries `HomeDealItem` because rendering
        /// only needs deal plus appID
        case loaded([HomeDealItem])
        /// 请求失败，携带归一化后的错误 / Request failed, carrying the normalised error
        case failed(LoadError)
    }

    /// Giveaways 区块的独立状态机：与 deals 互不影响 /
    /// Independent state machine for the giveaways section; it never affects the deals state
    enum GiveawaysLoadState: Equatable {
        /// 首次加载或重试中 / First load, or a retry in progress
        case loading
        /// 请求成功，列表可能为空 / Request succeeded; the list itself may still be empty
        case loaded(ITADGiveawaysResponse)
        /// 请求失败，携带归一化后的错误 / Request failed, carrying the normalised error
        case failed(LoadError)
    }

    /// Deals 列表当前状态 / Current state of the deal list
    private(set) var dealState: DealLoadState = .loading

    /// Giveaways 区块当前状态 / Current state of the giveaways section
    private(set) var giveawaysState: GiveawaysLoadState = .loading

    /// 当前请求使用的区域，由界面从设置读出后传入 / Region used by requests; the view reads it from settings and passes it in
    private(set) var region: AppRegion = .australia

    /// 请求体，country 跟随当前区域 / Request body; country follows the current region
    private var requestBody: [String: Any] {
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

    /// 界面传入区域后加载两个区块 / Loads both sections with the region handed in by the view
    func load(region: AppRegion) async {
        self.region = region

        async let deals: Void = fetchDeals()
        async let giveaways: Void = fetchGiveaways()
        _ = await (deals, giveaways) 
    }

    /// 拉取优惠列表 / Fetches the deal list
    func fetchDeals() async {
        dealState = .loading

        do {
            let data = try await ITADAPI.fetchDeals(body: requestBody)
            // 先发布状态让卡片立刻渲染，appID 仍是 `.pending` / Publish first so the cards render; the appID is still `.pending`
            dealState = .loaded(data.list.map { HomeDealItem(deal: $0) })

            // 预取在渲染之后，不挡首屏 / Prefetch runs after publishing so it never delays the first frame
            await prefetchAppIDs()

        } catch {
            // 取消（视图离开或任务重跑）不算失败 / A cancellation (view left, or the task restarted) is not a failure
            if Task.isCancelled { return }

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
        await fetchDeals()
    }

    // MARK: - Steam appID 预取 / Prefetching Steam appIDs

    /// 给列表里每个游戏批量补上 Steam appID（实测 60 个 UUID 一次请求即可）/
    /// Resolves the Steam appID for every game in the feed, in one batch
    ///
    /// 失败不改 `dealState`：列表是好的，只是进不了详情页 / A failure leaves `dealState` alone
    func prefetchAppIDs() async {
        guard case .loaded(let items) = dealState else { return }
        guard !items.isEmpty else { return }

        let uuids = items.map(\.id)

        do {
            let lookup = try await ITADAPI.lookupShopIDs(uuids: uuids)

            // 请求在飞时可能换了地区或重试，结果已过期 / The region may have changed while this was in flight
            guard !Task.isCancelled else { return }

            // 整批一次写回，避免 N 次数组复制 / Apply the whole batch in one pass
            applyAppIDs { lookup.appID(for: $0) }

            #if DEBUG
            print("[GamesViewModel] Steam appID 预取 / prefetch: \(lookup.trackedCount)/\(uuids.count) 条拿到 appID")
            #endif

        } catch {
            if Task.isCancelled { return }

            print("[GamesViewModel] prefetchAppIDs failed / failed: \(error)")
            if let itadError = error as? ITADAPIError {
                print("[GamesViewModel] detailed error / detail: \(itadError.debugDescription)")
            }

            // 还挂在 `.pending` 的条目标成 `.unavailable`，否则卡片永远点不动 / Settle anything still `.pending`
            settlePendingAsUnavailable()
        }
    }

    /// 按解析结果整批更新列表 / Applies a resolver across the whole feed in one pass
    ///
    /// - Parameter resolve: 输入 ITAD UUID，返回 appID；`nil` 表示查不到 /
    ///   takes an ITAD UUID, returns the appID, or `nil` when there is none
    private func applyAppIDs(_ resolve: (String) -> Int?) {
        guard case .loaded(let items) = dealState else { return }

        dealState = .loaded(items.map { item in
            var updated = item

            switch resolve(item.id) {
            case .some(let appID):
                updated.steam = .resolved(appID)
            case .none:
                updated.steam = .unavailable
            }

            return updated
        })
    }

    /// 把仍在等待中的条目落定为 `.unavailable` /
    /// Settles every still-pending entry as `.unavailable`
    private func settlePendingAsUnavailable() {
        guard case .loaded(let items) = dealState else { return }

        dealState = .loaded(items.map { item in
            guard item.steam == .pending else { return item }
            var updated = item
            updated.steam = .unavailable
            return updated
        })
    }

    /// 拉取限时免费领取列表 / Fetches the giveaways list
    func fetchGiveaways() async {
        giveawaysState = .loading

        do {
            let giveaways = try await ITADAPI.fetchGiveaways()
            giveawaysState = .loaded(giveaways)

        } catch {
            // 取消（视图离开或任务重跑）不算失败 / A cancellation (view left, or the task restarted) is not a failure
            if Task.isCancelled { return }

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
