//
//  LLMAPI.swift
//  steaL
//
//  Created by yijiang on 2026/9/25.
//

import Foundation
import OSLog

/// 通用的 LLM 调用，走 OpenAI Chat Completions 兼容协议；换服务商只改 `Secrets.xcconfig` 的地址、key、模型名 /
/// A generic LLM call over the OpenAI Chat Completions compatible protocol; switching providers only
/// changes the URL, key and model in `Secrets.xcconfig`
enum LLMAPI {

    /// 思考模式要先推理再回答，比普通请求慢得多 / A thinking model reasons before answering, far slower than usual
    private static let timeout: TimeInterval = 120

    /// 发一次 system + user 消息，把回答的 JSON 解成指定类型 /
    /// Sends one system + user message pair and decodes the JSON answer into the given type
    ///
    /// - Parameter systemPrompt: 规则与输出格式，要提到 "json" 并给出示例 /
    ///   the rules and output format; must mention "json" and give an example
    /// - Parameter userPrompt: 这次的输入数据 / this request's input data
    /// - Parameter type: 回答要解成的类型 / the type to decode the answer into
    /// - Throws: `LLMAPIError`、`URLError`，或回答不是约定格式时的 `DecodingError` /
    ///   `LLMAPIError`, `URLError`, or `DecodingError` when the answer is not in the agreed format
    static func respond<Content: Decodable>(
        systemPrompt: String,
        userPrompt: String,
        generating type: Content.Type
    ) async throws -> Content {
        let systemMessage = LLMChatRequest.Message(role: .system, content: systemPrompt)
        let userMessage = LLMChatRequest.Message(role: .user, content: userPrompt)
        let body = LLMChatRequest(
            model: Config.llmModel,
            messages: [systemMessage, userMessage],
            responseFormat: .jsonObject
        )

        let request = try makeRequest(body: body)

        #if DEBUG
        logRequest(request)
        #endif

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw LLMAPIError.invalidResponse
        }

        #if DEBUG
        logResponse(httpResponse, data: data)
        #endif

        // 非 2xx 的 body 里通常是服务商给的原因（key 错、余额不足）/ A non-2xx body usually says why
        guard (200..<300).contains(httpResponse.statusCode) else {
            throw LLMAPIError.requestFailed(
                statusCode: httpResponse.statusCode,
                body: String(data: data, encoding: .utf8)
            )
        }

        // 第一次解码：拿到外层包装里的回答字符串 / First decode: the answer string inside the wrapper
        let chatResponse = try JSONDecoder().decode(LLMChatResponse.self, from: data)
        guard let choice = chatResponse.choices.first else {
            throw LLMAPIError.emptyContent
        }

        // 被截断的 JSON 解不出来，先单独报 / Truncated JSON never decodes, so report it on its own
        if choice.finishReason == "length" {
            throw LLMAPIError.truncated
        }

        // DeepSeek 文档说偶尔会回空内容 / DeepSeek documents occasional empty content
        let content = choice.message.content ?? ""
        let trimmedContent = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedContent.isEmpty else {
            throw LLMAPIError.emptyContent
        }

        // 第二次解码：回答本身，严格按约定格式 / Second decode: the answer itself, strictly in the agreed format
        let contentData = Data(trimmedContent.utf8)
        return try JSONDecoder().decode(type, from: contentData)
    }

    /// 组 `POST {base}/chat/completions`；配置没填就不发请求 /
    /// Builds `POST {base}/chat/completions`; nothing is sent while the configuration is missing
    private static func makeRequest(body: LLMChatRequest) throws -> URLRequest {
        let apiKey = Config.llmAPIKey
        let model = Config.llmModel
        let baseURL = Config.llmAPIURL

        guard !apiKey.isEmpty, !model.isEmpty, !baseURL.isEmpty else {
            throw LLMAPIError.missingConfiguration
        }

        // 去掉结尾的 `/`，填 `…/v1` 或 `…/v1/` 都行 / Drop a trailing `/` so both `…/v1` and `…/v1/` work
        var trimmedBaseURL = baseURL
        while trimmedBaseURL.hasSuffix("/") {
            trimmedBaseURL.removeLast()
        }

        guard let url = URL(string: trimmedBaseURL + "/chat/completions") else {
            throw LLMAPIError.missingConfiguration
        }

        var request = URLRequest(url: url, timeoutInterval: timeout)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONEncoder().encode(body)

        return request
    }
}

// MARK: - 错误 / Errors

/// `LLMAPI` 的错误；UI 由 `LoadError.from(_:)` 归一 / `LLMAPI`'s errors, normalised for the UI by `LoadError.from(_:)`
enum LLMAPIError: Error {
    /// `Secrets.xcconfig` 里 `LLM_API_URL` / `LLM_API_KEY` / `LLM_MODEL` 有空值或 URL 不合法 /
    /// `LLM_API_URL`, `LLM_API_KEY` or `LLM_MODEL` in `Secrets.xcconfig` is empty, or the URL is invalid
    case missingConfiguration
    /// 响应不是 HTTP / The response is not HTTP
    case invalidResponse
    /// 状态码不是 2xx / A non-2xx status
    case requestFailed(statusCode: Int, body: String?)
    /// 没有回答，或回答为空 / No answer, or an empty one
    case emptyContent
    /// 回答被截断（`finish_reason == "length"`）/ The answer was cut off
    case truncated
}

// MARK: - 调试日志 / Debug logging

#if DEBUG
private extension LLMAPI {

    static func logRequest(_ request: URLRequest) {
        Logger.llm.debug("\(request.httpMethod ?? "?") \(request.url?.absoluteString ?? "?")")

        // 别把 API Key 打进日志 / Never print the API key
        var headers = request.allHTTPHeaderFields ?? [:]
        if headers["Authorization"] != nil {
            headers["Authorization"] = "Bearer ********"
        }
        Logger.llm.debug("headers: \(headers)")

        if let body = request.httpBody {
            let text = String(data: body, encoding: .utf8) ?? "<\(body.count) bytes>"
            Logger.llm.debug("body: \(text)")
        }
    }

    static func logResponse(_ response: HTTPURLResponse, data: Data) {
        Logger.llm.debug("status: \(response.statusCode)")

        let text = String(data: data, encoding: .utf8) ?? "<\(data.count) bytes>"
        Logger.llm.debug("body: \(text)")
    }
}
#endif
