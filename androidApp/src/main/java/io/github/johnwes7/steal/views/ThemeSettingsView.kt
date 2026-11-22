package io.github.johnwes7.steal.views

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.views.components.shared.PageBackButton
import io.github.johnwes7.steal.views.components.shared.PageScaffold
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 「主题与色彩」设置页：外观、动态配色、调色板、毛玻璃。
 * The "Theme & color" settings screen: appearance, dynamic color, palette and frosted bars.
 *
 * 从 Settings 根页面推入，[onBack] 回到上一页。
 * Pushed from the Settings root screen; [onBack] returns to it.
 */
@Composable
fun ThemeSettingsView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    PageScaffold(
        header = { Text(text = stringResource(R.string.settings_theme_and_color)) },
        scrollState = listState,
        modifier = modifier,
        navigationIcon = { PageBackButton(onClick = onBack) },
    ) { contentPadding ->
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
        }
    }
}

@Preview
@Composable
private fun ThemeSettingsViewPreview() {
    SteaLTheme(darkTheme = true) {
        ThemeSettingsView(onBack = {})
    }
}
