package io.github.johnwes7.steal.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnwes7.steal.LocalAppContainer
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.viewmodels.RegionSettingsViewModel
import io.github.johnwes7.steal.views.components.settings.settingsItemColors
import io.github.johnwes7.steal.views.components.shared.PageBackButton
import io.github.johnwes7.steal.views.components.shared.PageScaffold
import io.github.johnwes7.steal.views.components.shared.PageTopBarStyle
import io.github.johnwes7.steal.views.theme.SteaLTheme

// MARK: - 页面 / Screen

/**
 * 「地区与货币」单选页：选商店地区，价格和货币跟着它变。
 * The "Region & currency" single-choice screen: picks the store region, which the prices and currency follow.
 *
 * 从 Settings 根页面推入，[onBack] 回到上一页。点了马上生效，不自动返回。
 * Pushed from the Settings root screen; [onBack] returns to it. A choice takes effect right away without going back.
 */
@Composable
fun RegionSettingsView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 1. 取 ViewModel、收集当前地区
    // 1. Get the view model and collect the current region
    val viewModelFactory = LocalAppContainer.current.regionSettingsViewModelFactory
    val viewModel: RegionSettingsViewModel = viewModel(factory = viewModelFactory)
    val region by viewModel.region.collectAsStateWithLifecycle()

    // 2. 两行的顶栏，上滑收成一行；地区还没读到（页面刚出现的一瞬间）时不画内容
    // 2. A two-row top bar that collapses as content scrolls; no content until the region arrives (only for a
    //    moment as the screen appears)
    val listState = rememberLazyListState()
    PageScaffold(
        header = { Text(text = stringResource(R.string.settings_region)) },
        scrollState = listState,
        modifier = modifier,
        navigationIcon = { PageBackButton(onClick = onBack) },
        topBarStyle = PageTopBarStyle.Collapsing,
    ) { contentPadding ->
        val currentRegion = region
        if (currentRegion != null) {
            RegionSettingsContent(
                selectedRegion = currentRegion,
                onSelect = { newRegion -> viewModel.setRegion(newRegion) },
                listState = listState,
                contentPadding = contentPadding,
            )
        }
    }
}

// MARK: - 内容 / Content

/**
 * 地区的单选列表：每个可选的地区一行，[selectedRegion] 那一项选中。
 * The single-choice list of regions: one row per available region, with [selectedRegion] selected.
 */
@Composable
fun RegionSettingsContent(
    selectedRegion: AppRegion,
    onSelect: (AppRegion) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val regionOptions = AppRegion.entries
    val segmentedListItemColors = settingsItemColors()
    LazyColumn(
        state = listState,
        contentPadding = contentPadding + PaddingValues(top = 8.dp, bottom = 24.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                for ((index, option) in regionOptions.withIndex()) {
                    val isSelected = option == selectedRegion
                    SegmentedListItem(
                        selected = isSelected,
                        onClick = { onSelect(option) },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = regionOptions.size),
                        colors = segmentedListItemColors,
                        verticalAlignment = Alignment.CenterVertically,
                        leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                    ) {
                        Text(text = regionLabel(region = option))
                    }
                }
            }
        }
    }
}

// MARK: - 显示名字 / Display name

/**
 * 地区在设置里显示的名字。
 * The name a region shows in settings.
 */
@Composable
fun regionLabel(region: AppRegion): String {
    val resourceId = when (region) {
        AppRegion.Australia -> R.string.region_name_australia
        AppRegion.China -> R.string.region_name_china
        AppRegion.UnitedStates -> R.string.region_name_united_states
        AppRegion.UnitedKingdom -> R.string.region_name_united_kingdom
        AppRegion.Japan -> R.string.region_name_japan
        AppRegion.Korea -> R.string.region_name_korea
        AppRegion.Europe -> R.string.region_name_europe
    }
    return stringResource(resourceId)
}

@Preview
@Composable
private fun RegionSettingsContentPreview() {
    // 只改本地状态，Interactive 模式下点得动。
    // Only changes local state, so it responds to taps in Interactive mode.
    var previewRegion by remember { mutableStateOf(AppRegion.Australia) }
    SteaLTheme(darkTheme = true) {
        RegionSettingsContent(
            selectedRegion = previewRegion,
            onSelect = { newRegion -> previewRegion = newRegion },
            listState = rememberLazyListState(),
            contentPadding = PaddingValues(0.dp),
        )
    }
}
