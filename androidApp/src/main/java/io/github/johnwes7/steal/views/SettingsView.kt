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
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.platform.AppLanguageSetting
import io.github.johnwes7.steal.platform.LocalAppLanguageSetting
import io.github.johnwes7.steal.shared.models.settings.AppLanguage
import io.github.johnwes7.steal.views.components.settings.SettingsIcon
import io.github.johnwes7.steal.views.components.settings.settingsItemColors
import io.github.johnwes7.steal.views.components.shared.PageHeaderView
import io.github.johnwes7.steal.views.components.shared.PageScaffold
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * Settings tab 的根页面：设置的分组列表，点一行推入那一组的页面。
 * The Settings tab's root screen: the list of setting groups, where tapping a row pushes that group's screen.
 *
 * 这一层只是外框（顶栏 + 毛玻璃），列表在 [SettingsContent]：以后设置入口挪到别处时只换外框。
 * This layer is only the frame (top bar and frosted glass); the list lives in [SettingsContent], so moving the
 * settings entry point elsewhere later only replaces the frame.
 */
@Composable
fun SettingsView(
    onOpenThemeSettings: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLanguageSetting.current.language
    val listState = rememberLazyListState()
    PageScaffold(
        header = { PageHeaderView(title = stringResource(R.string.tab_settings)) },
        scrollState = listState,
        modifier = modifier,
    ) { contentPadding ->
        SettingsContent(
            language = language,
            listState = listState,
            contentPadding = contentPadding,
            onOpenThemeSettings = onOpenThemeSettings,
            onOpenLanguageSettings = onOpenLanguageSettings,
        )
    }
}

/**
 * 设置的分组列表：每行是一张圆角分段卡片（圆形图标 + 标题 + 摘要），标题和推入的页面标题一致。
 * The list of setting groups: each row is a rounded segmented card (circular icon, title and summary) whose title
 * matches the title of the screen it pushes.
 *
 * 「Language」行的摘要是当前选的 [language]（`null` = 跟随系统）。
 * The "Language" row's summary is the current [language] (`null` follows the system).
 */
@Composable
fun SettingsContent(
    language: AppLanguage?,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onOpenThemeSettings: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val segmentedListItemColors = settingsItemColors()
    LazyColumn(
        state = listState,
        contentPadding = contentPadding + PaddingValues(bottom = 24.dp, top = 24.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                SegmentedListItem(
                    onClick = onOpenThemeSettings,
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                    colors = segmentedListItemColors,
                    verticalAlignment = Alignment.CenterVertically,
                    leadingContent = { SettingsIcon(painter = painterResource(R.drawable.palette_24px)) },
                    supportingContent = { Text(text = stringResource(R.string.settings_theme_and_color_summary)) },
                ) {
                    Text(text = stringResource(R.string.settings_theme_and_color))
                }

                SegmentedListItem(
                    onClick = onOpenLanguageSettings,
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                    colors = segmentedListItemColors,
                    verticalAlignment = Alignment.CenterVertically,
                    leadingContent = { SettingsIcon(painter = painterResource(R.drawable.language_24px)) },
                    supportingContent = { Text(text = languageLabel(language = language)) },
                ) {
                    Text(text = stringResource(R.string.settings_language))
                }
            }
        }
    }
}

@Preview
@Composable
private fun SettingsViewPreview() {
    // 外框读 LocalAppLanguageSetting，预览里没人提供，这里给一个假的。
    // The frame reads LocalAppLanguageSetting, which nothing provides in a preview, so a stand-in is given here.
    val previewLanguageSetting = AppLanguageSetting(language = null, setLanguage = {})
    CompositionLocalProvider(LocalAppLanguageSetting provides previewLanguageSetting) {
        SteaLTheme(darkTheme = true) {
            SettingsView(onOpenThemeSettings = {}, onOpenLanguageSettings = {})
        }
    }
}
