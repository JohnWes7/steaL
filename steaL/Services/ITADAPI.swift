//
//  ITADAPI.swift
//  steaL
//
//  Created by yijiang on 2026/9/10.
//

import Foundation

class ITADAPI {
    //TODO: 改造弱类型fetchDeals body

    /// 根据 Steam appID 查找 ITAD 游戏 UUID，未收录时返回 nil /
    /// Looks up an ITAD game UUID by Steam appID, returning nil when it is not tracked
    static func lookupUUID(appID: Int) async throws -> String? {
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

        return try JSONDecoder().decode(ITADGameLookupResponse?.self, from: data)?.id
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
            print("[ITAD] 解码失败 / Decoding failed: \(error)")
            #endif
            throw error
        }
    }

    /// 批量把 ITAD 游戏 ID 换成 Steam 商店 ID /
    /// Batch-converts ITAD game IDs into Steam shop IDs
    ///
    /// `POST https://api.isthereanydeal.com/lookup/shop/61/id/v1`
    ///
    /// 请求体是**裸 UUID 的数组**（`["<uuid>", ...]`），60 个实测一次返回，无需分批 /
    /// The body is a plain array of bare UUIDs; 60 of them were verified in a single call, so
    /// no chunking is needed for a home feed
    ///
    /// 注意「裸」是重点：另一个方向的接口（`lookup/id/shop/61/v1`）反而要求 `"app/123"` 前缀，
    /// 两个接口规则相反，很容易写错 /
    /// "Bare" matters: the reverse endpoint requires an `"app/123"` prefix instead, and mixing
    /// the two up is the easiest mistake to make here
    ///
    /// 解析交给 `ITADShopIDsResponse`，调用方只需 `appID(for:)` /
    /// Parsing lives in `ITADShopIDsResponse`, so callers only need `appID(for:)`
    ///
    /// - Parameter uuids: ITAD 游戏 UUID 列表 / ITAD game UUIDs
    /// - Returns: 以 UUID 为 key 的查表结果 / a lookup keyed by UUID
    static func lookupShopIDs(uuids: [String]) async throws -> ITADShopIDsResponse {
        // 空数组会被服务端拒绝（`Request body must be non-empty JSON array`），
        // 也会让请求失去意义 —— 直接短路，连网络都不发 /
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
            print("[ITAD] 解码失败 / Decoding failed: \(error)")
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
            print("[ITAD] 解码失败 / Decoding failed: \(error)")
            #endif
            throw error
        }
    }

    /// 查一批游戏在各商店的价格与历史低价 / Loads prices and historical lows for a batch of games
    ///
    /// `POST https://api.isthereanydeal.com/games/prices/v3?country=<CC>&deals=true`
    ///
    /// 请求体是**裸 UUID 数组**，最多 200 个 / the body is a plain array of bare UUIDs, at most 200
    ///
    /// ⚠️ 响应是**稀疏数组**：没有 deal 的游戏整条不返回（实测 Portal 2 返回空数组），
    /// 调用方必须按 `id` 匹配；某个 UUID 查不到就当作「没有比价数据」处理，不是错误 /
    /// ⚠️ The response is **sparse**: a game without deals is omitted entirely (verified with
    /// Portal 2 returning an empty array). Callers match by `id`, and a missing UUID means
    /// "no price data", not an error
    ///
    /// `deals=true` 固定打开：详情页的比价区只关心正在打折的价格 /
    /// `deals=true` is always on, since the detail page's price block only cares about discounted prices
    ///
    /// - Parameters:
    ///   - uuids: ITAD 游戏 UUID 列表，最多 200 个 / ITAD game UUIDs, at most 200
    ///   - region: 区域，决定货币与可用商店 / Region, drives the currency and available shops
    /// - Returns: 查到价格的游戏，顺序与请求无关 / The games that have prices, in no particular order
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
            URLQueryItem(name: "deals", value: "true"),
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
            print("[ITAD] 解码失败 / Decoding failed: \(error)")
            #endif
            throw error
        }
    }

    /// 查一个游戏的评分、标签与热度 / Loads one game's reviews, tags and popularity
    ///
    /// `GET https://api.isthereanydeal.com/games/info/v2?id=<uuid>`
    ///
    /// 这是详情页的**可选增强**：拿不到就少一块评分与热度，主流程不受影响。
    /// 未发售的游戏也能查到（实测 The Wolf Among Us 2），只是 `releaseDate` 为 `null`、
    /// `reviews` 为空数组、`players` 四个值都是 `0` /
    /// This is an **optional enhancement** for the detail page: without it there is simply no
    /// review or popularity block. Unreleased games are covered as well (verified with
    /// The Wolf Among Us 2); they just carry a `null` `releaseDate`, an empty `reviews` array and
    /// four zeroed `players` counts
    ///
    /// ⚠️ `assets` 字段在同一接口里会返回**数组或对象**两种形态，DTO 里已兼容 /
    /// ⚠️ The `assets` field arrives as either an array or an object; the DTO handles both
    ///
    /// - Parameters:
    ///   - uuid: ITAD 游戏 UUID / ITAD game UUID
    /// - Returns: 该游戏的评分与热度信息 / The game's review and popularity info
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
            print("[ITAD] 解码失败 / Decoding failed: \(error)")
            #endif
            throw error
        }
    }

}

enum ITADAPIError: Error, LocalizedError {
    case invalidResponse
    case requestFailed(statusCode: Int, body: String?)

    /// 面向用户 / For the UI
    var errorDescription: String? {
        switch self {
        case .invalidResponse:
            return "Invalid response from the server."
        case .requestFailed(let statusCode, _):
            return "Request failed with status code \(statusCode)."
        }
    }

    /// 面向调试，包含服务端原始响应 / For debugging, includes the raw server body
    var debugDescription: String {
        switch self {
        case .invalidResponse:
            return "invalidResponse: 响应的类型不是 HTTPURLResponse"
        case .requestFailed(let statusCode, let body):
            return "HTTP \(statusCode): \(body ?? "<empty body>")"
        }
    }
}

#if DEBUG
private extension ITADAPI {

    static func logRequest(_ request: URLRequest) {
        print("[ITAD] \(request.httpMethod ?? "?") \(request.url?.absoluteString ?? "?")")

        // 别把 API Key 打进日志 / Never print the API key
        var headers = request.allHTTPHeaderFields ?? [:]
        if headers["ITAD-API-Key"] != nil { headers["ITAD-API-Key"] = "********" }
        print("[ITAD] headers: \(headers)")

        if let body = request.httpBody {
            print("[ITAD] body: \(String(data: body, encoding: .utf8) ?? "<binary>")")
        }
    }

    static func logResponse(_ response: HTTPURLResponse, data: Data) {
        print("[ITAD] status: \(response.statusCode)")

        let text: String
        if let object = try? JSONSerialization.jsonObject(with: data),
           let pretty = try? JSONSerialization.data(withJSONObject: object, options: [.prettyPrinted, .sortedKeys]),
           let string = String(data: pretty, encoding: .utf8) {
            text = String(string.prefix(2000))   // deals 响应很大，截断
        } else {
            text = String(data: data, encoding: .utf8) ?? "<\(data.count) bytes>"
        }
        print("[ITAD] body:\n\(text)")
    }
}
#endif
