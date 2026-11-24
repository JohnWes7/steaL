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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnwes7.steal.LocalAppContainer
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.platform.LocalAppLanguageSetting
import io.github.johnwes7.steal.shared.models.settings.AppLanguage
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.viewmodels.SettingsViewModel
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
    onOpenRegionSettings: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 1. 摘要要用的值：设置来自 ViewModel，App 语言来自根上的 LocalAppLanguageSetting
    // 1. The values the summaries need: settings from the view model, the app language from LocalAppLanguageSetting
    val viewModel: SettingsViewModel = viewModel(factory = LocalAppContainer.current.settingsViewModelFactory)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val language = LocalAppLanguageSetting.current.language

    // 2. 外框；设置还没读到（第一次打开的一瞬间）时不画列表，免得摘要后出现、行高跳一下
    // 2. The frame; no list until the settings arrive (a moment on first open), so the summaries don't pop in and
    //    change the row heights
    val listState = rememberLazyListState()
    PageScaffold(
        header = { PageHeaderView(title = stringResource(R.string.tab_settings)) },
        scrollState = listState,
        modifier = modifier,
    ) { contentPadding ->
        val currentSettings = settings
        if (currentSettings != null) {
            SettingsContent(
                region = currentSettings.region,
                language = language,
                listState = listState,
                contentPadding = contentPadding,
                onOpenRegionSettings = onOpenRegionSettings,
                onOpenThemeSettings = onOpenThemeSettings,
                onOpenLanguageSettings = onOpenLanguageSettings,
            )
        }
    }
}

/**
 * 设置的分组列表：每行是一张圆角分段卡片（圆形图标 + 标题 + 摘要），标题和推入的页面标题一致。
 * The list of setting groups: each row is a rounded segmented card (circular icon, title and summary) whose title
 * matches the title of the screen it pushes.
 *
 * 「Region & currency」行的摘要是当前的 [region]；「Language」行的摘要是当前选的 [language]（`null` = 跟随系统）。
 * The "Region & currency" row's summary is the current [region]; the "Language" row's summary is the current
 * [language] (`null` follows the system).
 */
@Composable
fun SettingsContent(
    region: AppRegion,
    language: AppLanguage?,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onOpenRegionSettings: () -> Unit,
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
                    onClick = onOpenRegionSettings,
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
                    colors = segmentedListItemColors,
                    verticalAlignment = Alignment.CenterVertically,
                    leadingContent = { SettingsIcon(painter = painterResource(R.drawable.globe_asia_24px)) },
                    supportingContent = { Text(text = regionLabel(region = region)) },
                ) {
                    Text(text = stringResource(R.string.settings_region))
                }

                SegmentedListItem(
                    onClick = onOpenThemeSettings,
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                    colors = segmentedListItemColors,
                    verticalAlignment = Alignment.CenterVertically,
                    leadingContent = { SettingsIcon(painter = painterResource(R.drawable.palette_24px)) },
                    supportingContent = { Text(text = stringResource(R.string.settings_theme_and_color_summary)) },
                ) {
                    Text(text = stringResource(R.string.settings_theme_and_color))
                }

                SegmentedListItem(
                    onClick = onOpenLanguageSettings,
                    shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
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
private fun SettingsContentPreview() {
    SteaLTheme(darkTheme = true) {
        SettingsContent(
            region = AppRegion.Australia,
            language = null,
            listState = rememberLazyListState(),
            contentPadding = PaddingValues(0.dp),
            onOpenRegionSettings = {},
            onOpenThemeSettings = {},
            onOpenLanguageSettings = {},
        )
    }
}
