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

    // MARK: - 显示 / Display

    /// 显示主题（system / light / dark）/ The display theme
    var theme: AppTheme = AppTheme.dark

    // MARK: - 区域与货币 / Region & currency

    /// 商店区域与货币 / The store region and its currency
    var region: AppRegion = AppRegion.australia

    // MARK: - 语言 / Language

    /// App 与 API 的语言 / The preferred app and API language
    var language: AppLanguage = AppLanguage.english

    // MARK: - 通知开关 / Notification toggles

    /// 是否开启降价通知 / Whether price-drop notifications are on
    var isPriceDropAlertsEnabled: Bool = true

    /// 是否开启发售通知 / Whether release notifications are on
    var isGameReleaseAlertsEnabled: Bool = true

    // MARK: - AI 模型 / AI model

    // 只记选中的是哪一个；服务商和模型列表在 `LLMProvider`，找不到（被删了）就用内置的 /
    // Only records which one is chosen; providers and model lists live in `LLMProvider`, and a missing one falls back to the built-in

    /// 选中的 `LLMProvider.id`；`nil` 是内置的服务商 / The chosen `LLMProvider.id`; `nil` means the built-in provider
    var llmProviderID: UUID?

    /// 选中服务商下的模型名；`nil` 是内置的模型 / The chosen model name within that provider; `nil` means the built-in model
    var llmModelName: String?

    // MARK: - 初始化 / Init

    init(
        theme: AppTheme = AppTheme.dark,
        region: AppRegion = AppRegion.australia,
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
