//
//  LLMProviderViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import Foundation
import OSLog

/// 服务商页的 API key 状态：Keychain 里有没有这条 key；只查是否存在，不读内容 /
/// The provider page's API key status: whether the Keychain holds the key; it checks existence and never reads the key
@MainActor
@Observable
final class LLMProviderViewModel {

    /// key 的状态 / The key's status
    enum KeyState: Equatable {
        /// 还在查 / Still checking
        case checking
        /// 存了 / Saved
        case saved
        /// 没存（比如从不加密的备份恢复后）/ Not saved, e.g. after restoring an unencrypted backup
        case notSet
        /// 查询失败 / The check failed
        case failed
    }

    /// 当前状态 / Current state
    private(set) var keyState: KeyState = .checking

    /// 查一次 key 在不在；再次查时保留旧结果，不闪回「查询中」/
    /// Checks whether the key exists; a re-check keeps the old result instead of flashing back to checking
    func checkKey(for providerID: UUID) async {
        do {
            let hasKey = try await APIKeyStore.hasKey(for: providerID)

            guard !Task.isCancelled else { return }

            if hasKey {
                keyState = .saved
            } else {
                keyState = .notSet
            }
        } catch {
            guard !Task.isCancelled else { return }

            Logger.settings.error("check API key failed / 查询 key 失败: \(String(describing: error))")

            keyState = .failed
        }
    }
}
