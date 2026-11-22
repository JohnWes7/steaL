package io.github.johnwes7.steal.shared.viewmodels

import io.github.johnwes7.steal.shared.models.settings.AppSettings
import io.github.johnwes7.steal.shared.models.settings.AppTheme
import io.github.johnwes7.steal.shared.models.settings.BarBlurSettings
import io.github.johnwes7.steal.shared.models.settings.ColorPalette
import io.github.johnwes7.steal.shared.services.InMemoryPreferencesDataStore
import io.github.johnwes7.steal.shared.services.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [ThemeSettingsViewModel] 的测试：读当前设置、保存改动、保存失败不崩溃。
 * Tests for [ThemeSettingsViewModel]: reading the current settings, saving changes, and surviving a failed save.
 *
 * [ThemeSettingsViewModel.settings] 只在有人收集时才读（`WhileSubscribed`），测试在 `backgroundScope` 里收集它，
 * 代替界面。
 * [ThemeSettingsViewModel.settings] only reads while collected (`WhileSubscribed`), so tests collect it in
 * `backgroundScope` in place of the UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeSettingsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 读到之前是 `null`，读到后是当前设置。
     * `null` until read, then the current settings.
     */
    @Test
    fun settingsAreNullUntilRead() = runTest {
        val viewModel = ThemeSettingsViewModel(
            settingsRepository = SettingsRepository(dataStore = InMemoryPreferencesDataStore()),
        )
        val initialSettings = viewModel.settings.value

        collectSettings(viewModel)
        advanceUntilIdle()

        assertNull(initialSettings)
        assertEquals(AppSettings.DEFAULT, viewModel.settings.value)
    }

    /**
     * 每个操作都存进存储，并经 [ThemeSettingsViewModel.settings] 回到界面。
     * Each action is saved to storage and comes back to the UI through [ThemeSettingsViewModel.settings].
     */
    @Test
    fun changesAreSavedAndShown() = runTest {
        val repository = SettingsRepository(dataStore = InMemoryPreferencesDataStore())
        val viewModel = ThemeSettingsViewModel(settingsRepository = repository)
        collectSettings(viewModel)

        viewModel.setTheme(AppTheme.System)
        viewModel.setUseDynamicColor(true)
        viewModel.setPalette(ColorPalette.ThemeBuilderIosGray)
        viewModel.setBarBlurEnabled(false)
        viewModel.setBarBlurRadius(8)
        viewModel.setBarBlurTintPercent(40)
        viewModel.setBarBlurNoisePercent(5)
        advanceUntilIdle()

        val expected = AppSettings(
            theme = AppTheme.System,
            useDynamicColor = true,
            palette = ColorPalette.ThemeBuilderIosGray,
            barBlur = BarBlurSettings(isEnabled = false, radiusDp = 8, tintPercent = 40, noisePercent = 5),
        )
        assertEquals(expected, viewModel.settings.value)
        assertEquals(expected, repository.settings.first())
    }

    /**
     * 恢复默认把三个数值改回默认，开关不变。
     * Restoring defaults puts the three numbers back and leaves the switch alone.
     */
    @Test
    fun resetRestoresBlurValues() = runTest {
        val repository = SettingsRepository(dataStore = InMemoryPreferencesDataStore())
        val viewModel = ThemeSettingsViewModel(settingsRepository = repository)
        collectSettings(viewModel)
        viewModel.setBarBlurEnabled(false)
        viewModel.setBarBlurRadius(3)
        advanceUntilIdle()

        viewModel.resetBarBlurValues()
        advanceUntilIdle()

        val expectedBlur = BarBlurSettings.DEFAULT.copy(isEnabled = false)
        assertEquals(expectedBlur, viewModel.settings.value?.barBlur)
    }

    /**
     * 保存失败时不崩溃，界面留在原来的值。
     * A failed save doesn't crash, and the UI keeps the previous value.
     */
    @Test
    fun failedSaveKeepsPreviousValue() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val viewModel = ThemeSettingsViewModel(settingsRepository = SettingsRepository(dataStore = dataStore))
        collectSettings(viewModel)
        advanceUntilIdle()

        dataStore.failsWrites = true
        viewModel.setTheme(AppTheme.Light)
        advanceUntilIdle()

        assertEquals(AppSettings.DEFAULT.theme, viewModel.settings.value?.theme)
    }

    /**
     * 代替界面一直收集 [ThemeSettingsViewModel.settings]，测试结束时自动停止。
     * Keeps collecting [ThemeSettingsViewModel.settings] in place of the UI, stopping when the test ends.
     */
    private fun TestScope.collectSettings(viewModel: ThemeSettingsViewModel) {
        backgroundScope.launch {
            viewModel.settings.collect { _ -> }
        }
    }
}
