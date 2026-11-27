package io.github.johnwes7.steal.shared.viewmodels

import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.models.settings.AppTheme
import io.github.johnwes7.steal.shared.services.InMemoryPreferencesDataStore
import io.github.johnwes7.steal.shared.services.SettingsRepository
import io.github.johnwes7.steal.shared.services.SteamApi
import io.github.johnwes7.steal.shared.services.installAppPlugins
import io.github.johnwes7.steal.shared.viewmodels.SearchViewModel.SearchState
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * [SearchViewModel] 的状态测试：一个假引擎扮演 Steam 的 `storesearch`，不联网。
 * State tests for [SearchViewModel]: one mock engine plays Steam's `storesearch`, without the network.
 *
 * 地区来自内存里的设置，默认澳大利亚；语言由测试调 `loadIfNeeded` 交进去。写法同 `GameDetailViewModelTest`：
 * `Dispatchers.setMain` 换成测试调度器，用 `StateFlow.first { … }` 等状态，要停在「请求在途中」就让假服务器等闸门。
 * 只有测防抖的几个用例要推进虚拟时间（`advanceTimeBy`），因为要在 500ms 之内接着输入。
 * The region comes from in-memory settings and defaults to Australia; tests hand in the language through
 * `loadIfNeeded`. Written like `GameDetailViewModelTest`: `Dispatchers.setMain` swaps in a test dispatcher, tests
 * wait for states with `StateFlow.first { … }`, and a gate on the fake server holds a request in flight. Only the
 * debounce cases advance virtual time (`advanceTimeBy`), as they type again within 500 ms.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // MARK: - 搜索 / Searching

    /**
     * 空白的搜索词：停在 Idle，不发请求。
     * A blank query stays Idle and sends no request.
     */
    @Test
    fun blankQueryStaysIdle() = runTest {
        val server = FakeServer()
        val viewModel = server.makeViewModel()
        viewModel.loadIfNeeded(steamLanguage = "english")

        viewModel.applyQuery(query = "   ")
        advanceUntilIdle()

        assertEquals(SearchState.Idle, viewModel.searchState.value)
        assertTrue(server.requests.value.isEmpty())
    }

    /**
     * 搜到了：`Loaded` 带去掉空白的搜索词和结果，请求带澳区和英文。
     * Results arrive: `Loaded` carries the trimmed query and the items, and the request is for Australia in
     * English.
     */
    @Test
    fun queryLoadsResults() = runTest {
        val server = FakeServer()
        val viewModel = server.makeViewModel()
        viewModel.loadIfNeeded(steamLanguage = "english")

        viewModel.applyQuery(query = "  elden ring ")

        val state = viewModel.searchState.first { state -> state is SearchState.Loaded }
        val loaded = state as SearchState.Loaded
        assertEquals("elden ring", loaded.query)
        assertEquals("Result elden ring AU english", loaded.items[0].name)
        assertEquals(listOf("elden ring/AU/english"), server.requests.value)
    }

    /**
     * 什么都没搜到是 `NoResults`，不是失败。
     * Matching nothing is `NoResults`, not a failure.
     */
    @Test
    fun noMatchIsNoResults() = runTest {
        val server = FakeServer()
        server.noMatchTerms.add("zzz")
        val viewModel = server.makeViewModel()
        viewModel.loadIfNeeded(steamLanguage = "english")

        viewModel.applyQuery(query = "zzz")

        val state = viewModel.searchState.first { state -> state is SearchState.NoResults }
        assertEquals(SearchState.NoResults(query = "zzz"), state)
    }

    /**
     * 请求失败是 `Failed`，非 2xx 归成 `LoadError.Server`。
     * A failed request is `Failed`, with a non-2xx normalised to `LoadError.Server`.
     */
    @Test
    fun failureIsFailed() = runTest {
        val server = FakeServer()
        server.status = HttpStatusCode.BadGateway
        val viewModel = server.makeViewModel()
        viewModel.loadIfNeeded(steamLanguage = "english")

        viewModel.applyQuery(query = "elden")

        val state = viewModel.searchState.first { state -> state is SearchState.Failed }
        assertEquals(SearchState.Failed(error = LoadError.Server(502)), state)
    }

    /**
     * 清空搜索词马上回到 Idle，不发请求。
     * Clearing the query returns to Idle at once without a request.
     */
    @Test
    fun clearingQueryReturnsToIdle() = runTest {
        val server = FakeServer()
        val viewModel = server.makeViewModel()
        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.applyQuery(query = "elden")
        viewModel.searchState.first { state -> state is SearchState.Loaded }

        viewModel.applyQuery(query = "")

        viewModel.searchState.first { state -> state == SearchState.Idle }
        assertEquals(1, server.requests.value.size)
    }

    // MARK: - 防抖 / Debounce

    /**
     * 500ms 之内接着输入：只搜最后的那个词。
     * Typing again within 500 ms searches only the last query.
     */
    @Test
    fun typingSearchesOnlyLastQuery() = runTest {
        val server = FakeServer()
        val viewModel = server.makeViewModel()
        viewModel.loadIfNeeded(steamLanguage = "english")

        viewModel.applyQuery(query = "el")
        advanceTimeBy(300.milliseconds)
        viewModel.applyQuery(query = "elden")

        viewModel.searchState.first { state -> state is SearchState.Loaded }
        assertEquals(listOf("elden/AU/english"), server.requests.value)
    }

    /**
     * 搜成功后删一个字又打回来：和上次一样，不重发，状态还是原来那个。
     * Deleting a letter and typing it back after a success matches the last search: no new request, and the state
     * stays the same object.
     */
    @Test
    fun sameQueryAfterSuccessIsNotResent() = runTest {
        val server = FakeServer()
        val viewModel = server.makeViewModel()
        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.applyQuery(query = "elden")
        val settled = viewModel.searchState.first { state -> state is SearchState.Loaded }

        viewModel.applyQuery(query = "elde")
        advanceTimeBy(100.milliseconds)
        viewModel.applyQuery(query = "elden")
        advanceUntilIdle()

        assertSame(settled, viewModel.searchState.value)
        assertEquals(1, server.requests.value.size)
    }

    /**
     * 失败后删一个字又打回来：重新搜（没有重试按钮，靠改搜索词重试）。
     * Deleting a letter and typing it back after a failure searches again (there is no retry button; editing the
     * query is the retry).
     */
    @Test
    fun sameQueryAfterFailureSearchesAgain() = runTest {
        val server = FakeServer()
        server.status = HttpStatusCode.BadGateway
        val viewModel = server.makeViewModel()
        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.applyQuery(query = "elden")
        viewModel.searchState.first { state -> state is SearchState.Failed }

        server.status = HttpStatusCode.OK
        viewModel.applyQuery(query = "elde")
        advanceTimeBy(100.milliseconds)
        viewModel.applyQuery(query = "elden")

        viewModel.searchState.first { state -> state is SearchState.Loaded }
        assertEquals(2, server.requests.value.size)
    }

    // MARK: - 加载中 / Loading

    /**
     * 第一次搜、屏幕上没有结果：请求在途中时是 Loading。
     * The first search, with no results on screen, is Loading while the request is in flight.
     */
    @Test
    fun firstSearchShowsLoading() = runTest {
        val server = FakeServer()
        server.gates["elden"] = CompletableDeferred()
        val viewModel = server.makeViewModel()
        viewModel.loadIfNeeded(steamLanguage = "english")

        viewModel.applyQuery(query = "elden")
        server.requests.first { requests -> requests.isNotEmpty() }

        assertEquals(SearchState.Loading, viewModel.searchState.value)
    }

    /**
     * 搜下一个词时上一次的结果留着，新结果回来才换。
     * While the next query is searched the earlier results stay, and are replaced only when the new ones arrive.
     */
    @Test
    fun nextSearchKeepsEarlierResults() = runTest {
        val server = FakeServer()
        val portalGate = CompletableDeferred<Unit>()
        server.gates["portal"] = portalGate
        val viewModel = server.makeViewModel()
        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.applyQuery(query = "elden")
        val settled = viewModel.searchState.first { state -> state is SearchState.Loaded }

        viewModel.applyQuery(query = "portal")
        server.requests.first { requests -> requests.size == 2 }

        assertSame(settled, viewModel.searchState.value)
        portalGate.complete(Unit)
        val state = viewModel.searchState.first { state -> state is SearchState.Loaded && state.query == "portal" }
        assertTrue(state is SearchState.Loaded)
    }

    /**
     * 新的搜索词来了，还在途中的旧请求被取消，结果是新词的。
     * A new query cancels the old request still in flight, and the results belong to the new query.
     */
    @Test
    fun newQueryCancelsInFlightSearch() = runTest {
        val server = FakeServer()
        server.gates["elden"] = CompletableDeferred()
        val viewModel = server.makeViewModel()
        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.applyQuery(query = "elden")
        server.requests.first { requests -> requests.isNotEmpty() }

        viewModel.applyQuery(query = "portal")

        viewModel.searchState.first { state -> state is SearchState.Loaded && state.query == "portal" }
        // 旧请求是被取消的，不是回来以后被忽略的。
        // The old request was cancelled, not answered and then ignored.
        server.cancelledTerms.first { terms -> terms.contains("elden") }
    }

    // MARK: - 地区和语言 / Region and language

    /**
     * 换地区：用同一个词按新地区再搜一次。
     * A region change searches the same query again for the new region.
     */
    @Test
    fun regionChangeSearchesAgain() = runTest {
        val settings = SettingsRepository(dataStore = InMemoryPreferencesDataStore())
        val server = FakeServer()
        val viewModel = server.makeViewModel(settingsRepository = settings)
        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.applyQuery(query = "elden")
        viewModel.searchState.first { state -> state is SearchState.Loaded }

        settings.setRegion(AppRegion.UnitedStates)

        viewModel.searchState.first { state -> state.firstName() == "Result elden US english" }
        assertEquals(listOf("elden/AU/english", "elden/US/english"), server.requests.value)
    }

    /**
     * 换语言：用同一个词按新语言再搜一次。
     * A language change searches the same query again in the new language.
     */
    @Test
    fun languageChangeSearchesAgain() = runTest {
        val server = FakeServer()
        val viewModel = server.makeViewModel()
        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.applyQuery(query = "elden")
        viewModel.searchState.first { state -> state is SearchState.Loaded }

        viewModel.loadIfNeeded(steamLanguage = "schinese")

        viewModel.searchState.first { state -> state.firstName() == "Result elden AU schinese" }
        assertEquals(listOf("elden/AU/english", "elden/AU/schinese"), server.requests.value)
    }

    /**
     * 改别的设置（深浅）、再交一次同样的语言（页面再次出现）：都不重搜，状态还是原来那个。
     * Changing another setting (the theme) or handing in the same language again (the page reappears) doesn't
     * search again; the state stays the same object.
     */
    @Test
    fun unchangedRequestDoesNotSearchAgain() = runTest {
        val settings = SettingsRepository(dataStore = InMemoryPreferencesDataStore())
        val server = FakeServer()
        val viewModel = server.makeViewModel(settingsRepository = settings)
        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.applyQuery(query = "elden")
        val settled = viewModel.searchState.first { state -> state is SearchState.Loaded }

        settings.setTheme(AppTheme.Light)
        viewModel.loadIfNeeded(steamLanguage = "english")
        advanceUntilIdle()

        assertSame(settled, viewModel.searchState.value)
        assertEquals(1, server.requests.value.size)
    }

    // MARK: - 假服务器 / Fake server

    /**
     * 一次测试里的假 Steam：按设好的状态码回答，记下收到的请求，还能用闸门卡住请求。
     * The fake Steam for one test: answers with the configured status code, records the requests it receives, and
     * can hold a request at a gate.
     *
     * 搜到的结果只有一条，名字是「Result <搜索词> <地区> <语言>」，看得出结果来自哪次请求。
     * Each search returns one item named "Result <query> <region> <language>", telling which request it came from.
     */
    private class FakeServer {

        /** 接口的状态码，每次请求时读 / The endpoint's status code, read on every request. */
        var status: HttpStatusCode = HttpStatusCode.OK

        /** 什么都搜不到的搜索词 / Queries that match nothing. */
        val noMatchTerms = mutableSetOf<String>()

        /** 按搜索词卡住请求的闸门 / Gates holding requests, by query. */
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

        /** 收到的请求，「搜索词/地区/语言」，按顺序 / Requests received as "query/region/language", in order. */
        val requests = MutableStateFlow<List<String>>(emptyList())

        /** 在闸门上被取消的请求的搜索词 / The query of each request cancelled at its gate. */
        val cancelledTerms = MutableStateFlow<List<String>>(emptyList())

        /**
         * 建一个连着这个假服务器的 [SearchViewModel]；地区从 [settingsRepository] 读，不传就是澳大利亚。
         * Builds a [SearchViewModel] wired to this fake server; the region comes from [settingsRepository], which
         * defaults to Australia.
         */
        fun makeViewModel(
            settingsRepository: SettingsRepository = SettingsRepository(dataStore = InMemoryPreferencesDataStore()),
        ): SearchViewModel {
            val engine = MockEngine { request -> answer(request) }
            val client = HttpClient(engine) {
                installAppPlugins(logsRequests = false, cacheStorage = CacheStorage.Unlimited())
            }
            return SearchViewModel(steamApi = SteamApi(client = client), settingsRepository = settingsRepository)
        }

        private suspend fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
            if (request.url.encodedPath != "/api/storesearch") {
                error("没想到的请求 / Unexpected request: ${request.url}")
            }
            val term = request.url.parameters["term"].orEmpty()
            val country = request.url.parameters["cc"]
            val language = request.url.parameters["l"]
            requests.update { received -> received + "$term/$country/$language" }

            val gate = gates[term]
            if (gate != null) {
                try {
                    gate.await()
                } catch (cancellation: CancellationException) {
                    cancelledTerms.update { terms -> terms + term }
                    throw cancellation
                }
            }

            if (status != HttpStatusCode.OK) {
                return respond(content = "", status = status, headers = JSON_HEADERS)
            }
            if (term in noMatchTerms) {
                return respond(content = """{"total":0,"items":[]}""", status = status, headers = JSON_HEADERS)
            }
            val item = """{"type":"app","name":"Result $term $country $language","id":1}"""
            val body = """{"total":1,"items":[$item]}"""
            return respond(content = body, status = status, headers = JSON_HEADERS)
        }
    }

    private companion object {

        /** JSON 响应头 / The JSON response header. */
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}

/**
 * 第一条结果的名字；不是 `Loaded` 时是 `null`。
 * The first result's name, or `null` when the state is not `Loaded`.
 */
private fun SearchState.firstName(): String? {
    if (this !is SearchState.Loaded) {
        return null
    }
    return items.first().name
}
