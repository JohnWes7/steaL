package io.github.johnwes7.steal.views.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.shared.models.settings.AppSettings
import io.github.johnwes7.steal.shared.models.settings.AppTheme
import io.github.johnwes7.steal.shared.models.settings.ColorPalette
import io.github.johnwes7.steal.views.theme.colorScheme

// 临时文件：「主题与色彩」页的三套界面方案，用户挑完后只留一套（Settings Planning.md 第 7、8 步）。
// Temporary file: three layouts for the "Theme & color" screen; only the chosen one stays (Settings Planning.md
// steps 7 and 8).

/**
 * 三套界面方案。
 * The three layout options.
 */
enum class ThemeSettingsVariant { A, B, C }

/**
 * 每套方案都要用的、由设置和手机支持情况算出来的开关状态。
 * The enabled states every layout needs, derived from the settings and what the phone supports.
 */
private class ThemeSettingsAvailability(settings: AppSettings, supportsDynamicColor: Boolean, supportsBlur: Boolean) {
    val isPaletteEnabled: Boolean = !(supportsDynamicColor && settings.useDynamicColor)
    val areBlurValuesEnabled: Boolean = supportsBlur && settings.barBlur.isEnabled
    val canResetBlur: Boolean = areBlurValuesEnabled && !settings.barBlur.hasDefaultValues()
}

// MARK: - A：Expressive 分段卡片 / A: Expressive segmented cards

/**
 * 方案 A：组内每行一块圆角卡片（`SegmentedListItem`），外观用连接式按钮组，调色板是一排圆形色块。
 * Layout A: one rounded card per row within a group (`SegmentedListItem`), a connected button group for the
 * appearance, and a row of round swatches for the palette.
 */
@Composable
fun ThemeSettingsVariantA(
    settings: AppSettings,
    supportsDynamicColor: Boolean,
    supportsBlur: Boolean,
    callbacks: ThemeSettingsCallbacks,
) {
    val availability = ThemeSettingsAvailability(settings, supportsDynamicColor, supportsBlur)
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    val groupModifier = Modifier.padding(horizontal = 16.dp)
    val groupArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)

    Column {
        // 外观
        SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance))
        Column(modifier = groupModifier, verticalArrangement = groupArrangement) {
            SegmentedListItem(shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2), colors = colors) {
                Column {
                    Text(text = stringResource(R.string.settings_theme))
                    Spacer(modifier = Modifier.height(12.dp))
                    ThemeButtonGroup(selected = settings.theme, onSelect = callbacks.onThemeChange)
                }
            }
            DynamicColorSegmentedItem(
                settings = settings,
                supportsDynamicColor = supportsDynamicColor,
                index = 1,
                count = 2,
                callbacks = callbacks,
            )
        }

        // 调色板
        SettingsSectionHeader(title = stringResource(R.string.settings_section_palette))
        Column(modifier = groupModifier) {
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                colors = colors,
                enabled = availability.isPaletteEnabled,
                supportingContent = paletteUnavailableText(isEnabled = availability.isPaletteEnabled),
            ) {
                PaletteSwatchRow(
                    selected = settings.palette,
                    enabled = availability.isPaletteEnabled,
                    onSelect = callbacks.onPaletteChange,
                )
            }
        }

        // 毛玻璃
        SettingsSectionHeader(title = stringResource(R.string.settings_section_frosted_glass))
        Column(modifier = groupModifier, verticalArrangement = groupArrangement) {
            FrostedBarsSegmentedItem(settings = settings, supportsBlur = supportsBlur, callbacks = callbacks)
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                colors = colors,
                enabled = availability.areBlurValuesEnabled,
                supportingContent = blurOffHintText(settings = settings, supportsBlur = supportsBlur),
            ) {
                BarBlurSliders(
                    barBlur = settings.barBlur,
                    enabled = availability.areBlurValuesEnabled,
                    callbacks = callbacks,
                )
            }
            SegmentedListItem(
                onClick = callbacks.onResetBarBlur,
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                colors = colors,
                enabled = availability.canResetBlur,
            ) {
                Text(text = stringResource(R.string.settings_reset_to_defaults))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 外观三选一的连接式按钮组（M3 Expressive 用它代替分段按钮）。
 * The connected button group for picking the appearance (M3 Expressive's replacement for segmented buttons).
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
                Text(text = appThemeLabel(theme = theme, short = true), maxLines = 1)
            }
        }
    }
}

