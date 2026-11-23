package io.github.johnwes7.steal.views.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 设置一级页面行首的图标：`secondaryContainer` 的圆底上画 `onSecondaryContainer` 的图标。
 * The leading icon of a top-level settings row: an `onSecondaryContainer` icon on a `secondaryContainer` circle.
 *
 * 图标只是装饰，不朗读；行的标题已经说明了这一行是什么。
 * The icon is decorative and not announced; the row's title already says what the row is.
 */
@Composable
fun SettingsIcon(painter: Painter, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .background(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape),
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Preview
@Composable
private fun SettingsIconPreview() {
    SteaLTheme(darkTheme = true) {
        SettingsIcon(painter = painterResource(R.drawable.palette_24px))
    }
}
