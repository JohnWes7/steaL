package io.github.johnwes7.steal.views

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.views.components.shared.PlaceholderView
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * Settings tab 的根页面，目前是占位。
 * The Settings tab's root screen; a placeholder for now.
 */
@Composable
fun SettingsView(modifier: Modifier = Modifier) {
    val title = stringResource(R.string.tab_settings)
    PlaceholderView(title = title, modifier = modifier)
}

@Preview
@Composable
private fun SettingsViewPreview() {
    SteaLTheme {
        SettingsView()
    }
}
