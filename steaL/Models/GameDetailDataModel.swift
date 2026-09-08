//
//  GameMetaDataModel.swift
//  steaL
//
//  Created by yijiang on 2026/8/28.
//

import Foundation
import SwiftData

@Model
final class GameDetaildataModel {
    @Attribute(.unique) var appID: Int

    var name: String
    var gameDescription: String
    var headerImageURL: String?
    var releaseDate: Date?
    var tags: [String]
    var trailerURL: String?

    /// last time when fetch it
    var fetchedAt: Date

    init(appID: Int, name: String, gameDescription: String,
         headerImageURL: String? = nil, releaseDate: Date? = nil,
         tags: [String] = [], trailerURL: String? = nil,
         fetchedAt: Date = .now) {
        self.appID = appID
        self.name = name
        self.gameDescription = gameDescription
        self.headerImageURL = headerImageURL
        self.releaseDate = releaseDate
        self.tags = tags
        self.trailerURL = trailerURL
        self.fetchedAt = fetchedAt
    }
}
