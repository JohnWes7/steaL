package io.github.johnwes7.steal.views

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnwes7.steal.LocalAppContainer
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.shared.viewmodels.ThemeSettingsViewModel
import io.github.johnwes7.steal.views.components.settings.ThemeSettingsCallbacks
import io.github.johnwes7.steal.views.components.settings.ThemeSettingsVariant
import io.github.johnwes7.steal.views.components.settings.ThemeSettingsVariantA
import io.github.johnwes7.steal.views.components.settings.ThemeSettingsVariantB
import io.github.johnwes7.steal.views.components.settings.ThemeSettingsVariantC
import io.github.johnwes7.steal.views.components.shared.PageBackButton
import io.github.johnwes7.steal.views.components.shared.PageScaffold
import io.github.johnwes7.steal.views.components.shared.PageTopBarStyle

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
    // 1. 取 ViewModel、收集设置
    // 1. Get the view model and collect the settings
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

    // 3. 临时：三套界面方案的切换（第 7 步截图用，选定后删掉）
    // 3. Temporary: switching between the three layouts (for the step 7 screenshots; removed once one is chosen)
    var variant by rememberSaveable { mutableStateOf(ThemeSettingsVariant.A) }

    val listState = rememberLazyListState()
    PageScaffold(
        header = { Text(text = stringResource(R.string.settings_theme_and_color)) },
        scrollState = listState,
        modifier = modifier,
        navigationIcon = { PageBackButton(onClick = onBack) },
        topBarStyle = PageTopBarStyle.Collapsing,
    ) { contentPadding ->
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (option in ThemeSettingsVariant.entries) {
                        FilterChip(
                            selected = option == variant,
                            onClick = { variant = option },
                            label = { Text(text = "Layout ${option.name}") },
                        )
                    }
                }
            }
            // 设置还没读到（页面刚出现的一瞬间）就先什么都不画。
            // Draw nothing until the settings arrive (only for a moment as the screen appears).
            val currentSettings = settings
            if (currentSettings != null) {
                item {
                    when (variant) {
                        ThemeSettingsVariant.A -> ThemeSettingsVariantA(
                            settings = currentSettings,
                            supportsDynamicColor = isAndroid12OrLater,
                            supportsBlur = isAndroid12OrLater,
                            callbacks = callbacks,
                        )
                        ThemeSettingsVariant.B -> ThemeSettingsVariantB(
                            settings = currentSettings,
                            supportsDynamicColor = isAndroid12OrLater,
                            supportsBlur = isAndroid12OrLater,
                            callbacks = callbacks,
                        )
                        ThemeSettingsVariant.C -> ThemeSettingsVariantC(
                            settings = currentSettings,
                            supportsDynamicColor = isAndroid12OrLater,
                            supportsBlur = isAndroid12OrLater,
                            callbacks = callbacks,
                        )
                    }
                }
            }
        }
    }
}
