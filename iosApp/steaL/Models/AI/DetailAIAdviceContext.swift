//
//  DetailAIAdviceContext.swift
//  steaL
//
//  Created by yijiang on 2026/9/25.
//

import Foundation

// MARK: - 根对象 / Root

/// 详情页 AI 购买建议的输入：从详情页已加载的数据里挑出 AI 需要的部分，编码成 JSON 放进 prompt /
/// Input of the detail page's AI purchase advice: the parts of the already-loaded detail data the model needs,
/// encoded as JSON into the prompt
///
/// 只发给 AI，不在界面显示；字段按 AI 好读来取名，之后可以为省 token 调整，界面不依赖它 /
/// Sent to the model only, never shown; named for the model to read, and free to be trimmed for tokens
/// because no view depends on it
struct DetailAIAdviceContext: Encodable, Hashable, Sendable {

    // MARK: 游戏 / Game

    /// 游戏名称 / Game title
    let title: String
    /// 定价类型：`paid` / `freeForever` / `unreleased` / How the store prices the title
    let kind: String
    /// Steam 的发售日期文本，如 `"19 Feb, 2026"` / Steam's release date text
    let releaseDate: String?
    /// 是否抢先体验 / Whether the game is in early access
    let isEarlyAccess: Bool
    /// Steam 的类型，如 `"Action"` / Steam genres such as "Action"
    let genres: [String]
    /// ITAD 标签，如 `"Souls-like"` / ITAD tags such as "Souls-like"
    let tags: [String]

    // MARK: 价格 / Prices

    /// ISO 4217 货币代码，告诉 AI 价格单位 / ISO 4217 code, so the model knows the unit
    let currency: String
    /// 在 Steam 激活的最便宜几家商店（含卖 Steam key 的第三方店），从低到高；和详情页默认的 Steam 筛选一致 /
    /// The cheapest Steam-activating shops (third-party key shops included), cheapest first, matching the detail page's default filter
    let deals: [Deal]
    /// 历史低价；永久免费为 `nil`（那是它免费之前的价）/
    /// Historical lows; `nil` for a free-forever title, whose lows predate it going free
    let historyLow: HistoryLow?

    // MARK: 口碑与热度 / Reviews and popularity

    /// 各来源的评分 / Scores from each review source
    let reviews: [Review]
    /// 在线人数；未发售游戏四个值都是 `0` / Player counts; all `0` for an unreleased game
    let players: Players?

    /// 发给 AI 的商店数上限，控制 token（端上模型上下文小）/
    /// Cap on shops sent to the model, to keep tokens down for the small on-device context
    static let maxDeals = 5

    /// 用详情页已加载的数据组装；`info` 查不到时是 `nil` /
    /// Builds the context from the detail page's loaded data; `info` is `nil` when ITAD had none
    init(
        title: String,
        details: SteamAppDetails,
        prices: ITADPriceGame,
        info: ITADGameInfoResponse?
    ) {
        self.title = title
        kind = Self.kindName(details.gameKind)
        releaseDate = details.releaseDate?.date
        isEarlyAccess = info?.earlyAccess ?? false

        let steamGenres = details.genres ?? []
        genres = steamGenres.compactMap(\.description)
        tags = info?.tags ?? []

        // 货币跟着第一条 deal；没有 deal 时用史低的货币 / The currency follows the deals, else the all-time low
        let firstPrice = prices.deals.first?.price ?? prices.allTimeLow
        currency = firstPrice?.currency ?? ""

        let sortedDeals = prices.steamDeals.sorted { first, second in
            first.price.amountInt < second.price.amountInt
        }
        let cheapestDeals = sortedDeals.prefix(Self.maxDeals)
        deals = cheapestDeals.map { deal in
            Deal(deal: deal)
        }

        if details.gameKind.showsPriceHistory {
            historyLow = HistoryLow(history: prices.historyLow)
        } else {
            historyLow = nil
        }

        let infoReviews = info?.reviews ?? []
        reviews = infoReviews.compactMap { review in
            Review(review: review)
        }

        if let infoPlayers = info?.players {
            players = Players(counts: infoPlayers)
        } else {
            players = nil
        }
    }

    /// 定价类型的英文名，AI 读的是字符串 / The kind as a plain string for the model
    private static func kindName(_ kind: SteamAppDetails.GameKind) -> String {
        switch kind {
        case .demo: "demo"
        case .freeForever: "freeForever"
        case .unreleased: "unreleased"
        case .paid: "paid"
        }
    }
}

// MARK: - 嵌套结构 / Nested types

extension DetailAIAdviceContext {

    /// 一家商店当前的价格 / One shop's current price
    struct Deal: Encodable, Hashable, Sendable {

        /// 商店名称 / Shop name
        let shop: String
        /// 现价 / Current price
        let price: Double
        /// 原价 / Regular price
        let regularPrice: Double
        /// 折扣百分比 / Discount in percent
        let discountPercent: Int
        /// 优惠结束时间（ISO8601），没有已知截止为 `nil` / When the sale ends (ISO8601), `nil` when unknown
        let saleEnds: String?

        init(deal: ITADPriceDeal) {
            shop = deal.shop.name
            price = deal.price.amount
            regularPrice = deal.regular.amount
            discountPercent = deal.cut
            saleEnds = deal.expiry
        }
    }

    /// 历史低价三档 / The three historical-low windows
    struct HistoryLow: Encodable, Hashable, Sendable {

        /// 全史最低 / All-time low
        let allTime: Double?
        /// 近一年最低 / Lowest within the last year
        let lastYear: Double?
        /// 近三个月最低 / Lowest within the last three months
        let last3Months: Double?

        init(history: ITADPriceHistory) {
            allTime = history.all?.amount
            lastYear = history.y1?.amount
            last3Months = history.m3?.amount
        }
    }

    /// 一个来源的评分 / One source's score
    struct Review: Encodable, Hashable, Sendable {

        /// 来源名，如 `"Steam"`（分数是好评百分比）/ Source name; for "Steam" the score is the positive percentage
        let source: String
        /// 分数 / Score
        let score: Int
        /// 参与人数 / Number of ratings behind the score
        let count: Int?

        /// 来源或分数缺失时不发给 AI / Skipped when the source or the score is missing
        init?(review: ITADReview) {
            guard let source = review.source else { return nil }
            guard let score = review.score else { return nil }

            self.source = source
            self.score = score
            count = review.count
        }
    }

    /// 在线人数 / Player counts
    struct Players: Encodable, Hashable, Sendable {

        /// 最近在线 / Most recent count
        let recent: Int?
        /// 过去一天峰值 / Past day
        let lastDay: Int?
        /// 过去一周峰值 / Past week
        let lastWeek: Int?
        /// 历史峰值 / All-time peak
        let allTimePeak: Int?

        init(counts: ITADPlayerCounts) {
            recent = counts.recent
            lastDay = counts.day
            lastWeek = counts.week
            allTimePeak = counts.peak
        }
    }
}
