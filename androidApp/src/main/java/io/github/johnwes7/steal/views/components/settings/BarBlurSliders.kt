package io.github.johnwes7.steal.views.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.shared.models.settings.BarBlurSettings
import io.github.johnwes7.steal.views.theme.SteaLTheme
import kotlin.math.roundToInt

/**
 * 毛玻璃的三个滑块（模糊半径、叠色不透明度、噪点），拖动中每次变化都交出取整后的值。
 * The three frosted glass sliders (blur radius, tint opacity, noise), each handing over its rounded value on every
 * change while dragged.
 */
@Composable
fun BarBlurSliders(
    barBlur: BarBlurSettings,
    enabled: Boolean,
    onRadiusChange: (Int) -> Unit,
    onTintChange: (Int) -> Unit,
    onNoiseChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        BlurValueSlider(
            label = stringResource(R.string.settings_blur_radius),
            value = barBlur.radiusDp,
            valueRange = BarBlurSettings.RADIUS_RANGE_DP,
            formatValue = { number -> stringResource(R.string.settings_value_dp, number) },
            enabled = enabled,
            onValueChangeFinished = onRadiusChange,
        )
        BlurValueSlider(
            label = stringResource(R.string.settings_tint_opacity),
            value = barBlur.tintPercent,
            valueRange = BarBlurSettings.PERCENT_RANGE,
            formatValue = { number -> stringResource(R.string.settings_value_percent, number) },
            enabled = enabled,
            onValueChangeFinished = onTintChange,
        )
        BlurValueSlider(
            label = stringResource(R.string.settings_noise),
            value = barBlur.noisePercent,
            valueRange = BarBlurSettings.PERCENT_RANGE,
            formatValue = { number -> stringResource(R.string.settings_value_percent, number) },
            enabled = enabled,
            onValueChangeFinished = onNoiseChange,
        )
    }
}

/**
 * 一个数值滑块：名字和当前值在上，滑块在下；松手时把取整后的值交给 [onValueChangeFinished]。
 * One value slider: the name and current value above, the slider below; on release it hands the rounded value to
 * [onValueChangeFinished].
 *
 * 拖动中只改这一行自己的状态（官方 Slider 示例的写法），存的 [value] 变了（保存完、恢复默认）就从新值重新开始。
 * While dragging only this row's own state changes (as in the official Slider samples); when the stored [value]
 * changes (after saving or a reset) it starts over from the new value.
 */
@Composable
private fun BlurValueSlider(
    label: String,
    value: Int,
    valueRange: IntRange,
    formatValue: @Composable (Int) -> String,
    enabled: Boolean,
    onValueChangeFinished: (Int) -> Unit,
) {
    var dragValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val shownValue = dragValue.roundToInt()
    val sliderRange = valueRange.first.toFloat()..valueRange.last.toFloat()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, modifier = Modifier.weight(1f))
            Text(text = formatValue(shownValue), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = dragValue,
            onValueChange = { newValue -> dragValue = newValue },
            onValueChangeFinished = { onValueChangeFinished(dragValue.roundToInt()) },
            valueRange = sliderRange,
            enabled = enabled,
        )
    }
}

@Preview
@Composable
private fun BarBlurSlidersPreview() {
    SteaLTheme(darkTheme = true) {
        BarBlurSliders(
            barBlur = BarBlurSettings.DEFAULT,
            enabled = true,
            onRadiusChange = { _ -> },
            onTintChange = { _ -> },
            onNoiseChange = { _ -> },
        )
    }
}
