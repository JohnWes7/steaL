//
//  DetailAIAdviceResponse.swift
//  steaL
//
//  Created by yijiang on 2026/9/25.
//

import Foundation

// MARK: - 根对象 / Root

/// 详情页 AI 购买建议的结果：AI 返回的 JSON 解码成它，sheet 的结论与理由都用它显示 /
/// Result of the detail page's AI purchase advice: the model's JSON decodes into it, and the sheet's
/// verdict and reasons are drawn from it
///
/// 不含价格数字：价格那一行由 App 直接从 `ITADPriceGame` 显示，不经过 AI（见 `docs/AI Advisor Planning.md`）/
/// Carries no prices: the price row comes straight from `ITADPriceGame`, never through the model
struct DetailAIAdviceResponse: Codable, Hashable, Sendable {

    /// 结论 / The verdict
    let verdict: Verdict
    /// 0–100，是「现在买的时机」好不好，不是游戏好不好 / 0–100, how good a time it is to buy, not how good the game is
    let score: Int
    /// 一句话结论 / One-sentence conclusion
    let headline: String
    /// 2 到 4 条理由 / Two to four reasons
    let reasons: [Reason]
    /// 值得入手的目标价，只在 `wait` 时有 / The price worth waiting for, only on `wait`
    let targetPrice: Double?
}

// MARK: - 派生 / Derived

extension DetailAIAdviceResponse {

    /// 按分数段推出的结论，分数段和 system prompt 的规则一致（80+ buy / 50–79 wait / 50 以下 skip）；
    /// AI 给的 `verdict` 和分数对不上时以分数为准，界面只用这个 /
    /// The verdict implied by the score, using the same bands as the system prompt;
    /// the score wins when the model's `verdict` disagrees, and the UI only uses this
    var scoredVerdict: Verdict {
        if score >= 80 {
            return .buy
        }

        if score >= 50 {
            return .wait
        }

        return .skip
    }
}

// MARK: - 嵌套结构 / Nested types

extension DetailAIAdviceResponse {

    /// 买、等还是跳过 / Buy, wait, or skip
    enum Verdict: String, Codable, Hashable, Sendable, CaseIterable {
        case buy
        case wait
        case skip
    }

    /// 一条理由 / One reason
    struct Reason: Codable, Hashable, Sendable {

        /// 利好、注意还是不利，决定图标 / For, caution, or against; decides the icon
        let stance: Stance
        /// 讲的是哪方面，只能是有数据的主题 / What it is about, limited to topics the app has data for
        let topic: Topic
        /// 理由正文 / The reason itself
        let text: String
    }

    /// 理由的立场 / A reason's stance
    enum Stance: String, Codable, Hashable, Sendable, CaseIterable {
        /// 利好 / In favour of buying
        case pro
        /// 需要注意 / Worth a caution
        case caution
        /// 不利 / Against buying
        case con
    }

    /// 理由的主题；没有游戏时长这类 App 拿不到数据的主题 /
    /// A reason's topic; nothing the app has no data for, such as play time
    enum Topic: String, Codable, Hashable, Sendable, CaseIterable {
        /// 价格与史低 / Price and historical lows
        case price
        /// 好评率 / Review scores
        case reviews
        /// 在线人数 / Player counts
        case players
        /// 抢先体验、预购、DRM 等风险 / Early access, pre-orders, DRM and similar risks
        case risk
    }
}
