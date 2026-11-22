package io.github.johnwes7.steal.shared.viewmodels

import io.github.johnwes7.steal.shared.models.settings.AppSettings
import io.github.johnwes7.steal.shared.models.settings.AppTheme
import io.github.johnwes7.steal.shared.services.InMemoryPreferencesDataStore
import io.github.johnwes7.steal.shared.services.SettingsRepository
import io.github.johnwes7.steal.shared.viewmodels.RootViewModel.SettingsLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [RootViewModel] 的状态测试：读到设置前是 `Loading`，之后跟着设置变。
 * State tests for [RootViewModel]: `Loading` until the settings are read, then following every change.
 *
 * 存储是内存里的假 DataStore，全部跑在测试调度器上，所以用 `advanceUntilIdle()` 让读取跑完。
 * Storage is an in-memory fake DataStore and everything runs on the test dispatcher, so `advanceUntilIdle()` lets
 * the reads finish.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 一开始是 `Loading`，读到后是 `Loaded`；没存过时是默认设置。
     * Starts as `Loading` and becomes `Loaded` once read; the defaults when nothing has been saved.
     */
    @Test
    fun startsLoadingThenLoadsDefaults() = runTest {
        val repository = SettingsRepository(dataStore = InMemoryPreferencesDataStore())

        val viewModel = RootViewModel(settingsRepository = repository)
        val initialState = viewModel.settingsState.value
        advanceUntilIdle()
        val loadedState = viewModel.settingsState.value

        assertEquals(SettingsLoadState.Loading, initialState)
        assertEquals(SettingsLoadState.Loaded(AppSettings.DEFAULT), loadedState)
    }

    /**
     * 没有界面收集也会去读（启动画面在等它），之后设置改了状态跟着变。
     * Reads without any UI collecting (the splash screen is waiting on it), then follows later changes.
     */
    @Test
    fun followsSettingChanges() = runTest {
        val repository = SettingsRepository(dataStore = InMemoryPreferencesDataStore())
        val viewModel = RootViewModel(settingsRepository = repository)
        advanceUntilIdle()

        repository.setTheme(AppTheme.Light)
        advanceUntilIdle()

        val expectedSettings = AppSettings.DEFAULT.copy(theme = AppTheme.Light)
        assertEquals(SettingsLoadState.Loaded(expectedSettings), viewModel.settingsState.value)
    }

    /**
     * 读文件失败时也会变成 `Loaded`（默认设置），启动画面不会卡住。
     * A failed read still becomes `Loaded` with the defaults, so the splash screen never gets stuck.
     */
    @Test
    fun readFailureStillLoads() = runTest {
        val repository = SettingsRepository(dataStore = InMemoryPreferencesDataStore(failsReads = true))

        val viewModel = RootViewModel(settingsRepository = repository)
        advanceUntilIdle()

        assertEquals(SettingsLoadState.Loaded(AppSettings.DEFAULT), viewModel.settingsState.value)
    }
}
