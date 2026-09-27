//
//  AIAdviceTip.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import SwiftUI
import TipKit

/// 详情页 ✨ 按钮的提示：告诉用户可以问 AI 该现在买还是再等等。
/// The tip on the detail page's ✨ button: tells people they can ask AI whether to buy now or wait.
///
/// ✨ 图标不直观，功能又只要一步，适合 tip。打开过两次详情页才显示，避开刚看完欢迎页的第一次；
/// tip 直接指向 ✨ 按钮，所以不再放同一个图标。
/// The ✨ symbol isn't self-explanatory and the feature takes one step, which suits a tip. It shows only after
/// two detail page visits, skipping the first one right after the welcome sheet; it points straight at the ✨
/// button, so it doesn't repeat the same symbol.
struct AIAdviceTip: Tip {

    /// 打开详情页的事件，每进一次详情页记一次。
    /// The detail page visit event, donated once per visit.
    static let detailViewed = Tips.Event(id: "detailViewed")

    /// 标题：直接说功能是什么。
    /// The title: says plainly what the feature is.
    var title: Text {
        Text("Ask AI Before You Buy")
    }

    /// 说明：用这个功能能得到什么。
    /// The message: what people get from the feature.
    var message: Text? {
        Text("Find out whether to buy now or wait for a better price.")
    }

    /// 显示条件：打开过至少两次详情页。
    /// When it shows: after at least two detail page visits.
    var rules: [Rule] {
        #Rule(Self.detailViewed) { event in
            event.donations.count >= 2
        }
    }
}
