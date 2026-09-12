//
//  ITADAPI.swift
//  steaL
//
//  Created by yijiang on 2026/9/10.
//

import Foundation

class ITADAPI {
    
    static func fetchDeals(body: [String: Any] = [:]) async throws -> ITADDealsData {
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
            return try JSONDecoder().decode(ITADDealsData.self, from: data)
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
    /// 解析交给 `ITADLookupData`，调用方只需 `appID(for:)` /
    /// Parsing lives in `ITADLookupData`, so callers only need `appID(for:)`
    ///
    /// - Parameter uuids: ITAD 游戏 UUID 列表 / ITAD game UUIDs
    /// - Returns: 以 UUID 为 key 的查表结果 / a lookup keyed by UUID
    static func lookupShopIDs(uuids: [String]) async throws -> ITADLookupData {
        // 空数组会被服务端拒绝（`Request body must be non-empty JSON array`），
        // 也会让请求失去意义 —— 直接短路，连网络都不发 /
        // An empty array is rejected by the server, so short-circuit before any request
        guard !uuids.isEmpty else { return ITADLookupData(entries: [:]) }

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
            return try JSONDecoder().decode(ITADLookupData.self, from: data)
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
    ) async throws -> ITADGiveawaysData {
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
            return try JSONDecoder().decode(ITADGiveawaysData.self, from: data)
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
