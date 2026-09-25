//
//  ITADAPI.swift
//  steaL
//
//  Created by yijiang on 2026/9/10.
//

import Foundation
import OSLog

class ITADAPI {
    //TODO: 改造弱类型fetchDeals body

    /// 根据 Steam appID 查找 ITAD 游戏，返回完整 lookup response /
    /// Looks up an ITAD game by Steam appID and returns the complete lookup response
    static func lookupUUID(appID: Int) async throws -> ITADGameLookupResponse {
        var urlComponents = URLComponents(string: "https://api.isthereanydeal.com/games/lookup/v1")!
        urlComponents.queryItems = [
            URLQueryItem(name: "key", value: Config.itadAPIKey),
            URLQueryItem(name: "appid", value: String(appID)),
        ]
        guard let url = urlComponents.url else { throw URLError(.badURL) }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue(Config.itadAPIKey, forHTTPHeaderField: "ITAD-API-Key")

        let (data, resp) = try await URLSession.shared.data(for: request)
        guard let httpResponse = resp as? HTTPURLResponse else {
            throw ITADAPIError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            throw ITADAPIError.requestFailed(
                statusCode: httpResponse.statusCode,
                body: String(data: data, encoding: .utf8)
            )
        }

        return try JSONDecoder().decode(ITADGameLookupResponse.self, from: data)
    }

    static func fetchDeals(body: [String: Any] = [:]) async throws -> ITADDealsResponse {
        // 构建请求 URL / Construct request URL
        let urlComponents = URLComponents(string: "https://api.isthereanydeal.com/deals/v2")!
        guard let url = urlComponents.url else { throw URLError(.badURL) }

        // 构建请求体 / Construct request body
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(Config.itadAPIKey, forHTTPHeaderField: "ITAD-API-Key")
        
        let defaultBody : [String: Any] = [
            "country": "AU",
            "offset": 0,
            "limit": 20,
            "sort": "-cut",
            "nondeals": false,
            "filter": [
                "type": [1],
                "wasSpotlight": true,
                "drm": [61],
            ],
        ]
        
        var requestBody : [String: Any] = defaultBody
        if !body.isEmpty {
            requestBody = body
        }
        // 将请求体编码为 JSON / Encode request body as JSON
        request.httpBody = try JSONSerialization.data(withJSONObject: requestBody, options: [])

        #if DEBUG
        logRequest(request)
        #endif

        // 发送请求并解析响应 / Send request and parse response
        let (data, resp) = try await URLSession.shared.data(for: request)

        // 检查响应类型 / Check response type
        guard let httpResponse = resp as? HTTPURLResponse else {
            throw ITADAPIError.invalidResponse
        }

        #if DEBUG
        logResponse(httpResponse, data: data)
        #endif

        // 检查状态码是否为 2xx / Check if the status code is 2xx
        guard (200..<300).contains(httpResponse.statusCode) else {
            throw ITADAPIError.requestFailed(
                statusCode: httpResponse.statusCode,
                body: String(data: data, encoding: .utf8)
            )
        }

        do {
            return try JSONDecoder().decode(ITADDealsResponse.self, from: data)
        } catch {
            #if DEBUG
            // 解码失败时把原始 body 打出来，方便对照字段名 / Log raw body to compare field names
            Logger.itad.error("解码失败 / Decoding failed: \(String(describing: error))")
            #endif
            throw error
        }
    }

    /// 批量把 ITAD 游戏 ID 换成 Steam 商店 ID（`POST .../lookup/shop/61/id/v1`）/
    /// Batch-converts ITAD game IDs into Steam shop IDs
    ///
    /// 请求体是裸 UUID 数组（60 个一次返回）；反向接口要 `"app/123"` 前缀 / A plain array of bare UUIDs
    ///
    /// - Parameter uuids: ITAD 游戏 UUID 列表 / ITAD game UUIDs
    static func lookupShopIDs(uuids: [String]) async throws -> ITADShopIDsResponse {
        // 空数组会被服务端拒绝（`Request body must be non-empty JSON array`），直接短路 /
        // An empty array is rejected by the server, so short-circuit before any request
        guard !uuids.isEmpty else { return ITADShopIDsResponse(entries: [:]) }

        // 构建请求 URL / Construct request URL
        let urlComponents = URLComponents(string: "https://api.isthereanydeal.com/lookup/shop/61/id/v1")!
        guard let url = urlComponents.url else { throw URLError(.badURL) }

        // 构建请求，和其它接口一样用 POST + JSON body /
        // Construct the request; POST with a JSON body, same as the other endpoints
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(Config.itadAPIKey, forHTTPHeaderField: "ITAD-API-Key")

        request.httpBody = try JSONEncoder().encode(uuids)

        #if DEBUG
        logRequest(request)
        #endif

        // 发送请求并解析响应 / Send request and parse response
        let (data, resp) = try await URLSession.shared.data(for: request)

        // 检查响应类型 / Check response type
        guard let httpResponse = resp as? HTTPURLResponse else {
            throw ITADAPIError.invalidResponse
        }

        #if DEBUG
        logResponse(httpResponse, data: data)
        #endif

        // 检查状态码是否为 2xx / Check if the status code is 2xx
        guard (200..<300).contains(httpResponse.statusCode) else {
            throw ITADAPIError.requestFailed(
                statusCode: httpResponse.statusCode,
                body: String(data: data, encoding: .utf8)
            )
        }

        do {
            return try JSONDecoder().decode(ITADShopIDsResponse.self, from: data)
        } catch {
            #if DEBUG
            // 解码失败时把原始 body 打出来，方便对照字段名 / Log raw body to compare field names
            Logger.itad.error("解码失败 / Decoding failed: \(String(describing: error))")
            #endif
            throw error
        }
    }

