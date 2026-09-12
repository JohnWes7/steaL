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

    /// 获取游戏详情 / Fetches one game's store details
    ///
    /// `GET https://store.steampowered.com/api/appdetails?appids=<id>&cc=<region>&l=<language>`
    ///
    /// 这是详情页的**硬门槛**：`success == false` 表示游戏在 Steam 上不存在（下架 / 锁区），
    /// 此时抛 `.gameNotFound`，页面不该渲染 /
    /// This is the detail page's **hard requirement**: `success == false` means the game is not on
    /// Steam, which throws `.gameNotFound` instead of rendering a page
    ///
    /// 注意**不要**加 `filters=basic`，它会把 `movies` / `screenshots` / `genres` / `developers`
    /// 全部裁掉 / Never pass `filters=basic`; it strips `movies`, `screenshots`, `genres` and
    /// `developers` from the response
    ///
    /// - Parameters:
    ///   - appID: Steam App ID / Steam application ID
    ///   - region: 区域，决定货币与商店可用性 / Region; drives currency and store availability
    ///   - language: 语言，Steam 会据此本地化游戏名与视频名 / Language; Steam localises the title and movie names with it
    /// - Returns: 该游戏在 Steam 商店的详情 / The game's store details
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

        // 只查了一个 appid，所以字典里只有一条；`success == false` 或 `data` 缺失都算查不到 /
        // Only one appid was requested, so there is exactly one entry; a failed `success` or a
        // missing `data` both mean the game cannot be shown
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
