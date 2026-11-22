package io.github.johnwes7.steal.views.components.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.shared.models.settings.AppTheme
import io.github.johnwes7.steal.shared.models.settings.BarBlurSettings
import io.github.johnwes7.steal.shared.models.settings.ColorPalette
import io.github.johnwes7.steal.views.theme.colorScheme
import kotlin.math.roundToInt

// MARK: - 操作 / Actions

/**
 * 「主题与色彩」页的操作：界面只调这些回调，由页面接到 ViewModel。
 * The "Theme & color" screen's actions: the UI only calls these callbacks, which the screen wires to its view model.
 *
 * 三个滑块的回调只在松手时调一次。
 * The three slider callbacks are called once, when the slider is released.
 */
class ThemeSettingsCallbacks(
    val onThemeChange: (AppTheme) -> Unit,
    val onDynamicColorChange: (Boolean) -> Unit,
    val onPaletteChange: (ColorPalette) -> Unit,
    val onBarBlurEnabledChange: (Boolean) -> Unit,
    val onBarBlurRadiusChange: (Int) -> Unit,
    val onBarBlurTintChange: (Int) -> Unit,
    val onBarBlurNoiseChange: (Int) -> Unit,
    val onResetBarBlur: () -> Unit,
)

// MARK: - 文字 / Labels

/**
 * 外观选项的名字；[short] 给并排的按钮用，「跟随系统」只写 System。
 * An appearance option's name; [short] is for side-by-side buttons, where "System default" becomes "System".
 */
@Composable
fun appThemeLabel(theme: AppTheme, short: Boolean = false): String {
    return when (theme) {
        AppTheme.System -> {
            val resource = if (short) R.string.settings_theme_system_short else R.string.settings_theme_system
            stringResource(resource)
        }
        AppTheme.Light -> stringResource(R.string.settings_theme_light)
        AppTheme.Dark -> stringResource(R.string.settings_theme_dark)
    }
}

/**
 * 调色板的名字。
 * A color palette's name.
 */
@Composable
fun colorPaletteLabel(palette: ColorPalette): String {
    return when (palette) {
        ColorPalette.ThemeBuilder -> stringResource(R.string.settings_palette_theme_builder)
        ColorPalette.ThemeBuilderIosGray -> stringResource(R.string.settings_palette_ios_gray)
        ColorPalette.SimulatedC -> stringResource(R.string.settings_palette_simulated_c)
    }
}

/**
 * 设置分组的小标题；[horizontalPadding] 是左右留白，外面已经有留白时传小一点。
 * A settings group's heading; [horizontalPadding] is the side padding, smaller when the parent already has some.
 */
@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier, horizontalPadding: Dp = 16.dp) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = horizontalPadding, end = horizontalPadding, top = 24.dp, bottom = 8.dp),
    )
}

// MARK: - 毛玻璃滑块 / Frosted glass sliders

/**
 * 毛玻璃数值的滑块：名字和当前值在上，滑块在下；松手时把取整后的值交给 [onValueChangeFinished]。
 * A frosted glass value slider: the name and current value above, the slider below; on release it hands the
 * rounded value to [onValueChangeFinished].
 *
 * 拖动中只改这一行自己的状态，存的 [value] 变了（保存完、恢复默认）就从新值重新开始。
 * While dragging only this row's own state changes; when the stored [value] changes (after saving or a reset) it
 * starts over from the new value.
 */
@Composable
fun BlurValueSlider(
    label: String,
    value: Int,
    valueRange: IntRange,
    formatValue: @Composable (Int) -> String,
    enabled: Boolean,
    onValueChangeFinished: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val shownValue = dragValue.roundToInt()
    val sliderRange = valueRange.first.toFloat()..valueRange.last.toFloat()

    Column(modifier = modifier) {
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

/**
 * 三个毛玻璃滑块（半径、叠色、噪点），各种界面方案共用。
 * The three frosted glass sliders (radius, tint, noise), shared by every layout.
 */
@Composable
fun BarBlurSliders(
    barBlur: BarBlurSettings,
    enabled: Boolean,
    callbacks: ThemeSettingsCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        BlurValueSlider(
            label = stringResource(R.string.settings_blur_radius),
            value = barBlur.radiusDp,
            valueRange = BarBlurSettings.RADIUS_RANGE_DP,
            formatValue = { number -> stringResource(R.string.settings_value_dp, number) },
            enabled = enabled,
            onValueChangeFinished = callbacks.onBarBlurRadiusChange,
        )
        BlurValueSlider(
            label = stringResource(R.string.settings_tint_opacity),
            value = barBlur.tintPercent,
            valueRange = BarBlurSettings.PERCENT_RANGE,
            formatValue = { number -> stringResource(R.string.settings_value_percent, number) },
            enabled = enabled,
            onValueChangeFinished = callbacks.onBarBlurTintChange,
        )
        BlurValueSlider(
            label = stringResource(R.string.settings_noise),
            value = barBlur.noisePercent,
            valueRange = BarBlurSettings.PERCENT_RANGE,
            formatValue = { number -> stringResource(R.string.settings_value_percent, number) },
            enabled = enabled,
            onValueChangeFinished = callbacks.onBarBlurNoiseChange,
        )
    }
}

/**
 * 三个数值是不是都还是默认值；是的话「恢复默认」没有意义，按钮禁用。
 * Whether all three values are still the defaults, in which case "Reset to defaults" is disabled.
 */
fun BarBlurSettings.hasDefaultValues(): Boolean {
    val defaults = BarBlurSettings.DEFAULT
    return radiusDp == defaults.radiusDp && tintPercent == defaults.tintPercent && noisePercent == defaults.noisePercent
}

// MARK: - 调色板色块 / Palette swatch

/**
 * 一套调色板的圆形色块：上半是主色容器、左下是次色容器、右下是卡片底色；[selected] 时加一圈描边和对勾。
 * A round swatch of a palette: the primary container on top, the secondary container bottom-left and the card
 * surface bottom-right; when [selected] it gets an outline ring and a check mark.
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
