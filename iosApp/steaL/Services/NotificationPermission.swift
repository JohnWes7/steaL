//
//  NotificationPermission.swift
//  steaL
//
//  Created by yijiang on 2026/10/8.
//

import Foundation
import OSLog
import UserNotifications

/// 读取与请求系统通知权限，供提醒检查、Profile 和收藏入口共用。
/// Reads and requests the system notification permission, shared by the alert check, Profile and the favourite buttons.
///
/// 权限属于系统通知中心，不属于 Firestore 或某个页面，所以单独放一处。用户随时可能在系统设置里改，系统不会主动告诉 App，
/// 所以不保存状态，每次用都重新读。
/// The permission belongs to the system notification center, not to Firestore or any one page, so it lives on its own.
/// The user can change it in Settings at any time without the app being told, so nothing is stored and every use re-reads it.
enum NotificationPermission {

    /// 读取当前的通知权限。
    /// Reads the current notification permission.
    ///
    /// - Returns: 系统里本 App 的权限状态 / The app's permission status in the system.
    static func status() async -> UNAuthorizationStatus {
        let center = UNUserNotificationCenter.current()
        let notificationSettings = await center.notificationSettings()
        return notificationSettings.authorizationStatus
    }

    /// 是否可以发通知。
    /// Whether the app may send notifications.
    ///
    /// - Returns: 用户允许了（含静默送达）返回 `true` / `true` when the user has allowed them, including quiet delivery.
    static func isAuthorized() async -> Bool {
        let currentStatus = await status()

        switch currentStatus {
        case .authorized, .provisional:
            return true
        case .denied, .notDetermined, .ephemeral:
            return false
        @unknown default:
            return false
        }
    }

    /// 还没问过用户时请求通知权限，问过就什么都不做。
    /// Requests the notification permission if the user hasn't been asked yet; does nothing otherwise.
    ///
    /// 在用户第一次收藏时调用：这时用户看得懂通知是干什么用的。系统本来也只在第一次弹窗，先查状态是为了不发多余的请求。
    /// 只要横幅和声音，不要 badge：App 不设角标，只申请用得到的。
    /// Called on the user's first favourite, when the purpose of notifications is clear. The system only prompts once anyway;
    /// checking first just avoids a needless request. Only alerts and sounds are asked for, not badges, since the app sets none.
    static func requestIfNeeded() async {
        let currentStatus = await status()
        guard currentStatus == .notDetermined else { return }

        let center = UNUserNotificationCenter.current()
        do {
            let isGranted = try await center.requestAuthorization(options: [.alert, .sound])
            Logger.notifications.debug("notification permission granted: \(isGranted)")
        } catch {
            Logger.notifications.error("request notification permission failed / failed: \(String(describing: error))")
        }
    }
}
