//
//  SettingEnum.swift
//  steaL
//
//  Created by yijiang on 2026/8/28.
//

import Combine
import Foundation

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
}

/// 购物区域；Steam 与第三方商店按区定价，它同时决定货币格式与 API 参数 /
/// The store region the user is shopping in; it drives the currency format and the API parameters
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

    /// 该区域的 ISO 4217 货币代码，如 `"CNY"` / The region's ISO 4217 currency code, e.g. `"CNY"`
    var currencyCode: String {
        switch self {
        case .australia: return "AUD"
        case .china: return "CNY"
        case .unitedStates: return "USD"
        case .unitedKingdom: return "GBP"
        case .japan: return "JPY"
        case .korea: return "KRW"
        case .europe: return "EUR"
        }
    }

    /// 该区域格式化价格时用的货币符号 / The symbol used when formatting prices for the region
    var currencySymbol: String {
        switch self {
        case .australia: return "$"
        case .china: return "¥"
        case .unitedStates: return "$"
        case .unitedKingdom: return "£"
        case .japan: return "¥"
        case .korea: return "₩"
        case .europe: return "€"
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
        case .europe: return "EU"
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
}
