package io.github.johnwes7.steal.shared.services

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.models.settings.AppSettings
import io.github.johnwes7.steal.shared.models.settings.AppTheme
import io.github.johnwes7.steal.shared.models.settings.BarBlurSettings
import io.github.johnwes7.steal.shared.models.settings.ColorPalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [SettingsRepository] 的读写测试：没存过的项、存坏的值、恢复默认。
 * Read and write tests for [SettingsRepository]: items never saved, bad stored values, and restoring defaults.
 *
 * 预先放进存储的值用 key 的名字直接写：key 的名字在用户的文件里，测试顺便确认它们没有被改掉。
 * Values placed in storage up front are written by key name directly: those names live in users' files, so the
 * tests also guard against renaming them.
 */
class SettingsRepositoryTest {

    /**
     * 什么都没存过时读到默认设置；地区默认澳大利亚，同 iOS 读不到设置时的兜底。
     * Reads the default settings when nothing has been saved; the region defaults to Australia, matching iOS's
     * fallback when it has no settings.
     */
    @Test
    fun emptyStorageReadsDefaults() = runTest {
        val repository = SettingsRepository(dataStore = InMemoryPreferencesDataStore())

        val settings = repository.settings.first()

        assertEquals(AppSettings.DEFAULT, settings)
        assertEquals(AppRegion.Australia, settings.region)
    }

    /**
     * 每一项存了都能原样读回来。
     * Every item reads back exactly as saved.
     */
    @Test
    fun savedValuesReadBack() = runTest {
        val repository = SettingsRepository(dataStore = InMemoryPreferencesDataStore())

        repository.setTheme(AppTheme.Light)
        repository.setUseDynamicColor(true)
        repository.setPalette(ColorPalette.SimulatedC)
        repository.setBarBlurEnabled(false)
        repository.setBarBlurRadius(32)
        repository.setBarBlurTintPercent(55)
        repository.setBarBlurNoisePercent(0)
        repository.setRegion(AppRegion.Japan)
        val settings = repository.settings.first()

        val expected = AppSettings(
            theme = AppTheme.Light,
            useDynamicColor = true,
            palette = ColorPalette.SimulatedC,
            barBlur = BarBlurSettings(isEnabled = false, radiusDp = 32, tintPercent = 55, noisePercent = 0),
            region = AppRegion.Japan,
        )
        assertEquals(expected, settings)
    }

    /**
     * 认不出的枚举名用默认值，超出范围的数收到边界上。
     * Unknown enum names use the defaults, and out-of-range numbers are clamped to the bounds.
     */
    @Test
    fun badStoredValuesFallBack() = runTest {
        val stored = preferencesOf(
            stringPreferencesKey("theme") to "Purple",
            stringPreferencesKey("color_palette") to "RenamedPalette",
            booleanPreferencesKey("bar_blur_enabled") to false,
            intPreferencesKey("bar_blur_radius_dp") to 999,
            intPreferencesKey("bar_blur_tint_percent") to -5,
            intPreferencesKey("bar_blur_noise_percent") to 30,
            stringPreferencesKey("region") to "Atlantis",
        )
        val repository = SettingsRepository(dataStore = InMemoryPreferencesDataStore(initial = stored))

        val settings = repository.settings.first()

        assertEquals(AppSettings.DEFAULT.theme, settings.theme)
        assertEquals(AppSettings.DEFAULT.palette, settings.palette)
        assertEquals(AppSettings.DEFAULT.region, settings.region)
        val expectedBlur = BarBlurSettings(isEnabled = false, radiusDp = 40, tintPercent = 0, noisePercent = 30)
        assertEquals(expectedBlur, settings.barBlur)
    }

    /**
     * 写入超出范围的数时存的是边界值。
     * Writing an out-of-range number stores the bound instead.
     */
    @Test
    fun settersClampOutOfRangeValues() = runTest {
        val repository = SettingsRepository(dataStore = InMemoryPreferencesDataStore())

        repository.setBarBlurRadius(100)
        repository.setBarBlurTintPercent(150)
        repository.setBarBlurNoisePercent(-1)
        val barBlur = repository.settings.first().barBlur

        assertEquals(BarBlurSettings.RADIUS_RANGE_DP.last, barBlur.radiusDp)
        assertEquals(BarBlurSettings.PERCENT_RANGE.last, barBlur.tintPercent)
        assertEquals(BarBlurSettings.PERCENT_RANGE.first, barBlur.noisePercent)
    }

    /**
     * 恢复默认只动半径、叠色、噪点，不动毛玻璃开关和其他设置。
     * Restoring defaults only touches the radius, tint and noise, leaving the switch and other settings alone.
     */
    @Test
    fun resetRestoresBlurValuesOnly() = runTest {
        val repository = SettingsRepository(dataStore = InMemoryPreferencesDataStore())
        repository.setTheme(AppTheme.System)
        repository.setBarBlurEnabled(false)
        repository.setBarBlurRadius(5)
        repository.setBarBlurTintPercent(80)
        repository.setBarBlurNoisePercent(0)

        repository.resetBarBlurValues()
        val settings = repository.settings.first()

        assertEquals(AppTheme.System, settings.theme)
        val expectedBlur = BarBlurSettings.DEFAULT.copy(isEnabled = false)
        assertEquals(expectedBlur, settings.barBlur)
    }

    /**
     * 读文件失败时发默认设置，不让读的人一直等。
     * A failed read emits the default settings rather than leaving readers waiting.
     */
    @Test
    fun readFailureEmitsDefaults() = runTest {
        val dataStore = InMemoryPreferencesDataStore(failsReads = true)
        val repository = SettingsRepository(dataStore = dataStore)

        val settings = repository.settings.first()

        assertEquals(AppSettings.DEFAULT, settings)
    }
}
