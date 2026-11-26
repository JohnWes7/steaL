package io.github.johnwes7.steal.views.components.detail

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 详情页一个区块：主色小标题直接放在页面上，下面是内容（不包卡片）。
 * One detail page section: a small primary-colored title straight on the page, with the content below (no card).
 */
@Composable
fun DetailSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .semantics { heading() },
        )
        content()
    }
}

/**
 * 不能点的标签（类型、分类）。
 * A tag that can't be tapped (genres, categories).
 *
 * 外形像 Chip，但不用 `Chip`：M3 的 Chip 都是可交互的。
 * Looks like a chip without using `Chip`, since every M3 chip is interactive.
 */
@Composable
fun DetailTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/**
 * 展开 / 收起的文字按钮：「More ⌄」或「Show less ⌃」。
 * The expand / collapse text button: "More ⌄" or "Show less ⌃".
 */
@Composable
fun DetailMoreButton(isExpanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val textRes = if (isExpanded) R.string.game_detail_show_less else R.string.game_detail_more
    val iconRes = if (isExpanded) R.drawable.expand_less_24px else R.drawable.expand_more_24px
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text = stringResource(textRes), style = MaterialTheme.typography.labelLarge)
        Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

/**
 * 区块里「没找到」或「加载失败」的一行说明；[onRetry] 不是 `null` 时右边带重试按钮。
 * A one-line "not found" or "couldn't load" note inside a section, with a retry button when [onRetry] isn't `null`.
 */
@Composable
fun DetailUnavailablePrompt(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Text(text = stringResource(R.string.action_retry))
            }
        }
    }
}

/**
 * 圆角方块里的图标（试玩版、完整游戏、在线人数、排名）。
 * An icon inside a rounded tile (demo, full game, players online, rank).
 */
@Composable
fun DetailIconTile(@DrawableRes iconRes: Int, containerColor: Color, iconColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(color = containerColor, shape = MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = iconColor)
    }
}

/**
 * 一张可点的入口卡片：左边图标方块，中间标题和说明，右边说明点了去哪的图标。
 * A tappable entry card: an icon tile on the left, title and subtitle in the middle, and an icon on the right that
 * says where the tap goes.
 */
@Composable
fun DetailLinkCard(
    @DrawableRes iconRes: Int,
    iconContainerColor: Color,
    iconColor: Color,
    title: String,
    subtitle: String,
    @DrawableRes trailingIconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DetailIconTile(iconRes = iconRes, containerColor = iconContainerColor, iconColor = iconColor)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(trailingIconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// MARK: - 预览 / Previews

@Preview
@Composable
private fun DetailSectionPartsPreview() {
    SteaLTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DetailSection(title = "Section title") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailTag(text = "Action")
                    DetailTag(text = "Adventure")
                }
                DetailMoreButton(isExpanded = false, onClick = {}, modifier = Modifier.align(Alignment.End))
            }
            DetailUnavailablePrompt(message = "Couldn't load prices.", onRetry = {})
            DetailUnavailablePrompt(message = "No prices found for this game.", onRetry = null)
        }
    }
}