/**
 * 动态配色的开关行（分段卡片样式）；系统不支持时禁用并写原因。
 * The dynamic color switch row (segmented card style); disabled with the reason when the system doesn't support it.
 *
 * 开关行用 `onClick` 重载、点整行切换：`checked` 重载是给多选列表的，打开时整行会换成选中的容器色。
 * Switch rows use the `onClick` overload and toggle on a tap anywhere in the row: the `checked` overload is meant
 * for multi-select lists and turns the whole row the selected container color when on.
 */
@Composable
private fun DynamicColorSegmentedItem(
    settings: AppSettings,
    supportsDynamicColor: Boolean,
    index: Int,
    count: Int,
    callbacks: ThemeSettingsCallbacks,
) {
    val summary = if (supportsDynamicColor) {
        stringResource(R.string.settings_dynamic_color_summary)
    } else {
        stringResource(R.string.settings_requires_android_12)
    }
    SegmentedListItem(
        onClick = { callbacks.onDynamicColorChange(!(settings.useDynamicColor && supportsDynamicColor)) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        enabled = supportsDynamicColor,
        supportingContent = { Text(text = summary) },
        trailingContent = {
            Switch(
                checked = settings.useDynamicColor && supportsDynamicColor,
                onCheckedChange = null,
                enabled = supportsDynamicColor,
            )
        },
    ) {
        Text(text = stringResource(R.string.settings_dynamic_color))
    }
}

/**
 * 毛玻璃总开关（分段卡片样式，组里的第一行）。
 * The frosted bars master switch (segmented card style, the first row of its group).
 */
@Composable
private fun FrostedBarsSegmentedItem(settings: AppSettings, supportsBlur: Boolean, callbacks: ThemeSettingsCallbacks) {
    val summary = if (supportsBlur) {
        stringResource(R.string.settings_frosted_bars_summary)
    } else {
        stringResource(R.string.settings_requires_android_12)
    }
    SegmentedListItem(
        onClick = { callbacks.onBarBlurEnabledChange(!settings.barBlur.isEnabled) },
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        enabled = supportsBlur,
        supportingContent = { Text(text = summary) },
        trailingContent = {
            Switch(checked = settings.barBlur.isEnabled, onCheckedChange = null, enabled = supportsBlur)
        },
    ) {
        Text(text = stringResource(R.string.settings_frosted_bars))
    }
}

/**
 * 一排三个调色板色块，下面写名字。
 * A row of three palette swatches with their names below.
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

// MARK: - B：经典列表 + 单选对话框 / B: classic list with choice dialogs

/**
 * 方案 B 打开的对话框。
 * The dialog layout B has open.
 */
private enum class ThemeSettingsDialog { None, Theme, Palette }

/**
 * 方案 B：普通列表行 + 分组标题；外观和调色板那一行显示当前值，点开是单选对话框。
 * Layout B: plain list rows under group headings; the appearance and palette rows show the current value and open
 * a single-choice dialog.
 */
@Composable
fun ThemeSettingsVariantB(
    settings: AppSettings,
    supportsDynamicColor: Boolean,
    supportsBlur: Boolean,
    callbacks: ThemeSettingsCallbacks,
) {
    val availability = ThemeSettingsAvailability(settings, supportsDynamicColor, supportsBlur)
    var openDialog by rememberSaveable { mutableStateOf(ThemeSettingsDialog.None) }
    val dynamicColorSummary = if (supportsDynamicColor) {
        stringResource(R.string.settings_dynamic_color_summary)
    } else {
        stringResource(R.string.settings_requires_android_12)
    }
    val frostedSummary = if (supportsBlur) {
        stringResource(R.string.settings_frosted_bars_summary)
    } else {
        stringResource(R.string.settings_requires_android_12)
    }

    Column {
        // 外观
        SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance))
        ListItem(
            onClick = { openDialog = ThemeSettingsDialog.Theme },
            supportingContent = { Text(text = appThemeLabel(settings.theme)) },
        ) {
            Text(text = stringResource(R.string.settings_theme))
        }
        ListItem(
            onClick = { callbacks.onDynamicColorChange(!(settings.useDynamicColor && supportsDynamicColor)) },
            enabled = supportsDynamicColor,
            supportingContent = { Text(text = dynamicColorSummary) },
            trailingContent = {
                Switch(
                    checked = settings.useDynamicColor && supportsDynamicColor,
                    onCheckedChange = null,
                    enabled = supportsDynamicColor,
                )
            },
        ) {
            Text(text = stringResource(R.string.settings_dynamic_color))
        }

        // 调色板
        SettingsSectionHeader(title = stringResource(R.string.settings_section_palette))
        ListItem(
            onClick = { openDialog = ThemeSettingsDialog.Palette },
            enabled = availability.isPaletteEnabled,
            leadingContent = { PaletteSwatch(palette = settings.palette, selected = false, size = 40.dp) },
            supportingContent = paletteUnavailableText(isEnabled = availability.isPaletteEnabled),
        ) {
            Text(text = colorPaletteLabel(settings.palette))
        }

        // 毛玻璃
        SettingsSectionHeader(title = stringResource(R.string.settings_section_frosted_glass))
        ListItem(
            onClick = { callbacks.onBarBlurEnabledChange(!settings.barBlur.isEnabled) },
            enabled = supportsBlur,
            supportingContent = { Text(text = frostedSummary) },
            trailingContent = {
                Switch(checked = settings.barBlur.isEnabled, onCheckedChange = null, enabled = supportsBlur)
            },
        ) {
            Text(text = stringResource(R.string.settings_frosted_bars))
        }
        BarBlurSliders(
            barBlur = settings.barBlur,
            enabled = availability.areBlurValuesEnabled,
            callbacks = callbacks,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        val offHint = blurOffHintText(settings = settings, supportsBlur = supportsBlur)
        if (offHint != null) {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                offHint()
            }
        }
        ListItem(onClick = callbacks.onResetBarBlur, enabled = availability.canResetBlur) {
            Text(text = stringResource(R.string.settings_reset_to_defaults))
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    // 单选对话框：点一项就保存并关掉
    when (openDialog) {
        ThemeSettingsDialog.None -> Unit
        ThemeSettingsDialog.Theme -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = AppTheme.entries,
            selected = settings.theme,
            label = { theme -> appThemeLabel(theme) },
            onSelect = callbacks.onThemeChange,
            onDismiss = { openDialog = ThemeSettingsDialog.None },
        )
        ThemeSettingsDialog.Palette -> ChoiceDialog(
            title = stringResource(R.string.settings_section_palette),
            options = ColorPalette.entries,
            selected = settings.palette,
            label = { palette -> colorPaletteLabel(palette) },
            onSelect = callbacks.onPaletteChange,
            onDismiss = { openDialog = ThemeSettingsDialog.None },
        )
    }
}

