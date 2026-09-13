//
//  SearchViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/13.
//

import Foundation


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

	/// 调用 Steam 搜索接口 / Calls Steam's store search endpoint
	///
	/// 空白输入不会发请求，并会清空旧结果。请求失败会转换成 UI 可用的 `LoadError` /
	/// Blank input skips the request and clears old results. Failures are mapped to `LoadError`
	/// for presentation by the UI.
	func search(
		query: String,
		region: AppRegion = .australia,
		language: AppLanguage = .english
	) async {
		let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)

		guard !trimmedQuery.isEmpty else {
			state = .idle
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
			state = .loaded(response.items)
		} catch {
			guard !Task.isCancelled else { return }
			state = .failed(LoadError.from(error))
		}

	}

}
