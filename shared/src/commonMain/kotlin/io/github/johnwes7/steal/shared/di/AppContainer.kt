package io.github.johnwes7.steal.shared.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.johnwes7.steal.shared.services.ItadApi
import io.github.johnwes7.steal.shared.services.SteamApi
import io.github.johnwes7.steal.shared.services.SteamAppIDResolver
import io.github.johnwes7.steal.shared.services.createHttpClient
import io.github.johnwes7.steal.shared.viewmodels.GamesViewModel
import io.ktor.client.HttpClient

/**
 * 建好全 App 共用的 Service 与各页面 ViewModel 的工厂。
 * Creates the app-wide services and each screen's view model factory.
 *
 * 平台入口只建一份（Android 在 `SteaLApplication`）：每建一份就多一个 HTTP 客户端和一套 Service。
 * The platform entry point creates exactly one (in `SteaLApplication` on Android): every instance brings its own
 * HTTP client and set of services.
 *
 * [logsRequests] 决定是否打印 HTTP 请求与响应，调试版传 `true`；[itadApiKey] 由平台从自己的配置读出来传入。
 * [logsRequests] decides whether HTTP requests and responses are logged (debug builds pass `true`); the platform
 * reads [itadApiKey] from its own configuration.
 */
class AppContainer(logsRequests: Boolean, itadApiKey: String) {

    /**
     * 所有 Service 共用的 HTTP 客户端，连接可以复用。
     * The HTTP client every service shares, so connections are reused.
     */
    private val httpClient: HttpClient = createHttpClient(logsRequests = logsRequests)

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
     * 首页 [GamesViewModel] 的工厂，交给页面的 `viewModel(factory = …)`。
     * The factory for the home page's [GamesViewModel], passed to the page's `viewModel(factory = …)`.
     */
    val gamesViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            GamesViewModel(itadApi = itadApi, steamAppIDResolver = steamAppIDResolver)
        }
    }
}
