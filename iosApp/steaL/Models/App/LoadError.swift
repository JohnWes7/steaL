//
//  LoadError.swift
//  steaL
//
//  Created by yijiang on 2026/9/26.
//

import Foundation

// 不遵守 `Error`：它从不被 `throw`，只是交给 View 的值 / Not an `Error`: it is never thrown, just a value handed to the view
/// 在viewmodel中使用 把网络层的失败归一成几种状况，每种自带固定英文文案与 SF Symbol /
/// UI-facing error: normalises network failures into a few cases, each with fixed copy and an
/// SF Symbol
enum LoadError: Equatable {
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

    /// 把底层抛出的任意错误归一化。
    /// Normalises whatever the lower layers threw.
    ///
    /// 这里只按错误类型分派，每种错误怎么映射写在下面各自的重载里。
    /// This only dispatches on the error type; how each one maps lives in its own overload below.
    static func from(_ error: Error) -> LoadError {
        switch error {
        case let urlError as URLError:
            return from(urlError)
        case let apiError as ITADAPIError:
            return from(apiError)
        case let apiError as SteamAPIError:
            return from(apiError)
        case let apiError as SaleEventsAPIError:
            return from(apiError)
        case let apiError as LLMAPIError:
            return from(apiError)
        case is DecodingError:
            return .decoding
        default:
            return .unknown
        }
    }

    /// 把 `URLError` 归一成离线或未知。
    /// Normalises a `URLError` into offline or unknown.
    ///
    /// `.timedOut` 归未知：超时可能只是服务器慢，不断定成离线。
    /// `.timedOut` is unknown: a timeout may just be a slow server, so it is not called offline.
    private static func from(_ urlError: URLError) -> LoadError {
        switch urlError.code {
        case .notConnectedToInternet, .networkConnectionLost, .cannotFindHost,
             .cannotConnectToHost, .dataNotAllowed:
            return .offline
        default:
            return .unknown
        }
    }

    /// 把 `ITADAPIError` 归一化。
    /// Normalises an `ITADAPIError`.
    private static func from(_ apiError: ITADAPIError) -> LoadError {
        switch apiError {
        case .invalidResponse:
            return .unknown
        case .requestFailed(let statusCode, _):
            return .server(statusCode: statusCode)
        }
    }

    /// 把 `SteamAPIError` 归一化。
    /// Normalises a `SteamAPIError`.
    ///
    /// 查不到游戏不是失败，`SteamAPI` 返回 `nil`，不会走到这里。
    /// A missing game is not a failure; `SteamAPI` returns `nil` instead, so it never gets here.
    private static func from(_ apiError: SteamAPIError) -> LoadError {
        switch apiError {
        case .invalidResponse:
            return .unknown
        case .requestFailed(let statusCode, _):
            return .server(statusCode: statusCode)
        }
    }

    /// 把 `SaleEventsAPIError` 归一化。
    /// Normalises a `SaleEventsAPIError`.
    ///
    /// 429 限流与 503 暂时不可用都落到 `.server`，它的文案就是「稍后再试」。
    /// 429 rate limiting and 503 unavailable both land in `.server`, whose copy already says try again in a moment.
    private static func from(_ apiError: SaleEventsAPIError) -> LoadError {
        switch apiError {
        case .invalidResponse:
            return .unknown
        case .requestFailed(let statusCode, _):
            return .server(statusCode: statusCode)
        }
    }

    /// 把 `LLMAPIError` 归一化。
    /// Normalises an `LLMAPIError`.
    ///
    /// 配置缺失是开发期问题，用户只需要 Retry，不单独给文案。
    /// A missing configuration is a developer problem; users just get Retry, with no copy of its own.
    private static func from(_ apiError: LLMAPIError) -> LoadError {
        switch apiError {
        case .missingConfiguration, .invalidResponse:
            return .unknown
        case .requestFailed(let statusCode, _):
            return .server(statusCode: statusCode)
        case .emptyContent, .truncated:
            return .decoding
        }
    }
}
