package io.github.johnwes7.steal.views.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
            onValueChange = onRadiusChange,
        )
        BlurValueSlider(
            label = stringResource(R.string.settings_tint_opacity),
            value = barBlur.tintPercent,
            valueRange = BarBlurSettings.PERCENT_RANGE,
            formatValue = { number -> stringResource(R.string.settings_value_percent, number) },
            enabled = enabled,
            onValueChange = onTintChange,
        )
        BlurValueSlider(
            label = stringResource(R.string.settings_noise),
            value = barBlur.noisePercent,
            valueRange = BarBlurSettings.PERCENT_RANGE,
            formatValue = { number -> stringResource(R.string.settings_value_percent, number) },
            enabled = enabled,
            onValueChange = onNoiseChange,
        )
    }
}

/**
 * 一个数值滑块：名字和当前值在上，滑块在下；拖动中每次变化都把取整后的值交给 [onValueChange]。
 * One value slider: the name and current value above, the slider below; every change while dragging hands the
 * rounded value to [onValueChange].
 *
 * 滑块和数字直接显示存下来的 [value]，没有自己的状态：拖动时要等新值存好、读回来才会动。
 * The slider and the number show the stored [value] directly, with no state of their own: while dragging they
 * only move once the new value has been saved and read back.
 */
@Composable
private fun BlurValueSlider(
    label: String,
    value: Int,
    valueRange: IntRange,
    formatValue: @Composable (Int) -> String,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    val sliderRange = valueRange.first.toFloat()..valueRange.last.toFloat()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, modifier = Modifier.weight(1f))
            Text(text = formatValue(value), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { newValue ->
                // 取整后和存的值一样就不写，免得同一个值排队写好几次
                // Skip values that round to what is already stored, so the same value isn't queued many times
                val roundedValue = newValue.roundToInt()
                if (roundedValue != value) {
                    onValueChange(roundedValue)
                }
            },
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
