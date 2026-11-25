package io.github.johnwes7.steal.shared.services

import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.itad.ItadDealsFilter
import io.github.johnwes7.steal.shared.models.itad.ItadDealsRequest
import io.github.johnwes7.steal.shared.models.itad.game
import io.github.johnwes7.steal.shared.models.settings.AppRegion
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
import kotlin.test.assertFalse
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

    // MARK: - 详情页的三个接口 / The detail page's three endpoints

    /**
     * 按 appID 找游戏发 `GET /games/lookup/v1`，`appid` 是裸数字（不带 `app/`），key 在请求头里不在网址里。
     * Looking a game up by appID sends `GET /games/lookup/v1` with a bare `appid` (no `app/`), and the key in the
     * header, not the URL.
     */
    @Test
    fun lookupGameSendsBareAppID() = runTest {
        var captured: HttpRequestData? = null
        val api = makeApi { request ->
            captured = request
            respond(content = LOOKUP_FOUND_BODY, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val lookup = api.lookupGame(appID = 1245620)

        val request = captured!!
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("api.isthereanydeal.com", request.url.host)
        assertEquals("/games/lookup/v1", request.url.encodedPath)
        assertEquals("1245620", request.url.parameters["appid"])
        assertNull(request.url.parameters["key"])
        assertEquals(TEST_KEY, request.headers[ItadApi.API_KEY_HEADER])

        assertTrue(lookup.found)
        assertEquals(ELDEN_RING, lookup.game?.id)
    }

    /**
     * 没收录（`found:false`）照常返回，不抛错。
     * An untracked game (`found:false`) comes back normally without throwing.
     */
    @Test
    fun lookupGameNotFoundIsNotError() = runTest {
        val api = makeApi {
            respond(content = """{"found":false}""", status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val lookup = api.lookupGame(appID = 4349200)

        assertFalse(lookup.found)
        assertNull(lookup.game)
    }

    /**
     * 游戏信息发 `GET /games/info/v2?id=<UUID>`，key 在请求头里。
     * Game info sends `GET /games/info/v2?id=<UUID>`, with the key in the header.
     */
    @Test
    fun fetchGameInfoSendsUUID() = runTest {
        var captured: HttpRequestData? = null
        val api = makeApi { request ->
            captured = request
            respond(content = INFO_BODY, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val info = api.fetchGameInfo(uuid = ELDEN_RING)

        val request = captured!!
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/games/info/v2", request.url.encodedPath)
        assertEquals(ELDEN_RING, request.url.parameters["id"])
        assertNull(request.url.parameters["key"])
        assertEquals(TEST_KEY, request.headers[ItadApi.API_KEY_HEADER])

        assertEquals("Elden Ring", info.title)
        assertNull(info.assets)
    }

    /**
     * 比价发 `POST /games/prices/v3?country=<代码>&deals=false`（Europe 是 `DE`），请求体是 UUID 数组；
     * 响应是稀疏的，没有价格的 UUID 取不到。
     * Prices send `POST /games/prices/v3?country=<code>&deals=false` (`DE` for Europe) with an array of UUIDs as the
     * body; the response is sparse, so a UUID without prices is not found.
     */
    @Test
    fun fetchPricesSendsExpectedRequest() = runTest {
        var captured: HttpRequestData? = null
        val api = makeApi { request ->
            captured = request
            respond(content = PRICES_BODY, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val prices = api.fetchPrices(uuids = listOf(ELDEN_RING, UNKNOWN_UUID), region = AppRegion.Europe)

        val request = captured!!
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/games/prices/v3", request.url.encodedPath)
        assertEquals("DE", request.url.parameters["country"])
        assertEquals("false", request.url.parameters["deals"])
        assertNull(request.url.parameters["key"])
        assertEquals(TEST_KEY, request.headers[ItadApi.API_KEY_HEADER])
        val sentBody = request.body.toByteArray().decodeToString()
        val expectedBody = Json.parseToJsonElement("""["$ELDEN_RING","$UNKNOWN_UUID"]""")
        assertEquals(expectedBody, Json.parseToJsonElement(sentBody))

        assertEquals(1, prices.size)
        assertEquals(4553, prices.game(ELDEN_RING)?.allTimeLow?.amountInt)
        assertNull(prices.game(UNKNOWN_UUID))
    }

    /**
     * UUID 列表为空时比价不发请求，直接返回空列表。
     * An empty UUID list sends no prices request and returns an empty list.
     */
    @Test
    fun fetchPricesSkipsEmptyList() = runTest {
        var requestCount = 0
        val api = makeApi {
            requestCount += 1
            respond(content = "[]", status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val prices = api.fetchPrices(uuids = emptyList(), region = AppRegion.Australia)

        assertEquals(0, requestCount)
        assertTrue(prices.isEmpty())
    }

    /**
     * 三个接口非 2xx 都抛 [ItadApiException]（如 `info/v2` 传错 ID 时的 400）。
     * All three endpoints throw [ItadApiException] on a non-2xx (such as `info/v2`'s 400 for a wrong ID).
     */
    @Test
    fun detailEndpointsThrowOnNonSuccess() = runTest {
        val api = makeApi {
            respond(content = BAD_REQUEST_BODY, status = HttpStatusCode.BadRequest, headers = JSON_HEADERS)
        }

        val lookupError = assertFailsWith<ItadApiException> {
            api.lookupGame(appID = 1245620)
        }
        val infoError = assertFailsWith<ItadApiException> {
            api.fetchGameInfo(uuid = "1245620")
        }
        val pricesError = assertFailsWith<ItadApiException> {
            api.fetchPrices(uuids = listOf(ELDEN_RING), region = AppRegion.Australia)
        }

        assertEquals(400, lookupError.statusCode)
        assertEquals(400, infoError.statusCode)
        assertEquals(BAD_REQUEST_BODY, infoError.body)
        assertEquals(400, pricesError.statusCode)
        assertEquals(LoadError.Server(400), LoadError.from(pricesError))
    }

    private companion object {

        /**
         * 只留标识的 ELDEN RING lookup 响应（完整的真实样例在 `ItadGameLookupTest`）。
         * ELDEN RING's lookup response trimmed to its identity (the full real sample is in `ItadGameLookupTest`).
         */
        const val LOOKUP_FOUND_BODY =
            """{"found":true,"game":{"id":"018d937f-590c-728b-ac35-38bcff85f086","slug":"elden-ring","title":"Elden Ring","type":"game"}}"""

        /**
         * 只留必填字段的 ELDEN RING 信息（完整的真实样例在 `ItadGameInfoTest`）。
         * ELDEN RING's info trimmed to the required fields (the full real sample is in `ItadGameInfoTest`).
         */
        const val INFO_BODY =
            """{"id":"018d937f-590c-728b-ac35-38bcff85f086","slug":"elden-ring","title":"Elden Ring","type":"game","mature":false}"""

        /**
         * 只有 ELDEN RING 一条、没有报价的比价响应（完整的真实样例在 `ItadPricesTest`）。
         * A prices response with only ELDEN RING and no offers (the full real sample is in `ItadPricesTest`).
         */
        const val PRICES_BODY =
            """[{"id":"018d937f-590c-728b-ac35-38bcff85f086","historyLow":{"all":{"amount":45.53,"amountInt":4553,"currency":"AUD"},"y1":null,"m3":null},"deals":[]}]"""

        /** 400 的响应原文（形状不重要）/ A 400 reply (its shape does not matter). */
        const val BAD_REQUEST_BODY = """{"status_code":400,"reason_phrase":"Bad Request"}"""

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
