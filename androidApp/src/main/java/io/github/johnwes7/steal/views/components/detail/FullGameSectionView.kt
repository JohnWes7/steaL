package io.github.johnwes7.steal.views.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 完整游戏入口：只在试玩版页面上出现，点了在 App 内推入本体的详情页。
 * The full game entry: only on a demo's page, and a tap pushes the full game's detail page inside the app.
 */
@Composable
fun FullGameSectionView(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DetailSection(title = stringResource(R.string.game_detail_section_full_game), modifier = modifier) {
        DetailLinkCard(
            iconRes = R.drawable.sports_esports_fill1_24px,
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            title = name,
            subtitle = stringResource(R.string.game_detail_full_game_subtitle),
            trailingIconRes = R.drawable.chevron_right_24px,
            onClick = onClick,
        )
    }
}

@Preview
@Composable
private fun FullGameSectionPreview() {
    SteaLTheme(darkTheme = true) {
        Column(modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)) {
            FullGameSectionView(name = "10hit", onClick = {})
        }
    }
}
