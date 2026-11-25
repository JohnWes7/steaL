package io.github.johnwes7.steal.shared.services

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `installAppPlugins` 里 HTTP 缓存的测试：按响应头决定用不用缓存，文件缓存换一个 client 还在。
 * Tests for the HTTP cache in `installAppPlugins`: response headers decide whether the cache is used, and the file
 * cache survives a new client.
 */
class HttpCacheTest {

    /**
     * 用假引擎和 [cacheStorage] 建一个装了全 App 配置的 client；[handler] 决定每个请求返回什么。
     * Builds a client with the app-wide configuration on the mock engine and [cacheStorage]; [handler] decides what
     * each request gets back.
     */
    private fun makeClient(
        cacheStorage: CacheStorage,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient {
        val engine = MockEngine(handler)
        val client = HttpClient(engine) {
            installAppPlugins(logsRequests = false, cacheStorage = cacheStorage)
        }
        return client
    }

    /**
     * 自建后端的 `max-age=3600`：有效期内第二次直接用缓存，不发请求，结果一样。
     * Our backend's `max-age=3600`: the second fetch within the window comes from the cache without a request and
     * gives the same result.
     */
    @Test
    fun freshResponseComesFromCache() = runTest {
        var requestCount = 0
        val client = makeClient(cacheStorage = CacheStorage.Unlimited()) {
            requestCount += 1
            respond(content = ONE_EVENT, status = HttpStatusCode.OK, headers = CACHEABLE_HEADERS)
        }
        val api = SaleEventsApi(client = client, baseUrl = BASE_URL)

        val first = api.fetchSaleEvents()
        val second = api.fetchSaleEvents()

        assertEquals(1, requestCount)
        assertEquals(first, second)
    }

    /**
     * ITAD 的响应没有缓存头：每次都照常请求，不会拿到旧的 giveaways。
     * ITAD responses carry no cache headers: every fetch goes to the network, so giveaways are never stale.
     */
    @Test
    fun responseWithoutCacheHeadersIsRequestedEveryTime() = runTest {
        var requestCount = 0
        val client = makeClient(cacheStorage = CacheStorage.Unlimited()) {
            requestCount += 1
            respond(content = "[]", status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }
        val itadApi = ItadApi(client = client, apiKey = "test-key")

        itadApi.fetchGiveaways()
        itadApi.fetchGiveaways()

        assertEquals(2, requestCount)
    }

    /**
     * 失败的响应不进缓存：503 之后再请求会真的发出去，成功后才开始用缓存。
     * Failed responses stay out of the cache: after a 503 the next fetch really goes out, and only the success is
     * cached.
     */
    @Test
    fun failedResponseIsNotCached() = runTest {
        var requestCount = 0
        val client = makeClient(cacheStorage = CacheStorage.Unlimited()) {
            requestCount += 1
            if (requestCount == 1) {
                respond(content = UNAVAILABLE, status = HttpStatusCode.ServiceUnavailable, headers = JSON_HEADERS)
            } else {
                respond(content = ONE_EVENT, status = HttpStatusCode.OK, headers = CACHEABLE_HEADERS)
            }
        }
        val api = SaleEventsApi(client = client, baseUrl = BASE_URL)

        assertFailsWith<SaleEventsApiException> {
            api.fetchSaleEvents()
        }
        api.fetchSaleEvents()
        api.fetchSaleEvents()

        assertEquals(2, requestCount)
    }

    /**
     * 文件缓存换一个 client 还在（相当于 App 重开）：第二个 client 不发请求。
     * The file cache survives a new client (as if the app restarted): the second client sends no request.
     */
    @Test
    fun fileCacheSurvivesNewClient() = runTest {
        val directoryName = "steal-http-cache-test-${Random.nextInt(from = 0, until = Int.MAX_VALUE)}"
        val directory = Path(SystemTemporaryDirectory, directoryName)
        var requestCount = 0
        val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
            requestCount += 1
            respond(content = ONE_EVENT, status = HttpStatusCode.OK, headers = CACHEABLE_HEADERS)
        }

        try {
            val firstClient = makeClient(cacheStorage = FileStorage(directory = directory), handler = handler)
            val firstApi = SaleEventsApi(client = firstClient, baseUrl = BASE_URL)
            firstApi.fetchSaleEvents()
            firstClient.close()

            val secondClient = makeClient(cacheStorage = FileStorage(directory = directory), handler = handler)
            val secondApi = SaleEventsApi(client = secondClient, baseUrl = BASE_URL)
            val eventList = secondApi.fetchSaleEvents()
            secondClient.close()

            assertEquals(1, requestCount)
            assertEquals("Winter Sale 2026", eventList.events[0].name)
        } finally {
            deleteDirectory(directory)
        }
    }

    /**
     * 删掉测试建的缓存目录和里面的文件（FileStorage 只在目录里平铺文件）。
     * Deletes the cache directory the test created and its files (FileStorage keeps files flat in the directory).
     */
    private fun deleteDirectory(directory: Path) {
        if (!SystemFileSystem.exists(directory)) {
            return
        }
        val files = SystemFileSystem.list(directory)
        for (file in files) {
            SystemFileSystem.delete(file)
        }
        SystemFileSystem.delete(directory)
    }

    private companion object {

        /**
         * 测试用的后端地址。
         * The backend address used in tests.
         */
        const val BASE_URL = "https://example.com/v1"

        /**
         * 没有缓存头的 JSON 响应头（同 ITAD）。
         * JSON response headers without cache headers (as from ITAD).
         */
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

        /**
         * 带 `Cache-Control: public, max-age=3600` 的 JSON 响应头（同自建后端）。
         * JSON response headers with `Cache-Control: public, max-age=3600` (as from our backend).
         */
        val CACHEABLE_HEADERS = headersOf(
            HttpHeaders.ContentType to listOf("application/json"),
            HttpHeaders.CacheControl to listOf("public, max-age=3600"),
        )

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
         * 网关并发打满时的响应体。
         * The gateway's body when concurrency is exhausted.
         */
        const val UNAVAILABLE = """{"message":"Service Unavailable"}"""
    }
}
