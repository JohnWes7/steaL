package io.github.johnwes7.steal.shared.services

import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.models.steam.SteamAppAvailabilityEntry
import io.github.johnwes7.steal.shared.models.steam.SteamAppAvailabilityResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
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