    /// 获取限时免费领取列表，默认只返回未过期条目，按发布时间从新到旧 /
    /// Fetch the giveaways list; by default unexpired entries only, newest first
    ///
    /// `GET https://api.isthereanydeal.com/giveaways/v1`
    static func fetchGiveaways(
        offset: Int = 0,
        limit: Int = 20,
        expired: Bool = false,
        mature: Bool = false,
        sort: String = "-publish"
    ) async throws -> ITADGiveawaysResponse {
        // 构建请求 URL / Construct request URL
        var urlComponents = URLComponents(string: "https://api.isthereanydeal.com/giveaways/v1")!
        urlComponents.queryItems = [
            URLQueryItem(name: "offset", value: String(offset)),
            URLQueryItem(name: "limit", value: String(limit)),
            URLQueryItem(name: "expired", value: String(expired)),
            URLQueryItem(name: "mature", value: String(mature)),
            URLQueryItem(name: "sort", value: sort),
        ]
        guard let url = urlComponents.url else { throw URLError(.badURL) }

        // 构建请求，giveaways 是 GET 接口 / Construct request; giveaways is a GET endpoint
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue(Config.itadAPIKey, forHTTPHeaderField: "ITAD-API-Key")

        #if DEBUG
        logRequest(request)
        #endif

        // 发送请求并解析响应 / Send request and parse response
        let (data, resp) = try await URLSession.shared.data(for: request)

        // 检查响应类型 / Check response type
        guard let httpResponse = resp as? HTTPURLResponse else {
            throw ITADAPIError.invalidResponse
        }

        #if DEBUG
        logResponse(httpResponse, data: data)
        #endif

        // 检查状态码是否为 2xx / Check if the status code is 2xx
        guard (200..<300).contains(httpResponse.statusCode) else {
            throw ITADAPIError.requestFailed(
                statusCode: httpResponse.statusCode,
                body: String(data: data, encoding: .utf8)
            )
        }

        do {
            return try JSONDecoder().decode(ITADGiveawaysResponse.self, from: data)
        } catch {
            #if DEBUG
            // 解码失败时把原始 body 打出来，方便对照字段名 / Log raw body to compare field names
            Logger.itad.error("解码失败 / Decoding failed: \(String(describing: error))")
            #endif
            throw error
        }
    }

    /// 查一批游戏在各商店的价格与历史低价（`POST .../games/prices/v3`，裸 UUID 数组，最多 200）/
    /// Loads prices and historical lows for a batch of games
    ///
    /// ⚠️ 响应**稀疏**：没有价格数据的游戏整条不返回，所以必须按 `id` 匹配 / ⚠️ The response is **sparse**
    ///
    /// - Parameter uuids: ITAD 游戏 UUID 列表，最多 200 个 / ITAD game UUIDs, at most 200
    /// - Parameter region: 区域，决定货币与可用商店 / the region, which drives currency and shops
    static func fetchPrices(uuids: [String], region: AppRegion) async throws -> ITADPricesResponse {
        // 空数组会被服务端拒绝，也不会返回任何数据 —— 直接短路，连网络都不发 /
        // An empty array would be rejected by the server and could not return anything anyway
        guard !uuids.isEmpty else { return [] }

        // 构建请求 URL，鉴权沿用其它接口的 key 查询参数写法 /
        // Construct request URL; authentication follows the other endpoints and uses the key query parameter
        var urlComponents = URLComponents(string: "https://api.isthereanydeal.com/games/prices/v3")!
        urlComponents.queryItems = [
            URLQueryItem(name: "key", value: Config.itadAPIKey),
            URLQueryItem(name: "country", value: region.apiRegionCode),
            URLQueryItem(name: "deals", value: "false"),
        ]
        guard let url = urlComponents.url else { throw URLError(.badURL) }

        // 构建请求，和其它接口一样用 POST + JSON body /
        // Construct the request; POST with a JSON body, same as the other endpoints
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(Config.itadAPIKey, forHTTPHeaderField: "ITAD-API-Key")

        request.httpBody = try JSONEncoder().encode(uuids)

        #if DEBUG
        logRequest(request)
        #endif

        // 发送请求并解析响应 / Send request and parse response
        let (data, resp) = try await URLSession.shared.data(for: request)

        // 检查响应类型 / Check response type
        guard let httpResponse = resp as? HTTPURLResponse else {
            throw ITADAPIError.invalidResponse
        }

        #if DEBUG
        logResponse(httpResponse, data: data)
        #endif

        // 检查状态码是否为 2xx / Check if the status code is 2xx
        guard (200..<300).contains(httpResponse.statusCode) else {
            throw ITADAPIError.requestFailed(
                statusCode: httpResponse.statusCode,
                body: String(data: data, encoding: .utf8)
            )
        }

        do {
            return try JSONDecoder().decode(ITADPricesResponse.self, from: data)
        } catch {
            #if DEBUG
            // 解码失败时把原始 body 打出来，方便对照字段名 / Log raw body to compare field names
            Logger.itad.error("解码失败 / Decoding failed: \(String(describing: error))")
            #endif
            throw error
        }
    }

