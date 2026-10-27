//
//  SectionLoadingView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 区块内加载中：一块局部转圈，不把同页其它区块也打回加载态 /
/// In-section loading: a local spinner that leaves the other sections on the page alone
struct SectionLoadingView: View {

    var body: some View {
        ProgressView()
            .progressViewStyle(.circular)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 40)
    }
}

#Preview("加载中 / Loading") {
    SectionLoadingView()
        .background(Color.appBackground)
}
