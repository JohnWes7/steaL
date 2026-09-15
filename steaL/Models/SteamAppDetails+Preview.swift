//
//  SteamAppDetails+Preview.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import Foundation

#if DEBUG

extension SteamAppDetails {

    /// 预览用示例数据：`SteamAppDetails` 手写了 `init(from:)`，没有 memberwise init，只能从 JSON 构造 /
    /// Sample data for previews; the hand-written `init(from:)` leaves no memberwise init to use
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
