//
//  LLMProviderView.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import SwiftData
import SwiftUI

/// 服务商页，只负责「管」：编辑、增删模型、删除服务商；内容还没做 /
/// The provider page, which only manages: editing, adding and removing models, deleting the provider; the content is not built yet
struct LLMProviderView: View {

    /// 这一页管理的服务商 / The provider this page manages
    let provider: LLMProvider

    var body: some View {
        List {
        }
        .scrollContentBackground(.hidden)
        .background(Color.appBackground)
        .navigationTitle(provider.name)
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview(traits: .llmProviderSampleData) {
    @Previewable @Query(sort: \LLMProvider.createdAt) var providers: [LLMProvider]

    NavigationStack {
        if let provider = providers.first {
            LLMProviderView(provider: provider)
        }
    }
}
