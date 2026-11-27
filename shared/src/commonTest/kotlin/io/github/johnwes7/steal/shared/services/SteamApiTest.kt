package io.github.johnwes7.steal.shared.services

import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.settings.AppRegion
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SteamApi] 的请求与错误测试，用 Ktor 的假引擎，不联网。
 * Request and error tests for [SteamApi], using Ktor's mock engine without the network.
 */
class SteamApiTest {

    /**
     * 用假引擎建一个 [SteamApi]，装的是和正式 client 一样的配置；[handler] 决定每个请求返回什么。
     * Builds a [SteamApi] on the mock engine with the same configuration as the real client; [handler] decides
     * what each request gets back.
     */
    private fun makeApi(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): SteamApi {
        val engine = MockEngine(handler)
        val client = HttpClient(engine) {
            installAppPlugins(logsRequests = false, cacheStorage = CacheStorage.Unlimited())
        }
        val api = SteamApi(client = client)
        return api
    }

    /**
     * 请求发到 `GET /api/appdetails`，appID 用逗号连起来，带地区代码和 `filters=price_overview`。
     * The request goes to `GET /api/appdetails`, with appIDs joined by commas, the region code and
     * `filters=price_overview`.
     */
    @Test
    fun fetchAvailabilitySendsExpectedQuery() = runTest {
        var captured: HttpRequestData? = null
        val api = makeApi { request ->
            captured = request
            respond(content = AVAILABILITY_BODY, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        api.fetchAvailability(appIDs = listOf(601100, 743980, 582010), region = AppRegion.Australia)

        val request = captured!!
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("store.steampowered.com", request.url.host)
        assertEquals("/api/appdetails", request.url.encodedPath)
        assertEquals("601100,743980,582010", request.url.parameters["appids"])
        assertEquals("AU", request.url.parameters["cc"])
        assertEquals("price_overview", request.url.parameters["filters"])
    }

    /**
     * 响应包进 `SteamAppAvailabilityResponse` 后能分出存在、有价格的那个候选。
     * Wrapped in `SteamAppAvailabilityResponse`, the response tells which candidate exists and has a price.
     */
    @Test
    fun fetchAvailabilityWrapsResponse() = runTest {
        val api = makeApi {
            respond(content = AVAILABILITY_BODY, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val availability = api.fetchAvailability(appIDs = listOf(601100, 743980, 582010), region = AppRegion.Australia)

        assertFalse(availability.isAvailable(601100))
        assertFalse(availability.isAvailable(743980))
        assertTrue(availability.hasPrice(582010))
    }

    /**
     * appID 列表为空时不发请求，直接返回空结果。
     * An empty appID list sends no request and returns an empty result.
     */
    @Test
    fun fetchAvailabilitySkipsEmptyList() = runTest {
        var requestCount = 0
        val api = makeApi {
            requestCount += 1
            respond(content = "{}", status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val availability = api.fetchAvailability(appIDs = emptyList(), region = AppRegion.Australia)

        assertEquals(0, requestCount)
        assertTrue(availability.entries.isEmpty())
    }

    /**
     * 非 2xx 抛 [SteamApiException]，带状态码与原文，并归成 `LoadError.Server`。
     * A non-2xx throws [SteamApiException] with the status and raw reply, normalised to `LoadError.Server`.
     */
    @Test
    fun fetchAvailabilityThrowsOnNonSuccess() = runTest {
        val api = makeApi {
            respond(content = "Internal Server Error", status = HttpStatusCode.InternalServerError)
        }

        val error = assertFailsWith<SteamApiException> {
            api.fetchAvailability(appIDs = listOf(582010), region = AppRegion.Australia)
        }

        assertEquals(500, error.statusCode)
        assertEquals("Internal Server Error", error.body)
        assertEquals(LoadError.Server(500), LoadError.from(error))
    }

    // MARK: - 详情 / Details

    /**
     * 详情请求发到 `GET /api/appdetails`，带一个 appID、地区代码（Europe 是 `DE`）和 `l=`，不带 `filters`。
     * The details request goes to `GET /api/appdetails` with one appID, the region code (`DE` for Europe) and
     * `l=`, and no `filters`.
     */
    @Test
    fun fetchAppDetailsSendsExpectedQuery() = runTest {
        var captured: HttpRequestData? = null
        val api = makeApi { request ->
            captured = request
            respond(content = DETAILS_BODY, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        api.fetchAppDetails(appID = ELDEN_RING, region = AppRegion.Europe, steamLanguage = "schinese")

        val request = captured!!
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("store.steampowered.com", request.url.host)
        assertEquals("/api/appdetails", request.url.encodedPath)
        assertEquals("1245620", request.url.parameters["appids"])
        assertEquals("DE", request.url.parameters["cc"])
        assertEquals("schinese", request.url.parameters["l"])
        assertNull(request.url.parameters["filters"])
    }

    /**
     * 从以 appID 为 key 的字典里取出这个 app 的详情。
     * The app's details are taken out of the dictionary keyed by appID.
     */
    @Test
    fun fetchAppDetailsReturnsDetails() = runTest {
        val api = makeApi {
            respond(content = DETAILS_BODY, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val details = api.fetchAppDetails(appID = ELDEN_RING, region = AppRegion.Australia, steamLanguage = "english")

        assertNotNull(details)
        assertEquals("ELDEN RING", details.name)
        assertEquals(8995, details.priceOverview?.final)
    }

    /**
     * Steam 上没有的 app（`success:false`）返回 `null`，不抛错。
     * An app Steam does not have (`success:false`) returns `null` without throwing.
     */
    @Test
    fun fetchAppDetailsMissingAppIsNull() = runTest {
        val api = makeApi {
            respond(content = """{"9999999":{"success":false}}""", status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val details = api.fetchAppDetails(appID = 9999999, region = AppRegion.Australia, steamLanguage = "english")

        assertNull(details)
    }

    /**
     * 详情请求非 2xx 也抛 [SteamApiException]，归成 `LoadError.Server`。
     * A non-2xx details request also throws [SteamApiException], normalised to `LoadError.Server`.
     */
    @Test
    fun fetchAppDetailsThrowsOnNonSuccess() = runTest {
        val api = makeApi {
            respond(content = "Too Many Requests", status = HttpStatusCode.TooManyRequests)
        }

        val error = assertFailsWith<SteamApiException> {
            api.fetchAppDetails(appID = ELDEN_RING, region = AppRegion.Australia, steamLanguage = "english")
        }

        assertEquals(429, error.statusCode)
        assertEquals(LoadError.Server(429), LoadError.from(error))
    }

    // MARK: - 搜索 / Search

    /**
     * 搜索请求发到 `GET /api/storesearch`，`term` 去掉首尾空白，带地区代码（Europe 是 `DE`）和 `l=`。
     * The search request goes to `GET /api/storesearch` with `term` trimmed, the region code (`DE` for Europe)
     * and `l=`.
     */
    @Test
    fun fetchSearchResultsSendsExpectedQuery() = runTest {
        var captured: HttpRequestData? = null
        val api = makeApi { request ->
            captured = request
            respond(content = SEARCH_BODY, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        api.fetchSearchResults(query = "  elden ring ", region = AppRegion.Europe, steamLanguage = "schinese")

        val request = captured!!
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("store.steampowered.com", request.url.host)
        assertEquals("/api/storesearch", request.url.encodedPath)
        assertEquals("elden ring", request.url.parameters["term"])
        assertEquals("DE", request.url.parameters["cc"])
        assertEquals("schinese", request.url.parameters["l"])
    }

    /**
     * 响应解成 `SteamSearchResponse`。
     * The response decodes into `SteamSearchResponse`.
     */
    @Test
    fun fetchSearchResultsReturnsItems() = runTest {
        val api = makeApi {
            respond(content = SEARCH_BODY, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val response = api.fetchSearchResults(query = "elden ring", region = AppRegion.Australia, steamLanguage = "english")

        assertEquals(1, response.items.size)
        assertEquals(ELDEN_RING, response.items[0].id)
        assertEquals("ELDEN RING", response.items[0].name)
        assertEquals(8995, response.items[0].price?.final)
    }

    /**
     * 空白的搜索词不发请求，直接返回空结果（空的 `term` 会让 Steam 返回热门游戏）。
     * A blank query sends no request and returns an empty result (an empty `term` makes Steam return popular
     * games).
     */
    @Test
    fun fetchSearchResultsSkipsBlankQuery() = runTest {
        var requestCount = 0
        val api = makeApi {
            requestCount += 1
            respond(content = SEARCH_BODY, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        val response = api.fetchSearchResults(query = "   ", region = AppRegion.Australia, steamLanguage = "english")

        assertEquals(0, requestCount)
        assertTrue(response.items.isEmpty())
    }

    /**
     * 响应不带缓存头，搜同一个词两次会发两次请求，不读 HTTP 缓存。
     * The response has no cache headers, so searching the same term twice sends two requests instead of reading
     * the HTTP cache.
     */
    @Test
    fun fetchSearchResultsIsNotCached() = runTest {
        var requestCount = 0
        val api = makeApi {
            requestCount += 1
            respond(content = SEARCH_BODY, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        api.fetchSearchResults(query = "elden ring", region = AppRegion.Australia, steamLanguage = "english")
        api.fetchSearchResults(query = "elden ring", region = AppRegion.Australia, steamLanguage = "english")

        assertEquals(2, requestCount)
    }

    /**
     * 搜索请求非 2xx 也抛 [SteamApiException]，归成 `LoadError.Server`。
     * A non-2xx search request also throws [SteamApiException], normalised to `LoadError.Server`.
     */
    @Test
    fun fetchSearchResultsThrowsOnNonSuccess() = runTest {
        val api = makeApi {
            respond(content = "Bad Gateway", status = HttpStatusCode.BadGateway)
        }

        val error = assertFailsWith<SteamApiException> {
            api.fetchSearchResults(query = "elden ring", region = AppRegion.Australia, steamLanguage = "english")
        }

        assertEquals(502, error.statusCode)
        assertEquals(LoadError.Server(502), LoadError.from(error))
    }

    private companion object {

        /** ELDEN RING 的 Steam appID / ELDEN RING's Steam appID. */
        const val ELDEN_RING = 1245620

        /**
         * 只留几个字段的 ELDEN RING 详情（完整的真实样例在 `SteamAppDetailsTest`）。
         * ELDEN RING's details trimmed to a few fields (the full real sample is in `SteamAppDetailsTest`).
         */
        const val DETAILS_BODY =
            """{"1245620":{"success":true,"data":{"type":"game","name":"ELDEN RING","steam_appid":1245620,"is_free":false,"price_overview":{"currency":"AUD","initial":8995,"final":8995,"discount_percent":0}}}}"""

        /**
         * Monster Hunter: World 三个候选的真实响应（2026-11-19）。
         * The real response for Monster Hunter: World's three candidates (2026-11-19).
         */
        const val AVAILABILITY_BODY =
            """{"601100":{"success":false},"743980":{"success":false},"582010":{"success":true,"data":{"price_overview":{"currency":"AUD","initial":4494,"final":4494,"discount_percent":0,"initial_formatted":"","final_formatted":"A$ 44.94"}}}}"""

        /**
         * 只留一条的搜索结果（完整的真实样例在 `SteamSearchResponseTest`）。
         * A search result trimmed to one item (the full real sample is in `SteamSearchResponseTest`).
         */
        const val SEARCH_BODY =
            """{"total":1,"items":[{"type":"app","name":"ELDEN RING","id":1245620,"price":{"currency":"AUD","initial":8995,"final":8995},"tiny_image":"https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1245620/capsule_231x87.jpg"}]}"""

        /** JSON 响应头 / The JSON response header. */
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
