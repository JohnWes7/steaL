package io.github.johnwes7.steal.shared.services

import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.itad.ItadDealsFilter
import io.github.johnwes7.steal.shared.models.itad.ItadDealsRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.cache.storage.CacheStorage
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
import kotlin.test.assertNull
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
            installAppPlugins(logsRequests = false, cacheStorage = CacheStorage.Unlimited())
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

    /**
     * giveaways 发 `GET /giveaways/v1`，查询参数是 iOS 的默认值，key 在请求头里不在网址里。
     * Giveaways send `GET /giveaways/v1` with iOS's default query parameters, and the key in the header, not the URL.
     */
    @Test
    fun fetchGiveawaysSendsDefaultQuery() = runTest {
        var captured: HttpRequestData? = null
        val api = makeApi { request ->
            captured = request
            respond(content = "[]", status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val giveaways = api.fetchGiveaways()

        assertTrue(giveaways.isEmpty())
        val request = captured!!
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/giveaways/v1", request.url.encodedPath)
        assertEquals("0", request.url.parameters["offset"])
        assertEquals("20", request.url.parameters["limit"])
        assertEquals("false", request.url.parameters["expired"])
        assertEquals("false", request.url.parameters["mature"])
        assertEquals("-publish", request.url.parameters["sort"])
        assertNull(request.url.parameters["key"])
        assertEquals(TEST_KEY, request.headers[ItadApi.API_KEY_HEADER])
    }

    /**
     * giveaways 非 2xx 同样抛 [ItadApiException]。
     * A non-2xx from giveaways throws [ItadApiException] as well.
     */
    @Test
    fun fetchGiveawaysThrowsOnNonSuccess() = runTest {
        val api = makeApi {
            respond(content = FORBIDDEN_BODY, status = HttpStatusCode.Forbidden, headers = JSON_HEADERS)
        }

        val error = assertFailsWith<ItadApiException> {
            api.fetchGiveaways()
        }

        assertEquals(403, error.statusCode)
    }

    /**
     * lookup 的请求体是 UUID 数组，响应包进 `ItadShopIDsResponse` 后能取出 appID。
     * The lookup body is an array of UUIDs, and the response wrapped in `ItadShopIDsResponse` yields appIDs.
     */
    @Test
    fun lookupShopIDsSendsUUIDArray() = runTest {
        var captured: HttpRequestData? = null
        val api = makeApi { request ->
            captured = request
            respond(content = SHOP_IDS_BODY, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val response = api.lookupShopIDs(listOf(ELDEN_RING, UNKNOWN_UUID))

        val request = captured!!
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://api.isthereanydeal.com/lookup/shop/61/id/v1", request.url.toString())
        assertEquals(TEST_KEY, request.headers[ItadApi.API_KEY_HEADER])
        val sentBody = request.body.toByteArray().decodeToString()
        val expectedBody = Json.parseToJsonElement("""["$ELDEN_RING","$UNKNOWN_UUID"]""")
        assertEquals(expectedBody, Json.parseToJsonElement(sentBody))

        assertEquals(listOf(1245620), response.appIDs(ELDEN_RING))
        assertTrue(response.appIDs(UNKNOWN_UUID).isEmpty())
    }

    /**
     * UUID 列表为空时不发请求，直接返回空结果。
     * An empty UUID list sends no request and returns an empty result.
     */
    @Test
    fun lookupShopIDsSkipsEmptyList() = runTest {
        var requestCount = 0
        val api = makeApi {
            requestCount += 1
            respond(content = "{}", status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val response = api.lookupShopIDs(emptyList())

        assertEquals(0, requestCount)
        assertTrue(response.entries.isEmpty())
    }

    private companion object {

        /** ELDEN RING 的 ITAD UUID / ELDEN RING's ITAD UUID. */
        const val ELDEN_RING = "018d937f-590c-728b-ac35-38bcff85f086"

        /** ITAD 没收录的假 UUID / A made-up UUID ITAD does not track. */
        const val UNKNOWN_UUID = "00000000-0000-0000-0000-000000000000"

        /** 上面两个 UUID 的真实 lookup 响应（2026-11-18）/ The real lookup response for the two UUIDs above (2026-11-18). */
        const val SHOP_IDS_BODY =
            """{"018d937f-590c-728b-ac35-38bcff85f086":["sub\/432247","sub\/440410","app\/1245620","sub\/440408"],"00000000-0000-0000-0000-000000000000":null}"""

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
