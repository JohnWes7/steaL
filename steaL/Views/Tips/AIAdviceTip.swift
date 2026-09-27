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
/// ✨ 图标不直观，功能又只要一步，适合 tip。第一次看到 ✨ 就显示，点过 ✨ 由详情页让它失效，不另设规则；
/// tip 直接指向 ✨ 按钮，所以不再放同一个图标。
/// The ✨ symbol isn't self-explanatory and the feature takes one step, which suits a tip. It shows the first time
/// the ✨ button appears and the detail page invalidates it once ✨ is tapped, so it needs no rules; it points
/// straight at the ✨ button, so it doesn't repeat the same symbol.
struct AIAdviceTip: Tip {

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
}
