package io.github.johnwes7.steal.views.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 还没做的页面的占位：页面背景上居中显示 [title]。
 * A stand-in for a screen not built yet: [title] centered on the page background.
 */
@Composable
fun PlaceholderView(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Preview
@Composable
private fun PlaceholderViewPreview() {
    SteaLTheme {
        PlaceholderView(title = "Games")
    }
}
