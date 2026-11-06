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

    /// 当前状态。
    /// The current state.
    private(set) var state: State = .loading

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
}
