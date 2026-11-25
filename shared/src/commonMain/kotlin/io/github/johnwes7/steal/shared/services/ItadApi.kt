package io.github.johnwes7.steal.shared.services

import io.github.johnwes7.steal.shared.models.itad.ItadDealsRequest
import io.github.johnwes7.steal.shared.models.itad.ItadDealsResponse
import io.github.johnwes7.steal.shared.models.itad.ItadGameInfoResponse
import io.github.johnwes7.steal.shared.models.itad.ItadGameLookupResponse
import io.github.johnwes7.steal.shared.models.itad.ItadGiveaway
import io.github.johnwes7.steal.shared.models.itad.ItadPriceGame
import io.github.johnwes7.steal.shared.models.itad.ItadShopIDsResponse
import io.github.johnwes7.steal.shared.models.itad.ItadShopRef
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * IsThereAnyDeal 的接口。
 * The IsThereAnyDeal API.
 *
 * [client] 是全 App 共用的那一个，[apiKey] 由创建它的地方传入：`shared` 不读平台的配置，iOS 以后也能用同一个类。
 * key 只放在 `ITAD-API-Key` 请求头里，不放进网址。
 * [client] is the one the whole app shares, and [apiKey] comes from whoever creates this: `shared` never reads
 * platform configuration, so iOS can use the same class later. The key goes only in the `ITAD-API-Key` header,
 * never in the URL.
 */
