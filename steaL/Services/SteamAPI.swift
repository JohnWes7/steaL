//
//  SteamAPI.swift
//  steaL
//
//  Created by yijiang on 2026/9/13.
//

import Foundation

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
            print("[Steam] 搜索解码失败 / Search decoding failed: \(error)")
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
            print("[Steam] 解码失败 / Decoding failed: \(error)")
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
}

enum SteamAPIError: Error, LocalizedError {
    case invalidResponse
    case gameNotFound(appID: Int)
    case requestFailed(statusCode: Int, body: String?)

    /// 面向用户 / For the UI
    var errorDescription: String? {
        switch self {
        case .invalidResponse:
            return "Invalid response from the server."
        case .gameNotFound:
            return "This game isn't available on Steam."
        case .requestFailed(let statusCode, _):
            return "Request failed with status code \(statusCode)."
        }
    }

    /// 面向调试，包含服务端原始响应 / For debugging, includes the raw server body
    var debugDescription: String {
        switch self {
        case .invalidResponse:
            return "invalidResponse: 响应的类型不是 HTTPURLResponse"
        case .gameNotFound(let appID):
            return "gameNotFound: appid \(appID) 在 Steam 上不存在或已下架"
        case .requestFailed(let statusCode, let body):
            return "HTTP \(statusCode): \(body ?? "<empty body>")"
        }
    }
}

#if DEBUG
private extension SteamAPI {

    static func logRequest(_ request: URLRequest) {
        print("[Steam] \(request.httpMethod ?? "?") \(request.url?.absoluteString ?? "?")")
    }

    static func logResponse(_ response: HTTPURLResponse, data: Data) {
        print("[Steam] status: \(response.statusCode)")

        // appdetails 响应很大，截断 / The appdetails payload is large, so truncate it
        let text = String(data: data, encoding: .utf8) ?? "<\(data.count) bytes>"
        print("[Steam] body:\n\(String(text.prefix(2000)))")
    }
}
#endif
