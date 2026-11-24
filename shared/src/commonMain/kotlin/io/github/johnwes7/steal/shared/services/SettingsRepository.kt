package io.github.johnwes7.steal.shared.services

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.johnwes7.steal.shared.config.AppLog
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.models.settings.AppSettings
import io.github.johnwes7.steal.shared.models.settings.AppTheme
import io.github.johnwes7.steal.shared.models.settings.BarBlurSettings
import io.github.johnwes7.steal.shared.models.settings.ColorPalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * 读写用户设置，存在 Preferences DataStore 里。
 * Reads and writes the user's settings, stored in a Preferences DataStore.
 *
 * 全 App 只建一份（在 `AppContainer`）：同一个文件有两个 DataStore 实例时读写会出错。
 * 写入函数在磁盘出错时抛 `IOException`，由调用方处理。
 * The app creates exactly one (in `AppContainer`): two DataStore instances on the same file break reads and
 * writes. The write functions throw `IOException` on disk errors, which callers handle.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    /**
     * 当前的设置；任何一项改了都会再发一次。读文件失败时发默认设置。
     * The current settings, emitted again whenever any item changes; the defaults when the file can't be read.
     *
     * 读失败也要发一个值：App 启动时等第一个值才收起启动画面，不发就一直停在那里。
     * A read failure still emits a value: the app keeps its splash screen up until the first value arrives, so
     * without one it would never go away.
     */
    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error !is IOException) {
                throw error
            }
            AppLog.settings.e(error) { "Failed to read settings; using the defaults" }
            emit(emptyPreferences())
        }
        .map { preferences -> settingsFrom(preferences) }

    // MARK: - 写入 / Writes

    /**
     * 保存商店地区。
     * Saves the store region.
     */
    suspend fun setRegion(region: AppRegion) {
        dataStore.edit { preferences -> preferences[SettingsKeys.REGION] = region.name }
    }

    /**
     * 保存深浅色。
     * Saves the light or dark appearance.
     */
    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { preferences -> preferences[SettingsKeys.THEME] = theme.name }
    }

    /**
     * 保存是否用动态配色。
     * Saves whether to use dynamic color.
     */
    suspend fun setUseDynamicColor(useDynamicColor: Boolean) {
        dataStore.edit { preferences -> preferences[SettingsKeys.USE_DYNAMIC_COLOR] = useDynamicColor }
    }

    /**
     * 保存选用的配色方案。
     * Saves the chosen color palette.
     */
    suspend fun setPalette(palette: ColorPalette) {
        dataStore.edit { preferences -> preferences[SettingsKeys.PALETTE] = palette.name }
    }

    /**
     * 保存毛玻璃开关。
     * Saves whether the bars are frosted.
     */
    suspend fun setBarBlurEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences -> preferences[SettingsKeys.BAR_BLUR_ENABLED] = isEnabled }
    }

    /**
     * 保存模糊半径（dp），超出范围的值收到边界上。
     * Saves the blur radius in dp, clamping values outside the range.
     */
    suspend fun setBarBlurRadius(radiusDp: Int) {
        val clamped = radiusDp.coerceIn(BarBlurSettings.RADIUS_RANGE_DP)
        dataStore.edit { preferences -> preferences[SettingsKeys.BAR_BLUR_RADIUS_DP] = clamped }
    }

    /**
     * 保存叠色不透明度（百分数），超出范围的值收到边界上。
     * Saves the tint opacity as a percentage, clamping values outside the range.
     */
    suspend fun setBarBlurTintPercent(tintPercent: Int) {
        val clamped = tintPercent.coerceIn(BarBlurSettings.PERCENT_RANGE)
        dataStore.edit { preferences -> preferences[SettingsKeys.BAR_BLUR_TINT_PERCENT] = clamped }
    }

    /**
     * 保存噪点不透明度（百分数），超出范围的值收到边界上。
     * Saves the noise opacity as a percentage, clamping values outside the range.
     */
    suspend fun setBarBlurNoisePercent(noisePercent: Int) {
        val clamped = noisePercent.coerceIn(BarBlurSettings.PERCENT_RANGE)
        dataStore.edit { preferences -> preferences[SettingsKeys.BAR_BLUR_NOISE_PERCENT] = clamped }
    }

    /**
     * 把模糊半径、叠色、噪点恢复成默认值；毛玻璃开关不变。
     * Restores the blur radius, tint and noise to their defaults; the on/off switch is left alone.
     *
     * 做法是删掉这三项，读的时候落回默认值，以后改了默认值也跟着变。
     * It removes the three items, so reads fall back to the defaults, including any future change to them.
     */
    suspend fun resetBarBlurValues() {
        dataStore.edit { preferences ->
            preferences.remove(SettingsKeys.BAR_BLUR_RADIUS_DP)
            preferences.remove(SettingsKeys.BAR_BLUR_TINT_PERCENT)
            preferences.remove(SettingsKeys.BAR_BLUR_NOISE_PERCENT)
        }
    }

    // MARK: - 读取 / Reading

    /**
     * 把存储里的值换成 [AppSettings]：没存过、存的值认不出或超出范围的项用默认值。
     * Turns the stored values into [AppSettings]; items never saved, unrecognized or out of range use the defaults.
     */
    private fun settingsFrom(preferences: Preferences): AppSettings {
        // 1. 地区、外观与配色：枚举按名字存，认不出的名字（以后改了枚举名）用默认值
        // 1. Region, appearance and colors: enums stored by name; unknown names (a later rename) use the default
        val defaults = AppSettings.DEFAULT

        val regionName = preferences[SettingsKeys.REGION]
        val region = AppRegion.entries.firstOrNull { entry -> entry.name == regionName } ?: defaults.region
        val themeName = preferences[SettingsKeys.THEME]
        val theme = AppTheme.entries.firstOrNull { entry -> entry.name == themeName } ?: defaults.theme
        val paletteName = preferences[SettingsKeys.PALETTE]
        val palette = ColorPalette.entries.firstOrNull { entry -> entry.name == paletteName } ?: defaults.palette
        val useDynamicColor = preferences[SettingsKeys.USE_DYNAMIC_COLOR] ?: defaults.useDynamicColor

        // 2. 毛玻璃：数值收进范围
        // 2. Frosted bars: numbers clamped into range
        val blurDefaults = defaults.barBlur
        val isBlurEnabled = preferences[SettingsKeys.BAR_BLUR_ENABLED] ?: blurDefaults.isEnabled
        val storedRadius = preferences[SettingsKeys.BAR_BLUR_RADIUS_DP] ?: blurDefaults.radiusDp
        val storedTint = preferences[SettingsKeys.BAR_BLUR_TINT_PERCENT] ?: blurDefaults.tintPercent
        val storedNoise = preferences[SettingsKeys.BAR_BLUR_NOISE_PERCENT] ?: blurDefaults.noisePercent
        val barBlur = BarBlurSettings(
            isEnabled = isBlurEnabled,
            radiusDp = storedRadius.coerceIn(BarBlurSettings.RADIUS_RANGE_DP),
            tintPercent = storedTint.coerceIn(BarBlurSettings.PERCENT_RANGE),
            noisePercent = storedNoise.coerceIn(BarBlurSettings.PERCENT_RANGE),
        )

        // 3. 合起来
        // 3. Put them together
        return AppSettings(
            theme = theme,
            useDynamicColor = useDynamicColor,
            palette = palette,
            barBlur = barBlur,
            region = region,
        )
    }
}

/**
 * 每项设置在 DataStore 里的 key。
 * Each setting's key in the DataStore.
 *
 * key 的名字写进了用户的文件，改名等于丢掉那一项。
 * The key names are written into the user's file, so renaming one loses that setting.
 */
private object SettingsKeys {
    val REGION = stringPreferencesKey("region")
    val THEME = stringPreferencesKey("theme")
    val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    val PALETTE = stringPreferencesKey("color_palette")
    val BAR_BLUR_ENABLED = booleanPreferencesKey("bar_blur_enabled")
    val BAR_BLUR_RADIUS_DP = intPreferencesKey("bar_blur_radius_dp")
    val BAR_BLUR_TINT_PERCENT = intPreferencesKey("bar_blur_tint_percent")
    val BAR_BLUR_NOISE_PERCENT = intPreferencesKey("bar_blur_noise_percent")
}
