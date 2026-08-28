//
//  UserSettingModel.swift
//  steaL
//
//  Created by yijiang on 2026/8/28.
//

import Foundation
import SwiftData


@Model
final class UserSettingModel {

    @Attribute(.unique) private(set) var id = "main"

    // MARK: Display

    /// Display theme (system / light / dark).
    var theme: AppTheme = AppTheme.system

    // MARK: Region & currency

    /// Store region & currency.
    var region: AppRegion = AppRegion.china

    // MARK: Language

    /// Preferred app / API language.
    var language: AppLanguage = AppLanguage.english

    // MARK: Notification toggles (Bool)

    /// Whether price-drop notifications are enabled.
    var isPriceDropAlertsEnabled: Bool = true

    /// Whether game-release notifications are enabled.
    var isGameReleaseAlertsEnabled: Bool = true

    // MARK: Init

    init(
        theme: AppTheme = AppTheme.system,
        region: AppRegion = AppRegion.china,
        language: AppLanguage = AppLanguage.english,
        isPriceDropAlertsEnabled: Bool = true,
        isGameReleaseAlertsEnabled: Bool = true
    ) {
        self.theme = theme
        self.region = region
        self.language = language
        self.isPriceDropAlertsEnabled = isPriceDropAlertsEnabled
        self.isGameReleaseAlertsEnabled = isGameReleaseAlertsEnabled
    }
}
