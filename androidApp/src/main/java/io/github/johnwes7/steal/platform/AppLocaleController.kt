package io.github.johnwes7.steal.platform

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import io.github.johnwes7.steal.shared.models.settings.AppLanguage
import java.util.Locale

/**
 * 存放 App 自己选的语言的 SharedPreferences 文件名。
 * The name of the SharedPreferences file that holds the app's own language choice.
 */
private const val PREFERENCES_NAME = "app_locale"

/**
 * 选中语言的标签；没有这一项表示跟随系统。
 * The chosen language's tag; a missing entry means following the system.
 */
private const val KEY_LANGUAGE_TAG = "language_tag"

/**
 * 是否已经把旧系统上存的语言交给 Android 13+ 的系统。
 * Whether the language saved on an older Android has been handed to the Android 13+ system.
 */
private const val KEY_MIGRATED_TO_SYSTEM = "migrated_to_system"

/**
 * App 的界面语言：读当前选的语言、改语言，并在旧系统上让 Activity 用上它。
 * The app's UI language: reads the current choice, changes it, and applies it to activities on older Android.
 *
 * Android 13 以上由系统保存（和系统设置里「应用 → 语言」是同一个值）；12 及以下系统没有这一项，存在 App 自己的
 * SharedPreferences 里，并要求 Activity 在 `attachBaseContext` 里调 [wrapBaseContext]。全 App 只建一份。
 * On Android 13 and later the system stores it (the same value as "Apps > Language" in system settings); Android 12
 * and earlier have no such setting, so it lives in the app's own SharedPreferences and activities must call
 * [wrapBaseContext] from `attachBaseContext`. Create only one for the whole app.
 */
class AppLocaleController(context: Context) {

    private val appContext: Context = context.applicationContext

    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    init {
        migrateToSystemIfNeeded()
    }

    /**
     * 当前单独给 App 选的语言；`null` 表示跟随系统。
     * The language currently chosen for the app alone; `null` means following the system.
     *
     * 选的标签不在 [AppLanguage] 里（比如用 adb 设的）也返回 `null`。
     * Also `null` when the chosen tag is not one of [AppLanguage], for example one set through adb.
     */
    val language: AppLanguage?
        get() {
            val languageTag = currentLanguageTag()
            if (languageTag == null) {
                return null
            }
            return AppLanguage.fromLanguageTag(languageTag)
        }

    /**
     * 把 App 的语言改成 [language]（`null` = 跟随系统），界面会重建成新语言；和当前一样就什么也不做。
     * Changes the app's language to [language] (`null` follows the system) and rebuilds the UI in it; does nothing
     * when it is already the current one.
     *
     * Android 13 以上由系统重建 Activity；12 及以下保存后调 [activity] 的 `recreate()`。
     * On Android 13 and later the system recreates the Activity; on 12 and earlier this saves and then calls
     * `recreate()` on [activity].
     */
    @MainThread
    fun setLanguage(activity: Activity, language: AppLanguage?) {
        val targetLanguageTag = language?.languageTag
        if (targetLanguageTag == currentLanguageTag()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = if (language == null) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(language.languageTag)
            }
            localeManager().applicationLocales = locales
        } else {
            // edit { } 默认用 apply：先改内存里的值，recreate 后 wrapBaseContext 马上读得到，不用等写盘。
            // edit { } uses apply by default: the in-memory value changes first, so wrapBaseContext sees it right
            // after recreate without waiting for the disk write.
            preferences.edit {
                if (language == null) {
                    remove(KEY_LANGUAGE_TAG)
                } else {
                    putString(KEY_LANGUAGE_TAG, language.languageTag)
                }
            }
            activity.recreate()
        }
    }

    /**
     * 返回换成 App 语言的 [base]；Android 13 以上或跟随系统时原样返回。
     * Returns [base] switched to the app's language; returns it unchanged on Android 13 and later or when following
     * the system.
     *
     * 在 Activity 的 `attachBaseContext` 里调用。也会改进程的默认 `Locale`，让 `Locale.getDefault()` 和 13 以上一样
     * 跟着 App 的语言。
     * Call it from an Activity's `attachBaseContext`. It also changes the process's default `Locale`, so
     * `Locale.getDefault()` follows the app's language as it does on 13 and later.
     */
    fun wrapBaseContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        val languageTag = preferences.getString(KEY_LANGUAGE_TAG, null)
        if (languageTag == null) {
            return base
        }

        // 1. 语言列表：选的语言在前，后面接系统的语言，选的语言缺资源时还能退到系统语言
        // 1. Locale list: the chosen language first, then the system's, so missing resources can still fall back to them
        val chosenLocale = Locale.forLanguageTag(languageTag)
        val systemLocales = Resources.getSystem().configuration.locales
        val localeList = mutableListOf(chosenLocale)
        for (index in 0 until systemLocales.size()) {
            localeList.add(systemLocales.get(index))
        }
        val locales = LocaleList(*localeList.toTypedArray())

        // 2. 进程默认语言：同 13 以上
        // 2. Process default locale: same as on 13 and later
        LocaleList.setDefault(locales)

        // 3. 只覆盖语言：新建的 Configuration 其他字段是「未定义」，字体缩放、密度等照常跟系统
        // 3. Override only the locales: other fields of a new Configuration are undefined, so font scale, density and
        //    the rest still follow the system
        val overrideConfiguration = Configuration()
        overrideConfiguration.setLocales(locales)
        return base.createConfigurationContext(overrideConfiguration)
    }

    /**
     * 当前选的第一个语言标签；跟随系统时是 `null`。
     * The first chosen language tag; `null` when following the system.
     */
    private fun currentLanguageTag(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val applicationLocales = localeManager().applicationLocales
            if (applicationLocales.isEmpty) {
                return null
            }
            return applicationLocales.get(0).toLanguageTag()
        }
        return preferences.getString(KEY_LANGUAGE_TAG, null)
    }

    /**
     * 手机从 12 及以下升级到 13 以上后，把旧系统上存的语言交给系统，只做一次。
     * After the phone upgrades from 12 or earlier to 13 or later, hands the language saved on the old system to the
     * system, once.
     */
    private fun migrateToSystemIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        // 1. 只做一次
        // 1. Only once
        val hasMigrated = preferences.getBoolean(KEY_MIGRATED_TO_SYSTEM, false)
        if (hasMigrated) {
            return
        }

        // 2. 系统里还没有选过、旧文件里有，才交给系统：用户在系统设置里选过的优先
        // 2. Hand it over only when the system has no choice yet and the old file has one: a choice made in system
        //    settings wins
        val savedLanguageTag = preferences.getString(KEY_LANGUAGE_TAG, null)
        val manager = localeManager()
        if (manager.applicationLocales.isEmpty && savedLanguageTag != null) {
            manager.applicationLocales = LocaleList.forLanguageTags(savedLanguageTag)
        }

        // 3. 记下已迁移，删掉旧值：之后只认系统
        // 3. Record the migration and drop the old value: only the system counts from now on
        preferences.edit {
            putBoolean(KEY_MIGRATED_TO_SYSTEM, true)
            remove(KEY_LANGUAGE_TAG)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun localeManager(): LocaleManager {
        return appContext.getSystemService(LocaleManager::class.java)
    }
}
