//
//  SteamAPI.swift
//  steaL
//
//  Created by yijiang on 2026/9/13.
//

import Foundation
import OSLog

/// Steam 官方商店接口 / Steam's own store endpoints
///
/// 与 `ITADAPI` 不同，这里**不需要 API Key**，`cc` / `l` 直接决定返回的货币和语言 /
/// Unlike `ITADAPI` this needs **no API key**; `cc` and `l` alone decide the currency and language
class SteamAPI {

    /// 按名称搜索 Steam 商店（`GET .../storesearch`），返回的是列表用的轻量数据 /
    /// Searches the Steam store by name; lightweight data for the list only
    ///
    /// 选中后仍要走 `fetchAppDetails` 才能拿到完整详情 / Follow up with `fetchAppDetails` for details
    ///
    /// - Parameter query: 用户输入的搜索词 / the search text entered by the user
    /// - Parameter region: 区域，决定价格和货币 / the region, which drives pricing and currency
    /// - Parameter language: 返回名称的语言 / the language used for localised names
    static func searchStore(
        query: String,
        region: AppRegion = .australia,
        language: AppLanguage = .english
    ) async throws -> SteamSearchResponse {
        let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedQuery.isEmpty else { return .empty }

        var urlComponents = URLComponents(string: "https://store.steampowered.com/api/storesearch")!
        urlComponents.queryItems = [
            URLQueryItem(name: "term", value: trimmedQuery),
            URLQueryItem(name: "cc", value: region.apiRegionCode),
            URLQueryItem(name: "l", value: language.apiCode),
        ]
        guard let url = urlComponents.url else { throw URLError(.badURL) }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"

        #if DEBUG
        logRequest(request)
        #endif

        let (data, resp) = try await URLSession.shared.data(for: request)
        guard let httpResponse = resp as? HTTPURLResponse else {
            throw SteamAPIError.invalidResponse
        }

        #if DEBUG
        logResponse(httpResponse, data: data)
        #endif

        guard (200..<300).contains(httpResponse.statusCode) else {
            throw SteamAPIError.requestFailed(
                statusCode: httpResponse.statusCode,
                body: String(data: data, encoding: .utf8)
            )
        }

        do {
            return try JSONDecoder().decode(SteamSearchResponse.self, from: data)
        } catch {
            #if DEBUG
            Logger.steam.error("搜索解码失败 / Search decoding failed: \(String(describing: error))")
            #endif
            throw error
        }
    }

    /// 获取游戏详情：详情页的**硬门槛**，`success == false` 抛 `.gameNotFound` /
    /// Fetches one game's store details, the detail page's **hard requirement**
    ///
    /// **不要**加 `filters=basic`（会裁掉 `movies` / `screenshots` / `genres`）/ **Never** pass `filters=basic`
    ///
    /// - Parameter appID: Steam App ID / the Steam application ID
    /// - Parameter region: 区域，决定货币与商店可用性 / the region; drives currency and availability
    /// - Parameter language: 语言，用于本地化标题与视频名 / the language used for titles and movie names
    static func fetchAppDetails(
        appID: Int,
        region: AppRegion = .australia,
        language: AppLanguage = .english
    ) async throws -> SteamAppDetails {
        // 构建请求 URL / Construct request URL
        var urlComponents = URLComponents(string: "https://store.steampowered.com/api/appdetails")!
        urlComponents.queryItems = [
            URLQueryItem(name: "appids", value: String(appID)),
            URLQueryItem(name: "cc", value: region.apiRegionCode),
            URLQueryItem(name: "l", value: language.apiCode),
        ]
        guard let url = urlComponents.url else { throw URLError(.badURL) }

        // 构建请求，appdetails 是 GET 接口 / Construct request; appdetails is a GET endpoint
        var request = URLRequest(url: url)
        request.httpMethod = "GET"

        #if DEBUG
        logRequest(request)
        #endif

        // 发送请求并解析响应 / Send request and parse response
        let (data, resp) = try await URLSession.shared.data(for: request)

        // 检查响应类型 / Check response type
        guard let httpResponse = resp as? HTTPURLResponse else {
            throw SteamAPIError.invalidResponse
        }

        #if DEBUG
        logResponse(httpResponse, data: data)
        #endif

        // 检查状态码是否为 2xx / Check if the status code is 2xx
        guard (200..<300).contains(httpResponse.statusCode) else {
            throw SteamAPIError.requestFailed(
                statusCode: httpResponse.statusCode,
                body: String(data: data, encoding: .utf8)
            )
        }

        let response: SteamAppDetailsResponse
        do {
            response = try JSONDecoder().decode(SteamAppDetailsResponse.self, from: data)
        } catch {
            #if DEBUG
            // 解码失败时把原始 body 打出来，方便对照字段名 / Log raw body to compare field names
            Logger.steam.error("解码失败 / Decoding failed: \(String(describing: error))")
            #endif
            throw error
        }

        // 只查一个 appid，字典只有一条；`success == false` 与 `data` 缺失都算查不到 /
        // Only one appid was requested, so a failed `success` and a missing `data` both mean a miss
        guard let details = response.entries.values.first?.data else {
            throw SteamAPIError.gameNotFound(appID: appID)
        }

        return details
    }

