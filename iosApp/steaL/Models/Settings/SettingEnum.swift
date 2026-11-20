//
//  SettingEnum.swift
//  steaL
//
//  Created by yijiang on 2026/8/28.
//

import Combine
import Foundation
import SwiftUI

// MARK: - 枚举 / Enums

/// 界面外观偏好（system / light / dark），直接映射到 SwiftUI 的 `colorScheme` /
/// The app's visual appearance preference, mapped onto SwiftUI's `colorScheme`
enum AppTheme: String, Codable, CaseIterable, Identifiable {
    case system
    case light
    case dark

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .system: return "System"
        case .light: return "Light"
        case .dark: return "Dark"
        }
    }

    /// 交给 `.preferredColorScheme` 的值；`nil` 表示跟随系统 / The value for `.preferredColorScheme`; `nil` follows the system
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

/// 购物区域；Steam 与第三方商店按区定价，它决定请求参数，从而决定返回的价格与货币 /
/// The store region the user is shopping in; it drives the request parameters, and so the prices and currency returned
enum AppRegion: String, Codable, CaseIterable, Identifiable {
    case australia
    case china
    case unitedStates
    case unitedKingdom
    case japan
    case korea
    case europe

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .australia: return "Australia"
        case .china: return "China"
        case .unitedStates: return "United States"
        case .unitedKingdom: return "United Kingdom"
        case .japan: return "Japan"
        case .korea: return "Korea"
        case .europe: return "Europe"
        }
    }

    /// 拼 Steam / ITAD 请求时用的国家或地区代码 / The country code used when building Steam and ITAD payloads
    var apiRegionCode: String {
        switch self {
        case .australia: return "AU"
        case .china: return "CN"
        case .unitedStates: return "US"
        case .unitedKingdom: return "GB"
        case .japan: return "JP"
        case .korea: return "KR"
        // EU 不是国家代码（ITAD 返回 400，Steam 退回美元），用德国代表欧元区。
        // EU is not a country code (ITAD answers 400, Steam falls back to US dollars), so Germany stands in for the eurozone.
        case .europe: return "DE"
        }
    }
}

/// App 与 Steam 的语言偏好；raw value 就是 Steam 的 API 语言代码，如 `"schinese"` /
/// Preferred language of the app and Steam; the raw value is Steam's own API language code
enum AppLanguage: String, Codable, CaseIterable, Identifiable {
    case english
    case simplifiedChinese

    var id: String { rawValue }

    /// 请求里用的 Steam 语言代码 / The Steam language code used in API requests
    var apiCode: String {
        switch self {
        case .english: return "english"
        case .simplifiedChinese: return "schinese"
        }
    }

    var displayName: String {
        switch self {
        case .english: return "English"
        case .simplifiedChinese: return "简体中文"
        }
    }

    /// 语言的英文名，告诉 AI 用什么语言回答 / The language's English name, telling the model which language to reply in
    var englishName: String {
        switch self {
        case .english: return "English"
        case .simplifiedChinese: return "Simplified Chinese"
        }
    }
}
