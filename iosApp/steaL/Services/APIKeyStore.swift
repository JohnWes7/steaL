//
//  APIKeyStore.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import Foundation
import Security

/// 用 Keychain 存用户自己的 LLM 服务商 API key，每个 `LLMProvider.id` 对应一条。
/// Stores the user's own LLM provider API keys in the Keychain, one item per `LLMProvider.id`.
///
/// SecItem 函数会阻塞调用线程，Apple 文档要求不在主线程调用，而项目默认隔离是 `MainActor`，
/// 所以整个类型是 `nonisolated`、每个方法都是 `@concurrent`。
/// SecItem calls block the calling thread and Apple's documentation says to keep them off the main thread,
/// while the project's default isolation is `MainActor`, so the type is `nonisolated` and every method is `@concurrent`.
nonisolated enum APIKeyStore {

    /// 这类条目共用的 service。
    /// The service shared by these items.
    ///
    /// 写死而不从 Bundle 读：它只用来在本 App 的 Keychain 里标出这类条目，要求的是永远不变。
    /// Hard-coded rather than read from the bundle: it only marks these items in the app's Keychain and must never change.
    private static let service = "edu.monash.steaL.llm"

    // MARK: - 保存 / Save

    /// 保存某个服务商的 key，已有就覆盖。
    /// Saves a provider's key, replacing any existing one.
    ///
    /// - Parameters:
    ///   - apiKey: 要保存的 key / The key to save.
    ///   - providerID: key 所属服务商的 id / The id of the provider the key belongs to.
    /// - Throws: Keychain 拒绝写入时抛 `APIKeyStoreError.unexpectedStatus` /
    ///   `APIKeyStoreError.unexpectedStatus` when the Keychain rejects the write.
    @concurrent
    static func save(_ apiKey: String, for providerID: UUID) async throws {
        let keyData = Data(apiKey.utf8)

        // 先新增；已存在再更新，不删了重加。
        // Add first; update when it already exists, never delete and re-add.
        var addAttributes = itemQuery(for: providerID)
        addAttributes[kSecValueData as String] = keyData
        // 可访问性只在新增时设：解锁时可读，加密备份换新设备时跟着迁移（Apple 的默认值，这里显式写出）。
        // Accessibility is set only when adding: readable while unlocked, and migrates with encrypted backups
        // (Apple's default, written out here).
        addAttributes[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlocked

        let addStatus = SecItemAdd(addAttributes as CFDictionary, nil)
        if addStatus == errSecSuccess {
            return
        }
        guard addStatus == errSecDuplicateItem else {
            throw APIKeyStoreError.unexpectedStatus(addStatus)
        }

        let updateQuery = itemQuery(for: providerID)
        let changes: [String: Any] = [kSecValueData as String: keyData]
        let updateStatus = SecItemUpdate(updateQuery as CFDictionary, changes as CFDictionary)
        guard updateStatus == errSecSuccess else {
            throw APIKeyStoreError.unexpectedStatus(updateStatus)
        }
    }

    // MARK: - 读取 / Read

    /// 读取某个服务商的 key。
    /// Reads a provider's key.
    ///
    /// 读取失败时只抛错，调用方绝不能因此删掉这条 key：设备锁着时会是 `errSecInteractionNotAllowed`，key 其实还在。
    /// A failed read only throws, and callers must never delete the key over it: a locked device gives
    /// `errSecInteractionNotAllowed` while the key is still there.
    ///
    /// - Parameter providerID: 服务商的 id / The provider's id.
    /// - Returns: 存着的 key；没存过是 `nil`，「没有」不是错误 / The stored key, or `nil` when none was saved, which is not an error.
    /// - Throws: `APIKeyStoreError.unexpectedStatus` 或存的不是 UTF-8 文本时的 `APIKeyStoreError.unexpectedData` /
    ///   `APIKeyStoreError.unexpectedStatus`, or `APIKeyStoreError.unexpectedData` when the stored value isn't UTF-8 text.
    @concurrent
    static func read(for providerID: UUID) async throws -> String? {
        var query = itemQuery(for: providerID)
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        query[kSecReturnData as String] = true

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        if status == errSecItemNotFound {
            return nil
        }
        guard status == errSecSuccess else {
            throw APIKeyStoreError.unexpectedStatus(status)
        }

        guard let keyData = result as? Data else {
            throw APIKeyStoreError.unexpectedData
        }
        guard let apiKey = String(data: keyData, encoding: .utf8) else {
            throw APIKeyStoreError.unexpectedData
        }
        return apiKey
    }

    // MARK: - 是否存在 / Existence

    /// 查某个服务商有没有存 key，不读出 key 的内容。
    /// Checks whether a key is saved for a provider without reading the key.
    ///
    /// - Parameter providerID: 服务商的 id / The provider's id.
    /// - Returns: 存了是 `true` / `true` when a key is saved.
    /// - Throws: 查询失败时抛 `APIKeyStoreError.unexpectedStatus` / `APIKeyStoreError.unexpectedStatus` when the lookup fails.
    @concurrent
    static func hasKey(for providerID: UUID) async throws -> Bool {
        var query = itemQuery(for: providerID)
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        // 只要属性、不要数据：key 本身不会被取出来。
        // Attributes only, no data, so the key itself is never returned.
        query[kSecReturnAttributes as String] = true

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        if status == errSecItemNotFound {
            return false
        }
        guard status == errSecSuccess else {
            throw APIKeyStoreError.unexpectedStatus(status)
        }
        return true
    }

    // MARK: - 删除 / Delete

    /// 删除某个服务商的 key，本来就没有也算成功。
    /// Deletes a provider's key; a key that is already gone counts as success.
    ///
    /// - Parameter providerID: 服务商的 id / The provider's id.
    /// - Throws: 删除失败时抛 `APIKeyStoreError.unexpectedStatus` / `APIKeyStoreError.unexpectedStatus` when the delete fails.
    @concurrent
    static func delete(for providerID: UUID) async throws {
        let query = itemQuery(for: providerID)
        let status = SecItemDelete(query as CFDictionary)

        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw APIKeyStoreError.unexpectedStatus(status)
        }
    }

    // MARK: - 查询条件 / Query

    /// 定位某个服务商那条 key 的查询条件。
    /// Returns the query that identifies one provider's key item.
    ///
    /// 只放唯一约束里的属性（class + service + account），所以最多匹配一条：`SecItemDelete` 默认删掉所有匹配项，靠这个保证只删这一条。
    /// 每次调用各拿一份新字典，不把上一次调用的字典改了再用。
    /// Only the uniqueness attributes (class, service, account) go in, so at most one item matches: `SecItemDelete`
    /// removes every match by default, and this keeps it to one. Each call gets its own fresh dictionary instead of
    /// reusing another call's.
    ///
    /// - Parameter providerID: 服务商的 id / The provider's id.
    /// - Returns: 新的查询字典 / A fresh query dictionary.
    private static func itemQuery(for providerID: UUID) -> [String: Any] {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: providerID.uuidString
        ]
        return query
    }
}

// MARK: - 错误 / Errors

/// `APIKeyStore` 的错误。
/// Errors from `APIKeyStore`.
///
/// 纯 `Error`，只到 ViewModel，不显示给用户。
/// A plain `Error` that only reaches the view model and is never shown to the user.
nonisolated enum APIKeyStoreError: Error {
    /// SecItem 返回了意料之外的状态码，如设备锁着时的 `errSecInteractionNotAllowed`（-25308）。
    /// SecItem returned an unexpected status, such as `errSecInteractionNotAllowed` (-25308) on a locked device.
    case unexpectedStatus(OSStatus)
    /// 存的值不是 UTF-8 文本。
    /// The stored value isn't UTF-8 text.
    case unexpectedData
}