    /// 查一个游戏的评分、标签与热度（`GET .../games/info/v2?id=<uuid>`）/
    /// Loads one game's reviews, tags and popularity
    ///
    /// 详情页的**可选增强**：拿不到就少一块；未发售游戏 `releaseDate` 为 `null` / An optional enhancement
    ///
    /// - Parameter uuid: ITAD 游戏 UUID / ITAD game UUID
    static func fetchGameInfo(uuid: String) async throws -> ITADGameInfoResponse {
        // 构建请求 URL，鉴权沿用其它接口的 key 查询参数写法 /
        // Construct request URL; authentication follows the other endpoints' key query parameter
        var urlComponents = URLComponents(string: "https://api.isthereanydeal.com/games/info/v2")!
        urlComponents.queryItems = [
            URLQueryItem(name: "key", value: Config.itadAPIKey),
            URLQueryItem(name: "id", value: uuid),
        ]
        guard let url = urlComponents.url else { throw URLError(.badURL) }

        // 构建请求，info 是 GET 接口 / Construct request; info is a GET endpoint
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue(Config.itadAPIKey, forHTTPHeaderField: "ITAD-API-Key")

        #if DEBUG
        logRequest(request)
        #endif

        // 发送请求并解析响应 / Send request and parse response
        let (data, resp) = try await URLSession.shared.data(for: request)

        // 检查响应类型 / Check response type
        guard let httpResponse = resp as? HTTPURLResponse else {
            throw ITADAPIError.invalidResponse
        }

        #if DEBUG
        logResponse(httpResponse, data: data)
        #endif

        // 检查状态码是否为 2xx，未收录的游戏会返回 404 / Check for 2xx; an untracked game answers 404
        guard (200..<300).contains(httpResponse.statusCode) else {
            throw ITADAPIError.requestFailed(
                statusCode: httpResponse.statusCode,
                body: String(data: data, encoding: .utf8)
            )
        }

        do {
            return try JSONDecoder().decode(ITADGameInfoResponse.self, from: data)
        } catch {
            #if DEBUG
            // 解码失败时把原始 body 打出来，方便对照字段名 / Log raw body to compare field names
            Logger.itad.error("解码失败 / Decoding failed: \(String(describing: error))")
            #endif
            throw error
        }
    }

}

enum ITADAPIError: Error {
    /// 响应不是 HTTP / The response is not HTTP
    case invalidResponse
    /// 状态码不是 2xx，带服务端原始响应 / A non-2xx status, with the raw server body
    case requestFailed(statusCode: Int, body: String?)
}

#if DEBUG
private extension ITADAPI {

    static func logRequest(_ request: URLRequest) {
        Logger.itad.debug("\(request.httpMethod ?? "?") \(request.url?.absoluteString ?? "?")")

        // 别把 API Key 打进日志 / Never print the API key
        var headers = request.allHTTPHeaderFields ?? [:]
        if headers["ITAD-API-Key"] != nil { headers["ITAD-API-Key"] = "********" }
        Logger.itad.debug("headers: \(headers)")

        if let body = request.httpBody {
            Logger.itad.debug("body: \(String(data: body, encoding: .utf8) ?? "<binary>")")
        }
    }

    static func logResponse(_ response: HTTPURLResponse, data: Data) {
        Logger.itad.debug("status: \(response.statusCode)")

        let text: String
        if let object = try? JSONSerialization.jsonObject(with: data),
           let pretty = try? JSONSerialization.data(withJSONObject: object, options: [.prettyPrinted, .sortedKeys]),
           let string = String(data: pretty, encoding: .utf8) {
            text = String(string.prefix(2000))   // deals 响应很大，截断
        } else {
            text = String(data: data, encoding: .utf8) ?? "<\(data.count) bytes>"
        }
        Logger.itad.debug("body:\n\(text)")
    }
}
#endif
