package io.github.johnwes7.steal.shared.viewmodels

import androidx.datastore.core.IOException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.johnwes7.steal.shared.config.AppLog
import io.github.johnwes7.steal.shared.models.settings.AppSettings
import io.github.johnwes7.steal.shared.models.settings.AppTheme
import io.github.johnwes7.steal.shared.models.settings.ColorPalette
import io.github.johnwes7.steal.shared.services.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 「主题与色彩」设置页的状态：读当前设置，把用户的改动存下来。
 * The state of the "Theme & color" settings screen: reads the current settings and saves the user's changes.
 *
 * 改动存好后经 [settings] 回到界面，App 最外层也同时收到；保存失败只记日志，界面留在原来的值。
 * Saved changes come back to the UI through [settings], and reach the app's outermost layer at the same time; a
 * failed save is only logged, and the UI keeps the previous value.
 */
class ThemeSettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    // MARK: - 对外状态 / Exposed state

    /**
     * 当前的设置；`null` 表示还没读到（只在页面刚出现的一瞬间）。
     * The current settings; `null` until they are read (only for a moment as the screen appears).
     */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    // MARK: - 界面调用 / Called by the UI

    /**
     * 保存深浅色。
     * Saves the light or dark appearance.
     */
    fun setTheme(theme: AppTheme) {
        save { settingsRepository.setTheme(theme) }
    }

    /**
     * 保存是否用动态配色。
     * Saves whether to use dynamic color.
     */
    fun setUseDynamicColor(useDynamicColor: Boolean) {
        save { settingsRepository.setUseDynamicColor(useDynamicColor) }
    }

    /**
     * 保存选用的配色方案。
     * Saves the chosen color palette.
     */
    fun setPalette(palette: ColorPalette) {
        save { settingsRepository.setPalette(palette) }
    }

    /**
     * 保存毛玻璃开关。
     * Saves whether the bars are frosted.
     */
    fun setBarBlurEnabled(isEnabled: Boolean) {
        save { settingsRepository.setBarBlurEnabled(isEnabled) }
    }

    /**
     * 保存模糊半径（dp）。
     * Saves the blur radius in dp.
     *
     * 拖动滑块时每次变化都调用，每次都写一次。
     * Called on every change while the slider is dragged, writing each time.
     */
    fun setBarBlurRadius(radiusDp: Int) {
        save { settingsRepository.setBarBlurRadius(radiusDp) }
    }

    /**
     * 保存叠色不透明度（百分数），拖动滑块时每次变化都调用。
     * Saves the tint opacity as a percentage; called on every change while the slider is dragged.
     */
    fun setBarBlurTintPercent(tintPercent: Int) {
        save { settingsRepository.setBarBlurTintPercent(tintPercent) }
    }

    /**
     * 保存噪点不透明度（百分数），拖动滑块时每次变化都调用。
     * Saves the noise opacity as a percentage; called on every change while the slider is dragged.
     */
    fun setBarBlurNoisePercent(noisePercent: Int) {
        save { settingsRepository.setBarBlurNoisePercent(noisePercent) }
    }

    /**
     * 把模糊半径、叠色、噪点恢复成默认值，毛玻璃开关不变。
     * Restores the blur radius, tint and noise to their defaults, leaving the on/off switch alone.
     */
    fun resetBarBlurValues() {
        save { settingsRepository.resetBarBlurValues() }
    }

    // MARK: - 保存 / Saving

    /**
     * 在 `viewModelScope` 里执行一次写入；磁盘出错时记日志，不让 App 崩溃。
     * Runs one write in `viewModelScope`; a disk error is logged rather than crashing the app.
     */
    private fun save(write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
            } catch (error: IOException) {
                AppLog.settings.e(error) { "Failed to save settings" }
            }
        }
    }
}
