//
//  DealGenre.swift
//  steaL
//
//  Created by yijiang on 2026/10/9.
//

import Foundation

/// Hot Deals 页可选的游戏大类，`rawValue` 就是 ITAD `deals/v2` 的 `filter.tags` 取值。
/// The game genres offered on the Hot Deals page; `rawValue` is the ITAD `deals/v2` `filter.tags` value.
///
/// ITAD 没有列出全部标签的接口，标签拼错也不报错、只返回空列表，所以清单写死在这里，只放结果可靠的大类。
/// ITAD has no endpoint listing its tags, and a misspelled tag returns an empty list instead of an error, so the list is fixed here and holds only broad genres with reliable results.
enum DealGenre: String, CaseIterable, Identifiable {
    case action = "Action"
    case adventure = "Adventure"
    case rpg = "RPG"
    case strategy = "Strategy"
    case simulation = "Simulation"
    case racing = "Racing"
    case sports = "Sports"
    case horror = "Horror"
    case indie = "Indie"
    case casual = "Casual"

    var id: String { rawValue }

    /// 菜单里显示的名字。
    /// The name shown in the menu.
    var title: String {
        switch self {
        case .action: return "Action"
        case .adventure: return "Adventure"
        case .rpg: return "RPG"
        case .strategy: return "Strategy"
        case .simulation: return "Simulation"
        case .racing: return "Racing"
        case .sports: return "Sports"
        case .horror: return "Horror"
        case .indie: return "Indie"
        case .casual: return "Casual"
        }
    }
}
