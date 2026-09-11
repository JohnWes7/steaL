//
//  SettingModel.swift
//  steaL
//
//  Created by yijiang on 2026/8/28.
//

import Combine
import Foundation

// MARK: - Enums

/// The app's visual appearance preference (system / light / dark).
///
/// Maps directly to SwiftUI's `colorScheme`. Persisted as the enum's raw value.
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

/// The store region the user is shopping in.
///
/// Because Steam and authorised third-party storefronts price games per region,
/// the selected region drives both the currency format and the API parameters used
/// when querying the Steam / ITAD services.
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

    /// ISO 4217 currency code for the region (e.g. "CNY").
    /// for dispary
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

    /// Symbol used when formatting prices for the region.
    /// for dispay
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

    /// Country / region code used when building Steam & ITAD query payloads.
    /// use in api to get data
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

/// Preferred language of the app and the Steam
///
/// The raw value is the Steam-compatible API language code (e.g. "schinese").
enum AppLanguage: String, Codable, CaseIterable, Identifiable {
    case english
    case simplifiedChinese

    var id: String { rawValue }

    /// Steam  language code used in API requests.
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
