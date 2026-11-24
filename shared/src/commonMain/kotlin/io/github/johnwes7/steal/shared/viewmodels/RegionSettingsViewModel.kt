package io.github.johnwes7.steal.shared.viewmodels

import androidx.datastore.core.IOException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.johnwes7.steal.shared.config.AppLog
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.services.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 「地区与货币」单选页的状态：读当前地区，把用户选的地区存下来。
 * The state of the "Region & currency" single-choice screen: reads the current region and saves the user's choice.
 *
 * 存好后经 [region] 回到界面，首页的 `GamesViewModel` 也同时收到、重新加载；保存失败只记日志，界面留在原来的值。
 * A saved choice comes back to the UI through [region] and reaches the home page's `GamesViewModel`, which reloads;
 * a failed save is only logged, and the UI keeps the previous value.
 */
class RegionSettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _region: Flow<AppRegion> = settingsRepository.settings.map { settings -> settings.region }

    // MARK: - 对外状态 / Exposed state

    /**
     * 当前的地区；`null` 表示还没读到（只在页面刚出现的一瞬间）。
     * The current region; `null` until it is read (only for a moment as the screen appears).
     */
    val region: StateFlow<AppRegion?> = _region.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    // MARK: - 界面调用 / Called by the UI

    /**
     * 保存选中的地区；磁盘出错时记日志，不让 App 崩溃。
     * Saves the chosen region; a disk error is logged rather than crashing the app.
     */
    fun setRegion(region: AppRegion) {
        viewModelScope.launch {
            try {
                settingsRepository.setRegion(region)
            } catch (error: IOException) {
                AppLog.settings.e(error) { "Failed to save the region" }
            }
        }
    }
}
