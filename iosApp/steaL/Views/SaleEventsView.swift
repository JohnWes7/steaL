//
//  SaleEventsView.swift
//  steaL
//
//  Created by yijiang on 2026/11/6.
//

import SwiftUI

/// Sales tab：各商店的促销活动日历。
/// The Sales tab: a calendar of sale events across stores.
struct SaleEventsView: View {
    var body: some View {
        // 正文还没做，先只放背景，让顶栏有地方挂 / The content is not built yet; only the background is here for the top bar to sit on
        Color.clear
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.appBackground)
            .safeAreaBar(edge: .top, spacing: 0) {
                PageHeaderView(title: "Sales")
            }
            .toolbar(.hidden, for: .navigationBar)
    }
}

#Preview {
    NavigationStack {
        SaleEventsView()
    }
}
