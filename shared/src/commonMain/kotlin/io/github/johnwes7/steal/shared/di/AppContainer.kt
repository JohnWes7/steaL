package io.github.johnwes7.steal.shared.di

import io.github.johnwes7.steal.shared.services.ItadApi
import io.github.johnwes7.steal.shared.services.SteamApi
import io.github.johnwes7.steal.shared.services.SteamAppIDResolver
import io.github.johnwes7.steal.shared.services.createHttpClient
import io.ktor.client.HttpClient

/**
 * 全 App 共用的对象：建好所有 Service，并接好它们之间的依赖。
 * The objects the whole app shares: builds every service and wires up their dependencies.
 *
 * 写法照 Android 官方「手写依赖注入」和 Google 的 KMP 示例 Fruitties：一个普通类，由平台入口建一份、整个 App 共用
 * （Android 是 `SteaLApplication`）。放在 `shared` 是因为 ViewModel 也在 `shared`，以后各页面 ViewModel 的工厂也放这里，
 * iOS 接入时能用同一份。ViewModel 只从构造参数收到自己要的 Service，不知道这个容器。
 * Written after Android's "Manual dependency injection" guide and Google's KMP sample Fruitties: a plain class that
 * the platform entry point creates once for the whole app (`SteaLApplication` on Android). It lives in `shared`
 * because the view models do; each screen's view model factory will live here too, so iOS can use the same one
 * later. A view model only receives the services it needs through its constructor and never sees this container.
 *
 * [logsRequests] 决定 HTTP 客户端是否打印请求与响应（调试版传 `true`）；[itadApiKey] 是 ITAD 的 key，
 * 由平台从自己的配置读出来传进来（`shared` 不读平台配置）。
 * [logsRequests] decides whether the HTTP client logs requests and responses (debug builds pass `true`);
 * [itadApiKey] is the ITAD key, which the platform reads from its own configuration and passes in
 * (`shared` never reads platform configuration).
 */
class AppContainer(logsRequests: Boolean, itadApiKey: String) {

    /**
     * 所有 Service 共用的 HTTP 客户端。
     * The HTTP client every service shares.
     */
    private val httpClient: HttpClient = createHttpClient(logsRequests = logsRequests)

    /**
     * IsThereAnyDeal 接口：deals、giveaways、Steam appID 查询。
     * The IsThereAnyDeal API: deals, giveaways, and Steam appID lookups.
     */
    val itadApi: ItadApi = ItadApi(client = httpClient, apiKey = itadApiKey)

    /**
     * Steam 商店接口。
     * The Steam store API.
     */
    val steamApi: SteamApi = SteamApi(client = httpClient)

    /**
     * 把 ITAD 游戏 UUID 换成 Steam appID，首页与 Hot Deals 共用。
     * Turns ITAD game UUIDs into Steam appIDs; shared by the home page and Hot Deals.
     */
    val steamAppIDResolver: SteamAppIDResolver = SteamAppIDResolver(itadApi = itadApi, steamApi = steamApi)
}
