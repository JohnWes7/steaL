package io.github.johnwes7.steal.views

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnwes7.steal.LocalAppContainer
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.shared.models.settings.AppSettings
import io.github.johnwes7.steal.shared.models.settings.AppTheme
import io.github.johnwes7.steal.shared.models.settings.BarBlurSettings
import io.github.johnwes7.steal.shared.models.settings.ColorPalette
import io.github.johnwes7.steal.shared.viewmodels.ThemeSettingsViewModel
import io.github.johnwes7.steal.views.components.settings.BarBlurSliders
import io.github.johnwes7.steal.views.components.settings.PaletteSwatch
import io.github.johnwes7.steal.views.components.settings.SettingsSectionHeader
import io.github.johnwes7.steal.views.components.settings.SwitchSegmentedListItem
import io.github.johnwes7.steal.views.components.settings.settingsItemColors
import io.github.johnwes7.steal.views.components.shared.PageBackButton
import io.github.johnwes7.steal.views.components.shared.PageScaffold
import io.github.johnwes7.steal.views.components.shared.PageTopBarStyle
import io.github.johnwes7.steal.views.theme.SteaLTheme

// MARK: - 页面 / Screen

/**
 * 「主题与色彩」设置页：外观、动态配色、调色板、毛玻璃。
 * The "Theme & color" settings screen: appearance, dynamic color, palette and frosted bars.
 *
 * 从 Settings 根页面推入，[onBack] 回到上一页。改动马上保存，整个 App 同时生效。
 * Pushed from the Settings root screen; [onBack] returns to it. Changes are saved right away and take effect across
 * the whole app.
 */
@Composable
fun ThemeSettingsView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 1. 取 ViewModel、收集设置，把操作接到 ViewModel
    // 1. Get the view model, collect the settings and wire the actions to the view model
    val viewModel: ThemeSettingsViewModel = viewModel(factory = LocalAppContainer.current.themeSettingsViewModelFactory)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val callbacks = remember(viewModel) {
        ThemeSettingsCallbacks(
            onThemeChange = viewModel::setTheme,
            onDynamicColorChange = viewModel::setUseDynamicColor,
            onPaletteChange = viewModel::setPalette,
            onBarBlurEnabledChange = viewModel::setBarBlurEnabled,
            onBarBlurRadiusChange = viewModel::setBarBlurRadius,
            onBarBlurTintChange = viewModel::setBarBlurTintPercent,
            onBarBlurNoiseChange = viewModel::setBarBlurNoisePercent,
            onResetBarBlur = viewModel::resetBarBlurValues,
        )
    }

    // 2. 这台手机支持什么：动态配色和模糊都要 Android 12 以上
    // 2. What this phone supports: dynamic color and blur both need Android 12 or later
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // 3. 两行的顶栏，上滑收成一行；设置还没读到（页面刚出现的一瞬间）时不画内容
    // 3. A two-row top bar that collapses as content scrolls; no content until the settings arrive (only for a
    //    moment as the screen appears)
    val listState = rememberLazyListState()
    PageScaffold(
        header = { Text(text = stringResource(R.string.settings_theme_and_color)) },
        scrollState = listState,
        modifier = modifier,
        navigationIcon = { PageBackButton(onClick = onBack) },
        topBarStyle = PageTopBarStyle.Collapsing,
    ) { contentPadding ->
        val currentSettings = settings
        if (currentSettings != null) {
            ThemeSettingsContent(
                settings = currentSettings,
                supportsDynamicColor = isAndroid12OrLater,
                supportsBlur = isAndroid12OrLater,
                callbacks = callbacks,
                listState = listState,
                contentPadding = contentPadding,
            )
        }
    }
}

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

// MARK: - 内容 / Content

/**
 * 「主题与色彩」的设置列表：外观、调色板、毛玻璃三组，每组是一列圆角分段卡片（同 Android 16 的系统设置）。
 * The "Theme & color" settings list: appearance, palette and frosted glass groups, each a column of rounded
 * segmented cards (as in Android 16's Settings app).
 *
 * [supportsDynamicColor] / [supportsBlur] 为 `false` 时对应的设置禁用并写明原因；依赖别的设置的项也是禁用 + 原因，不隐藏。
 * When [supportsDynamicColor] / [supportsBlur] is `false` the matching settings are disabled with the reason;
 * settings that depend on another one are likewise disabled with a reason rather than hidden.
 */
