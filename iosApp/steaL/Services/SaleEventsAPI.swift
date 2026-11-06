//
//  SaleEventsAPI.swift
//  steaL
//
//  Created by yijiang on 2026/11/6.
//

import Foundation
import OSLog

/// 自建后端的促销活动接口，契约见 `api/openapi.yaml`。
/// Our backend's sale events endpoint; the contract is `api/openapi.yaml`.
///
/// 不需要 key、不需要登录；地址来自 `Config.backendBaseURL`。
/// No key and no sign-in; the address comes from `Config.backendBaseURL`.
class SaleEventsAPI {

    /// 取所有商店还没结束的活动（`GET /v1/sale-events`）。
    /// Fetches every store's events that have not ended (`GET /v1/sale-events`).
    ///
    /// 用默认缓存策略：后端返回 `Cache-Control: max-age=3600`，一小时内 `URLCache` 直接给缓存，不发请求。
    /// Uses the default cache policy: the backend sends `Cache-Control: max-age=3600`, so within an hour
    /// `URLCache` answers from cache without a request.
    ///
    /// - Returns: 活动列表，`events` 可能为空（没有活动不是错误）/ The event list; `events` may be empty (no events is not an error).
    /// - Throws: 非 HTTP 或非 2xx（429 限流、503 暂时不可用等）抛 `SaleEventsAPIError`；断网抛 `URLError`；
    ///   响应对不上 DTO 抛 `DecodingError` / `SaleEventsAPIError` for a non-HTTP or non-2xx response (429 rate
    ///   limited, 503 temporarily unavailable, …); `URLError` when offline; `DecodingError` when the body does not match the DTOs.
    static func fetchSaleEvents() async throws -> SaleEventList {
        let url = Config.backendBaseURL.appending(path: "sale-events")
        var request = URLRequest(url: url)
        request.httpMethod = "GET"

        #if DEBUG
        logRequest(request)
        #endif

        let (data, resp) = try await URLSession.shared.data(for: request)
        guard let httpResponse = resp as? HTTPURLResponse else {
            throw SaleEventsAPIError.invalidResponse
        }

        #if DEBUG
        logResponse(httpResponse, data: data)
        #endif

        guard (200..<300).contains(httpResponse.statusCode) else {
            throw SaleEventsAPIError.requestFailed(
                statusCode: httpResponse.statusCode,
                body: String(data: data, encoding: .utf8)
            )
        }

        // 契约保证时刻是整秒 UTC，`.iso8601` 就能解。
        // The contract guarantees whole-second UTC times, which `.iso8601` can decode.
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601

        do {
            return try decoder.decode(SaleEventList.self, from: data)
        } catch {
            #if DEBUG
            Logger.saleEvents.error("活动解码失败 / Sale events decoding failed: \(String(describing: error))")
            #endif
            throw error
        }
    }
}

/// `SaleEventsAPI` 的请求失败。
/// A failed `SaleEventsAPI` request.
enum SaleEventsAPIError: Error {
    /// 响应不是 HTTP。
    /// The response is not HTTP.
    case invalidResponse

    /// 状态码不是 2xx，带服务端原始响应（problem+json 或网关的 `{"message": ...}`）。
    /// A non-2xx status, with the raw server body (problem+json or the gateway's `{"message": ...}`).
    case requestFailed(statusCode: Int, body: String?)
}

#if DEBUG
private extension SaleEventsAPI {

    /// 打印请求的方法与地址。
    /// Logs the request's method and URL.
    static func logRequest(_ request: URLRequest) {
        Logger.saleEvents.debug("\(request.httpMethod ?? "?") \(request.url?.absoluteString ?? "?")")
    }

    /// 打印状态码与响应体（截断）。
    /// Logs the status code and the (truncated) body.
    static func logResponse(_ response: HTTPURLResponse, data: Data) {
        Logger.saleEvents.debug("status: \(response.statusCode)")

        let text = String(data: data, encoding: .utf8) ?? "<\(data.count) bytes>"
        Logger.saleEvents.debug("body:\n\(String(text.prefix(2000)))")
    }
}
#endif
