package io.github.johnwes7.steal.shared.viewmodels

import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.models.settings.AppTheme
import io.github.johnwes7.steal.shared.models.steam.SteamAppDetails
import io.github.johnwes7.steal.shared.services.InMemoryPreferencesDataStore
import io.github.johnwes7.steal.shared.services.ItadApi
import io.github.johnwes7.steal.shared.services.SettingsRepository
import io.github.johnwes7.steal.shared.services.SteamApi
import io.github.johnwes7.steal.shared.services.appNetworkJson
import io.github.johnwes7.steal.shared.services.installAppPlugins
import io.github.johnwes7.steal.shared.viewmodels.GameDetailViewModel.DetailLoadState
import io.github.johnwes7.steal.shared.viewmodels.GameDetailViewModel.InfoLoadState
import io.github.johnwes7.steal.shared.viewmodels.GameDetailViewModel.PricesLoadState
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

/**
 * [GameDetailViewModel] 的状态测试：一个假引擎同时扮演 Steam 和 ITAD，按路径回答，不联网。
 * State tests for [GameDetailViewModel]: one mock engine plays both Steam and ITAD, answering by path, without the
 * network.
 *
 * 地区来自内存里的设置，默认澳大利亚；语言由测试调 `loadIfNeeded` 交进去。写法同 `GamesViewModelTest`：
 * `Dispatchers.setMain` 换成测试调度器，用 `StateFlow.first { … }` 等状态，要停在「请求在途中」就让假服务器等闸门。
 * The region comes from in-memory settings and defaults to Australia; tests hand in the language through
 * `loadIfNeeded`. Written like `GamesViewModelTest`: `Dispatchers.setMain` swaps in a test dispatcher, tests wait
 * for states with `StateFlow.first { … }`, and a gate on the fake server holds a request in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailViewModelTest {

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
     * 从首页进来（带 UUID）：不查 UUID，Steam、比价、信息各请求一次，都带澳区和英文。
     * Entered from the home page (with a UUID): no UUID lookup, and Steam, prices and info are each requested once,
     * all for Australia in English.
     */
    @Test
    fun homePathLoadsEverySection() = runTest {
        val servers = FakeServers()
        val viewModel = servers.makeViewModel(uuid = GAME_UUID)

        viewModel.loadIfNeeded(steamLanguage = "english")

        val detail = viewModel.steamDetailState.first { state -> state is DetailLoadState.Loaded }
        assertEquals("Game AU english", detail.title(fallback = TITLE))
        val prices = viewModel.itadPricesState.first { state -> state is PricesLoadState.Loaded }
        assertTrue(prices is PricesLoadState.Loaded)
        viewModel.itadInfoState.first { state -> state is InfoLoadState.Loaded }

        assertEquals(listOf("AU/english"), servers.steamRequests.value)
        assertEquals(listOf("AU"), servers.pricesCountries.value)
        assertEquals(1, servers.infoRequestCount.value)
        assertEquals(0, servers.lookupRequestCount.value)
    }

    /**
     * 从搜索进来（没有 UUID）：只查一次 UUID，比价和信息共用。
     * Entered from search (no UUID): the UUID is looked up once and shared by the prices and the info.
     */
    @Test
    fun searchPathLooksUpUUIDOnce() = runTest {
        val servers = FakeServers()
        val viewModel = servers.makeViewModel(uuid = null)

        viewModel.loadIfNeeded(steamLanguage = "english")

        viewModel.itadPricesState.first { state -> state is PricesLoadState.Loaded }
        viewModel.itadInfoState.first { state -> state is InfoLoadState.Loaded }
        assertEquals(1, servers.lookupRequestCount.value)
    }

    // MARK: - 游戏类型 / Kinds of game

    /**
     * 试玩版：两块 ITAD 都不显示，也不发任何 ITAD 请求。
     * A demo: neither ITAD section shows, and no ITAD request is sent.
     */
    @Test
    fun demoSkipsItad() = runTest {
        val servers = FakeServers()
        servers.detailsData = DEMO_DATA
        val viewModel = servers.makeViewModel(uuid = null)

        viewModel.loadIfNeeded(steamLanguage = "english")

        viewModel.steamDetailState.first { state -> state is DetailLoadState.Loaded }
        advanceUntilIdle()
        assertEquals(PricesLoadState.Hidden, viewModel.itadPricesState.value)
        assertEquals(InfoLoadState.Hidden, viewModel.itadInfoState.value)
        assertEquals(0, servers.lookupRequestCount.value)
        assertTrue(servers.pricesCountries.value.isEmpty())
        assertEquals(0, servers.infoRequestCount.value)
    }

    /**
     * 未发售、还没开预购（查不到比价）：比价藏起来，不算「没有比价」。
     * Unreleased and not open for pre-order (no prices found): the prices are hidden rather than "no prices".
     */
    @Test
    fun unreleasedWithoutPricesIsHidden() = runTest {
        val servers = FakeServers()
        servers.detailsData = UNRELEASED_DATA
        servers.hasPrices = false
        val viewModel = servers.makeViewModel(uuid = GAME_UUID)

        viewModel.loadIfNeeded(steamLanguage = "english")

        viewModel.itadInfoState.first { state -> state is InfoLoadState.Loaded }
        servers.pricesCountries.first { countries -> countries.isNotEmpty() }
        val prices = viewModel.itadPricesState.first { state -> state != PricesLoadState.Loading }
        assertEquals(PricesLoadState.Hidden, prices)
    }

    /**
     * 付费游戏查不到比价：「没有比价」，不给重试。
     * A paid game with no prices found: "no prices", with no retry.
     */
    @Test
    fun paidWithoutPricesIsNotFound() = runTest {
        val servers = FakeServers()
        servers.hasPrices = false
        val viewModel = servers.makeViewModel(uuid = GAME_UUID)

        viewModel.loadIfNeeded(steamLanguage = "english")

        val prices = viewModel.itadPricesState.first { state -> state == PricesLoadState.NotFound }
        assertEquals(PricesLoadState.NotFound, prices)
    }

    /**
     * 永久免费：比价照查，但不带历史低价。
     * Free forever: the prices are still fetched, minus the historical lows.
     */
    @Test
    fun freeForeverHasNoHistory() = runTest {
        val servers = FakeServers()
        servers.detailsData = FREE_FOREVER_DATA
        val viewModel = servers.makeViewModel(uuid = GAME_UUID)

        viewModel.loadIfNeeded(steamLanguage = "english")

        val prices = viewModel.itadPricesState.first { state -> state is PricesLoadState.LoadedWithoutHistory }
        assertTrue(prices is PricesLoadState.LoadedWithoutHistory)
    }

    // MARK: - 查不到与失败 / Misses and failures

    /**
     * Steam 上没有：整页「没找到」，不发 ITAD 请求。
     * Steam does not have it: the page is "not found", and no ITAD request is sent.
     */
    @Test
    fun missingGameIsNotFound() = runTest {
        val servers = FakeServers()
        servers.detailsData = null
        val viewModel = servers.makeViewModel(uuid = null)

        viewModel.loadIfNeeded(steamLanguage = "english")

        val detail = viewModel.steamDetailState.first { state -> state == DetailLoadState.NotFound }
        assertEquals(TITLE, detail.title(fallback = TITLE))
        advanceUntilIdle()
        assertEquals(0, servers.lookupRequestCount.value)
        assertEquals(PricesLoadState.Hidden, viewModel.itadPricesState.value)
    }

    /**
     * ITAD 没收录：比价「没有比价」、信息「没收录」，都不再发请求。
     * Not listed on ITAD: the prices are "no prices" and the info "not listed", with no further requests.
     */
    @Test
    fun notListedOnItadSettlesBothSections() = runTest {
        val servers = FakeServers()
        servers.isListedOnItad = false
        val viewModel = servers.makeViewModel(uuid = null)

        viewModel.loadIfNeeded(steamLanguage = "english")

        viewModel.itadPricesState.first { state -> state == PricesLoadState.NotFound }
        viewModel.itadInfoState.first { state -> state == InfoLoadState.NotFound }
        assertTrue(servers.pricesCountries.value.isEmpty())
        assertEquals(0, servers.infoRequestCount.value)
    }

    /**
     * 查 UUID 的请求失败：两块都失败；之后重试信息会再查一次 UUID 并成功。
     * The UUID lookup fails: both sections fail; retrying the info then looks the UUID up again and succeeds.
     */
    @Test
    fun lookupFailureFailsBothSectionsUntilRetry() = runTest {
        val servers = FakeServers()
        servers.lookupStatus = HttpStatusCode.InternalServerError
        val viewModel = servers.makeViewModel(uuid = null)
        viewModel.loadIfNeeded(steamLanguage = "english")

        viewModel.itadPricesState.first { state -> state == PricesLoadState.Failed }
        viewModel.itadInfoState.first { state -> state == InfoLoadState.Failed }

        servers.lookupStatus = HttpStatusCode.OK
        viewModel.retryInfo()

        viewModel.itadInfoState.first { state -> state is InfoLoadState.Loaded }
        assertEquals(2, servers.lookupRequestCount.value)
        assertEquals(PricesLoadState.Failed, viewModel.itadPricesState.value)
    }

    /**
     * Steam 请求失败：整页 `Failed(Server(500))`；重试先切回 `Loading`，再成功。
     * The Steam request fails: the page is `Failed(Server(500))`; a retry goes back to `Loading` and then succeeds.
     */
    @Test
    fun detailFailureRecoversOnRetry() = runTest {
        val servers = FakeServers()
        servers.detailsStatus = HttpStatusCode.InternalServerError
        val viewModel = servers.makeViewModel(uuid = GAME_UUID)
        viewModel.loadIfNeeded(steamLanguage = "english")

        val failed = viewModel.steamDetailState.first { state -> state is DetailLoadState.Failed }
        assertEquals(DetailLoadState.Failed(error = LoadError.Server(statusCode = 500)), failed)
        assertEquals(TITLE, failed.title(fallback = TITLE))

        servers.detailsStatus = HttpStatusCode.OK
        val steamGate = CompletableDeferred<Unit>()
        servers.steamGate = steamGate
        viewModel.retry()

        viewModel.steamDetailState.first { state -> state == DetailLoadState.Loading }
        steamGate.complete(Unit)
        viewModel.steamDetailState.first { state -> state is DetailLoadState.Loaded }
        viewModel.itadPricesState.first { state -> state is PricesLoadState.Loaded }
        assertEquals(listOf("AU/english", "AU/english"), servers.steamRequests.value)
    }

    /**
     * 比价失败后重试：只重查比价、用记下的 UUID；信息不受影响。
     * Retrying the prices after a failure refetches only them with the remembered UUID; the info is untouched.
     */
    @Test
    fun retryPricesRefetchesOnlyPrices() = runTest {
        val servers = FakeServers()
        servers.pricesStatus = HttpStatusCode.InternalServerError
        val viewModel = servers.makeViewModel(uuid = null)
        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.itadPricesState.first { state -> state == PricesLoadState.Failed }
        val infoBefore = viewModel.itadInfoState.first { state -> state is InfoLoadState.Loaded }

        servers.pricesStatus = HttpStatusCode.OK
        viewModel.retryPrices()

        viewModel.itadPricesState.first { state -> state is PricesLoadState.Loaded }
        // 要是误重查了信息，它的协程会在这里跑、先切到 Loading，状态对象就换了。
        // A mistaken info refetch would run here and switch to Loading first, replacing the state object.
        advanceUntilIdle()
        assertSame(infoBefore, viewModel.itadInfoState.value)
        assertEquals(1, servers.lookupRequestCount.value)
        assertEquals(1, servers.infoRequestCount.value)
        assertEquals(listOf("AU", "AU"), servers.pricesCountries.value)
    }

    // MARK: - 地区和语言 / Region and language

    /**
     * 换地区：整页按新地区重新加载，Steam 和比价都带新地区。
     * A region change reloads the whole page for the new region, in both the Steam and the prices requests.
     */
    @Test
    fun regionChangeReloads() = runTest {
        val settings = SettingsRepository(dataStore = InMemoryPreferencesDataStore())
        val servers = FakeServers()
        val viewModel = servers.makeViewModel(uuid = GAME_UUID, settingsRepository = settings)
        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.itadPricesState.first { state -> state is PricesLoadState.Loaded }

        settings.setRegion(AppRegion.UnitedStates)

        servers.pricesCountries.first { countries -> countries.contains("US") }
        val detail = viewModel.steamDetailState.first { state -> state.title(fallback = TITLE) == "Game US english" }
        assertTrue(detail is DetailLoadState.Loaded)
        viewModel.itadPricesState.first { state -> state is PricesLoadState.Loaded }
        assertEquals(listOf("AU/english", "US/english"), servers.steamRequests.value)
        assertEquals(listOf("AU", "US"), servers.pricesCountries.value)
    }

    /**
     * 换语言：整页按新语言重新加载，标题跟着变。
     * A language change reloads the whole page in the new language, and the title follows.
     */
    @Test
    fun languageChangeReloads() = runTest {
        val servers = FakeServers()
        val viewModel = servers.makeViewModel(uuid = GAME_UUID)
        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.steamDetailState.first { state -> state is DetailLoadState.Loaded }

        viewModel.loadIfNeeded(steamLanguage = "schinese")

        viewModel.steamDetailState.first { state -> state.title(fallback = TITLE) == "Game AU schinese" }
        assertEquals(listOf("AU/english", "AU/schinese"), servers.steamRequests.value)
    }

    /**
     * 同样的语言再交一次（页面再次出现）：不重新加载，状态还是原来那个。
     * Handing in the same language again (the page reappears) doesn't reload; the state stays the same object.
     */
    @Test
    fun sameLanguageDoesNotReload() = runTest {
        val servers = FakeServers()
        val viewModel = servers.makeViewModel(uuid = GAME_UUID)
        viewModel.loadIfNeeded(steamLanguage = "english")
        val settled = viewModel.steamDetailState.first { state -> state is DetailLoadState.Loaded }
        viewModel.itadPricesState.first { state -> state is PricesLoadState.Loaded }

        viewModel.loadIfNeeded(steamLanguage = "english")
        // 要是误重载了，排队的协程会在这里跑，状态会变成 Loading。
        // A mistaken reload would run its queued coroutine here and switch the state to Loading.
        advanceUntilIdle()

        assertSame(settled, viewModel.steamDetailState.value)
        assertEquals(listOf("AU/english"), servers.steamRequests.value)
    }

    /**
     * 改别的设置（深浅）不重新加载：整份设置会再发一次，但地区没变。
     * Changing another setting (the theme) doesn't reload: the whole settings are emitted again, but the region is
     * unchanged.
     */
    @Test
    fun otherSettingChangeDoesNotReload() = runTest {
        val settings = SettingsRepository(dataStore = InMemoryPreferencesDataStore())
        val servers = FakeServers()
        val viewModel = servers.makeViewModel(uuid = GAME_UUID, settingsRepository = settings)
        viewModel.loadIfNeeded(steamLanguage = "english")
        val settled = viewModel.steamDetailState.first { state -> state is DetailLoadState.Loaded }
        viewModel.itadPricesState.first { state -> state is PricesLoadState.Loaded }

        settings.setTheme(AppTheme.Light)
        advanceUntilIdle()

        assertSame(settled, viewModel.steamDetailState.value)
        assertEquals(listOf("AU/english"), servers.steamRequests.value)
    }

    /**
     * 旧地区的比价还在途中就换了地区：旧请求被取消，比价是新地区的。
     * The region changes while the old region's prices are still in flight: the old request is cancelled and the
     * prices belong to the new region.
     */
    @Test
    fun reloadCancelsInFlightPrices() = runTest {
        val settings = SettingsRepository(dataStore = InMemoryPreferencesDataStore())
        val servers = FakeServers()
        servers.pricesGates["AU"] = CompletableDeferred()
        val viewModel = servers.makeViewModel(uuid = GAME_UUID, settingsRepository = settings)
        viewModel.loadIfNeeded(steamLanguage = "english")
        // 等澳区的比价请求真的到了服务器、卡在闸门上。
        // Wait until the Australian prices request has really reached the server and is held at the gate.
        servers.pricesCountries.first { countries -> countries.contains("AU") }

        settings.setRegion(AppRegion.UnitedStates)

        servers.pricesCountries.first { countries -> countries.contains("US") }
        val prices = viewModel.itadPricesState.first { state -> state is PricesLoadState.Loaded }
        assertEquals("USD", (prices as PricesLoadState.Loaded).prices.allTimeLow?.currency)
        // 旧请求是被取消的，不是回来以后被忽略的。
        // The old request was cancelled, not answered and then ignored.
        servers.cancelledPricesCountries.first { countries -> countries.contains("AU") }
    }

    // MARK: - 标题 / Title

    /**
     * 有 Steam 名字用它；加载中、没找到、失败、名字为空都用 fallback。
     * Steam's name when there is one; the fallback while loading, when not found, after a failure, or for an empty
     * name.
     */
    @Test
    fun titlePrefersSteamName() {
        val named = appNetworkJson.decodeFromString<SteamAppDetails>("""{"name":"Steam Name"}""")
        val unnamed = appNetworkJson.decodeFromString<SteamAppDetails>("""{"name":""}""")

        assertEquals("Steam Name", DetailLoadState.Loaded(details = named).title(fallback = TITLE))
        assertEquals(TITLE, DetailLoadState.Loaded(details = unnamed).title(fallback = TITLE))
        assertEquals(TITLE, DetailLoadState.Loading.title(fallback = TITLE))
        assertEquals(TITLE, DetailLoadState.NotFound.title(fallback = TITLE))
        assertEquals(TITLE, DetailLoadState.Failed(error = LoadError.Offline).title(fallback = TITLE))
    }

    // MARK: - 假服务器 / Fake servers

    /**
     * 一次测试里的假 Steam 和 ITAD：按设好的内容和状态码回答，记下收到的请求，还能用闸门卡住请求。
     * The fake Steam and ITAD for one test: answers with the configured content and status codes, records the
     * requests it receives, and can hold a request at a gate.
     *
     * Steam 返回的名字是「Game <地区> <语言>」，用来看出是哪次请求的结果；比价的货币按地区（AU → AUD，其他 USD）。
     * Steam's name is "Game <region> <language>", telling which request a result came from; the prices' currency
     * follows the region (AU → AUD, anything else USD).
     */
    private class FakeServers {

        /** Steam 详情 `data` 里除名字外的字段；`null` 是 `success:false` / Steam's `data` fields besides the name; `null` means `success:false`. */
        var detailsData: String? = PAID_DATA

        /** Steam 接口的状态码 / The Steam endpoint's status code. */
        var detailsStatus: HttpStatusCode = HttpStatusCode.OK

        /** 卡住下一个 Steam 请求的闸门，`null` 是马上回答 / The gate holding Steam requests; `null` answers at once. */
        var steamGate: CompletableDeferred<Unit>? = null

        /** ITAD 有没有收录这款游戏 / Whether ITAD lists the game. */
        var isListedOnItad: Boolean = true

        /** lookup 接口的状态码 / The lookup endpoint's status code. */
        var lookupStatus: HttpStatusCode = HttpStatusCode.OK

        /** 比价里有没有这款游戏 / Whether the prices include the game. */
        var hasPrices: Boolean = true

        /** 比价接口的状态码 / The prices endpoint's status code. */
        var pricesStatus: HttpStatusCode = HttpStatusCode.OK

        /** 按地区代码卡住比价请求的闸门 / Gates holding prices requests, by region code. */
        val pricesGates = mutableMapOf<String, CompletableDeferred<Unit>>()

        /** 收到的 Steam 请求，「地区/语言」，按顺序 / Steam requests received as "region/language", in order. */
        val steamRequests = MutableStateFlow<List<String>>(emptyList())

        /** 收到的 lookup 请求数 / The number of lookup requests received. */
        val lookupRequestCount = MutableStateFlow(0)

        /** 收到的比价请求里的地区，按顺序 / The region of each prices request received, in order. */
        val pricesCountries = MutableStateFlow<List<String>>(emptyList())

        /** 在闸门上被取消的比价请求的地区 / The region of each prices request cancelled at its gate. */
        val cancelledPricesCountries = MutableStateFlow<List<String>>(emptyList())

        /** 收到的信息请求数 / The number of info requests received. */
        val infoRequestCount = MutableStateFlow(0)

        /**
         * 建一个连着这个假服务器的 [GameDetailViewModel]；地区从 [settingsRepository] 读，不传就是澳大利亚。
         * Builds a [GameDetailViewModel] wired to this fake server; the region comes from [settingsRepository],
         * which defaults to Australia.
         */
        fun makeViewModel(
            uuid: String?,
            settingsRepository: SettingsRepository = SettingsRepository(dataStore = InMemoryPreferencesDataStore()),
        ): GameDetailViewModel {
            val engine = MockEngine { request -> answer(request) }
            val client = HttpClient(engine) {
                installAppPlugins(logsRequests = false, cacheStorage = CacheStorage.Unlimited())
            }
            return GameDetailViewModel(
                appID = APP_ID,
                uuid = uuid,
                initialTitle = TITLE,
                steamApi = SteamApi(client = client),
                itadApi = ItadApi(client = client, apiKey = "test-key"),
                settingsRepository = settingsRepository,
            )
        }

        /**
         * 按路径分给各接口回答。
         * Routes a request to the matching endpoint by path.
         */
        private suspend fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
            val path = request.url.encodedPath
            if (path == "/api/appdetails") {
                return answerDetails(request)
            }
            if (path == "/games/lookup/v1") {
                return answerLookup()
            }
            if (path == "/games/prices/v3") {
                return answerPrices(request)
            }
            if (path == "/games/info/v2") {
                infoRequestCount.update { count -> count + 1 }
                return respond(content = INFO_BODY, status = HttpStatusCode.OK, headers = JSON_HEADERS)
            }
            error("没想到的请求 / Unexpected request: ${request.url}")
        }

        private suspend fun MockRequestHandleScope.answerDetails(request: HttpRequestData): HttpResponseData {
            val country = request.url.parameters["cc"]
            val language = request.url.parameters["l"]
            steamRequests.update { requests -> requests + "$country/$language" }
            steamGate?.await()

            val data = detailsData
            val body = if (data == null) {
                """{"$APP_ID":{"success":false}}"""
            } else {
                """{"$APP_ID":{"success":true,"data":{"name":"Game $country $language",$data}}}"""
            }
            return respond(content = body, status = detailsStatus, headers = JSON_HEADERS)
        }

        private fun MockRequestHandleScope.answerLookup(): HttpResponseData {
            lookupRequestCount.update { count -> count + 1 }
            if (lookupStatus != HttpStatusCode.OK) {
                return respond(content = "", status = lookupStatus, headers = JSON_HEADERS)
            }
            val body = if (isListedOnItad) {
                """{"found":true,"game":{"id":"$GAME_UUID","slug":"game","title":"Game","type":"game"}}"""
            } else {
                """{"found":false}"""
            }
            return respond(content = body, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        private suspend fun MockRequestHandleScope.answerPrices(request: HttpRequestData): HttpResponseData {
            val country = request.url.parameters["country"].orEmpty()
            pricesCountries.update { countries -> countries + country }

            val gate = pricesGates[country]
            if (gate != null) {
                try {
                    gate.await()
                } catch (cancellation: CancellationException) {
                    cancelledPricesCountries.update { countries -> countries + country }
                    throw cancellation
                }
            }

            if (!hasPrices) {
                return respond(content = "[]", status = pricesStatus, headers = JSON_HEADERS)
            }
            val currency = if (country == "AU") "AUD" else "USD"
            val price = """{"amount":9.99,"amountInt":999,"currency":"$currency"}"""
            val body = """[{"id":"$GAME_UUID","historyLow":{"all":$price,"y1":null,"m3":null},"deals":[]}]"""
            return respond(content = body, status = pricesStatus, headers = JSON_HEADERS)
        }
    }

    private companion object {

        /** 测试用的 Steam appID / The Steam appID in tests. */
        const val APP_ID = 100

        /** 测试用的 ITAD UUID / The ITAD UUID in tests. */
        const val GAME_UUID = "game-uuid"

        /** 导航传进来的标题 / The title passed in by navigation. */
        const val TITLE = "Route Title"

        /** 付费游戏 / A paid game. */
        const val PAID_DATA =
            """"type":"game","is_free":false,"release_date":{"coming_soon":false,"date":"1 Jan, 2020"},"price_overview":{"currency":"AUD","initial":1000,"final":1000,"discount_percent":0}"""

        /** 试玩版 / A demo. */
        const val DEMO_DATA = """"type":"demo","is_free":true,"fullgame":{"appid":"200","name":"Full Game"}"""

        /** 未发售、没开预购 / Unreleased, not open for pre-order. */
        const val UNRELEASED_DATA = """"type":"game","is_free":false,"release_date":{"coming_soon":true,"date":"2027"}"""

        /** 永久免费 / Free forever. */
        const val FREE_FOREVER_DATA = """"type":"game","is_free":true,"release_date":{"coming_soon":false,"date":"1 Jan, 2020"}"""

        /** 只有必填字段的游戏信息 / Game info with only the required fields. */
        const val INFO_BODY = """{"id":"game-uuid","slug":"game","title":"Game","type":"game","mature":false}"""

        /** JSON 响应头 / The JSON response header. */
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