class ItadApi(
    private val client: HttpClient,
    private val apiKey: String,
) {

    /**
     * 取一页优惠（`POST /deals/v2`），请求体是 [request]。
     * Fetches one page of deals (`POST /deals/v2`) with [request] as the body.
     *
     * 请求成功但列表为空不是错误，返回空的 `list`。非 2xx 抛 [ItadApiException]；断网、超时、响应对不上 DTO 抛 Ktor 自己的异常。
     * An empty list from a successful request is not an error. A non-2xx status throws [ItadApiException];
     * lost connections, timeouts and bodies that do not match the DTOs throw Ktor's own exceptions.
     */
    suspend fun fetchDeals(request: ItadDealsRequest): ItadDealsResponse {
        val response = client.post("$BASE_URL/deals/v2") {
            header(API_KEY_HEADER, apiKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response)

        val deals: ItadDealsResponse = response.body()
        return deals
    }

    /**
     * 取限时免费领取列表（`GET /giveaways/v1`）。
     * Fetches the list of limited-time giveaways (`GET /giveaways/v1`).
     *
     * 默认值同 iOS：从第 [offset] 条起取 [limit] 条，[expired] 为 `false` 只要没过期的，[mature] 为 `false` 不要限制级，
     * [sort] 默认 `-publish` 按发布时间从新到旧。响应是裸数组，空列表表示现在没有，不是错误。
     * Defaults match iOS: [limit] entries from [offset], only unexpired ones when [expired] is `false`, no mature
     * content when [mature] is `false`, and [sort] defaults to `-publish`, newest first. The response is a bare
     * array; an empty list means there are none right now, not an error.
     */
    suspend fun fetchGiveaways(
        offset: Int = 0,
        limit: Int = 20,
        expired: Boolean = false,
        mature: Boolean = false,
        sort: String = "-publish",
    ): List<ItadGiveaway> {
        val response = client.get("$BASE_URL/giveaways/v1") {
            header(API_KEY_HEADER, apiKey)
            parameter("offset", offset)
            parameter("limit", limit)
            parameter("expired", expired)
            parameter("mature", mature)
            parameter("sort", sort)
        }
        ensureSuccess(response)

        val giveaways: List<ItadGiveaway> = response.body()
        return giveaways
    }

    /**
     * 把一批 ITAD 游戏 UUID 换成它们在 Steam 上的商店 ID（`POST /lookup/shop/61/id/v1`）。
     * Turns a batch of ITAD game UUIDs into their shop IDs on Steam (`POST /lookup/shop/61/id/v1`).
     *
     * 请求体是 [uuids] 组成的 JSON 数组。[uuids] 为空时直接返回空结果、不发请求：服务器会拒绝空数组。
     * 响应顶层是字典，先解码成字典再包进 [ItadShopIDsResponse]。
     * The body is a JSON array of [uuids]. An empty [uuids] returns an empty result without a request, because the
     * server rejects an empty array. The response root is a dictionary, decoded first and then wrapped in
     * [ItadShopIDsResponse].
     */
    suspend fun lookupShopIDs(uuids: List<String>): ItadShopIDsResponse {
        if (uuids.isEmpty()) {
            return ItadShopIDsResponse(entries = emptyMap())
        }

        val response = client.post("$BASE_URL/lookup/shop/${ItadShopRef.STEAM_ID}/id/v1") {
            header(API_KEY_HEADER, apiKey)
            contentType(ContentType.Application.Json)
            setBody(uuids)
        }
        ensureSuccess(response)

        val entries: Map<String, List<String>?> = response.body()
        val shopIDs = ItadShopIDsResponse(entries = entries)
        return shopIDs
    }

    /**
     * 按 Steam [appID] 找对应的 ITAD 游戏（`GET /games/lookup/v1`）。
     * Finds the ITAD game for a Steam [appID] (`GET /games/lookup/v1`).
     *
     * `appid` 传裸数字，不加 `app/` 前缀。ITAD 没收录时 `found` 是 `false`，这不是错误。
     * 非 2xx 抛 [ItadApiException]；断网、超时、解码失败抛 Ktor 自己的异常。
     * `appid` takes the bare number without the `app/` prefix. When ITAD does not list the game, `found` is `false`,
     * which is not an error. A non-2xx status throws [ItadApiException]; lost connections, timeouts and decoding
     * failures throw Ktor's own exceptions.
     */
    suspend fun lookupGame(appID: Int): ItadGameLookupResponse {
        val response = client.get("$BASE_URL/games/lookup/v1") {
            header(API_KEY_HEADER, apiKey)
            parameter("appid", appID)
        }
        ensureSuccess(response)

        val lookup: ItadGameLookupResponse = response.body()
        return lookup
    }

    /**
     * 取一个游戏的评分、标签、热度和厂商（`GET /games/info/v2`）。
     * Fetches one game's reviews, tags, popularity and credits (`GET /games/info/v2`).
     *
     * [uuid] 是 ITAD 游戏 UUID，传 Steam appID 会 400。非 2xx 抛 [ItadApiException]；断网、超时、解码失败抛 Ktor 自己的异常。
     * [uuid] is the ITAD game UUID; a Steam appID gets a 400. A non-2xx status throws [ItadApiException]; lost
     * connections, timeouts and decoding failures throw Ktor's own exceptions.
     */
    suspend fun fetchGameInfo(uuid: String): ItadGameInfoResponse {
        val response = client.get("$BASE_URL/games/info/v2") {
            header(API_KEY_HEADER, apiKey)
            parameter("id", uuid)
        }
        ensureSuccess(response)

        val info: ItadGameInfoResponse = response.body()
        return info
    }

    /**
     * 取一批游戏在 [region] 的各家报价和历史低价（`POST /games/prices/v3?deals=false`），请求体是 [uuids] 组成的 JSON 数组。
     * Fetches the offers and historical lows of a batch of games in [region]
     * (`POST /games/prices/v3?deals=false`), with a JSON array of [uuids] as the body.
     *
     * `deals=false` 才会带上没打折的价格和历史低价。响应是稀疏的：没有价格数据的游戏整条不返回，要按 UUID 取
     * （`List<ItadPriceGame>.game(uuid)`），不能按下标对应。[uuids] 为空时直接返回空列表、不发请求：服务器会拒绝空数组；一次最多 200 个。
     * 非 2xx 抛 [ItadApiException]；断网、超时、解码失败抛 Ktor 自己的异常。
     * `deals=false` is what includes prices that are not on sale and the historical lows. The response is sparse: a
     * game without price data is left out entirely, so entries must be found by UUID (`List<ItadPriceGame>.game(uuid)`), never by index. An
     * empty [uuids] returns an empty list without a request, because the server rejects an empty array; at most 200
     * per call. A non-2xx status throws [ItadApiException]; lost connections, timeouts and decoding failures throw
     * Ktor's own exceptions.
     */
    suspend fun fetchPrices(uuids: List<String>, region: AppRegion): List<ItadPriceGame> {
        if (uuids.isEmpty()) {
            return emptyList()
        }

        val response = client.post("$BASE_URL/games/prices/v3") {
            header(API_KEY_HEADER, apiKey)
            parameter("country", region.apiRegionCode)
            parameter("deals", false)
            contentType(ContentType.Application.Json)
            setBody(uuids)
        }
        ensureSuccess(response)

        val prices: List<ItadPriceGame> = response.body()
        return prices
    }

    /**
     * 状态码不是 2xx 就抛 [ItadApiException]，带上服务器返回的原文。
     * Throws [ItadApiException] with the server's raw reply when the status is not 2xx.
     */
    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status.isSuccess()) {
            return
        }
        val body = response.bodyAsText()
        throw ItadApiException(statusCode = response.status.value, body = body)
    }

    companion object {

        /**
         * ITAD 接口的根地址。
         * The root address of the ITAD API.
         */
        const val BASE_URL: String = "https://api.isthereanydeal.com"

        /**
         * 放 key 的请求头名。
         * The name of the header that carries the key.
         */
        const val API_KEY_HEADER: String = "ITAD-API-Key"
    }
}

/**
 * ITAD 请求失败：服务器返回了非 2xx 的状态码 [statusCode]，[body] 是服务器返回的原文。
 * A failed ITAD request: the server answered with the non-2xx status [statusCode], and [body] is its raw reply.
 *
 * 只到 ViewModel，由 `LoadError.from` 转成 `LoadError.Server`；原文只进日志，不给用户看。
 * 断网、超时、解码失败不走这里，由 Ktor 抛它们自己的异常。
 * It only reaches view models, where `LoadError.from` turns it into `LoadError.Server`; the raw reply goes to the
 * logs, never to users. Lost connections, timeouts and decoding failures do not come through here; Ktor throws
 * its own exceptions for those.
 */
class ItadApiException(
    val statusCode: Int,
    val body: String,
) : Exception("ITAD request failed with HTTP $statusCode: ${body.take(MAX_BODY_IN_MESSAGE)}")

/**
 * 异常消息里最多带多少字符的响应原文，避免日志被长响应刷屏。
 * The most characters of the raw reply kept in the exception message, so long replies do not flood the logs.
 */
private const val MAX_BODY_IN_MESSAGE = 500
