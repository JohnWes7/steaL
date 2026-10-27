//
//  DetailAIAdviceResponse+Preview.swift
//  steaL
//
//  Created by yijiang on 2026/9/26.
//

import Foundation

#if DEBUG

extension DetailAIAdviceResponse {

    /// 预览用：现在就买 / Preview sample: buy now
    static let previewBuy = DetailAIAdviceResponse(
        verdict: .buy,
        score: 86,
        headline: "It matches its lowest price this year, and players still love it.",
        reasons: [
            Reason(
                stance: .pro,
                topic: .price,
                text: "At A$48.38 it is 52% off and equals the 1-year low."
            ),
            Reason(
                stance: .pro,
                topic: .reviews,
                text: "94% of 180,000 Steam reviews are positive."
            ),
            Reason(
                stance: .caution,
                topic: .price,
                text: "The all-time low of A$45.53 is A$2.85 cheaper."
            ),
        ],
        targetPrice: nil
    )

    /// 预览用：再等等，带目标价 / Preview sample: wait, with a target price
    static let previewWait = DetailAIAdviceResponse(
        verdict: .wait,
        score: 62,
        headline: "A fair discount, but it has been much cheaper before.",
        reasons: [
            Reason(
                stance: .pro,
                topic: .players,
                text: "12,400 people played in the last day, so the servers are busy."
            ),
            Reason(
                stance: .con,
                topic: .price,
                text: "A$69.95 is A$24.42 above the all-time low of A$45.53."
            ),
            Reason(
                stance: .caution,
                topic: .risk,
                text: "It is still in early access, so content may change."
            ),
        ],
        targetPrice: 48.38
    )
}

#endif
