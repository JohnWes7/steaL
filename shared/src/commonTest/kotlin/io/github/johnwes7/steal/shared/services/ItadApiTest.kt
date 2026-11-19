package io.github.johnwes7.steal.shared.services

import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.itad.ItadDealsFilter
import io.github.johnwes7.steal.shared.models.itad.ItadDealsRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [ItadApi] 的请求与错误测试，用 Ktor 的假引擎，不联网。
 * Request and error tests for [ItadApi], using Ktor's mock engine without the network.
 */
class ItadApiTest {

    /**
     * 首页用的请求体。
     * The request body the home page uses.
     */
    private val homeRequest = ItadDealsRequest(
        country = "AU",
        offset = 0,
        limit = 5,
        sort = "-hot",
        nondeals = false,
        filter = ItadDealsFilter(type = listOf(1), drm = listOf(61)),
    )

    /**
     * 用假引擎建一个 [ItadApi]，装的是和正式 client 一样的配置；[handler] 决定每个请求返回什么。
     * Builds an [ItadApi] on the mock engine with the same configuration as the real client; [handler] decides
     * what each request gets back.
     */
    private fun makeApi(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ItadApi {
        val engine = MockEngine(handler)
        val client = HttpClient(engine) {
            installAppPlugins(logsRequests = false)
        }
        val api = ItadApi(client = client, apiKey = TEST_KEY)
        return api
    }

    /**
     * 请求发到 `POST /deals/v2`，key 在请求头里、不在网址里，请求体是编码后的 [ItadDealsRequest]。
     * The request goes to `POST /deals/v2`, with the key in the header and not the URL, and the encoded
     * [ItadDealsRequest] as the body.
     */
    @Test
    fun fetchDealsSendsExpectedRequest() = runTest {
        var captured: HttpRequestData? = null
        val api = makeApi { request ->
            captured = request
            respond(content = EMPTY_DEALS, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        api.fetchDeals(homeRequest)

        val request = captured!!
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://api.isthereanydeal.com/deals/v2", request.url.toString())
        assertEquals(TEST_KEY, request.headers[ItadApi.API_KEY_HEADER])

        val sentBody = request.body.toByteArray().decodeToString()
        val expectedBody = Json.parseToJsonElement(
            """{"country":"AU","offset":0,"limit":5,"sort":"-hot","nondeals":false,"filter":{"type":[1],"drm":[61]}}""",
        )
        assertEquals(expectedBody, Json.parseToJsonElement(sentBody))
    }

    /**
     * 2xx 时返回解码好的响应；空列表不是错误。
     * A 2xx returns the decoded response; an empty list is not an error.
     */
    @Test
    fun fetchDealsDecodesSuccess() = runTest {
        val api = makeApi {
            respond(content = EMPTY_DEALS, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val response = api.fetchDeals(homeRequest)

        assertTrue(response.list.isEmpty())
        assertEquals(false, response.hasMore)
    }

    /**
     * 非 2xx 抛 [ItadApiException]，带状态码与原文，并归成 `LoadError.Server`。
     * A non-2xx throws [ItadApiException] with the status and raw reply, normalised to `LoadError.Server`.
     */
    @Test
    fun fetchDealsThrowsOnNonSuccess() = runTest {
        val api = makeApi {
            respond(content = FORBIDDEN_BODY, status = HttpStatusCode.Forbidden, headers = JSON_HEADERS)
        }

        val error = assertFailsWith<ItadApiException> {
            api.fetchDeals(homeRequest)
        }

        assertEquals(403, error.statusCode)
        assertEquals(FORBIDDEN_BODY, error.body)
        assertEquals(LoadError.Server(403), LoadError.from(error))
    }

    /**
     * 2xx 但响应对不上 DTO 时，Ktor 抛 `JsonConvertException`，归成 `LoadError.Decoding`。
     * When a 2xx body does not match the DTOs, Ktor throws `JsonConvertException`, normalised to `LoadError.Decoding`.
     */
    @Test
    fun fetchDealsDecodingFailureIsDecoding() = runTest {
        val api = makeApi {
            respond(content = """{"unexpected":true}""", status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val error = assertFailsWith<JsonConvertException> {
            api.fetchDeals(homeRequest)
        }

        assertEquals(LoadError.Decoding, LoadError.from(error))
    }

    private companion object {

        /** 测试用的假 key / A fake key for tests. */
        const val TEST_KEY = "test-key"

        /** 空的 deals 响应 / An empty deals response. */
        const val EMPTY_DEALS = """{"nextOffset":null,"hasMore":false,"list":[]}"""

        /** 403 的响应原文（形状不重要）/ A 403 reply (its shape does not matter). */
        const val FORBIDDEN_BODY = """{"status_code":403,"reason_phrase":"Forbidden"}"""

        /** JSON 响应头 / The JSON response header. */
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
