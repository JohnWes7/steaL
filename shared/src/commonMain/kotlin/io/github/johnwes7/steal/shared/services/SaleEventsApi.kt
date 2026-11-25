package io.github.johnwes7.steal.shared.services

import io.github.johnwes7.steal.shared.models.saleevents.SaleEventList
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * 自建后端的促销活动接口，契约见 `api/openapi.yaml`。
 * Our backend's sale events endpoint; the contract is `api/openapi.yaml`.
 *
 * 不需要 key、不需要登录。[client] 是全 App 共用的那一个；[baseUrl] 是到 `/v1` 为止的地址，由创建它的地方传入：
 * `shared` 不读平台的配置。
 * No key and no sign-in. [client] is the one the whole app shares; [baseUrl] is the address up to `/v1`, passed in
 * by whoever creates this: `shared` never reads platform configuration.
 */
class SaleEventsApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {

    /**
     * 取所有商店还没结束的活动（`GET /sale-events`）。
     * Fetches every store's events that have not ended (`GET /sale-events`).
     *
     * 后端返回 `Cache-Control: public, max-age=3600`，一小时内由 client 的 HTTP 缓存直接给结果、不发请求。
     * 列表为空不是错误。非 2xx（429 限流、503 暂时不可用等）抛 [SaleEventsApiException]；断网、超时、响应对不上 DTO
     * 抛 Ktor 自己的异常。
     * The backend sends `Cache-Control: public, max-age=3600`, so within an hour the client's HTTP cache answers
     * without a request. An empty list is not an error. A non-2xx status (429 rate limited, 503 temporarily
     * unavailable, …) throws [SaleEventsApiException]; lost connections, timeouts and bodies that do not match the
     * DTOs throw Ktor's own exceptions.
     */
    suspend fun fetchSaleEvents(): SaleEventList {
        val response = client.get("$baseUrl/sale-events")
        ensureSuccess(response)

        val eventList: SaleEventList = response.body()
        return eventList
    }

    /**
     * 状态码不是 2xx 就抛 [SaleEventsApiException]，带上服务器返回的原文。
     * Throws [SaleEventsApiException] with the server's raw reply when the status is not 2xx.
     */
    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status.isSuccess()) {
            return
        }
        val body = response.bodyAsText()
        throw SaleEventsApiException(statusCode = response.status.value, body = body)
    }
}

/**
 * 促销活动请求失败：服务器返回了非 2xx 的状态码 [statusCode]，[body] 是服务器返回的原文。
 * A failed sale events request: the server answered with the non-2xx status [statusCode], and [body] is its raw
 * reply.
 *
 * 原文可能是我们代码的 problem+json，也可能是 API Gateway 的 `{"message": ...}`。只到 ViewModel，由 `LoadError.from`
 * 转成 `LoadError.Server`；原文只进日志，不给用户看。
 * The reply is either our code's problem+json or API Gateway's `{"message": ...}`. It only reaches view models,
 * where `LoadError.from` turns it into `LoadError.Server`; the raw reply goes to the logs, never to users.
 */
class SaleEventsApiException(
    val statusCode: Int,
    val body: String,
) : Exception("Sale events request failed with HTTP $statusCode: ${body.take(MAX_BODY_IN_MESSAGE)}")

/**
 * 异常消息里最多带多少字符的响应原文，避免日志被长响应刷屏。
 * The most characters of the raw reply kept in the exception message, so long replies do not flood the logs.
 */
private const val MAX_BODY_IN_MESSAGE = 500
