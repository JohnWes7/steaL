package io.github.johnwes7.steal.shared.viewmodels

import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.models.settings.AppSettings
import io.github.johnwes7.steal.shared.services.InMemoryPreferencesDataStore
import io.github.johnwes7.steal.shared.services.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * [SettingsViewModel] 的测试：读到之前是 `null`，别处改了设置也跟着变。
 * Tests for [SettingsViewModel]: `null` until read, and following changes made elsewhere.
 *
 * [SettingsViewModel.settings] 只在有人收集时才读（`WhileSubscribed`），测试在 `backgroundScope` 里收集它，代替界面。
 * [SettingsViewModel.settings] only reads while collected (`WhileSubscribed`), so tests collect it in
 * `backgroundScope` in place of the UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

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
        val viewModel = SettingsViewModel(
            settingsRepository = SettingsRepository(dataStore = InMemoryPreferencesDataStore()),
        )
        val initialSettings = viewModel.settings.value

        collectSettings(viewModel)
        advanceUntilIdle()

        assertNull(initialSettings)
        assertEquals(AppSettings.DEFAULT, viewModel.settings.value)
    }

    /**
     * 在别的页面（地区单选页）改了地区，这里也换成新的值。
     * A region changed on another screen (the region page) shows up here too.
     */
    @Test
    fun followsChangesMadeElsewhere() = runTest {
        val repository = SettingsRepository(dataStore = InMemoryPreferencesDataStore())
        val viewModel = SettingsViewModel(settingsRepository = repository)
        collectSettings(viewModel)
        advanceUntilIdle()

        repository.setRegion(AppRegion.UnitedKingdom)
        advanceUntilIdle()

        assertEquals(AppRegion.UnitedKingdom, viewModel.settings.value?.region)
    }

    /**
     * 代替界面一直收集 [SettingsViewModel.settings]，测试结束时自动停止。
     * Keeps collecting [SettingsViewModel.settings] in place of the UI, stopping when the test ends.
     */
    private fun TestScope.collectSettings(viewModel: SettingsViewModel) {
        backgroundScope.launch {
            viewModel.settings.collect { _ -> }
        }
    }
}
