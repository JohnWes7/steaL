package io.github.johnwes7.steal.views.components.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 设置分组的小标题，主色小字，和下面那组卡片的边缘对齐。
 * A settings group's heading: small text in the primary color, aligned with the edge of the cards below it.
 */
@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Preview
@Composable
private fun SettingsSectionHeaderPreview() {
    SteaLTheme(darkTheme = true) {
        SettingsSectionHeader(title = "Appearance")
    }
}
