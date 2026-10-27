//
//  AIAdviceSheet.swift
//  steaL
//
//  Created by yijiang on 2026/9/26.
//

import SwiftUI

/// 详情页的 AI 购买建议 sheet。
/// The detail page's AI purchase advice sheet.
///
/// 结果放在详情页持有的 `AIAdviceViewModel` 里，关掉再开还在；整张 sheet 只看一个状态，AI 成功才显示内容，价格行也只在成功时出现。
/// The result lives in the page-owned `AIAdviceViewModel`, so it survives closing and reopening; the whole sheet
/// runs on one state, and content, the price row included, only appears once the advice is in.
struct AIAdviceSheet: View {

    /// AI 建议的状态机，由 `GameDetailView` 持有，sheet 只读。
    /// The advice state machine, owned by `GameDetailView`; the sheet only reads it.
    let viewModel: AIAdviceViewModel

    /// 详情页的比价状态，价格行和打开商店都从这里取。
    /// The detail page's price state, read by the price row and the store link.
    ///
    /// 和 AI 拿到的是同一份，界面与 AI 的判断不会对不上。
    /// It's the same data the model saw, so the screen and the advice never disagree.
    let pricesState: GameDetailViewModel.PricesState

    /// Retry / Regenerate：由详情页用最新数据重新拼输入。
    /// Retry and Regenerate; the detail page rebuilds the input from fresh data.
    let regenerate: () -> Void

