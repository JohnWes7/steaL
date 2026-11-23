package io.github.johnwes7.steal.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.platform.AppLanguageSetting
import io.github.johnwes7.steal.platform.LocalAppLanguageSetting
import io.github.johnwes7.steal.shared.models.settings.AppLanguage
import io.github.johnwes7.steal.views.components.settings.settingsItemColors
import io.github.johnwes7.steal.views.components.shared.PageBackButton
import io.github.johnwes7.steal.views.components.shared.PageScaffold
import io.github.johnwes7.steal.views.components.shared.PageTopBarStyle
import io.github.johnwes7.steal.views.theme.SteaLTheme

// MARK: - 页面 / Screen

/**
 * 「语言」单选页：跟随系统或单独给 App 选一种界面语言。
 * The "Language" single-choice screen: follow the system or pick a UI language for the app alone.
 *
 * 从 Settings 根页面推入，[onBack] 回到上一页。点了马上生效，不自动返回。
 * Pushed from the Settings root screen; [onBack] returns to it. A choice takes effect right away without going back.
 */
@Composable
fun LanguageSettingsView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val languageSetting = LocalAppLanguageSetting.current
    val listState = rememberLazyListState()
    PageScaffold(
        header = { Text(text = stringResource(R.string.settings_language)) },
        scrollState = listState,
        modifier = modifier,
        navigationIcon = { PageBackButton(onClick = onBack) },
        topBarStyle = PageTopBarStyle.Collapsing,
    ) { contentPadding ->
        LanguageSettingsContent(
            selectedLanguage = languageSetting.language,
            onSelect = languageSetting.setLanguage,
            listState = listState,
            contentPadding = contentPadding,
        )
    }
}

// MARK: - 内容 / Content

/**
 * 语言的单选列表：跟随系统在最前，后面是每种可选的语言，[selectedLanguage] 那一项选中（`null` = 跟随系统）。
 * The single-choice list of languages: following the system first, then each available language, with
 * [selectedLanguage] selected (`null` follows the system).
 */
@Composable
fun LanguageSettingsContent(
    selectedLanguage: AppLanguage?,
    onSelect: (AppLanguage?) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val languageOptions: List<AppLanguage?> = listOf(null) + AppLanguage.entries
    val segmentedListItemColors = settingsItemColors()
    LazyColumn(
        state = listState,
        contentPadding = contentPadding + PaddingValues(top = 8.dp, bottom = 24.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                for ((index, option) in languageOptions.withIndex()) {
                    val isSelected = option == selectedLanguage
                    SegmentedListItem(
                        selected = isSelected,
                        onClick = { onSelect(option) },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = languageOptions.size),
                        colors = segmentedListItemColors,
                        verticalAlignment = Alignment.CenterVertically,
                        leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                    ) {
                        Text(text = languageLabel(language = option))
                    }
                }
            }
        }
    }
}

// MARK: - 显示名字 / Display name

/**
 * 语言在设置里显示的名字；`null`（跟随系统）显示「System default」。
 * The name a language shows in settings; `null` (following the system) shows "System default".
 */
@Composable
fun languageLabel(language: AppLanguage?): String {
    val resourceId = when (language) {
        null -> R.string.settings_language_system_default
        AppLanguage.English -> R.string.language_name_english
        AppLanguage.SimplifiedChinese -> R.string.language_name_simplified_chinese
    }
    return stringResource(resourceId)
}

@Preview
@Composable
private fun LanguageSettingsViewPreview() {
    // 外框读 LocalAppLanguageSetting，预览里给一个只改本地状态的，Interactive 模式下点得动。
    // The frame reads LocalAppLanguageSetting; the preview gives one that only changes local state, so it responds to
    // taps in Interactive mode.
    var previewLanguage by remember { mutableStateOf<AppLanguage?>(AppLanguage.SimplifiedChinese) }
    val previewLanguageSetting = AppLanguageSetting(
        language = previewLanguage,
        setLanguage = { newLanguage -> previewLanguage = newLanguage },
    )
    CompositionLocalProvider(LocalAppLanguageSetting provides previewLanguageSetting) {
        SteaLTheme(darkTheme = true) {
            LanguageSettingsView(onBack = {})
        }
    }
}
