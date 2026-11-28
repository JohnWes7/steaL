package io.github.johnwes7.steal.shared.services

import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.models.steam.SteamAppAvailabilityEntry
import io.github.johnwes7.steal.shared.models.steam.SteamAppAvailabilityResponse
import io.github.johnwes7.steal.shared.models.steam.SteamAppDetails
import io.github.johnwes7.steal.shared.models.steam.SteamAppDetailsEntry
import io.github.johnwes7.steal.shared.models.steam.SteamAppDetailsResponse
import io.github.johnwes7.steal.shared.models.steam.SteamSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

/**
 * Steam 商店的接口。
 * The Steam store API.
 *
 * 不需要 key，只收全 App 共用的 [client]；区域决定返回的货币和是否锁区。
 * It needs no key, only the app-wide [client]; the region decides the currency returned and region locks.
 */
class SteamApi(
    private val client: HttpClient,
) {

    /**
     * 一次请求批量查 [appIDs] 在 [region] 存不存在、有没有价格，用来在 ITAD 给的多个候选里挑对的那个。
     * Checks in one request whether [appIDs] exist in [region] and have a price, used to pick the right one among
     * ITAD's candidates.
     *
     * `appids` 传多个时必须带 `filters=price_overview`，否则 Steam 返回 400。[appIDs] 为空时直接返回空结果、不发请求。
     * 响应顶层是字典，先解码成字典再包进 [SteamAppAvailabilityResponse]。非 2xx 抛 [SteamApiException]。
     * Several `appids` only work together with `filters=price_overview`; otherwise Steam answers 400. An empty
     * [appIDs] returns an empty result without a request. The response root is a dictionary, decoded first and
     * then wrapped in [SteamAppAvailabilityResponse]. A non-2xx status throws [SteamApiException].
     */
    suspend fun fetchAvailability(appIDs: List<Int>, region: AppRegion): SteamAppAvailabilityResponse {
        if (appIDs.isEmpty()) {
            return SteamAppAvailabilityResponse(entries = emptyMap())
        }

        val joinedAppIDs = appIDs.joinToString(separator = ",")
        val response = client.get("$BASE_URL/api/appdetails") {
            parameter("appids", joinedAppIDs)
            parameter("cc", region.apiRegionCode)
            parameter("filters", "price_overview")
        }
        ensureSuccess(response)

        val entries: Map<String, SteamAppAvailabilityEntry> = response.body()
        val availability = SteamAppAvailabilityResponse(entries = entries)
        return availability
    }

    /**
     * 取 [appID] 的商店详情（`GET /api/appdetails`，不带 `filters`）；Steam 上没有这个 app 时返回 `null`，这不是错误。
     * Fetches [appID]'s store details (`GET /api/appdetails` without `filters`); returns `null` when Steam has no
     * such app, which is not an error.
     *
     * [region] 决定价格的货币和是否锁区；[steamLanguage] 是 `l=` 的语言代码（如 `schinese`），决定名字、介绍、视频名的语言。
     * 不能加 `filters=basic`：它会裁掉视频、截图和类型。响应带 `max-age=3600`，一小时内同一游戏、地区、语言直接读 HTTP 缓存。
     * 非 2xx 抛 [SteamApiException]；断网、超时、解码失败抛 Ktor 自己的异常。
     * [region] decides the price currency and region locks; [steamLanguage] is the `l=` language code (such as
     * `schinese`) for the name, descriptions and movie names. `filters=basic` must not be added, as it strips the
     * movies, screenshots and genres. The response carries `max-age=3600`, so the same game, region and language is
     * read from the HTTP cache for an hour. A non-2xx status throws [SteamApiException]; lost connections, timeouts
     * and decoding failures throw Ktor's own exceptions.
     *
     * [bypassesCache] 为 `true` 时（下拉刷新）带 `Cache-Control: no-cache`，一定真的请求，新结果照常存进缓存。
     * When [bypassesCache] is `true` (pull to refresh), the request carries `Cache-Control: no-cache`, so it always
     * goes to the network, and the new result is cached as usual.
     */
    suspend fun fetchAppDetails(
        appID: Int,
        region: AppRegion,
        steamLanguage: String,
        bypassesCache: Boolean = false,
    ): SteamAppDetails? {
        val response = client.get("$BASE_URL/api/appdetails") {
            parameter("appids", appID)
            parameter("cc", region.apiRegionCode)
            parameter("l", steamLanguage)
            if (bypassesCache) {
                header(HttpHeaders.CacheControl, NO_CACHE)
            }
        }
        ensureSuccess(response)

        // 顶层是以 appID 字符串为 key 的字典，只查一个 app 也一样。
        // The root is a dictionary keyed by appID strings, even when only one app is asked for.
        val entries: Map<String, SteamAppDetailsEntry> = response.body()
        val appDetailsResponse = SteamAppDetailsResponse(entries = entries)
        val details = appDetailsResponse.details(appID)
        return details
    }

    /**
     * 按名字搜 Steam 商店（`GET /api/storesearch`），返回列表用的轻量结果，最多 10 条。
     * Searches the Steam store by name (`GET /api/storesearch`), returning light list results, at most 10.
     *
     * [query] 去掉首尾空白后为空时直接返回空结果、不发请求：空的 `term` 会返回 Steam 推荐的热门游戏，不是空列表。
     * [region] 决定价格的货币；[steamLanguage] 是 `l=` 的语言代码（如 `schinese`），决定名字的语言。
     * 响应不带缓存头，每次都会发请求。非 2xx 抛 [SteamApiException]；断网、超时、解码失败抛 Ktor 自己的异常。
     * A [query] that is empty after trimming returns an empty result without a request: an empty `term` returns
     * Steam's popular games, not an empty list. [region] decides the price currency; [steamLanguage] is the `l=`
     * language code (such as `schinese`) for the names. The response has no cache headers, so every call sends a
     * request. A non-2xx status throws [SteamApiException]; lost connections, timeouts and decoding failures throw
     * Ktor's own exceptions.
     */
    suspend fun fetchSearchResults(query: String, region: AppRegion, steamLanguage: String): SteamSearchResponse {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return SteamSearchResponse.Empty
        }

        val response = client.get("$BASE_URL/api/storesearch") {
            parameter("term", trimmedQuery)
            parameter("cc", region.apiRegionCode)
            parameter("l", steamLanguage)
        }
        ensureSuccess(response)

        val searchResponse: SteamSearchResponse = response.body()
        return searchResponse
    }

    /**
     * 状态码不是 2xx 就抛 [SteamApiException]，带上服务器返回的原文。
     * Throws [SteamApiException] with the server's raw reply when the status is not 2xx.
     */
    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status.isSuccess()) {
            return
        }
        val body = response.bodyAsText()
        throw SteamApiException(statusCode = response.status.value, body = body)
    }

    companion object {

        /**
         * Steam 商店的根地址。
         * The root address of the Steam store.
         */
        const val BASE_URL: String = "https://store.steampowered.com"

        /**
         * 要求绕过 HTTP 缓存的 `Cache-Control` 请求头值。
         * The `Cache-Control` request header value that asks to bypass the HTTP cache.
         */
        private const val NO_CACHE: String = "no-cache"
    }
}

/**
 * Steam 请求失败：服务器返回了非 2xx 的状态码 [statusCode]，[body] 是服务器返回的原文。
 * A failed Steam request: the server answered with the non-2xx status [statusCode], and [body] is its raw reply.
 *
 * 只到 ViewModel，由 `LoadError.from` 转成 `LoadError.Server`。查不到游戏不是失败：Steam 照样回 200，
 * 只是那一项 `success` 为 `false`。
 * It only reaches view models, where `LoadError.from` turns it into `LoadError.Server`. A missing game is not a
 * failure: Steam still answers 200, with `success` set to `false` for that entry.
 */
class SteamApiException(
    val statusCode: Int,
    val body: String,
) : Exception("Steam request failed with HTTP $statusCode: ${body.take(MAX_BODY_IN_MESSAGE)}")

/**
 * 异常消息里最多带多少字符的响应原文，避免日志被长响应刷屏。
 * The most characters of the raw reply kept in the exception message, so long replies do not flood the logs.
 */
private const val MAX_BODY_IN_MESSAGE = 500
