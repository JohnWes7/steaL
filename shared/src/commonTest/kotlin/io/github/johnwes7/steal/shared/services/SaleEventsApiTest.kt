package io.github.johnwes7.steal.shared.services

import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.saleevents.SaleEventStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [SaleEventsApi] 的请求与错误测试，用 Ktor 的假引擎，不联网。
 * Request and error tests for [SaleEventsApi], using Ktor's mock engine without the network.
 */
class SaleEventsApiTest {

    /**
     * 用假引擎建一个 [SaleEventsApi]，装的是和正式 client 一样的配置；[handler] 决定每个请求返回什么。
     * Builds a [SaleEventsApi] on the mock engine with the same configuration as the real client; [handler] decides
     * what each request gets back.
     */
    private fun makeApi(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): SaleEventsApi {
        val engine = MockEngine(handler)
        val client = HttpClient(engine) {
            installAppPlugins(logsRequests = false, cacheStorage = CacheStorage.Unlimited())
        }
        val api = SaleEventsApi(client = client, baseUrl = BASE_URL)
        return api
    }

    /**
     * 请求是 `GET {baseUrl}/sale-events`，没有查询参数。
     * The request is `GET {baseUrl}/sale-events` with no query parameters.
     */
    @Test
    fun fetchSendsGetToSaleEvents() = runTest {
        var captured: HttpRequestData? = null
        val api = makeApi { request ->
            captured = request
            respond(content = ONE_EVENT, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        api.fetchSaleEvents()

        val request = captured!!
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("$BASE_URL/sale-events", request.url.toString())
    }

    /**
     * 成功的响应解成 [io.github.johnwes7.steal.shared.models.saleevents.SaleEventList]。
     * A successful response decodes into [io.github.johnwes7.steal.shared.models.saleevents.SaleEventList].
     */
    @Test
    fun fetchDecodesSuccess() = runTest {
        val api = makeApi {
            respond(content = ONE_EVENT, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val eventList = api.fetchSaleEvents()

        assertEquals(1, eventList.events.size)
        assertEquals("Winter Sale 2026", eventList.events[0].name)
        assertEquals(SaleEventStore.Steam, eventList.events[0].store)
    }

    /**
     * 网关限流（429，`{"message": ...}`）抛 [SaleEventsApiException]，带状态码和原文，归成 `Server(429)`。
     * Gateway rate limiting (429, `{"message": ...}`) throws [SaleEventsApiException] with the status and raw reply,
     * which becomes `Server(429)`.
     */
    @Test
    fun rateLimitIsServerError() = runTest {
        val api = makeApi {
            respond(content = GATEWAY_429, status = HttpStatusCode.TooManyRequests, headers = JSON_HEADERS)
        }

        val error = assertFailsWith<SaleEventsApiException> {
            api.fetchSaleEvents()
        }

        assertEquals(429, error.statusCode)
        assertEquals(GATEWAY_429, error.body)
        assertEquals(LoadError.Server(429), LoadError.from(error))
    }

    /**
     * 还没刷新过（503，problem+json）同样是 [SaleEventsApiException]，归成 `Server(503)`。
     * Not refreshed yet (503, problem+json) is a [SaleEventsApiException] too, which becomes `Server(503)`.
     */
    @Test
    fun unavailableIsServerError() = runTest {
        val api = makeApi {
            respond(content = PROBLEM_503, status = HttpStatusCode.ServiceUnavailable, headers = PROBLEM_HEADERS)
        }

        val error = assertFailsWith<SaleEventsApiException> {
            api.fetchSaleEvents()
        }

        assertEquals(503, error.statusCode)
        assertTrue(error.body.contains("No sale events have been refreshed yet."))
        assertEquals(LoadError.Server(503), LoadError.from(error))
    }

    /**
     * 响应对不上 DTO（缺字段）时 Ktor 抛 JsonConvertException，归成 `Decoding`。
     * A body that does not match the DTOs (missing fields) makes Ktor throw JsonConvertException, which becomes
     * `Decoding`.
     */
    @Test
    fun mismatchedBodyIsDecodingError() = runTest {
        val api = makeApi {
            respond(content = """{"unexpected":true}""", status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val error = assertFailsWith<JsonConvertException> {
            api.fetchSaleEvents()
        }

        assertEquals(LoadError.Decoding, LoadError.from(error))
    }

    /**
     * 时刻解析不了时，标准库的 InstantFormatException 也被 Ktor 包成 JsonConvertException，归成 `Decoding`。
     * An unparsable time's InstantFormatException from the standard library is also wrapped by Ktor in
     * JsonConvertException, which becomes `Decoding`.
     */
    @Test
    fun badTimeIsDecodingError() = runTest {
        val api = makeApi {
            respond(content = BAD_TIME_EVENT, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val error = assertFailsWith<JsonConvertException> {
            api.fetchSaleEvents()
        }

        assertEquals(LoadError.Decoding, LoadError.from(error))
    }

    private companion object {

        /**
         * 测试用的后端地址。
         * The backend address used in tests.
         */
        const val BASE_URL = "https://example.com/v1"

        /**
         * JSON 响应头。
         * JSON response headers.
         */
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

        /**
         * problem+json 响应头（我们代码返回的错误）。
         * problem+json response headers (errors from our code).
         */
        val PROBLEM_HEADERS = headersOf(HttpHeaders.ContentType, "application/problem+json")

        /**
         * 一条活动的成功响应。
         * A successful response with one event.
         */
        const val ONE_EVENT = """
        {
          "refreshedAt": "2026-11-16T01:00:06Z",
          "events": [
            {
              "id": "steam-2026-12-17-winter-sale-2026",
              "store": "steam",
              "kind": "seasonalSale",
              "name": "Winter Sale 2026",
              "start": "2026-12-17T18:00:00Z",
              "end": "2027-01-04T18:00:00Z",
              "sourceURL": "https://partner.steamgames.com/doc/marketing/upcoming_events"
            }
          ]
        }
        """

        /**
         * 开始时刻不是 ISO 8601 的响应。
         * A response whose start time is not ISO 8601.
         */
        const val BAD_TIME_EVENT = """
        {
          "refreshedAt": "2026-11-16T01:00:06Z",
          "events": [
            {
              "id": "steam-bad",
              "store": "steam",
              "kind": "themedFest",
              "name": "Bad Fest",
              "start": "next Tuesday",
              "end": "2027-01-04T18:00:00Z",
              "sourceURL": "https://example.com/bad"
            }
          ]
        }
        """

        /**
         * API Gateway 限流时的响应体（契约里的样例）。
         * API Gateway's body when rate limiting (the contract's example).
         */
        const val GATEWAY_429 = """{"message":"Too Many Requests"}"""

        /**
         * 还没刷新过时我们代码返回的 problem+json（契约里的样例）。
         * The problem+json our code returns before any refresh (the contract's example).
         */
        const val PROBLEM_503 = """{"type":"about:blank","title":"Service Unavailable","status":503,""" +
            """"detail":"No sale events have been refreshed yet."}"""
    }
}