@Composable
fun ThemeSettingsContent(
    settings: AppSettings,
    supportsDynamicColor: Boolean,
    supportsBlur: Boolean,
    callbacks: ThemeSettingsCallbacks,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // 1. 由设置和手机支持情况算出哪些能用
    // 1. Work out what is available from the settings and what the phone supports
    val isDynamicColorOn = supportsDynamicColor && settings.useDynamicColor
    val isPaletteEnabled = !isDynamicColorOn
    val areBlurValuesEnabled = supportsBlur && settings.barBlur.isEnabled
    val canResetBlur = areBlurValuesEnabled && !settings.barBlur.hasDefaultValues()
    val segmentedListItemColors = settingsItemColors()
    val groupModifier = Modifier.padding(horizontal = 16.dp)
    val groupArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)

    // 2. 三组设置
    // 2. The three groups
    LazyColumn(
        state = listState,
        contentPadding = contentPadding + PaddingValues(bottom = 24.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        // 外观：深浅色三选一、动态配色
        // Appearance: light / dark / system, and dynamic color
        item {
            SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance))
            
            Column(modifier = groupModifier, verticalArrangement = groupArrangement) {
                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                    colors = segmentedListItemColors,
                ) {
                    Column {
                        Text(text = stringResource(R.string.settings_theme))
                        Spacer(modifier = Modifier.height(12.dp))
                        ThemeButtonGroup(selected = settings.theme, onSelect = callbacks.onThemeChange)
                    }
                }
                SwitchSegmentedListItem(
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = availableSummary(
                        isSupported = supportsDynamicColor,
                        summary = stringResource(R.string.settings_dynamic_color_summary),
                    ),
                    checked = isDynamicColorOn,
                    enabled = supportsDynamicColor,
                    onCheckedChange = callbacks.onDynamicColorChange,
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                    colors = segmentedListItemColors,
                )
            }
        }

        // 调色板：开了动态配色时禁用
        // Palette: disabled while dynamic color is on
        item {
            SettingsSectionHeader(title = stringResource(R.string.settings_section_palette))
            
            Column(modifier = groupModifier) {
                val unavailableReason = stringResource(R.string.settings_palette_unavailable)
                val supportingContent: (@Composable () -> Unit)? = if (isPaletteEnabled) {
                    null
                } else {
                    { Text(text = unavailableReason) }
                }
                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                    colors = segmentedListItemColors,
                    enabled = isPaletteEnabled,
                    supportingContent = supportingContent,
                ) {
                    PaletteSwatchRow(
                        selected = settings.palette,
                        enabled = isPaletteEnabled,
                        onSelect = callbacks.onPaletteChange,
                    )
                }
            }
        }

        // 毛玻璃：总开关、三个滑块、恢复默认
        // Frosted glass: the master switch, three sliders and reset
        item {
            SettingsSectionHeader(title = stringResource(R.string.settings_section_frosted_glass))
            
            Column(modifier = groupModifier, verticalArrangement = groupArrangement) {
                
                SwitchSegmentedListItem(
                    title = stringResource(R.string.settings_frosted_bars),
                    summary = availableSummary(
                        isSupported = supportsBlur,
                        summary = stringResource(R.string.settings_frosted_bars_summary),
                    ),
                    checked = settings.barBlur.isEnabled,
                    enabled = supportsBlur,
                    onCheckedChange = callbacks.onBarBlurEnabledChange,
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
                    colors = segmentedListItemColors,
                )
                
                // 系统不支持模糊时原因已写在开关上；这里只在开关关着时提示先打开。
                // When the system can't blur, the switch already says why; this only hints to turn the switch on.
                val offHint = stringResource(R.string.settings_frosted_bars_off_hint)
                val showsOffHint = supportsBlur && !settings.barBlur.isEnabled
                val slidersSupportingContent: (@Composable () -> Unit)? = if (showsOffHint) {
                    { Text(text = offHint) }
                } else {
                    null
                }
                
                // three sliders: radius, tint, noise; call the callbacks on every change while dragging
                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                    colors = segmentedListItemColors,
                    enabled = areBlurValuesEnabled,
                    supportingContent = slidersSupportingContent,
                ) {
                    BarBlurSliders(
                        barBlur = settings.barBlur,
                        enabled = areBlurValuesEnabled,
                        onRadiusChange = callbacks.onBarBlurRadiusChange,
                        onTintChange = callbacks.onBarBlurTintChange,
                        onNoiseChange = callbacks.onBarBlurNoiseChange,
                    )
                }
                
                // reset
                SegmentedListItem(
                    onClick = callbacks.onResetBarBlur,
                    shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                    colors = segmentedListItemColors,
                    enabled = canResetBlur,
                ) {
                    Text(text = stringResource(R.string.settings_reset_to_defaults))
                }
            }
        }
    }
}

// MARK: - 行 / Rows

/**
 * 系统支持时显示 [summary]，不支持时换成「需要 Android 12 以上」。
 * Shows [summary] when the system supports the setting, and "Requires Android 12 or later" otherwise.
 */
