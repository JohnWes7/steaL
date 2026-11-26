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
 * 试玩版入口：点了离开 App 去 Steam 商店页（ITAD 不收录试玩版，比价没有意义）。
 * The demo entry: a tap leaves the app for the Steam store page (ITAD doesn't track demos, so comparing prices is
 * pointless).
 *
 * 主色图标方块 + 「新窗口」图标表示离开 App，和完整游戏入口（次色 + 箭头，App 内推入）区分。
 * The primary icon tile and "open in new" icon mark leaving the app, unlike the full game entry (secondary with a
 * chevron, pushed in the app).
 */
@Composable
fun DemoSectionView(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DetailSection(title = stringResource(R.string.game_detail_section_demo), modifier = modifier) {
        DetailLinkCard(
            iconRes = R.drawable.download_24px,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            title = name,
            subtitle = stringResource(R.string.game_detail_demo_subtitle),
            trailingIconRes = R.drawable.open_in_new_24px,
            onClick = onClick,
        )
    }
}

@Preview
@Composable
private fun DemoSectionPreview() {
    SteaLTheme(darkTheme = true) {
        Column(modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)) {
            DemoSectionView(name = "PRAGMATA Demo", onClick = {})
        }
    }
}
