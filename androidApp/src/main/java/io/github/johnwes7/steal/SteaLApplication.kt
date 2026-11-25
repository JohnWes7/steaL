package io.github.johnwes7.steal

import android.app.Application
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.johnwes7.steal.config.Config
import io.github.johnwes7.steal.platform.AppLocaleController
import io.github.johnwes7.steal.shared.di.AppContainer
import io.github.johnwes7.steal.shared.services.createSettingsDataStore
import java.io.File

/**
 * 自定义的 `Application`：持有全 App 唯一的 [AppContainer]。
 * The custom `Application`; it holds the app's single [AppContainer].
 */
class SteaLApplication : Application() {

    /**
     * 全 App 共用的 Service 与 ViewModel 工厂；调试版打印 HTTP 请求与响应。
     * The services and view model factories the whole app shares; debug builds log HTTP requests and responses.
     */
    lateinit var appContainer: AppContainer
        private set

    /**
     * App 的界面语言，全 App 一份。
     * The app's UI language, one for the whole app.
     */
    lateinit var localeController: AppLocaleController
        private set

    override fun onCreate() {
        super.onCreate()
        
        // init lacale controller
        localeController = AppLocaleController(context = this)
        
        // init AppContainer 
        // HTTP 缓存放 cacheDir：空间不够时系统会清，缓存丢了只是多发一次请求。
        // The HTTP cache lives in cacheDir: the system clears it when space runs low, which only costs a request.
        val httpCacheDirectory = File(cacheDir, HTTP_CACHE_FOLDER)
        appContainer = AppContainer(
            logsRequests = BuildConfig.DEBUG,
            httpCacheDirectory = httpCacheDirectory.path,
            itadApiKey = Config.ITAD_API_KEY,
            backendBaseUrl = Config.BACKEND_BASE_URL,
            settingsDataStore = createSettingsDataStore(context = this),
        )
    }
}

/**
 * `cacheDir` 下放 HTTP 缓存的文件夹名。
 * The name of the folder under `cacheDir` that holds the HTTP cache.
 */
internal const val HTTP_CACHE_FOLDER = "http"

/**
 * 让 Compose 界面读到全 App 的 [AppContainer]，在 [MainActivity] 根上提供一次。
 * Lets Compose screens read the app's [AppContainer]; provided once at the root in [MainActivity].
 */
val LocalAppContainer: ProvidableCompositionLocal<AppContainer> =
    staticCompositionLocalOf { error("No AppContainer provided") }
