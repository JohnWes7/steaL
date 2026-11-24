package io.github.johnwes7.steal.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.johnwes7.steal.shared.models.settings.AppSettings
import io.github.johnwes7.steal.shared.services.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Settings tab 根页面的状态：只读当前设置，给各行的摘要用（如当前地区）。
 * The state of the Settings tab's root screen: reads the current settings, read-only, for the rows' summaries (such
 * as the current region).
 */
class SettingsViewModel(settingsRepository: SettingsRepository) : ViewModel() {

    // MARK: - 对外状态 / Exposed state

    /**
     * 当前的设置；`null` 表示还没读到（只在页面第一次出现的一瞬间）。
     * The current settings; `null` until they are read (only for a moment as the screen first appears).
     */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )
}
