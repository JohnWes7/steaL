//
//  SearchView.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import SwiftUI

/// 搜索页。本轮只做"进入 → 搜索框就位并自动聚焦 → 能退出"这条链路，还没有接任何网络请求 /
/// Search screen. This round only covers "get in, have the field focused, get back out";
/// no networking is wired up yet.
///
/// 顶部结构与 `GamesView` 保持一致：同样的内容顺序、同样的字体、同样的底栏材质和分隔线，
/// 唯一的差别是标题文字。这样两屏的搜索栏落在完全相同的 Y 上，切 tab 时它看起来没有动过 /
/// The top area mirrors `GamesView`: same content order, same type scale, same bar material and
/// divider. Only the title text differs. That puts the search bar at exactly the same Y on both
/// screens, so it looks untouched when the tab changes.
struct SearchView: View {

    /// 当前是否停留在搜索 tab，由 `MainTabView` 传入 /
    /// Whether the search tab is currently selected, passed in by `MainTabView`
    ///
    /// 一个信号同时管两个方向：进入时自动聚焦，离开时收起键盘。
    /// 之前用"请求聚焦"的独立信号时，切走 tab 并不会让输入框失焦——`SearchView`
    /// 只是被切走而没有销毁，键盘会一直挂在别的页面上 /
    /// One signal covers both directions: focus on entry, resign on exit. With the previous
    /// dedicated "focus request" signal, switching away did not resign the field — the tab content
    /// is kept alive rather than destroyed, so the keyboard stayed up over the other screens.
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
        // 点空白处收起键盘：搜索页最自然的"退出但不搜索"手势 /
        // Tapping empty space resigns the keyboard — the most natural "leave without searching" gesture
        .contentShape(Rectangle())
        .onTapGesture { isFieldFocused = false }
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
        // 先把上一次没跑完的聚焦任务掐掉，避免离开后又被它弹回键盘 /
        // Cancel any unfinished focus attempt first, otherwise it could pop the keyboard back up
        // after the user has already left
        focusTask?.cancel()

        guard selected else {
            isFieldFocused = false
            return
        }

        focusTask = Task { @MainActor in
            // 输入框上屏前赋值会被系统忽略；固定等一个较大值又会让键盘明显滞后。
            // 所以立刻先试一次，再在头 0.4 秒内补试几次：成功时几乎无延迟，失败时也能兜住。
            // 重复设 true 是幂等的，已经聚焦时不会打断用户 /
            // Assigning before the field is on screen is silently ignored, while waiting one longer
            // fixed interval makes the keyboard visibly lag. So try immediately, then re-assert over
            // the first ~0.4s: near-zero latency when it works, a safety net when it does not.
            // Re-setting true is idempotent and does not disturb an already focused field.
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

            // 未进入搜索时这一行只有一个搜索框，和 `GamesView` 完全一致；
            // 开始输入后才让出位置给取消按钮，外观变化只发生在"已经进入搜索"之后 /
            // Before search is active this row holds only the bar, matching `GamesView` exactly;
            // the cancel button only takes space once the user is actually searching
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

    /// 标题区。字号与 `GamesView` 的品牌标题一致（13pt 标签 + 44pt 圆体标题），
    /// 保证整块高度相同，搜索栏因此不会上下跳 /
    /// Title block. Same type scale as the `GamesView` wordmark (13pt label + 44pt rounded title)
    /// so the block keeps the same height and the search bar does not jump vertically
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

            // 有内容时给一个清除按钮，反复试聚焦时很方便 /
            // Clear button while there is text, handy when testing focus repeatedly
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

    /// 数据接入前先给出可读的占位：空关键字显示引导，有关键字则回显，
    /// 用来确认输入确实传到了这一层 /
    /// Readable placeholder before data is wired up: a hint when the keyword is empty, an echo
    /// when it is not — which proves the input actually reaches this layer
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