    /// 关闭 sheet。
    /// Closes the sheet.
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Should You Buy?")
                // HIG Generative AI：告诉用户这是 AI 生成的、可能出错。
                // HIG: say it is AI-generated and may be wrong.
                .navigationSubtitle("AI analysis, may contain errors")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        // iOS 26 的 `.close` 角色显示成系统 xmark 按钮。
                        // The `.close` role renders the system xmark button on iOS 26.
                        Button(role: .close) {
                            dismiss()
                        }
                    }
                }
        }
        // medium 放得下结论 + 价格一行 + 第一条理由。
        // Medium fits the verdict, the price row, and the first reason.
        .presentationDetents([.medium, .large])
    }

    /// 按状态切换的内容。
    /// The content, switched on the state.
    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        // idle 只在点了入口、任务还没跑起来的一瞬间出现。
        // idle only shows for the instant before the task starts.
        case .idle, .loading:
            // HIG：写具体在做什么，不要只写 "Processing…"。
            // HIG: say what is happening, not just "Processing…".
            ProgressView("Checking prices and reviews…")
                .tint(.brandGreen)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

        case .loaded(let advice, let generatedAt, let model, let providerName):
            adviceList(advice, generatedAt: generatedAt, model: model, providerName: providerName)

        case .missingAPIKey(let providerName):
            missingAPIKeyView(providerName: providerName)

        case .failed(let error):
            LoadErrorView(error: error, retry: regenerate)
        }
    }

    /// 选中的服务商没有 key 时的提示。
    /// The notice shown when the chosen provider has no key.
    ///
    /// 不给 Retry：重试还是读不到 key，只能去设置里补上或换回内置模型。
    /// No retry: trying again still finds no key; it has to be added in settings, or the built-in model chosen.
    ///
    /// - Parameter providerName: 服务商名字 / The provider's name.
    /// - Returns: 提示视图 / The notice view.
    private func missingAPIKeyView(providerName: String) -> some View {
        ContentUnavailableView {
            Label("No API Key", systemImage: "key.slash")
        } description: {
            Text("\(providerName) has no API key on this device. Add it in Profile > AI Model, or switch to the built-in model.")
        }
    }

    // MARK: - 结果 / Result

    /// 结论卡 → 主操作 → 价格一行 → 理由 → Regenerate 与来源。
    /// Verdict, main action, price row, reasons, then Regenerate and source.
    ///
    /// 主操作紧跟结论，看完结论就能点。
    /// The action sits right under the verdict, so it's one tap away after reading it.
    ///
    /// - Parameters:
    ///   - advice: AI 的回答 / The model's answer.
    ///   - generatedAt: 生成时间 / When it was generated.
    ///   - model: 生成它的模型名 / The name of the model that generated it.
    ///   - providerName: 服务商名，`nil` 是内置 / The provider's name, `nil` for the built-in one.
    /// - Returns: 结果列表 / The result list.
    private func adviceList(
        _ advice: DetailAIAdviceResponse,
        generatedAt: Date,
        model: String,
        providerName: String?
    ) -> some View {
        List {
            Section {
                AIAdviceVerdictView(advice: advice)
            }

            primaryActionSection(advice)

            if let bestSteamDeal, let historyLow {
                Section {
                    AIAdvicePriceFactView(deal: bestSteamDeal, historyLow: historyLow)
                }
            }

            Section("Why") {
                ForEach(sortedReasons(advice), id: \.self) { reason in
                    AIAdviceReasonRow(reason: reason)
                }
            }

            // 单独一组：和主按钮放同一组会被 List 连成一块。
            // Its own section, or the list joins it to the main button.
            Section {
                Button("Regenerate", systemImage: "arrow.clockwise", action: regenerate)
                    .tint(.brandBlue)
            } footer: {
                sourceFooter(generatedAt: generatedAt, model: model, providerName: providerName)
            }
        }
    }

    /// 利好在前，注意和不利在后。
    /// Pros first, then cautions and cons.
    ///
    /// prompt 已经要求过这个顺序，这里再保证一次。
    /// The prompt already asks for this order; this makes sure.
    ///
    /// - Parameter advice: AI 的回答 / The model's answer.
    /// - Returns: 排好序的理由 / The sorted reasons.
    private func sortedReasons(_ advice: DetailAIAdviceResponse) -> [DetailAIAdviceResponse.Reason] {
        let pros = advice.reasons.filter { reason in reason.stance == .pro }
        let others = advice.reasons.filter { reason in reason.stance != .pro }
        return pros + others
    }

    /// 主操作随结论变，自成一组：买 → 打开商店；等 → AI 的目标价；跳过 → 整组都没有。
    /// The main action follows the verdict in its own section: buy opens the store,
    /// wait shows the model's target price, skip has no section at all.
    ///
    /// - Parameter advice: AI 的回答 / The model's answer.
    /// - Returns: 主操作那一组 / The main action's section.
    @ViewBuilder
    private func primaryActionSection(_ advice: DetailAIAdviceResponse) -> some View {
        switch advice.scoredVerdict {
        case .buy:
            if let bestSteamDeal, let url = URL(string: bestSteamDeal.url) {
                Section {
                    // 离开 App 用 `arrow.up.forward.square`，沿用项目约定。
                    // Leaving the app uses `arrow.up.forward.square`, per the project's convention.
                    Link(destination: url) {
                        Label("Open in \(bestSteamDeal.shop.name)", systemImage: "arrow.up.forward.square")
                            .font(.headline)
                            .foregroundStyle(.onBrandGreen)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .tint(.brandGreen)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }
            }

        case .wait:
            if let targetPriceText = targetPriceText(advice) {
                Section {
                    Label("Worth it at \(targetPriceText)", systemImage: "tag")
                        .foregroundStyle(.primaryText)
                }
            }

        case .skip:
            EmptyView()
        }
    }

    /// AI 的目标价，用 deal 的币种显示。
    /// The model's target price, in the deal's currency.
    ///
    /// - Parameter advice: AI 的回答 / The model's answer.
    /// - Returns: 格式化后的价格；没有目标价或没有 deal 时是 `nil` / The formatted price, or `nil` without a target price or a deal.
    private func targetPriceText(_ advice: DetailAIAdviceResponse) -> String? {
        guard let targetPrice = advice.targetPrice else { return nil }
        guard let bestSteamDeal else { return nil }

        let currency = bestSteamDeal.price.currency
        return targetPrice.formatted(.currency(code: currency))
    }

    /// 数据来源 + 模型 + 生成时间。
    /// Data source, model and generation time.
    ///
    /// 结果跟着详情页，要让用户知道是谁、多久前的判断。自己的服务商可能有好几个，要写出名字；内置是默认选项，只写模型名。
    /// The result lives with the page, so say who judged it and how long ago. A user may have several providers, so theirs
    /// is named; the built-in one is the default and only shows the model.
    ///
    /// - Parameters:
    ///   - generatedAt: 生成时间 / When it was generated.
    ///   - model: 生成它的模型名 / The name of the model that generated it.
    ///   - providerName: 服务商名，`nil` 是内置 / The provider's name, `nil` for the built-in one.
    /// - Returns: 脚注文字 / The footer text.
    private func sourceFooter(generatedAt: Date, model: String, providerName: String?) -> Text {
        let relativeText = generatedAt.formatted(.relative(presentation: .named))

        if let providerName {
            return Text("Prices and reviews from IsThereAnyDeal. Generated by \(model) via \(providerName) \(relativeText).")
        }
        return Text("Prices and reviews from IsThereAnyDeal. Generated by \(model) \(relativeText).")
    }

    // MARK: - 比价 / Prices

    /// 最便宜的 Steam 激活 deal；比价不在已加载时是 `nil`。
    /// The cheapest Steam-activating deal; `nil` unless prices are loaded.
    private var bestSteamDeal: ITADPriceDeal? {
        switch pricesState {
        case .loaded(let prices), .loadedWithoutHistory(let prices):
            return prices.bestSteamDeal
        case .hidden, .loading, .notFound, .failed:
            return nil
        }
    }

    /// 价格行要的历史低价。
    /// The lows for the price row.
    ///
    /// 永久免费（`.loadedWithoutHistory`）不显示，那是它免费前的价。
    /// None for a free-forever title, whose lows predate it going free.
    private var historyLow: ITADPriceHistory? {
        guard case .loaded(let prices) = pricesState else { return nil }
        return prices.historyLow
    }
}

