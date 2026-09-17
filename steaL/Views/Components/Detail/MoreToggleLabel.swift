//
//  MoreToggleLabel.swift
//  steaL
//
//  Created by yijiang on 2026/9/18.
//

import SwiftUI

/// 蓝色 `More ⌄` / `Show less ⌃` 标签：文字和箭头始终挨在一起，收起向下、展开向上 /
/// The blue `More ⌄` / `Show less ⌃` label; the chevron stays attached to the text, pointing down
/// while collapsed and up while expanded
struct MoreToggleLabel: View {

    /// 决定文案与箭头方向的展开态 / The expanded state, which picks both the copy and the chevron
    let isExpanded: Bool

    var body: some View {
        HStack(spacing: 4) {
            Text(isExpanded ? "Show less" : "More")
                .font(.system(size: 15, weight: .semibold, design: .rounded))

            Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                .font(.system(size: 12, weight: .bold))
        }
        .foregroundStyle(.brandBlue)
    }
}

#Preview {
    VStack(alignment: .leading, spacing: 16) {
        MoreToggleLabel(isExpanded: false)
        MoreToggleLabel(isExpanded: true)
    }
    .padding(20)
    .background(Color.appBackground)
}
