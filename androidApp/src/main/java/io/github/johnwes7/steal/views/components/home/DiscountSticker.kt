package io.github.johnwes7.steal.views.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.views.theme.SteaLTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 折扣贴纸：品牌蓝的花边圆，略微倾斜，写着「-55%」这样的折扣。
 * The discount sticker: a slightly tilted, scalloped brand-blue circle showing a discount such as "-55%".
 *
 * [cut] 直接用 ITAD 给的值，不按价格重算，免得和服务端的舍入对不上；朗读成「55% off」而不是「minus 55 percent」。
 * [cut] is ITAD's own value rather than one recomputed from the prices, which could round differently; it reads
 * out as "55% off" rather than "minus 55 percent".
 */
@Composable
fun DiscountSticker(
    cut: Int,
    diameter: Dp,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.deal_discount_badge, cut)
    val description = stringResource(R.string.deal_discount_description, cut)

    Box(
        modifier = modifier
            .rotate(STICKER_TILT_DEGREES)
            .defaultMinSize(minWidth = diameter, minHeight = diameter)
            .shadow(elevation = 3.dp, shape = stickerShape)
            .background(color = MaterialTheme.colorScheme.secondary, shape = stickerShape)
            .padding(6.dp)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = textStyle,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSecondary,
            maxLines = 1,
        )
    }
}

/**
 * 贴纸逆时针歪一点，像手贴上去的价格标签。
 * Tilted a little counter-clockwise, like a price tag stuck on by hand.
 */
private const val STICKER_TILT_DEGREES = -10f

private val stickerShape: Shape = ScallopedCircleShape(scallops = 12, depth = 0.08f)

/**
 * 花边圆：圆周上有 [scallops] 个圆润的起伏，凹处比圆周缩进 [depth]（占半径的比例）。
 * A scalloped circle: [scallops] rounded bumps around the edge, with the dips [depth] (a fraction of the radius) in.
 */
private class ScallopedCircleShape(
    private val scallops: Int,
    private val depth: Float,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val radiusX = size.width / 2f
        val radiusY = size.height / 2f
        // 每个起伏取 8 个点连成折线，看起来已经是平滑的曲线。
        // Eight points per bump are enough for the polyline to read as a smooth curve.
        val pointCount = scallops * 8

        for (index in 0..pointCount) {
            val angle = 2.0 * PI * index / pointCount
            // cos 在凸起处为 1、凹处为 -1，换算成半径的缩放：凸起 1，凹处 1 - depth。
            // cos is 1 on a bump and -1 in a dip, mapped to a radius scale of 1 on bumps and 1 - depth in dips.
            val wave = cos(scallops * angle).toFloat()
            val scale = 1f - depth * (1f - wave) / 2f
            val x = radiusX + radiusX * scale * cos(angle).toFloat()
            val y = radiusY + radiusY * scale * sin(angle).toFloat()
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        return Outline.Generic(path)
    }
}

@Preview
@Composable
private fun DiscountStickerPreview() {
    SteaLTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            DiscountSticker(cut = 67, diameter = 60.dp, textStyle = MaterialTheme.typography.titleMedium)
        }
    }
}
