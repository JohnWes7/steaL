package io.github.johnwes7.steal.views.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 设置页卡片的颜色：卡片用 `surfaceContainerHigh`（默认是 `surface`，和页面背景同色，看不出卡片）。
 * The card colors for settings screens: `surfaceContainerHigh` cards (the default `surface` matches the page
 * background, hiding the cards).
 *
 * 禁用时底色不变、只有文字变淡（同 Material 的默认和系统设置），所以禁用底色也要设成同一个颜色。
 * A disabled row keeps its card color and only its text dims (as in Material's defaults and the system Settings
 * app), so the disabled container gets the same color.
 */
@Composable
fun settingsItemColors(): ListItemColors {
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    return ListItemDefaults.segmentedColors(
        containerColor = cardColor,
        disabledContainerColor = cardColor,
    )
}

/**
 * 带开关的 `SegmentedListItem`：点整行切换，右边的 `Switch` 只显示状态。
 * A `SegmentedListItem` with a switch: tapping anywhere in the row toggles it, and the `Switch` on the right only
 * shows the state.
 *
 * 用 `onClick` 重载而不是 `checked` 重载：后者是给多选列表的，打开时整行会换成选中的容器色。
 * Uses the `onClick` overload rather than the `checked` one, which is meant for multi-select lists and turns the
 * whole row the selected container color when on.
 */
@Composable
fun SwitchSegmentedListItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
    colors: ListItemColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        shapes = shapes,
        colors = colors,
        modifier = modifier,
        enabled = enabled,
        verticalAlignment = Alignment.CenterVertically,
        supportingContent = { Text(text = summary) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
    ) {
        Text(text = title)
    }
}

@Preview
@Composable
private fun SwitchSegmentedListItemPreview() {
    SteaLTheme(darkTheme = true) {
        val colors = settingsItemColors()
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            SwitchSegmentedListItem(
                title = "Frosted bars",
                summary = "Blur the top bar and tab bar",
                checked = true,
                onCheckedChange = {},
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                colors = colors,
            )
            SwitchSegmentedListItem(
                title = "Dynamic color",
                summary = "Requires Android 12 or later",
                checked = false,
                onCheckedChange = {},
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                colors = colors,
                enabled = false,
            )
        }
    }
}

/**
 * 对比 `SegmentedListItem` 另外两个可点的重载：单选（`selected` + `onClick`）、多选（`checked` + `onCheckedChange`）。
 * Compares the other two clickable `SegmentedListItem` overloads: single choice (`selected` + `onClick`) and multiple
 * choice (`checked` + `onCheckedChange`).
 *
 * 选中的行换成 `secondaryContainer`、四角都变大圆角；用 Interactive 模式点一点能看到切换。
 * A selected row turns `secondaryContainer` with large corners on all four sides; tap in Interactive mode to see it.
 */
@Preview(heightDp = 560)
@Composable
private fun SegmentedListItemOverloadsPreview() {
    SteaLTheme(darkTheme = true) {
        val colors = settingsItemColors()
        val labelStyle = MaterialTheme.typography.titleSmall
        val labelColor = MaterialTheme.colorScheme.primary
        val singleOptions = listOf("Theme Builder", "iOS gray", "Simulated C")
        val multiOptions = listOf("Steam", "Epic", "GOG")
        var selectedIndex by remember { mutableIntStateOf(0) }
        val checkedStates = remember { mutableStateListOf(true, false, true) }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            Text(text = "selected + onClick", style = labelStyle, color = labelColor)
            for ((index, option) in singleOptions.withIndex()) {
                val isSelected = index == selectedIndex
                SegmentedListItem(
                    selected = isSelected,
                    onClick = { selectedIndex = index },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = singleOptions.size),
                    colors = colors,
                    leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                ) {
                    Text(text = option)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(text = "checked + onCheckedChange", style = labelStyle, color = labelColor)
            for ((index, option) in multiOptions.withIndex()) {
                val isChecked = checkedStates[index]
                SegmentedListItem(
                    checked = isChecked,
                    onCheckedChange = { newValue -> checkedStates[index] = newValue },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = multiOptions.size),
                    colors = colors,
                    trailingContent = { Checkbox(checked = isChecked, onCheckedChange = null) },
                ) {
                    Text(text = option)
                }
            }
        }
    }
}
