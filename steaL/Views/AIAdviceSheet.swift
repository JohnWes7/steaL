//
//  AIAdviceSheet.swift
//  steaL
//
//  Created by yijiang on 2026/9/26.
//

import SwiftUI

/// 详情页的 AI 购买建议 sheet；结果放在详情页持有的 `AIAdviceViewModel` 里，关掉再开还在 /
/// The detail page's AI purchase advice sheet; the result lives in the page-owned `AIAdviceViewModel`,
/// so it survives closing and reopening
struct AIAdviceSheet: View {

    /// 由 `GameDetailView` 持有，sheet 只读 / Owned by `GameDetailView`; the sheet only reads it
    let viewModel: AIAdviceViewModel

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            // 内容之后按 plan 的 5 块补上 / The five blocks from the plan come later
            Color.clear
                .navigationTitle("Should You Buy?")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        // iOS 26 的 `.close` 角色显示成系统 xmark 按钮 / The `.close` role renders the system xmark button on iOS 26
                        Button(role: .close) {
                            dismiss()
                        }
                    }
                }
        }
        // medium 放得下结论 + 价格一行 + 第一条理由 / Medium fits the verdict, the price row, and the first reason
        .presentationDetents([.medium, .large])
    }
}

#Preview {
    Color.clear
        .sheet(isPresented: .constant(true)) {
            AIAdviceSheet(viewModel: AIAdviceViewModel())
        }
}
