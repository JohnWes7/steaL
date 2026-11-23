package io.github.johnwes7.steal.shared.models.settings

/**
 * App 可以单独选的界面语言；跟随系统不在这里，用 `null` 表示。
 * The UI languages the app can be set to on its own; following the system is not one of them and is `null` instead.
 *
 * 这个值不存在 `SettingsRepository` 里：各平台由系统或平台代码保存，要在界面出现之前就读到。
 * This value is not kept in `SettingsRepository`: each platform stores it with the system or platform code, because
 * it has to be known before any UI appears.
 */
enum class AppLanguage(
    /**
     * BCP 47 语言标签，和 Android 的翻译目录对应（`zh-Hans` ↔ `values-b+zh+Hans`）。
     * The BCP 47 language tag, matching an Android translation folder (`zh-Hans` ↔ `values-b+zh+Hans`).
     */
    val languageTag: String,
) {
    English(languageTag = "en"),
    SimplifiedChinese(languageTag = "zh-Hans"),
    ;

    companion object {
        /**
         * 按语言标签找对应的语言；标签不在列表里（包括空字符串）返回 `null`。
         * Finds the language with [languageTag]; returns `null` when the tag is not listed, including an empty one.
         */
        fun fromLanguageTag(languageTag: String): AppLanguage? {
            val matchingLanguage = entries.firstOrNull { language -> language.languageTag == languageTag }
            return matchingLanguage
        }
    }
}
