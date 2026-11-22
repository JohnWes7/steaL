package io.github.johnwes7.steal.shared.services

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.johnwes7.steal.shared.config.AppLog

/**
 * 设置文件的名字；Preferences DataStore 要求扩展名是 `preferences_pb`。
 * The settings file's name; Preferences DataStore requires the `preferences_pb` extension.
 */
private const val SETTINGS_FILE_NAME = "settings.preferences_pb"

/**
 * 建存设置的 DataStore，文件放在 App 的私有目录里，交给 [SettingsRepository]。
 * Creates the DataStore that holds the settings, in the app's private files directory, for [SettingsRepository].
 *
 * 全 App 只能建一份（`SteaLApplication` 建好传给 `AppContainer`）。文件坏了读不出来时换成空的，所有设置回到默认值。
 * The app must create only one (`SteaLApplication` creates it and passes it to `AppContainer`). A file too damaged
 * to read is replaced with an empty one, putting every setting back to its default.
 */
fun createSettingsDataStore(context: Context): DataStore<Preferences> {
    val corruptionHandler = ReplaceFileCorruptionHandler { exception ->
        AppLog.settings.e(exception) { "Settings file is corrupted; replacing it with the defaults" }
        emptyPreferences()
    }
    return PreferenceDataStoreFactory.create(
        corruptionHandler = corruptionHandler,
        produceFile = { context.filesDir.resolve(SETTINGS_FILE_NAME) },
    )
}
