package io.github.johnwes7.steal.views

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.johnwes7.steal.R
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
        header = { Text(text = stringResource(R.string.tab_settings)) },
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
 * 设置的分组列表；每行的文字和推入的页面标题一致。
 * The list of setting groups; each row's text matches the title of the screen it pushes.
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
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            ListItem(
                onClick = onOpenThemeSettings,
                leadingContent = {
                    Icon(painter = painterResource(R.drawable.palette_24px), contentDescription = null)
                },
                content = { Text(text = stringResource(R.string.settings_theme_and_color)) },
            )
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
