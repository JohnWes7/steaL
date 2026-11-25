package io.github.johnwes7.steal.shared.viewmodels

import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.services.SaleEventsApi
import io.github.johnwes7.steal.shared.services.installAppPlugins
import io.github.johnwes7.steal.shared.viewmodels.SaleEventsViewModel.SaleEventsLoadState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * [SaleEventsViewModel] 的状态测试：一个假引擎扮演自建后端，不联网。
 * State tests for [SaleEventsViewModel]: one mock engine plays our backend, without the network.
 *
 * 写法同 `GamesViewModelTest`：`Dispatchers.setMain` 换成测试调度器，用 `StateFlow.first { … }` 等状态。
 * 假响应不带缓存头，所以 client 的 HTTP 缓存不会把「多发了一次请求」藏起来。
 * Written like `GamesViewModelTest`: `Dispatchers.setMain` swaps in a test dispatcher, and tests wait with
 * `StateFlow.first { … }`. The fake responses carry no cache headers, so the client's HTTP cache never hides an
 * extra request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaleEventsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // MARK: - 第一次加载 / First load

    /**
     * 建好后不自己请求，停在 Loading，等页面调 `loadIfNeeded()`。
     * Creating the view model sends nothing; it stays Loading until the page calls `loadIfNeeded()`.
     */
    @Test
    fun doesNotLoadBeforeLoadIfNeeded() = runTest {
        val server = FakeServer()
        val viewModel = server.makeViewModel()

        advanceUntilIdle()

        assertSame(SaleEventsLoadState.Loading, viewModel.saleState.value)
        assertEquals(0, server.requestCount.value)
    }

    /**
     * 第一次加载成功后发布活动列表，只请求一次。
     * The first load publishes the event list after one request.
     */
    @Test
    fun firstLoadPublishesEvents() = runTest {
        val server = FakeServer()
        val viewModel = server.makeViewModel()

        viewModel.loadIfNeeded()

        val loaded = viewModel.saleState.first { state -> state is SaleEventsLoadState.Loaded }
        val eventList = assertIs<SaleEventsLoadState.Loaded>(loaded).eventList
        assertEquals(listOf("Winter Sale 2026"), eventList.events.map { event -> event.name })
        assertEquals(1, server.requestCount.value)
    }

    /**
     * 页面再次出现（再调 `loadIfNeeded()`）不重新请求，状态还是同一个对象。
     * The page appearing again (another `loadIfNeeded()`) sends no new request, and the state is the same object.
     */
    @Test
    fun secondCallDoesNotRequestAgain() = runTest {
        val server = FakeServer()
        val viewModel = server.makeViewModel()

        viewModel.loadIfNeeded()
        val loaded = viewModel.saleState.first { state -> state is SaleEventsLoadState.Loaded }
        viewModel.loadIfNeeded()
        advanceUntilIdle()

        assertSame(loaded, viewModel.saleState.value)
        assertEquals(1, server.requestCount.value)
    }

    // MARK: - 失败与重试 / Failure and retry

    /**
     * 503 变成 `Failed(Server(503))`；重试先切回 Loading，再加载成功。
     * A 503 becomes `Failed(Server(503))`; a retry shows Loading again and then loads.
     */
    @Test
    fun failureThenRetryLoads() = runTest {
        val server = FakeServer()
        server.failingRequests = setOf(1)
        val retryGate = CompletableDeferred<Unit>()
        server.gates[2] = retryGate
        val viewModel = server.makeViewModel()

        viewModel.loadIfNeeded()
        val failed = viewModel.saleState.first { state -> state is SaleEventsLoadState.Failed }
        assertEquals(SaleEventsLoadState.Failed(error = LoadError.Server(statusCode = 503)), failed)

        viewModel.retry()
        viewModel.saleState.first { state -> state is SaleEventsLoadState.Loading }
        retryGate.complete(Unit)
        viewModel.saleState.first { state -> state is SaleEventsLoadState.Loaded }

        assertEquals(2, server.requestCount.value)
    }

    /**
     * 第一次请求还在路上时重试：旧请求被取消，最后是新请求的结果。
     * Retrying while the first request is still in flight cancels it, and the new request's result wins.
     */
    @Test
    fun retryCancelsRequestInFlight() = runTest {
        val server = FakeServer()
        // 第一个请求的闸门永远不开，只能被取消。
        // The first request's gate never opens, so it can only be cancelled.
        server.gates[1] = CompletableDeferred()
        val viewModel = server.makeViewModel()

        viewModel.loadIfNeeded()
        server.requestCount.first { count -> count == 1 }
        viewModel.retry()

        viewModel.saleState.first { state -> state is SaleEventsLoadState.Loaded }
        server.cancelledRequests.first { requests -> requests == listOf(1) }
        assertEquals(2, server.requestCount.value)
    }

    // MARK: - 假服务器 / Fake server

    /**
     * 扮演自建后端：按请求序号（从 1 开始）决定失败与否、要不要停在闸门上。
     * Plays our backend: the request number (from 1) decides whether it fails and whether it waits at a gate.
     */
    private class FakeServer {

        /** 返回 503 的请求序号 / The request numbers that get a 503. */
        var failingRequests: Set<Int> = emptySet()

        /** 停在闸门上、等测试放行的请求序号 / The request numbers that wait at a gate until the test opens it. */
        val gates: MutableMap<Int, CompletableDeferred<Unit>> = mutableMapOf()

        /** 收到的请求数 / The number of requests received. */
        val requestCount = MutableStateFlow(0)

        /** 在闸门上被取消的请求序号 / The request numbers cancelled at their gate. */
        val cancelledRequests = MutableStateFlow<List<Int>>(emptyList())

        /**
         * 建一个连着这个假服务器的 [SaleEventsViewModel]，client 用和正式 App 一样的插件配置。
         * Builds a [SaleEventsViewModel] wired to this fake server, with a client using the app's real plugin setup.
         */
        fun makeViewModel(): SaleEventsViewModel {
            val engine = MockEngine { request -> answer(request) }
            val client = HttpClient(engine) {
                installAppPlugins(logsRequests = false, cacheStorage = CacheStorage.Unlimited())
            }
            val saleEventsApi = SaleEventsApi(client = client, baseUrl = BASE_URL)
            return SaleEventsViewModel(saleEventsApi = saleEventsApi)
        }

        /**
         * 回答一个请求：记下序号，有闸门就等，然后按序号返回 503 或一条活动。
         * Answers one request: records its number, waits at its gate if any, then returns a 503 or one event.
         */
        private suspend fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
            val requestNumber = requestCount.updateAndGet { count -> count + 1 }

            val gate = gates[requestNumber]
            if (gate != null) {
                try {
                    gate.await()
                } catch (cancellation: CancellationException) {
                    cancelledRequests.update { requests -> requests + requestNumber }
                    throw cancellation
                }
            }

            if (requestNumber in failingRequests) {
                return respond(content = UNAVAILABLE, status = HttpStatusCode.ServiceUnavailable, headers = JSON_HEADERS)
            }
            return respond(content = ONE_EVENT, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }
    }

    private companion object {

        /**
         * 测试用的后端地址。
         * The backend address used in tests.
         */
        const val BASE_URL = "https://example.com/v1"

        /**
         * 不带缓存头的 JSON 响应头。
         * JSON response headers without cache headers.
         */
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

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
