package io.github.johnwes7.steal.views.components.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.shared.models.settings.ColorPalette
import io.github.johnwes7.steal.views.theme.SteaLTheme
import io.github.johnwes7.steal.views.theme.colorScheme

/**
 * 一套调色板的圆形色块：上半是主色容器、左下是次色容器、右下是卡片底色；[selected] 时加一圈主色描边和对勾。
 * A round swatch of a palette: the primary container on top, the secondary container bottom-left and the card
 * surface bottom-right; when [selected] it gets a primary-colored ring and a check mark.
 *
 * 颜色按当前页面的深浅取，所以深色模式下看到的是这套调色板的深色版。
 * The colors follow the screen's current light or dark mode, so dark mode shows the palette's dark version.
 */
@Composable
fun PaletteSwatch(
    palette: ColorPalette,
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    // 1. 按当前页面的深浅取这套调色板的颜色
    // 1. This palette's colors in the screen's current light or dark mode
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scheme = palette.colorScheme(darkTheme = isDark)

    // 2. 选中时外面一圈主色描边
    // 2. A primary-colored ring around it when selected
    val ringModifier = if (selected) {
        Modifier.border(width = 2.dp, color = MaterialTheme.colorScheme.primary, shape = CircleShape)
    } else {
        Modifier
    }

    // 3. 画三块颜色，选中的在中间放对勾
    // 3. Draw the three color blocks, with a check mark in the middle when selected
    Box(
        modifier = modifier
            .size(size)
            .then(ringModifier)
            .padding(4.dp)
            .clip(CircleShape)
            .drawBehind {
                val half = Size(width = this.size.width / 2, height = this.size.height / 2)
                drawRect(color = scheme.primaryContainer, size = Size(this.size.width, half.height))
                drawRect(color = scheme.secondaryContainer, topLeft = Offset(0f, half.height), size = half)
                drawRect(color = scheme.surfaceContainerHigh, topLeft = Offset(half.width, half.height), size = half)
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.check_24px),
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
            )
        }
    }
}

@Preview
@Composable
private fun PaletteSwatchPreview() {
    SteaLTheme(darkTheme = true) {
        Row {
            for (palette in ColorPalette.entries) {
                PaletteSwatch(palette = palette, selected = palette == ColorPalette.ThemeBuilder)
            }
        }
    }
}
