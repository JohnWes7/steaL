package io.github.johnwes7.steal.views

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.views.components.shared.PlaceholderView
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * Profile tab 的根页面，对应 iOS `ProfileView`，目前是占位。
 * The Profile tab's root screen, mirroring iOS `ProfileView`; a placeholder for now.
 */
@Composable
fun ProfileView(modifier: Modifier = Modifier) {
    val title = stringResource(R.string.tab_profile)
    PlaceholderView(title = title, modifier = modifier)
}

@Preview
@Composable
private fun ProfileViewPreview() {
    SteaLTheme {
        ProfileView()
    }
}
