package io.github.johnwes7.steal.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.johnwes7.steal.shared.models.settings.AppSettings
import io.github.johnwes7.steal.shared.services.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * App 最外层的状态：整个 App 都要用的设置（主题、配色、毛玻璃）。
 * The app's outermost state: the settings the whole app uses (theme, colors, frosted bars).
 *
 * 平台入口持有一份（Android 是 `MainActivity`），设置读到之前一直是 [SettingsLoadState.Loading]，
 * 入口据此决定启动画面什么时候收起。
 * The platform entry point holds one (`MainActivity` on Android); it stays [SettingsLoadState.Loading] until the
 * settings are read, which the entry point uses to decide when the splash screen goes away.
 */
class RootViewModel(settingsRepository: SettingsRepository) : ViewModel() {

    // MARK: - 状态类型 / State types

    /**
     * 设置读到了没有。
     * Whether the settings have been read yet.
     */
    sealed interface SettingsLoadState {

        /**
         * 还在读第一次。
         * The first read is still running.
         */
        data object Loading : SettingsLoadState

        /**
         * 已读到 [settings]，之后每次改设置都会换成新的值。
         * [settings] has been read, and is replaced with the new value whenever a setting changes.
         */
        data class Loaded(val settings: AppSettings) : SettingsLoadState
    }

    private val _settingsState: Flow<SettingsLoadState> = settingsRepository.settings.map { settings ->
        SettingsLoadState.Loaded(settings)
    }

    // MARK: - 对外状态 / Exposed state
    
    /**
     * 当前的设置。
     * The current settings.
     *
     * 建好就开始读（`Eagerly`），不等界面收集：启动画面在等它，越早读到收得越快。
     * Reading starts as soon as the view model exists (`Eagerly`) rather than when the UI collects: the splash screen
     * is waiting on it, so the sooner it arrives the sooner the splash goes away.
     */
    val settingsState: StateFlow<SettingsLoadState> = _settingsState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsLoadState.Loading,
    )
}
