//
//  LLMProviderFormSheet.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import SwiftUI

/// 新增 / 编辑服务商的表单（Name / Base URL / API Key）；内容还没做 /
/// The form for adding or editing a provider (name, base URL, API key); the content is not built yet
struct LLMProviderFormSheet: View {

    // 用 `@Binding` 而不是 `onAdd` 回调：sheet 自己保存、自己关，只把结果写回弹出它的页面 /
    // A `@Binding` rather than an `onAdd` callback: the sheet saves and dismisses itself and only hands the result back
    /// 添加成功后写入新服务商，弹出它的页面据此推入 / Receives the new provider once added, so the presenting page can push it
    @Binding var addedProvider: LLMProvider?

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
            }
            .navigationTitle("Add Provider")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(role: .cancel) {
                        dismiss()
                    }
                }
            }
        }
    }
}

#Preview {
    LLMProviderFormSheet(addedProvider: .constant(nil))
}
