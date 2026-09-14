//
//  ITADGameInfoResponse+Preview.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import Foundation

#if DEBUG

extension ITADGameInfoResponse {

    /// 预览用示例数据，由真实响应形状的 JSON 解码得到 /
    /// Sample data for previews, decoded from JSON shaped like a real response
    ///
    /// 该类型的解码是手写的（`assets` 有对象与空数组两种形态），没有 memberwise init，所以样例
    /// 只能从 JSON 构造；第三条 review 故意没有 `score`，正是区块的过滤器要丢掉的那种 /
    /// The type hand-writes `init(from:)` (`assets` arrives as an object or as an empty array), so
    /// it has no memberwise init and the sample is built from JSON. The third review deliberately
    /// has no `score`, which is exactly what the section's filter drops
    static let previewSample: ITADGameInfoResponse = {
        let json = """
            {
              "id": "018d937f-0f5c-72f6-9b1e-2e5b3c4d5e6f",
              "slug": "portal-2",
              "title": "Portal 2",
              "type": "game",
              "mature": false,
              "reviews": [
                { "score": 93, "source": "Steam", "count": 421337 },
                { "score": 95, "source": "Metascore", "count": 43 },
                { "source": "OpenCritic" }
              ],
              "stats": { "rank": 12, "waitlisted": 1234, "collected": 5678 },
              "players": { "recent": 4321, "day": 12345, "week": 54321, "peak": 98765 }
            }
            """

        guard
            let info = try? JSONDecoder().decode(ITADGameInfoResponse.self, from: Data(json.utf8))
        else {
            preconditionFailure("预览样例解码失败 / Failed to decode the preview sample")
        }

        return info
    }()
}

#endif