/**
 * 单选对话框：每项一个单选按钮，点了就选中并关掉（Android 设置里的单选对话框行为）。
 * A single-choice dialog: one radio button per option; tapping one selects it and closes the dialog, as in Android
 * Settings.
 */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                for (option in options) {
                    val isSelected = option == selected
                    ListItem(
                        selected = isSelected,
                        onClick = {
                            onSelect(option)
                            onDismiss()
                        },
                        colors = itemColors,
                        leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                    ) {
                        Text(text = label(option))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

// MARK: - C：Animeko 式预览卡 / C: Animeko-style preview cards

/**
 * 方案 C：外观是三张小预览图，调色板是带名字和色条的卡片，开关和滑块放在圆角卡片里。
 * Layout C: three small previews for the appearance, cards with a name and color strip for the palette, and the
 * switches and sliders inside rounded cards.
 */
@Composable
fun ThemeSettingsVariantC(
    settings: AppSettings,
    supportsDynamicColor: Boolean,
    supportsBlur: Boolean,
    callbacks: ThemeSettingsCallbacks,
) {
    val availability = ThemeSettingsAvailability(settings, supportsDynamicColor, supportsBlur)
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val cardShape = MaterialTheme.shapes.extraLarge
    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    val dynamicColorSummary = if (supportsDynamicColor) {
        stringResource(R.string.settings_dynamic_color_summary)
    } else {
        stringResource(R.string.settings_requires_android_12)
    }
    val frostedSummary = if (supportsBlur) {
        stringResource(R.string.settings_frosted_bars_summary)
    } else {
        stringResource(R.string.settings_requires_android_12)
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // 外观：三张预览图
        SettingsSectionHeader(
            title = stringResource(R.string.settings_section_appearance),
            horizontalPadding = 4.dp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.selectableGroup()) {
            for (theme in AppTheme.entries) {
                AppearancePreviewCard(
                    theme = theme,
                    palette = settings.palette,
                    selected = theme == settings.theme,
                    onClick = { callbacks.onThemeChange(theme) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Surface(shape = cardShape, color = cardColor) {
            ListItem(
                onClick = { callbacks.onDynamicColorChange(!(settings.useDynamicColor && supportsDynamicColor)) },
                enabled = supportsDynamicColor,
                colors = itemColors,
                supportingContent = { Text(text = dynamicColorSummary) },
                trailingContent = {
                    Switch(
                        checked = settings.useDynamicColor && supportsDynamicColor,
                        onCheckedChange = null,
                        enabled = supportsDynamicColor,
                    )
                },
            ) {
                Text(text = stringResource(R.string.settings_dynamic_color))
            }
        }

        // 调色板：三张色条卡片
        SettingsSectionHeader(title = stringResource(R.string.settings_section_palette), horizontalPadding = 4.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.selectableGroup()) {
            for (palette in ColorPalette.entries) {
                PaletteCard(
                    palette = palette,
                    selected = palette == settings.palette,
                    enabled = availability.isPaletteEnabled,
                    onClick = { callbacks.onPaletteChange(palette) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (!availability.isPaletteEnabled) {
            Text(
                text = stringResource(R.string.settings_palette_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }

        // 毛玻璃：一张卡片装开关、滑块和恢复默认
        SettingsSectionHeader(title = stringResource(R.string.settings_section_frosted_glass), horizontalPadding = 4.dp)
        Surface(shape = cardShape, color = cardColor) {
            Column {
                ListItem(
                    onClick = { callbacks.onBarBlurEnabledChange(!settings.barBlur.isEnabled) },
                    enabled = supportsBlur,
                    colors = itemColors,
                    supportingContent = { Text(text = frostedSummary) },
                    trailingContent = {
                        Switch(checked = settings.barBlur.isEnabled, onCheckedChange = null, enabled = supportsBlur)
                    },
                ) {
                    Text(text = stringResource(R.string.settings_frosted_bars))
                }
                BarBlurSliders(
                    barBlur = settings.barBlur,
                    enabled = availability.areBlurValuesEnabled,
                    callbacks = callbacks,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        val offHint = blurOffHintText(settings = settings, supportsBlur = supportsBlur)
                        if (offHint != null) {
                            offHint()
                        }
                    }
                    TextButton(onClick = callbacks.onResetBarBlur, enabled = availability.canResetBlur) {
                        Text(text = stringResource(R.string.settings_reset_to_defaults))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 外观选项的小预览：画一个迷你的首页；「跟随系统」那张对角一半浅一半深。
 * A small preview of an appearance option: a miniature home page; the "System default" one is half light, half dark
 * along the diagonal.
 */
@Composable
private fun AppearancePreviewCard(
    theme: AppTheme,
    palette: ColorPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.large
    val borderWidth = if (selected) 3.dp else 1.dp
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val lightScheme = palette.colorScheme(darkTheme = false)
    val darkScheme = palette.colorScheme(darkTheme = true)

    Column(
        modifier = modifier
            .clip(shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.62f)
                .clip(shape)
                .border(width = borderWidth, color = borderColor, shape = shape),
        ) {
            when (theme) {
                AppTheme.Light -> MiniAppPreview(scheme = lightScheme)
                AppTheme.Dark -> MiniAppPreview(scheme = darkScheme)
                AppTheme.System -> {
                    MiniAppPreview(scheme = lightScheme)
                    Box(modifier = Modifier.fillMaxSize().clip(LowerRightTriangle)) {
                        MiniAppPreview(scheme = darkScheme)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Text(
                text = appThemeLabel(theme = theme, short = true),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

/**
 * 右下半个三角形，「跟随系统」预览里用来盖深色那一半。
 * The lower-right triangle, used to lay the dark half over the "System default" preview.
 */
private val LowerRightTriangle = GenericShape { size, _ ->
    moveTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

/**
 * 用 [scheme] 的颜色画一个迷你首页：标语、标题、大卡、两张小卡、底部导航栏。
 * A miniature home page in [scheme]'s colors: tagline, title, a large card, two small cards and the navigation bar.
 */
@Composable
private fun MiniAppPreview(scheme: ColorScheme) {
    val small = RoundedCornerShape(3.dp)
    val card = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(modifier = Modifier.width(22.dp).height(4.dp).clip(small).background(scheme.primary))
        Box(modifier = Modifier.fillMaxWidth(0.6f).height(9.dp).clip(small).background(scheme.onBackground))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(card)
                .background(scheme.surfaceContainerHigh),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .size(width = 22.dp, height = 8.dp)
                    .clip(small)
                    .background(scheme.primaryContainer),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(modifier = Modifier.weight(1f).height(30.dp).clip(card).background(scheme.surfaceContainerHigh))
            Box(modifier = Modifier.weight(1f).height(30.dp).clip(card).background(scheme.surfaceContainerHigh))
        }
        Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(small).background(scheme.surfaceContainer))
    }
}

/**
 * 调色板卡片：上面一条色条（主色容器、次色容器、主色、卡片底色），下面名字；选中时主色描边。
 * A palette card: a color strip on top (primary container, secondary container, primary, card surface) and the name
 * below; outlined in the primary color when selected.
 */
@Composable
private fun PaletteCard(
    palette: ColorPalette,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.large
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scheme = palette.colorScheme(darkTheme = isDark)
    val borderWidth = if (selected) 3.dp else 1.dp
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val cardAlpha = if (enabled) 1f else 0.38f
    val stripColors = listOf(
        scheme.primaryContainer,
        scheme.secondaryContainer,
        scheme.primary,
        scheme.surfaceContainerHigh,
    )

    Column(
        modifier = modifier
            .alpha(cardAlpha)
            .clip(shape)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            for (stripColor in stripColors) {
                Box(modifier = Modifier.weight(1f).fillMaxSize().background(stripColor))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = colorPaletteLabel(palette),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// MARK: - 共用的说明文字 / Shared explanatory text

/**
 * 调色板被禁用时的原因（动态配色开着）；能用时没有说明。
 * Why the palette is disabled (dynamic color is on); nothing when it is available.
 */
@Composable
private fun paletteUnavailableText(isEnabled: Boolean): (@Composable () -> Unit)? {
    if (isEnabled) {
        return null
    }
    val reason = stringResource(R.string.settings_palette_unavailable)
    return { Text(text = reason) }
}

/**
 * 毛玻璃关掉时滑块下面的提示（先打开开关）；系统不支持模糊时原因已写在开关上，这里不重复。
 * The hint under the sliders when frosted bars are off (turn the switch on first); when the system can't blur, the
 * switch already says why, so nothing is repeated here.
 */
@Composable
private fun blurOffHintText(settings: AppSettings, supportsBlur: Boolean): (@Composable () -> Unit)? {
    if (!supportsBlur || settings.barBlur.isEnabled) {
        return null
    }
    val hint = stringResource(R.string.settings_frosted_bars_off_hint)
    return {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
