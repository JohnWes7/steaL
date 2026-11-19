package io.github.johnwes7.steal.shared.services

import io.github.johnwes7.steal.shared.models.itad.ItadDealsRequest
import io.github.johnwes7.steal.shared.models.itad.ItadDealsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
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
