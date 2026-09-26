//
//  LLMModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import Foundation
import SwiftData

/// 某个 `LLMProvider` 下的一个模型，如 `deepseek-flash`；设置里选中的就是它 /
/// One model under an `LLMProvider`, such as `deepseek-flash`; this is what the settings select
@Model
final class LLMModel {

    /// 请求里 `model` 字段的值 / The value of the request's `model` field
    var name: String = ""

    /// 添加时间，同一服务商下的模型按它排序 / When it was added; models of one provider sort by it
    private(set) var createdAt = Date.now

    // MARK: - 关系 / Relationships

    // 非可选：没有服务商的模型编译不过，保存也会失败（2026-09-27 实测）/
    // Non-optional: a model without a provider does not compile, and saving one fails (tested 2026-09-27)
    /// 所属服务商；base URL 与 Keychain 里的 key 从它来 / The owning provider, which supplies the base URL and the Keychain key
    var provider: LLMProvider

    // 业务代码不读它：有了这个反向关系，删掉这个模型时设置里的 `llmModel` 会自动置空（默认 `.nullify`）/
    // Business code never reads it: with this inverse, deleting the model clears the settings' `llmModel` (the default `.nullify`)
    /// 选中它的设置行 / The settings row that selected it
    var selectedBy: UserSettingModel?

    // MARK: - 初始化 / Init

    // `provider` 要先插入 context；插入这个模型后，`provider.models` 会经反向关系自动带上它，不用再 append /
    // Insert `provider` first; once this model is inserted, the inverse adds it to `provider.models`, so no append is needed
    init(name: String, provider: LLMProvider) {
        self.name = name
        self.provider = provider
    }
}
