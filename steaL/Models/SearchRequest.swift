//
//  SearchRequest.swift
//  steaL
//
//  Created by yijiang on 2026/9/14.
//

import Foundation

/// Steam 搜索请求的参数 / Parameters used by a Steam search request
struct SearchRequest: Equatable, Hashable, Sendable {
    let query: String
    let region: AppRegion
    let language: AppLanguage
}