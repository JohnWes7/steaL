//
//  LLMModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import Foundation
import SwiftData

/// 某个 `LLMProvider` 下的一个模型，如 `deepseek-flash`。
/// One model under an `LLMProvider`, such as `deepseek-flash`.
///
/// 设置里选中的就是它（`UserSettingModel.llmModel`）。
/// This is what the settings select (`UserSettingModel.llmModel`).
@Model
final class LLMModel {

    /// 请求里 `model` 字段的值。
    /// The value of the request's `model` field.
    var name: String = ""

    /// 添加时间，同一服务商下的模型按它排序。
    /// When it was added; a provider's models sort by it.
    private(set) var createdAt = Date.now

    // MARK: - 关系 / Relationships

    /// 所属服务商，base URL 与 Keychain 里的 key 都从它来。
    /// The owning provider, which supplies the base URL and the Keychain key.
    ///
    /// 非可选：没有服务商的模型编译不过，保存也会失败。
    /// Non-optional: a model without a provider doesn't compile, and saving one fails.
    var provider: LLMProvider

    /// 选中它的设置行。
    /// The settings row that selected it.
    ///
    /// 业务代码不读它：有了这个反向关系，删掉这个模型时设置里的 `llmModel` 会自动置空（默认 `.nullify`）。
    /// Business code never reads it: with this inverse, deleting the model clears the settings' `llmModel`
    /// (the default `.nullify`).
    var selectedBy: UserSettingModel?

    // MARK: - 初始化 / Init

    /// 在某个服务商下新建一个模型。
    /// Creates a model under a provider.
    ///
    /// `provider` 要先插入 context。插入这个模型后，`provider.models` 的数据会经反向关系带上它，
    /// 但不会通知读这个数组的界面，所以列表用 `@Query` 查模型。
    /// Insert `provider` first. Once this model is inserted, the inverse adds it to `provider.models`,
    /// but views reading that array aren't notified, so lists query models with `@Query`.
    ///
    /// - Parameters:
    ///   - name: 模型名 / The model name.
    ///   - provider: 所属服务商，已插入 context / The owning provider, already inserted into the context.
    init(name: String, provider: LLMProvider) {
        self.name = name
        self.provider = provider
    }
}
