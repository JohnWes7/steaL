//
//  SteamAppDetails+Preview.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import Foundation

#if DEBUG

extension SteamAppDetails {

    /// 预览用示例数据，由真实响应形状的 JSON 解码得到 /
    /// Sample data for previews, decoded from JSON shaped like a real response
    ///
    /// `SteamAppDetails` 的解码是手写的（`required_age` 有数字与字符串两种形态），没有
    /// memberwise init，所以样例只能从 JSON 构造 /
    /// `SteamAppDetails` hand-writes `init(from:)` — `required_age` arrives as both a number and a
    /// string — so it has no memberwise init and the sample has to be built from JSON
    static let previewSample: SteamAppDetails = {
        let json = """
            {
              "name": "Portal 2",
              "type": "game",
              "short_description": "A first-person puzzle platformer with a full co-operative campaign, a level editor and two robots who are very bad at following instructions. Test chambers, gels, bridges and lasers await the player across a facility that keeps changing its mind.",
              "developers": ["Valve"],
              "publishers": ["Valve"],
              "categories": [
                { "id": 1, "description": "Single-player" },
                { "id": 9, "description": "Co-op" },
                { "id": 38, "description": "Steam Achievements" },
                { "id": 30, "description": "Steam Workshop" },
                { "id": 28, "description": "Full controller support" },
                { "id": 37, "description": "Remote Play on Phone" }
              ],
              "platforms": {
                "windows": true,
                "mac": true,
                "linux": true
              }
            }
            """

        guard
            let details = try? JSONDecoder().decode(SteamAppDetails.self, from: Data(json.utf8))
        else {
            preconditionFailure("预览样例解码失败 / Failed to decode the preview sample")
        }

        return details
    }()
}

#endif
