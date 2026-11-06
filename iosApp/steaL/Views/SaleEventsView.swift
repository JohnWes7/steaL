//
//  SaleEventsView.swift
//  steaL
//
//  Created by yijiang on 2026/11/6.
//

import SwiftUI

/// Sales tab：各商店的促销活动日历，进行中的在最上面，其余按开始月份分段。
/// The Sales tab: a calendar of sale events across stores, ongoing ones first and the rest by start month.
///
/// 方案见 `docs/Sales Page Planning.md`。
/// The plan is in `docs/Sales Page Planning.md`.
struct SaleEventsView: View {

    /// 页面的数据状态 / The page's data state.
    @State private var viewModel = SaleEventsViewModel()

    /// 切月份用的日历（带用户时区）/ The calendar used to split months (with the user's time zone).
    @Environment(\.calendar) private var calendar

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.appBackground)
            .safeAreaBar(edge: .top, spacing: 0) {
                PageHeaderView(title: "Sales")
            }
            .toolbar(.hidden, for: .navigationBar)
            .task {
                await viewModel.loadIfNeeded()
            }
    }

    // MARK: - 状态 / States

    /// 按加载状态显示转圈、错误或列表。
    /// Shows a spinner, the error or the list depending on the loading state.
    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading:
            ProgressView()
        case .failed(let error):
            LoadErrorView(error: error) {
                Task {
                    await viewModel.load()
                }
            }
        case .loaded:
            let sections = viewModel.sections(now: .now, calendar: calendar)
            if sections.isEmpty {
                ContentUnavailableView(
                    "No Upcoming Sales",
                    systemImage: "calendar",
                    description: Text("Store sales and fests will show up here once they're announced.")
                )
            } else {
                eventList(sections)
            }
        }
    }

    // MARK: - 列表 / List

    /// 分段的活动列表。
    /// The sectioned event list.
    private func eventList(_ sections: [SaleEventsViewModel.EventSection]) -> some View {
        List {
            ForEach(sections) { section in
                Section {
                    ForEach(section.events) { event in
                        SaleEventRowView(event: event, isHappeningNow: section.kind == .happeningNow)
                    }
                } header: {
                    sectionHeader(for: section.kind)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
    }

    /// 段标题：进行中写「Happening Now」，其余写月份和年份。
    /// The section header: "Happening Now" for ongoing events, otherwise the month and year.
    @ViewBuilder
    private func sectionHeader(for kind: SaleEventsViewModel.SectionKind) -> some View {
        switch kind {
        case .happeningNow:
            Text("Happening Now")
        case .month(let month):
            Text(month, format: .dateTime.month(.wide).year())
        }
    }
}

#Preview {
    // 直接请求线上的自建后端（公开接口，不需要 key）/ Requests the live backend directly (a public endpoint, no key needed)
    NavigationStack {
        SaleEventsView()
    }
}
