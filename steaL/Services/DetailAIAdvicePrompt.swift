//
//  DetailAIAdvicePrompt.swift
//  steaL
//
//  Created by yijiang on 2026/9/25.
//

import Foundation

/// 详情页 AI 购买建议的 prompt：`system` 放不变的规则与输出格式，`user` 放这款游戏的数据 /
/// The prompt for the detail page's AI purchase advice: fixed rules and output format go in `system`,
/// this game's data goes in `user`
///
/// 不绑定 DeepSeek，之后端上模型兜底也用同一套（`instructions` / `prompt`）/
/// Not tied to DeepSeek; the on-device fallback reuses it as its instructions and prompt
enum DetailAIAdvicePrompt {

    // MARK: - System

    /// 固定规则 + 输出格式示例 + 回答语言 / Fixed rules, the output format example, and the reply language
    ///
    /// - Throws: 编码格式示例失败时 / when the format example fails to encode
    static func systemPrompt(language: AppLanguage) throws -> String {
        let example = try formatExample()
        let verdicts = allowedValues(DetailAIAdviceResponse.Verdict.allCases)
        let stances = allowedValues(DetailAIAdviceResponse.Stance.allCases)
        let topics = allowedValues(DetailAIAdviceResponse.Topic.allCases)
        let replyLanguage = language.englishName

        // 规则只写 App 有数据的东西，缩小模型能编的范围（HIG Generative AI）/
        // The rules only cover what the app has data for, which limits what the model can make up
        return """
            You are the purchase advisor inside a Steam price-tracking app. \
            The user message is a JSON object describing one game. \
            Decide whether now is a good time to buy it.

            About the data:
            - Every price in "deals" is for a key that activates on Steam, cheapest first, in "currency".
            - "historyLow" is the lowest price across all stores tracked by IsThereAnyDeal, \
            so it can be lower than any current deal. It is missing for free-to-play games.
            - "kind" is "paid", "freeForever" (free to play) or "unreleased".
            - For "reviews", the "Steam" score is the percentage of positive reviews.
            - "players" are concurrent player counts; they are all 0 for unreleased games.

            Rules:
            - Only use facts and numbers that appear in the data. Never invent prices, dates, \
            sales events, play time, content, or probabilities.
            - "score" (0-100) rates how good a time it is to buy now, not how good the game is.
            - "verdict" is one of: \(verdicts). It must match the score: \
            80 or above is "buy", 50 to 79 is "wait", below 50 is "skip".
            - "headline" is one sentence that gives the main reason.
            - Give 2 to 4 "reasons". "stance" is one of: \(stances). \
            "topic" is one of: \(topics). Put "pro" reasons first.
            - Each reason is one or two sentences and cites at least one number from the data.
            - Include "targetPrice" (a number in "currency") only when the verdict is "wait"; \
            otherwise leave it out.
            - Write "headline" and every reason's "text" in \(replyLanguage). \
            Keep the JSON keys and the allowed values in English.

            Reply with json only: a single JSON object in exactly this format, with no other text.
            \(example)
            """
    }

    // MARK: - User

    /// context 编码成 JSON 字符串 / The context encoded as a JSON string
    ///
    /// - Throws: 编码失败时 / when encoding fails
    static func userMessage(context: DetailAIAdviceContext) throws -> String {
        return try jsonString(context)
    }

    // MARK: - 私有 / Private

    /// 不缩进以省 token；键排序让同一输入每次得到同一字符串 /
    /// No indentation to save tokens; sorted keys make the same input produce the same string
    private static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        return encoder
    }()

    /// 编码成紧凑的 JSON 字符串 / Encodes a value into a compact JSON string
    ///
    /// - Throws: 编码失败，或结果不是 UTF-8 时 / when encoding fails or the result is not UTF-8
    private static func jsonString(_ value: some Encodable) throws -> String {
        let data = try encoder.encode(value)

        guard let text = String(data: data, encoding: .utf8) else {
            let context = EncodingError.Context(
                codingPath: [], debugDescription: "JSON is not valid UTF-8")
            throw EncodingError.invalidValue(value, context)
        }

        return text
    }

    /// 格式示例由样例对象编码而来，改了 `DetailAIAdviceResponse` 字段示例会跟着变 /
    /// The format example is encoded from a sample, so it follows any change to `DetailAIAdviceResponse`
    ///
    /// 用 `wait` 是为了把可选的 `targetPrice` 也展示出来 / `wait` so the optional `targetPrice` shows too
    private static func formatExample() throws -> String {
        let priceReason = DetailAIAdviceResponse.Reason(
            stance: .pro,
            topic: .price,
            text: "At 40.00 it is 33% off, and the 1-year low is 35.00."
        )
        let reviewReason = DetailAIAdviceResponse.Reason(
            stance: .pro,
            topic: .reviews,
            text: "92% of 120000 Steam reviews are positive."
        )
        let cautionReason = DetailAIAdviceResponse.Reason(
            stance: .caution,
            topic: .price,
            text: "The all-time low of 30.00 is 10.00 below the current price."
        )
        let sample = DetailAIAdviceResponse(
            verdict: .wait,
            score: 64,
            headline: "A solid discount, but it has been cheaper before.",
            reasons: [priceReason, reviewReason, cautionReason],
            targetPrice: 30
        )

        return try jsonString(sample)
    }

    /// 枚举的原始值列成 `"a", "b", "c"` / Lists the raw values as `"a", "b", "c"`
    private static func allowedValues<Value: RawRepresentable<String>>(_ values: [Value]) -> String {
        let quoted = values.map { value in
            "\"\(value.rawValue)\""
        }
        return quoted.joined(separator: ", ")
    }
}