    /// 一次请求批量检查 appID 在 Steam 上存不存在、有没有价格，用来在 ITAD 给的多个候选里挑对的那个 /
    /// Checks in one request whether appIDs exist on Steam and have a price; used to pick the right one among ITAD's candidates
    ///
    /// `appids` 只有配 `filters=price_overview` 才能一次传多个，否则 Steam 直接报错 /
    /// `appids` accepts several values only together with `filters=price_overview`
    ///
    /// - Parameter appIDs: 要检查的 Steam App ID / the Steam application IDs to check
    /// - Parameter region: 区域，锁区游戏在该区返回 `success == false` / the region; region-locked games fail there
    static func fetchAvailability(
        of appIDs: [Int],
        region: AppRegion = .australia
    ) async throws -> SteamAppAvailabilityResponse {
        guard !appIDs.isEmpty else { return SteamAppAvailabilityResponse(entries: [:]) }

        var urlComponents = URLComponents(string: "https://store.steampowered.com/api/appdetails")!
        urlComponents.queryItems = [
            URLQueryItem(name: "appids", value: appIDs.map(String.init).joined(separator: ",")),
            URLQueryItem(name: "cc", value: region.apiRegionCode),
            URLQueryItem(name: "filters", value: "price_overview"),
        ]
        guard let url = urlComponents.url else { throw URLError(.badURL) }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"

        #if DEBUG
        logRequest(request)
        #endif

        let (data, resp) = try await URLSession.shared.data(for: request)
        guard let httpResponse = resp as? HTTPURLResponse else {
            throw SteamAPIError.invalidResponse
        }

        #if DEBUG
        logResponse(httpResponse, data: data)
        #endif

        guard (200..<300).contains(httpResponse.statusCode) else {
            throw SteamAPIError.requestFailed(
                statusCode: httpResponse.statusCode,
                body: String(data: data, encoding: .utf8)
            )
        }

        return try JSONDecoder().decode(SteamAppAvailabilityResponse.self, from: data)
    }
}

enum SteamAPIError: Error {
    /// 响应不是 HTTP / The response is not HTTP
    case invalidResponse
    /// 该 appID 在 Steam 上不存在或已下架 / The appID does not exist on Steam or was delisted
    case gameNotFound(appID: Int)
    /// 状态码不是 2xx，带服务端原始响应 / A non-2xx status, with the raw server body
    case requestFailed(statusCode: Int, body: String?)
}

#if DEBUG
private extension SteamAPI {

    static func logRequest(_ request: URLRequest) {
        Logger.steam.debug("\(request.httpMethod ?? "?") \(request.url?.absoluteString ?? "?")")
    }

    static func logResponse(_ response: HTTPURLResponse, data: Data) {
        Logger.steam.debug("status: \(response.statusCode)")

        // appdetails 响应很大，截断 / The appdetails payload is large, so truncate it
        let text = String(data: data, encoding: .utf8) ?? "<\(data.count) bytes>"
        Logger.steam.debug("body:\n\(String(text.prefix(2000)))")
    }
}
#endif
