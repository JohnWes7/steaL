//
//  steaLApp.swift
//  steaL
//
//  Created by yijiang on 2026/8/28.
//

import AVFoundation
import SwiftData
import SwiftUI
import FirebaseCore
import OSLog
import TipKit

class AppDelegate: NSObject, UIApplicationDelegate {

    /// 通知中心的 delegate；center 只弱引用它，由这里持有。
    /// The notification center's delegate; the center holds it weakly, so it is kept here.
    private let notificationDelegate = NotificationDelegate()

    // 在启动时配置 Firebase、音频会话和通知 delegate / Configures Firebase, the audio session and the notification delegate at launch
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {

        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            // Initialize Firebase
            FirebaseApp.configure()
        }
        configureAudioSession()

        // 必须在启动完成前赋值，否则点通知冷启动时可能漏掉
        // Must be assigned before launch finishes, or a notification that cold-launches the app may be missed
        UNUserNotificationCenter.current().delegate = notificationDelegate
        return true
    }

    /// 把音频会话设成视频播放类别。
    /// Sets the audio session to the movie playback category.
    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .moviePlayback)
        } catch {
            Logger.settings.error("音频会话配置失败 / Audio session configuration failed: \(String(describing: error))")
        }
    }
}

/// 决定通知在 App 里怎么显示。
/// Decides how notifications are presented inside the app.
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {

    /// App 在前台时也显示横幅并留在通知中心（系统默认前台不显示）。
    /// Shows a banner and keeps it in Notification Center while the app is in the foreground, where the system shows nothing by default.
    ///
    /// - Parameters:
    ///   - center: 通知中心 / The notification center.
    ///   - notification: 送达的通知 / The delivered notification.
    /// - Returns: 显示方式 / How to present it.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        return [.banner, .list]
    }
}

@main
struct steaLApp: App {

    // start in unity next is render
    // register app delegate for Firebase setup
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    /// 全 App 共用的 SwiftData 容器 / The SwiftData container shared by the whole app
    private let container: ModelContainer

    // awake in unity
    init() {
        // 图片缓存容量。系统默认只有 500 KB 内存 / 10 MB 磁盘，而一张 ITAD boxart 就有 47 KB，
        // 默认值大概只装得下半个首页。必须在任何请求发出前设置才生效 /
        // Image cache capacity. The system defaults are only 500 KB memory and 10 MB disk, while a
        // single ITAD boxart is already 47 KB. Must be set before any request goes out.
        URLCache.shared = URLCache(
            memoryCapacity: 64 * 1024 * 1024,   // 64 MB
            diskCapacity: 256 * 1024 * 1024     // 256 MB
        )

        let container: ModelContainer
        do {
            container = try ModelContainer(for: UserSettingModel.self, LLMProvider.self, LLMModel.self)
        } catch {
            fatalError("Failed to create ModelContainer / 创建容器失败: \(error)")
        }
        self.container = container

        // TipKit 要在任何提示显示前配置一次；多个提示时每天最多弹一个。
        // TipKit must be configured once before any tip shows; with several tips, at most one appears a day.
        do {
            #if DEBUG
            // 调试时每次启动都清空提示记录，失效过的提示能再显示；必须在 configure 之前调用。
            // In debug builds, clears tip records on every launch so invalidated tips show again; must run before configure.
            try Tips.resetDatastore()
            #endif
            try Tips.configure([.displayFrequency(.daily)])
        } catch {
            Logger.settings.error("TipKit 配置失败 / TipKit configuration failed: \(String(describing: error))")
        }

        // 首帧渲染前保证设置行存在 / Makes sure the settings row exists before the first frame
        let settings = bootstrapSettings(in: container.mainContext)

        #if DEBUG
        // 启动时打印当前设置内容，方便调试 / Logs the current settings at launch, for debugging
        let providerName = settings.llmModel?.provider.name
        let providerText = providerName ?? "built-in"
        let modelName = settings.llmModel?.name
        let modelText = modelName ?? "built-in"
        Logger.settings.debug("""
        当前设置 / current settings:
          id: \(settings.id)
          theme: \(settings.theme.rawValue)
          region: \(settings.region.rawValue)
          language: \(settings.language.rawValue)
          priceDropAlerts: \(settings.isPriceDropAlertsEnabled)
          gameReleaseAlerts: \(settings.isGameReleaseAlertsEnabled)
          hasCompletedOnboarding: \(settings.hasCompletedOnboarding)
          llmProvider: \(providerText)
          llmModel: \(modelText)
        """)
        #endif
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
        .modelContainer(container)
    }

    /// 保证单例设置行存在并返回：已有则沿用，没有则插入一条默认设置 /
    /// Makes sure the singleton settings row exists and returns it: keeps the stored one, or inserts the defaults
    private func bootstrapSettings(in context: ModelContext) -> UserSettingModel {
        let descriptor = FetchDescriptor<UserSettingModel>(
            predicate: #Predicate { $0.id == "main" }
        )

        if let existing = try? context.fetch(descriptor).first {
            return existing
        }

        let settings = UserSettingModel()
        context.insert(settings)
        return settings
    }
}
