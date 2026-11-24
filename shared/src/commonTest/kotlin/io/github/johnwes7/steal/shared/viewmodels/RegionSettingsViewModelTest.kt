package io.github.johnwes7.steal.shared.viewmodels

import io.github.johnwes7.steal.shared.models.settings.AppRegion
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
 * [RegionSettingsViewModel] 的测试：读当前地区、保存选择、保存失败不崩溃。
 * Tests for [RegionSettingsViewModel]: reading the current region, saving a choice, and surviving a failed save.
 *
 * [RegionSettingsViewModel.region] 只在有人收集时才读（`WhileSubscribed`），测试在 `backgroundScope` 里收集它，
 * 代替界面。
 * [RegionSettingsViewModel.region] only reads while collected (`WhileSubscribed`), so tests collect it in
 * `backgroundScope` in place of the UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegionSettingsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 读到之前是 `null`，读到后是当前地区（没存过时是默认的澳大利亚）。
     * `null` until read, then the current region (the default Australia when nothing is saved).
     */
    @Test
    fun regionIsNullUntilRead() = runTest {
        val viewModel = RegionSettingsViewModel(
            settingsRepository = SettingsRepository(dataStore = InMemoryPreferencesDataStore()),
        )
        val initialRegion = viewModel.region.value

        collectRegion(viewModel)
        advanceUntilIdle()

        assertNull(initialRegion)
        assertEquals(AppRegion.Australia, viewModel.region.value)
    }

    /**
     * 选的地区存进存储，并经 [RegionSettingsViewModel.region] 回到界面。
     * The chosen region is saved to storage and comes back to the UI through [RegionSettingsViewModel.region].
     */
    @Test
    fun choiceIsSavedAndShown() = runTest {
        val repository = SettingsRepository(dataStore = InMemoryPreferencesDataStore())
        val viewModel = RegionSettingsViewModel(settingsRepository = repository)
        collectRegion(viewModel)

        viewModel.setRegion(AppRegion.Japan)
        advanceUntilIdle()

        assertEquals(AppRegion.Japan, viewModel.region.value)
        assertEquals(AppRegion.Japan, repository.settings.first().region)
    }

    /**
     * 保存失败时不崩溃，界面留在原来的地区。
     * A failed save doesn't crash, and the UI keeps the previous region.
     */
    @Test
    fun failedSaveKeepsPreviousValue() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val viewModel = RegionSettingsViewModel(settingsRepository = SettingsRepository(dataStore = dataStore))
        collectRegion(viewModel)
        advanceUntilIdle()

        dataStore.failsWrites = true
        viewModel.setRegion(AppRegion.Korea)
        advanceUntilIdle()

        assertEquals(AppRegion.Australia, viewModel.region.value)
    }

    /**
     * 代替界面一直收集 [RegionSettingsViewModel.region]，测试结束时自动停止。
     * Keeps collecting [RegionSettingsViewModel.region] in place of the UI, stopping when the test ends.
     */
    private fun TestScope.collectRegion(viewModel: RegionSettingsViewModel) {
        backgroundScope.launch {
            viewModel.region.collect { _ -> }
        }
    }
}
