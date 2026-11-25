package io.github.johnwes7.steal.shared.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.johnwes7.steal.shared.services.ItadApi
import io.github.johnwes7.steal.shared.services.SaleEventsApi
import io.github.johnwes7.steal.shared.services.SettingsRepository
import io.github.johnwes7.steal.shared.services.SteamApi
import io.github.johnwes7.steal.shared.services.SteamAppIDResolver
import io.github.johnwes7.steal.shared.services.createHttpClient
import io.github.johnwes7.steal.shared.viewmodels.GamesViewModel
import io.github.johnwes7.steal.shared.viewmodels.RegionSettingsViewModel
import io.github.johnwes7.steal.shared.viewmodels.RootViewModel
import io.github.johnwes7.steal.shared.viewmodels.SettingsViewModel
import io.github.johnwes7.steal.shared.viewmodels.ThemeSettingsViewModel
import io.ktor.client.HttpClient

/**
 * 建好全 App 共用的 Service 与各页面 ViewModel 的工厂。
 * Creates the app-wide services and each screen's view model factory.
 *
 * 平台入口只建一份（Android 在 `SteaLApplication`）：每建一份就多一个 HTTP 客户端和一套 Service。
 * The platform entry point creates exactly one (in `SteaLApplication` on Android): every instance brings its own
 * HTTP client and set of services.
 *
 * [logsRequests] 决定是否打印 HTTP 请求与响应，调试版传 `true`；[httpCacheDirectory] 是 HTTP 缓存的目录；
 * [itadApiKey] 和自建后端的地址 [backendBaseUrl]（到 `/v1` 为止）由平台从自己的配置读出来传入；
 * [settingsDataStore] 由平台建好传入（文件放在哪里各平台不同），容器只用这一份。
 * [logsRequests] decides whether HTTP requests and responses are logged (debug builds pass `true`);
 * [httpCacheDirectory] is where the HTTP cache lives; the platform reads [itadApiKey] and our backend's address
 * [backendBaseUrl] (up to `/v1`) from its own configuration; the platform creates [settingsDataStore] (where the
 * file lives differs per platform), and the container uses only that one.
 */
class AppContainer(
    logsRequests: Boolean,
    httpCacheDirectory: String,
    itadApiKey: String,
    backendBaseUrl: String,
    settingsDataStore: DataStore<Preferences>,
) {

    /**
     * 所有 Service 共用的 HTTP 客户端，连接和缓存可以复用。
     * The HTTP client every service shares, so connections and the cache are reused.
     */
    private val httpClient: HttpClient = createHttpClient(
        logsRequests = logsRequests,
        cacheDirectory = httpCacheDirectory,
    )

    /**
     * IsThereAnyDeal 接口：deals、giveaways、appID 查询。
     * The IsThereAnyDeal API: deals, giveaways and appID lookups.
     */
    val itadApi: ItadApi = ItadApi(client = httpClient, apiKey = itadApiKey)

    /**
     * Steam 商店接口。
     * The Steam store API.
     */
    val steamApi: SteamApi = SteamApi(client = httpClient)

    /**
     * 把 ITAD 游戏 UUID 换成 Steam appID。
     * Turns ITAD game UUIDs into Steam appIDs.
     */
    val steamAppIDResolver: SteamAppIDResolver = SteamAppIDResolver(itadApi = itadApi, steamApi = steamApi)

    /**
     * 自建后端的促销活动接口。
     * Our backend's sale events API.
     */
    val saleEventsApi: SaleEventsApi = SaleEventsApi(client = httpClient, baseUrl = backendBaseUrl)

    /**
     * 用户设置的读写。
     * Reads and writes the user's settings.
     */
    val settingsRepository: SettingsRepository = SettingsRepository(dataStore = settingsDataStore)

    /**
     * App 最外层 [RootViewModel] 的工厂，交给平台入口。
     * The factory for the app's outermost [RootViewModel], used by the platform entry point.
     */
    val rootViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            RootViewModel(settingsRepository = settingsRepository)
        }
    }

    /**
     * 首页 [GamesViewModel] 的工厂，交给页面的 `viewModel(factory = …)`。
     * The factory for the home page's [GamesViewModel], passed to the page's `viewModel(factory = …)`.
     */
    val gamesViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            GamesViewModel(
                itadApi = itadApi,
                steamAppIDResolver = steamAppIDResolver,
                settingsRepository = settingsRepository,
            )
        }
    }

    /**
     * Settings tab 根页面 [SettingsViewModel] 的工厂。
     * The factory for the Settings tab root screen's [SettingsViewModel].
     */
    val settingsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            SettingsViewModel(settingsRepository = settingsRepository)
        }
    }

    /**
     * 「地区与货币」单选页 [RegionSettingsViewModel] 的工厂。
     * The factory for the "Region & currency" screen's [RegionSettingsViewModel].
     */
    val regionSettingsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            RegionSettingsViewModel(settingsRepository = settingsRepository)
        }
    }

    /**
     * 「主题与色彩」设置页 [ThemeSettingsViewModel] 的工厂。
     * The factory for the "Theme & color" settings screen's [ThemeSettingsViewModel].
     */
    val themeSettingsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ThemeSettingsViewModel(settingsRepository = settingsRepository)
        }
    }
}
