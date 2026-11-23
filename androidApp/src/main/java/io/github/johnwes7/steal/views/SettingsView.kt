package io.github.johnwes7.steal.views

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
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
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    PageScaffold(
        header = { PageHeaderView(title = stringResource(R.string.tab_settings)) },
        scrollState = listState,
        modifier = modifier,
    ) { contentPadding ->
        SettingsContent(
            listState = listState,
            contentPadding = contentPadding,
            onOpenThemeSettings = onOpenThemeSettings,
        )
    }
}

/**
 * 设置的分组列表：每行是一张圆角分段卡片（圆形图标 + 标题 + 摘要），标题和推入的页面标题一致。
 * The list of setting groups: each row is a rounded segmented card (circular icon, title and summary) whose title
 * matches the title of the screen it pushes.
 */
@Composable
fun SettingsContent(
    listState: LazyListState,
    contentPadding: PaddingValues,
    onOpenThemeSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        contentPadding = contentPadding + PaddingValues(bottom = 24.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            SegmentedListItem(
                onClick = onOpenThemeSettings,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                colors = settingsItemColors(),
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                leadingContent = { SettingsIcon(painter = painterResource(R.drawable.palette_24px)) },
                supportingContent = { Text(text = stringResource(R.string.settings_theme_and_color_summary)) },
            ) {
                Text(text = stringResource(R.string.settings_theme_and_color))
            }
        }
    }
}

@Preview
@Composable
private fun SettingsViewPreview() {
    SteaLTheme(darkTheme = true) {
        SettingsView(onOpenThemeSettings = {})
    }
}
