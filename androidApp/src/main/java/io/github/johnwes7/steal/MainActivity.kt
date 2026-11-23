package io.github.johnwes7.steal

import android.app.UiModeManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.johnwes7.steal.platform.AppLanguageSetting
import io.github.johnwes7.steal.platform.LocalAppLanguageSetting
import io.github.johnwes7.steal.shared.models.settings.AppTheme
import io.github.johnwes7.steal.shared.viewmodels.RootViewModel
import io.github.johnwes7.steal.shared.viewmodels.RootViewModel.SettingsLoadState
import io.github.johnwes7.steal.views.MainTabView
import io.github.johnwes7.steal.views.components.shared.LocalBarBlurSettings
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 导航栏半透明遮罩的颜色（浅色 / 深色），同 `enableEdgeToEdge` 的默认值（activity 1.13.0 里是 internal，抄过来）。
 * The translucent navigation bar scrims (light / dark), the same as `enableEdgeToEdge`'s defaults (internal in
 * activity 1.13.0, so copied here).
 */
private val NAVIGATION_BAR_LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NAVIGATION_BAR_DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/**
 * App 唯一的 Activity：等设置读到后，按设置套上主题、显示主框架。
 * The app's only Activity: once the settings are read, applies the theme they choose and shows the main frame.
 *
 * 设置读到之前启动画面一直留着，所以用户看到的第一帧就是设置里的深浅和配色。
 * The splash screen stays up until the settings are read, so the first frame people see already has the chosen
 * appearance and colors.
 */
class MainActivity : ComponentActivity() {

    /**
     * App 最外层的状态（设置）；Activity 重建（转屏等）时还是同一个。
     * The app's outermost state (the settings); the same instance survives Activity recreation such as rotation.
     */
    private val rootViewModel: RootViewModel by viewModels {
        (application as SteaLApplication).appContainer.rootViewModelFactory
    }

    override fun attachBaseContext(newBase: Context) {
        // Android 12 及以下在这里换成 App 选的语言；13 以上系统已经换好，原样传下去。
        // On Android 12 and earlier this switches to the app's chosen language; on 13 and later the system already
        // has, so it passes through unchanged.
        val application = newBase.applicationContext as SteaLApplication
        val localizedContext = application.localeController.wrapBaseContext(base = newBase)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. 启动画面：要在 super.onCreate 之前装上；设置读到之前一直留着
        // 1. Splash screen: installed before super.onCreate, and kept until the settings are read
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            rootViewModel.settingsState.value is SettingsLoadState.Loading
        }

        // 2. 内容画到系统栏下面：先按系统深浅，设置读到后再按 App 自己的深浅改
        // 2. Draw behind the system bars: follows the system at first, then the app's own appearance once read
        enableEdgeToEdge()

        // 3. 设置读到后套主题、显示主框架
        // 3. Once the settings are read, apply the theme and show the main frame
        val appContainer = (application as SteaLApplication).appContainer
        val localeController = (application as SteaLApplication).localeController
        setContent {
            val settingsState by rootViewModel.settingsState.collectAsStateWithLifecycle()
            val loadedState = settingsState
            if (loadedState is SettingsLoadState.Loaded) {
                val settings = loadedState.settings
                val darkTheme = settings.theme.isDark()

                LaunchedEffect(darkTheme) {
                    applySystemBarStyle(darkTheme = darkTheme)
                }
                LaunchedEffect(settings.theme) {
                    applyNightMode(theme = settings.theme)
                }

                val appLanguage by localeController.language.collectAsStateWithLifecycle()
                val appLanguageSetting = AppLanguageSetting(
                    language = appLanguage,
                    setLanguage = { newLanguage ->
                        localeController.setLanguage(activity = this@MainActivity, language = newLanguage)
                    },
                )

                // 在根上放一次，下面都能读到，同 SwiftUI 在 RootView 上 .environment(...)：容器给页面取 ViewModel，
                // 毛玻璃设置给两条栏，语言给设置页。
                // Provided once at the root for everything below, like .environment(...) on SwiftUI's RootView: the
                // container lets screens get their view models, the blur settings reach both bars, and the language
                // reaches the settings screens.
                CompositionLocalProvider(
                    LocalAppContainer provides appContainer,
                    LocalBarBlurSettings provides settings.barBlur,
                    LocalAppLanguageSetting provides appLanguageSetting,
                ) {
                    SteaLTheme(
                        darkTheme = darkTheme,
                        palette = settings.palette,
                        dynamicColor = settings.useDynamicColor,
                    ) {
                        MainTabView()
                    }
                }
            }
        }
    }

    /**
     * 让状态栏、导航栏的图标深浅跟 App 的深浅走，不跟系统。
     * Makes the status and navigation bar icons follow the app's appearance rather than the system's.
     *
     * 样式同 `enableEdgeToEdge` 的默认值，只把「是不是深色」换成 [darkTheme]。
     * The styles match `enableEdgeToEdge`'s defaults, with only "is it dark" replaced by [darkTheme].
     */
    private fun applySystemBarStyle(darkTheme: Boolean) {
        val statusBarStyle = SystemBarStyle.auto(
            lightScrim = Color.TRANSPARENT,
            darkScrim = Color.TRANSPARENT,
            detectDarkMode = { _ -> darkTheme },
        )
        val navigationBarStyle = SystemBarStyle.auto(
            lightScrim = NAVIGATION_BAR_LIGHT_SCRIM,
            darkScrim = NAVIGATION_BAR_DARK_SCRIM,
            detectDarkMode = { _ -> darkTheme },
        )
        enableEdgeToEdge(statusBarStyle = statusBarStyle, navigationBarStyle = navigationBarStyle)
    }

    /**
     * 告诉系统这个 App 用哪种深浅（Android 12 以上），系统启动画面下次就用对的颜色；更早的系统什么也不做。
     * Tells the system which appearance this app uses (Android 12 and later), so the system splash screen uses the
     * right colors next time; does nothing on earlier versions.
     *
     * 系统会一直记着这个值，直到 App 再改、清数据或卸载。
     * The system keeps the value until the app changes it, its data is cleared, or it is uninstalled.
     */
    private fun applyNightMode(theme: AppTheme) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        val nightMode = when (theme) {
            AppTheme.System -> UiModeManager.MODE_NIGHT_AUTO
            AppTheme.Light -> UiModeManager.MODE_NIGHT_NO
            AppTheme.Dark -> UiModeManager.MODE_NIGHT_YES
        }
        val uiModeManager = getSystemService(UiModeManager::class.java)
        uiModeManager.setApplicationNightMode(nightMode)
    }
}

/**
 * 这个外观设置在当前是不是深色：跟随系统时看系统，其他两种固定。
 * Whether this appearance setting is dark right now: the system's setting when following it, fixed otherwise.
 */
@Composable
private fun AppTheme.isDark(): Boolean {
    return when (this) {
        AppTheme.System -> isSystemInDarkTheme()
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }
}
