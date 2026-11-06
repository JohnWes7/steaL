//
//  SaleEventsViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/11/6.
//

import Foundation
import OSLog

/// Sales 页的数据状态：加载所有商店的促销活动。
/// The Sales page's data state: loads every store's sale events.
@MainActor
@Observable
class SaleEventsViewModel {

    /// 活动列表的加载状态。
    /// The loading state of the event list.
    enum State: Equatable {
        /// 正在加载。
        /// Loading.
        case loading
        /// 加载成功，`events` 可能为空（没有活动不是错误）。
        /// Loaded; `events` may be empty (no events is not an error).
        case loaded(SaleEventList)
        /// 请求失败，带给用户看的原因。
        /// The request failed, with the reason shown to the user.
        case failed(LoadError)
    }

    /// 列表里的一段：进行中，或某个月开始的活动。
    /// One section of the list: happening now, or the events starting in one month.
    enum SectionKind: Hashable {
        /// 已经开始、还没结束。
        /// Started and not yet ended.
        case happeningNow
        /// 在这个月开始；值是这个月第一天的 0 点（用户时区）。
        /// Starts in this month; the value is midnight on the month's first day (user's time zone).
        case month(Date)
    }

    /// 一段和它的活动。
    /// A section and its events.
    struct EventSection: Identifiable, Hashable {
        /// 哪一段。
        /// Which section this is.
        let kind: SectionKind
        /// 这一段的活动，按开始时间升序。
        /// The section's events, sorted by start ascending.
        let events: [SaleEvent]

        var id: SectionKind { kind }
    }

    /// 当前状态。
    /// The current state.
    private(set) var state: State = .loading

    /// 把已加载的活动分段：先是进行中，再按开始月份。
    /// Splits the loaded events into sections: happening now first, then by start month.
    ///
    /// 从 `state` 派生，不另存一份；接口返回后可能又有活动结束，所以按 `now` 再滤一次。
    /// Derived from `state` rather than stored separately; events may end after the response, so they are filtered by `now` again.
    ///
    /// - Parameters:
    ///   - now: 当前时刻，由调用方传入方便预览 / The current time, passed in so previews can fix it.
    ///   - calendar: 切月份用的日历（带用户时区）/ The calendar used to split months (with the user's time zone).
    /// - Returns: 没加载完或没有活动时为空 / Empty when not loaded or there are no events.
    func sections(now: Date, calendar: Calendar) -> [EventSection] {
        guard case .loaded(let eventList) = state else { return [] }

        let currentEvents = eventList.events.filter { event in event.end > now }
        let happeningNowEvents = currentEvents.filter { event in event.start <= now }
        let upcomingEvents = currentEvents.filter { event in event.start > now }

        // `Dictionary(grouping:)` 保留原顺序，每个月里仍按开始时间排好。
        // `Dictionary(grouping:)` keeps the original order, so each month stays sorted by start.
        let upcomingByMonth = Dictionary(grouping: upcomingEvents) { event in
            startOfMonth(for: event.start, calendar: calendar)
        }
        let months = upcomingByMonth.keys.sorted()
        let monthSections = months.map { month in
            let events = upcomingByMonth[month, default: []]
            return EventSection(kind: .month(month), events: events)
        }

        guard !happeningNowEvents.isEmpty else { return monthSections }
        let happeningNowSection = EventSection(kind: .happeningNow, events: happeningNowEvents)
        return [happeningNowSection] + monthSections
    }

    /// 还没加载成功过才加载，页面每次出现都可以调用。
    /// Loads only if nothing has loaded yet; safe to call every time the page appears.
    ///
    /// `.task` 在页面每次出现时都会重跑：已经加载完或失败了就不动，失败由用户点 Retry 重试；
    /// 被取消的加载（页面消失）停在 `.loading`，下次出现会重新加载。
    /// `.task` reruns every time the page appears: a loaded or failed state is left alone and failures wait
    /// for the user's Retry; a cancelled load (page gone) stays `.loading`, so the next appearance loads again.
    func loadIfNeeded() async {
        guard state == .loading else { return }
        await load()
    }

    /// 请求活动列表，失败转成 `LoadError`；Retry 也调它。
    /// Requests the event list and turns failures into `LoadError`; Retry calls this too.
    func load() async {
        state = .loading

        do {
            let eventList = try await SaleEventsAPI.fetchSaleEvents()

            guard !Task.isCancelled else { return }
            state = .loaded(eventList)
        } catch {
            guard !Task.isCancelled else { return }
            Logger.saleEvents.error("加载活动失败 / Loading sale events failed: \(String(describing: error))")
            state = .failed(LoadError.from(error))
        }
    }

    /// 返回某个时刻所在月份第一天的 0 点。
    /// Returns midnight on the first day of the month containing a time.
    private func startOfMonth(for date: Date, calendar: Calendar) -> Date {
        guard let monthInterval = calendar.dateInterval(of: .month, for: date) else { return date }
        return monthInterval.start
    }
}
