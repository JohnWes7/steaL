//
//  SearchView.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import SwiftUI

/// 搜索页：只做「进入 → 聚焦 → 退出」，未接任何网络请求 /
/// Search screen: only the "get in, focus, get back out" path, no networking
///
/// 顶部结构与 `GamesView` 对齐，两屏的搜索栏落在同一个 Y 上 / The top area mirrors `GamesView`
struct SearchView: View {

    /// 当前是否停留在搜索 tab，由 `MainTabView` 传入：进入时聚焦、离开时收起键盘 /
    /// Whether the search tab is selected; one signal covers focus on entry and resign on exit
    ///
    /// 用两个信号时切走 tab 不会失焦（内容没被销毁），键盘会留在别的页面上 / It stayed up before
    let isSelected: Bool

    /// 搜索关键字。暂时只用来驱动占位内容，方便确认输入链路是通的 /
    /// Search keyword. For now it only drives placeholder content, to prove the input path works
    @State private var query = ""

    /// 真实输入框的焦点 / Focus of the real text field
    @FocusState private var isFieldFocused: Bool

    /// 正在进行中的聚焦任务，离开搜索页时取消 / The in-flight focus task, cancelled when leaving search
    @State private var focusTask: Task<Void, Never>?

    var body: some View {
        ZStack {
            Color.appBackground
                .ignoresSafeArea()

            placeholderContent
        }
        // 点空白处收起键盘，即「退出但不搜索」/ Tapping empty space resigns the keyboard
        .contentShape(Rectangle())
        .onTapGesture { isFieldFocused = false }
        // 用 `.safeAreaInset` 而非 `.safeAreaBar`：后者的 bar 在独立上下文里，`@FocusState` 传不回来 /
        // `.safeAreaInset` rather than `.safeAreaBar`: the latter hosts the bar out of context
        .safeAreaInset(edge: .top, spacing: 0) {
            headerArea
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { handleSelectionChange(isSelected) }
        .onChange(of: isSelected) { _, selected in handleSelectionChange(selected) }
    }

    // MARK: - 进入 / 离开 / Enter / leave

    /// 进入搜索 tab 时聚焦输入框，离开时收起键盘 /
    /// Focus the field when the search tab is entered, resign the keyboard when it is left
    private func handleSelectionChange(_ selected: Bool) {
        // 掐掉上一次没跑完的聚焦任务，否则它会在离开后又把键盘弹回来 / Cancel any unfinished focus attempt
        focusTask?.cancel()

        guard selected else {
            isFieldFocused = false
            return
        }

        focusTask = Task { @MainActor in
            // 上屏前的赋值会被忽略，等固定间隔又会让键盘滞后：立即试一次，再在 0.4 秒内补试 /
            // Assignment before the field is on screen is ignored, a fixed wait visibly lags
            for _ in 0..<8 {
                if Task.isCancelled { return }
                isFieldFocused = true
                try? await Task.sleep(for: .milliseconds(50))
            }
        }
    }

    // MARK: - 顶部固定区 / Pinned header

    /// 与 `GamesView.headerArea` 同构：标题 + 搜索框，同一套内边距和分隔线 /
    /// Same shape as `GamesView.headerArea`: title plus search bar, same padding and divider
    @ViewBuilder
    private var headerArea: some View {
        VStack(alignment: .leading, spacing: 8) {
            header

            // 未搜索时这一行只有搜索框（与 `GamesView` 一致），开始输入才让出位置给取消按钮 /
            // Only the bar until the user actually types; the cancel button takes space after that
            HStack(spacing: 10) {
                searchField

                if isEditing {
                    cancelButton
                }
            }
            .animation(.easeOut(duration: 0.18), value: isEditing)
        }
        .padding(.horizontal, 14)
        .padding(.top, 2)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.bar)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(.white.opacity(0.08))
                .frame(height: 0.5)
        }
    }

    /// 标题区：字号与 `GamesView` 的品牌标题一致（13pt 标签 + 44pt 圆体），保证高度相同 /
    /// Title block, matching the `GamesView` wordmark scale so both blocks are the same height
    @ViewBuilder
    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("STEAM PRICE TRACKER")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(.brandGreen)

            Text("Search")
                .font(.system(size: 44, weight: .heavy, design: .rounded))
                .foregroundStyle(.primaryText)
        }
    }

    /// 真实输入框。外壳与 `GamesView` 的入口按钮共用 `SearchBarShell` /
    /// The real text field, sharing `SearchBarShell` with the `GamesView` entry button
    @ViewBuilder
    private var searchField: some View {
        SearchBarShell {
            TextField("Search games, publishers...", text: $query)
                .font(.system(size: 17))
                .foregroundStyle(.primaryText)
                .tint(.brandBlue)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .focused($isFieldFocused)

            // 有内容时给一个清除按钮 / A clear button while there is text
            if !query.isEmpty {
                Button {
                    query = ""
                    isFieldFocused = true
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 17))
                        .foregroundStyle(.secondaryText)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Clear search")
            }
        }
    }

    /// 退出搜索：清空关键字并收起键盘，不提交任何查询 /
    /// Leave search: clear the keyword and resign the keyboard without submitting anything
    @ViewBuilder
    private var cancelButton: some View {
        Button("Cancel", action: cancelSearch)
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(.brandBlue)
            .buttonStyle(.plain)
            .transition(.move(edge: .trailing).combined(with: .opacity))
            .accessibilityLabel("Cancel search")
    }

    /// 是否处于"正在搜索"：聚焦过或有内容时才显示取消按钮 /
    /// Whether search is active: the cancel button shows once the field is focused or non-empty
    private var isEditing: Bool {
        isFieldFocused || !query.isEmpty
    }

    /// 退出搜索但不提交。清掉任务，避免它稍后把键盘又弹回来 /
    /// Leaves search without submitting. Clears the task so it cannot raise the keyboard again
    private func cancelSearch() {
        focusTask?.cancel()
        isFieldFocused = false
        query = ""
    }

    // MARK: - 占位内容 / Placeholder content

    /// 数据接入前的占位：空关键字显示引导，有关键字则回显，可确认输入传到了这一层 /
    /// Placeholder before data is wired up: a hint when empty, an echo when not
    @ViewBuilder
    private var placeholderContent: some View {
        VStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(.secondaryText)

            if trimmedQuery.isEmpty {
                Text("Search for a Steam game")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.primaryText)

                Text("Prices from every store will show up here.")
                    .font(.system(size: 14))
                    .foregroundStyle(.secondaryText)
            } else {
                Text("You typed")
                    .font(.system(size: 14))
                    .foregroundStyle(.secondaryText)

                Text(trimmedQuery)
                    .font(.system(size: 22, weight: .heavy, design: .rounded))
                    .foregroundStyle(.primaryText)
                    .multilineTextAlignment(.center)

                Text("Search is not wired up yet.")
                    .font(.system(size: 14))
                    .foregroundStyle(.secondaryText)
            }
        }
        .padding(.horizontal, 28)
        .frame(maxWidth: .infinity)
    }

    /// 去掉首尾空白的输入 / Keyword with surrounding whitespace removed
    private var trimmedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

#Preview {
    SearchView(isSelected: true)
}
