package io.github.johnwes7.steal.views

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.shared.models.settings.AppLanguage
import io.github.johnwes7.steal.views.components.shared.PageBackButton
import io.github.johnwes7.steal.views.components.shared.PageScaffold
import io.github.johnwes7.steal.views.components.shared.PageTopBarStyle

// MARK: - 页面 / Screen

/**
 * 「语言」单选页：跟随系统或单独给 App 选一种界面语言。
 * The "Language" single-choice screen: follow the system or pick a UI language for the app alone.
 *
 * 从 Settings 根页面推入，[onBack] 回到上一页。
 * Pushed from the Settings root screen; [onBack] returns to it.
 */
@Composable
fun LanguageSettingsView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    PageScaffold(
        header = { Text(text = stringResource(R.string.settings_language)) },
        scrollState = listState,
        modifier = modifier,
        navigationIcon = { PageBackButton(onClick = onBack) },
        topBarStyle = PageTopBarStyle.Collapsing,
    ) { contentPadding ->
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
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