#Preview("Buy") {
    Color.clear
        .sheet(isPresented: .constant(true)) {
            AIAdviceSheet(
                viewModel: AIAdviceViewModel(state: .loaded(.previewBuy, generatedAt: .now.addingTimeInterval(-180), model: "deepseek-flash", providerName: nil)),
                pricesState: .loaded(.previewSample),
                regenerate: {}
            )
        }
}

#Preview("Wait") {
    Color.clear
        .sheet(isPresented: .constant(true)) {
            AIAdviceSheet(
                viewModel: AIAdviceViewModel(state: .loaded(.previewWait, generatedAt: .now, model: "deepseek-flash", providerName: nil)),
                pricesState: .loaded(.previewSample),
                regenerate: {}
            )
        }
}

#Preview("Own Provider") {
    Color.clear
        .sheet(isPresented: .constant(true)) {
            AIAdviceSheet(
                viewModel: AIAdviceViewModel(state: .loaded(.previewBuy, generatedAt: .now, model: "gpt-4o", providerName: "My OpenRouter")),
                pricesState: .loaded(.previewSample),
                regenerate: {}
            )
        }
}

#Preview("Loading") {
    Color.clear
        .sheet(isPresented: .constant(true)) {
            AIAdviceSheet(
                viewModel: AIAdviceViewModel(state: .loading),
                pricesState: .loaded(.previewSample),
                regenerate: {}
            )
        }
}

#Preview("Missing API Key") {
    Color.clear
        .sheet(isPresented: .constant(true)) {
            AIAdviceSheet(
                viewModel: AIAdviceViewModel(state: .missingAPIKey(providerName: "My OpenRouter")),
                pricesState: .loaded(.previewSample),
                regenerate: {}
            )
        }
}

#Preview("Failed") {
    Color.clear
        .sheet(isPresented: .constant(true)) {
            AIAdviceSheet(
                viewModel: AIAdviceViewModel(state: .failed(.offline)),
                pricesState: .loaded(.previewSample),
                regenerate: {}
            )
        }
}
