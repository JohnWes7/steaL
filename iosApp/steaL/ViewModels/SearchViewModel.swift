//
//  SearchViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/13.
//

import Foundation
import OSLog


@MainActor
@Observable
class SearchViewModel {

	/// 搜索页面状态 / Search screen state
	enum SearchState: Equatable {
		/// 尚未搜索 / No search has been submitted
		case idle
		/// 正在搜索 / A search request is in progress
		case loading
		/// 搜索成功，结果可能为空 / Search succeeded; results may be empty
		case loaded([SteamSearchItem])
		/// 搜索失败 / Search failed
		case failed(LoadError)
	}

	/// 当前搜索状态 / Current search state
	private(set) var state: SearchState = .idle

	/// 最近一次成功搜索的条件 / Conditions of the most recent successful search
	private var loadedRequest: SearchRequest?

	/// 调用 Steam 搜索接口；空白输入不发请求并清空旧结果，失败转成 `LoadError` /
	/// Calls Steam's store search endpoint; blank input requests nothing, failures become `LoadError`
	func search(
		query: String,
		region: AppRegion = .australia,
		language: AppLanguage = .english
	) async {
		let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)

		guard !trimmedQuery.isEmpty else {
			loadedRequest = nil
			state = .idle
			return
		}

		let request = SearchRequest(query: trimmedQuery, region: region, language: language)
		if loadedRequest == request {
			return
		}

		state = .loading

		do {
			let response = try await SteamAPI.searchStore(
				query: trimmedQuery,
				region: region,
				language: language
			)

			guard !Task.isCancelled else { return }
			loadedRequest = request
			state = .loaded(response.items)
		} catch {
			guard !Task.isCancelled else { return }
			Logger.search.error("search failed / failed: \(String(describing: error))")
			state = .failed(LoadError.from(error))
		}

	}

}