@Composable
private fun availableSummary(isSupported: Boolean, summary: String): String {
    if (isSupported) {
        return summary
    }
    return stringResource(R.string.settings_requires_android_12)
}

/**
 * 外观三选一的连接式按钮组（M3 Expressive 用它代替分段按钮），点一下就选中。
 * The connected button group for picking the appearance (M3 Expressive's replacement for segmented buttons); one tap
 * selects.
 */
@Composable
private fun ThemeButtonGroup(selected: AppTheme, onSelect: (AppTheme) -> Unit) {
    val themes = AppTheme.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        for ((index, theme) in themes.withIndex()) {
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                themes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = theme == selected,
                onCheckedChange = { _ -> onSelect(theme) },
                shapes = shapes,
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
            ) {
                Text(text = appThemeLabel(theme), maxLines = 1)
            }
        }
    }
}

/**
 * 一排三个调色板色块，下面写名字；[enabled] 为 `false` 时变淡、点不了。
 * A row of three palette swatches with their names below; faded and not tappable when [enabled] is `false`.
 */
@Composable
private fun PaletteSwatchRow(selected: ColorPalette, enabled: Boolean, onSelect: (ColorPalette) -> Unit) {
    val rowAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (palette in ColorPalette.entries) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(palette) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PaletteSwatch(palette = palette, selected = palette == selected)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = colorPaletteLabel(palette),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

// MARK: - 文字 / Labels

/**
 * 外观选项在按钮上的名字（「跟随系统」写 System）。
 * An appearance option's name on its button ("System" for following the system).
 */
@Composable
private fun appThemeLabel(theme: AppTheme): String {
    return when (theme) {
        AppTheme.System -> stringResource(R.string.settings_theme_system)
        AppTheme.Light -> stringResource(R.string.settings_theme_light)
        AppTheme.Dark -> stringResource(R.string.settings_theme_dark)
    }
}

/**
 * 调色板的名字。
 * A color palette's name.
 */
@Composable
private fun colorPaletteLabel(palette: ColorPalette): String {
    return when (palette) {
        ColorPalette.ThemeBuilder -> stringResource(R.string.settings_palette_theme_builder)
        ColorPalette.ThemeBuilderIosGray -> stringResource(R.string.settings_palette_ios_gray)
        ColorPalette.SimulatedC -> stringResource(R.string.settings_palette_simulated_c)
    }
}

/**
 * 三个数值是不是都还是默认值；是的话「恢复默认」禁用。
 * Whether all three values are still the defaults, in which case "Reset to defaults" is disabled.
 */
private fun BarBlurSettings.hasDefaultValues(): Boolean {
    val defaults = BarBlurSettings.DEFAULT
    return radiusDp == defaults.radiusDp && tintPercent == defaults.tintPercent && noisePercent == defaults.noisePercent
}

// MARK: - 预览 / Previews

/**
 * 预览用的空操作。
 * No-op actions for previews.
 */
private val PreviewCallbacks = ThemeSettingsCallbacks(
    onThemeChange = { _ -> },
    onDynamicColorChange = { _ -> },
    onPaletteChange = { _ -> },
    onBarBlurEnabledChange = { _ -> },
    onBarBlurRadiusChange = { _ -> },
    onBarBlurTintChange = { _ -> },
    onBarBlurNoiseChange = { _ -> },
    onResetBarBlur = {},
)

@Preview
@Composable
private fun ThemeSettingsContentPreview() {
    SteaLTheme(darkTheme = true) {
        ThemeSettingsContent(
            settings = AppSettings.DEFAULT,
            supportsDynamicColor = true,
            supportsBlur = true,
            callbacks = PreviewCallbacks,
            listState = rememberLazyListState(),
            contentPadding = PaddingValues(0.dp),
        )
    }
}

/**
 * 依赖关系禁用时的样子：开了动态配色（调色板禁用）、关了毛玻璃（滑块禁用）。
 * How disabled dependents look: dynamic color on (palette disabled) and frosted bars off (sliders disabled).
 */
@Preview
@Composable
private fun ThemeSettingsContentDisabledPreview() {
    val barBlur = BarBlurSettings.DEFAULT.copy(isEnabled = false)
    val settings = AppSettings.DEFAULT.copy(useDynamicColor = true, barBlur = barBlur)
    SteaLTheme(darkTheme = true) {
        ThemeSettingsContent(
            settings = settings,
            supportsDynamicColor = true,
            supportsBlur = true,
            callbacks = PreviewCallbacks,
            listState = rememberLazyListState(),
            contentPadding = PaddingValues(0.dp),
        )
    }
}
