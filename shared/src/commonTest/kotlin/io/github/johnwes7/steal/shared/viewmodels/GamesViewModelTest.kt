package io.github.johnwes7.steal.shared.viewmodels

import io.github.johnwes7.steal.shared.models.app.HomeDealItem.SteamLink
import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.models.settings.AppTheme
import io.github.johnwes7.steal.shared.services.InMemoryPreferencesDataStore
import io.github.johnwes7.steal.shared.services.ItadApi
import io.github.johnwes7.steal.shared.services.SettingsRepository
import io.github.johnwes7.steal.shared.services.SteamApi
import io.github.johnwes7.steal.shared.services.SteamAppIDResolver
import io.github.johnwes7.steal.shared.services.installAppPlugins
import io.github.johnwes7.steal.shared.viewmodels.GamesViewModel.DealLoadState
import io.github.johnwes7.steal.shared.viewmodels.GamesViewModel.GiveawaysLoadState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [GamesViewModel] 的状态测试：一个假引擎扮演 ITAD，按路径回答 deals、giveaways 与 lookup，不联网。
 * State tests for [GamesViewModel]: one mock engine plays ITAD, answering deals, giveaways and lookups by path,
 * without the network.
 *
 * 地区来自内存里的设置，默认澳大利亚；换地区的测试经 [SettingsRepository.setRegion] 改它，同设置页。
 * The region comes from in-memory settings and defaults to Australia; tests that change it call
 * [SettingsRepository.setRegion], as the settings screen does.
 *
 * `viewModelScope` 固定跑在 `Dispatchers.Main` 上，测试前用 `Dispatchers.setMain` 换成测试调度器（Android 官方
 * 「Coroutines best practices」的做法），测试后换回来。请求在假引擎自己的线程上回答，所以用 `StateFlow.first { … }`
 * 等状态变成想要的样子，而不是推进虚拟时间。
 * `viewModelScope` always runs on `Dispatchers.Main`, so each test swaps it for a test dispatcher with
 * `Dispatchers.setMain` (as in Android's "Coroutines best practices") and swaps it back afterwards. The mock engine
 * answers on its own threads, so tests wait with `StateFlow.first { … }` for the state they expect instead of
 * advancing virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GamesViewModelTest {

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
     * 列表先发布、每条都是 `Pending`，补 appID 回来后才写成 `Resolved` / `NotFound`；giveaways 同时加载。
     * The list is published first with every row `Pending`, and only becomes `Resolved` / `NotFound` once the lookup
     * returns; the giveaways load alongside.
     */
    @Test
    fun firstLoadPublishesListBeforeResolvingAppIDs() = runTest {
        val servers = FakeServers()
        val lookupGate = CompletableDeferred<Unit>()
        servers.lookupGate = lookupGate
        val viewModel = servers.makeViewModel()

        viewModel.loadIfNeeded()

        val published = viewModel.dealState.first { state -> state is DealLoadState.Loaded }
        assertEquals(listOf(SteamLink.Pending, SteamLink.Pending), steamLinks(published))

        lookupGate.complete(Unit)
        val settled = viewModel.dealState.first { state -> isSettled(state) }
        assertEquals(listOf(SteamLink.Resolved(appID = FOUND_APP_ID), SteamLink.NotFound), steamLinks(settled))
        assertEquals(listOf("AU-found", "AU-missing"), itadUUIDs(settled))

        val giveaways = viewModel.giveawaysState.first { state -> state is GiveawaysLoadState.Loaded }
        assertEquals(1, (giveaways as GiveawaysLoadState.Loaded).giveaways.size)
        assertEquals(listOf("AU"), servers.dealsCountries.value)
    }

    /**
     * 补 appID 的请求失败：列表还在，还在等的条目都标成 `Failed`。
     * The appID lookup fails: the list stays, and every row still waiting becomes `Failed`.
     */
    @Test
    fun lookupFailureMarksWaitingRowsFailed() = runTest {
        val servers = FakeServers()
        servers.lookupStatus = HttpStatusCode.InternalServerError
        val viewModel = servers.makeViewModel()

        viewModel.loadIfNeeded()

        val settled = viewModel.dealState.first { state -> isSettled(state) }
        assertEquals(listOf(SteamLink.Failed, SteamLink.Failed), steamLinks(settled))
    }

    // MARK: - 失败与重试 / Failures and retries

    /**
     * deals 失败只影响 deals：它是 `Failed(Server(500))`，giveaways 照常加载。
     * A deals failure affects only the deals: they become `Failed(Server(500))` while the giveaways load as usual.
     */
    @Test
    fun dealsFailureLeavesGiveawaysAlone() = runTest {
        val servers = FakeServers()
        servers.dealsStatus = HttpStatusCode.InternalServerError
        val viewModel = servers.makeViewModel()

        viewModel.loadIfNeeded()

        val failed = viewModel.dealState.first { state -> state is DealLoadState.Failed }
        assertEquals(DealLoadState.Failed(error = LoadError.Server(statusCode = 500)), failed)
        val giveaways = viewModel.giveawaysState.first { state -> state is GiveawaysLoadState.Loaded }
        assertTrue(giveaways is GiveawaysLoadState.Loaded)
    }

    /**
     * deals 失败后重试成功；重试只请求 deals，不碰 giveaways。
     * Retrying the deals after a failure succeeds; the retry requests only the deals, leaving the giveaways alone.
     */
    @Test
    fun retryDealsRecoversWithoutRefetchingGiveaways() = runTest {
        val servers = FakeServers()
        servers.dealsStatus = HttpStatusCode.InternalServerError
        val viewModel = servers.makeViewModel()
        viewModel.loadIfNeeded()
        viewModel.dealState.first { state -> state is DealLoadState.Failed }
        val giveawaysBefore = viewModel.giveawaysState.first { state -> state is GiveawaysLoadState.Loaded }

        servers.dealsStatus = HttpStatusCode.OK
        viewModel.retryDeals()

        val settled = viewModel.dealState.first { state -> isSettled(state) }
        assertEquals(listOf("AU-found", "AU-missing"), itadUUIDs(settled))
        // 要是误启动了 giveaways，它的协程会在这里跑、先切到 Loading，状态对象就换了。
        // A mistakenly started giveaways load would run here and switch to Loading first, replacing the state object.
        advanceUntilIdle()
        assertSame(giveawaysBefore, viewModel.giveawaysState.value)
        assertEquals(1, servers.giveawaysRequestCount.value)
    }

    // MARK: - 地区 / Region

    /**
     * 再调用 `loadIfNeeded` 不会再订阅一次：状态还是原来那个，不切回 `Loading`，也不多发请求。
     * Calling `loadIfNeeded` again doesn't subscribe a second time: the state stays the same object, never goes
     * back to `Loading`, and no extra requests are sent.
     */
    @Test
    fun secondCallDoesNotSubscribeAgain() = runTest {
        val servers = FakeServers()
        val viewModel = servers.makeViewModel()
        viewModel.loadIfNeeded()
        val settled = viewModel.dealState.first { state -> isSettled(state) }
        viewModel.giveawaysState.first { state -> state is GiveawaysLoadState.Loaded }

        viewModel.loadIfNeeded()
        // 要是又订阅了一次，新的订阅会在这里收到地区、开始加载，状态会变成 Loading。
        // A second subscription would receive the region here and start loading, switching the state to Loading.
        advanceUntilIdle()

        assertSame(settled, viewModel.dealState.value)
        assertEquals(listOf("AU"), servers.dealsCountries.value)
        assertEquals(1, servers.giveawaysRequestCount.value)
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
        val viewModel = servers.makeViewModel(settingsRepository = settings)
        viewModel.loadIfNeeded()
        val settled = viewModel.dealState.first { state -> isSettled(state) }
        viewModel.giveawaysState.first { state -> state is GiveawaysLoadState.Loaded }

        settings.setTheme(AppTheme.Light)
        // 要是误重载了，排队的协程会在这里跑，状态会变成 Loading。
        // A mistaken reload would run its queued coroutine here and switch the state to Loading.
        advanceUntilIdle()

        assertSame(settled, viewModel.dealState.value)
        assertEquals(listOf("AU"), servers.dealsCountries.value)
    }

    /**
     * 换地区只重新加载 deals（请求带新地区），giveaways 不重新请求。
     * A region change reloads only the deals, with the new region in the request; the giveaways are not refetched.
     */
    @Test
    fun regionChangeReloadsOnlyDeals() = runTest {
        val settings = SettingsRepository(dataStore = InMemoryPreferencesDataStore())
        val servers = FakeServers()
        val viewModel = servers.makeViewModel(settingsRepository = settings)
        viewModel.loadIfNeeded()
        viewModel.dealState.first { state -> isSettled(state) }
        val giveawaysBefore = viewModel.giveawaysState.first { state -> state is GiveawaysLoadState.Loaded }

        settings.setRegion(AppRegion.UnitedStates)

        val settled = viewModel.dealState.first { state -> isSettled(state) && itadUUIDs(state).contains("US-found") }
        assertEquals(listOf("US-found", "US-missing"), itadUUIDs(settled))
        assertEquals(listOf("AU", "US"), servers.dealsCountries.value)
        // 要是误启动了 giveaways，它的协程会在这里跑、先切到 Loading，状态对象就换了。
        // A mistakenly started giveaways load would run here and switch to Loading first, replacing the state object.
        advanceUntilIdle()
        assertSame(giveawaysBefore, viewModel.giveawaysState.value)
        assertEquals(1, servers.giveawaysRequestCount.value)
    }

    /**
     * 旧地区的 deals 还在途中就换了地区：旧请求被取消，列表是新地区的。
     * The region changes while the old region's deals are still in flight: the old request is cancelled and the
     * list belongs to the new region.
     */
    @Test
    fun regionChangeCancelsInFlightDeals() = runTest {
        val settings = SettingsRepository(dataStore = InMemoryPreferencesDataStore())
        val servers = FakeServers()
        val australiaGate = CompletableDeferred<Unit>()
        servers.dealsGates["AU"] = australiaGate
        val viewModel = servers.makeViewModel(settingsRepository = settings)

        viewModel.loadIfNeeded()
        // 等澳区的请求真的到了服务器、卡在闸门上。
        // Wait until the Australian request has really reached the server and is held at the gate.
        servers.dealsCountries.first { countries -> countries.contains("AU") }

        settings.setRegion(AppRegion.UnitedStates)

        val settled = viewModel.dealState.first { state -> isSettled(state) }
        assertEquals(listOf("US-found", "US-missing"), itadUUIDs(settled))
        // 旧请求是被取消的，不是回来以后被忽略的。
        // The old request was cancelled, not answered and then ignored.
        servers.cancelledCountries.first { countries -> countries.contains("AU") }
        assertSame(settled, viewModel.dealState.value)
    }

    // MARK: - 下拉刷新 / Pull to refresh

    /**
     * 下拉刷新期间旧列表一直留着（不切回 `Loading`），`isRefreshing` 为 `true`；两块都结束后变回 `false`。
     * During pull to refresh the old list stays (no switch back to `Loading`) and `isRefreshing` is `true`; it turns
     * back to `false` once both sections finish.
     */
    @Test
    fun refreshKeepsOldListUntilNewDataArrives() = runTest {
        val servers = FakeServers()
        val viewModel = servers.makeViewModel()
        viewModel.loadIfNeeded()
        val before = viewModel.dealState.first { state -> isSettled(state) }
        viewModel.giveawaysState.first { state -> state is GiveawaysLoadState.Loaded }
        val refreshGate = CompletableDeferred<Unit>()
        servers.dealsGates["AU"] = refreshGate

        viewModel.refresh()

        assertTrue(viewModel.isRefreshing.value)
        servers.dealsCountries.first { countries -> countries.size == 2 }
        assertSame(before, viewModel.dealState.value)

        refreshGate.complete(Unit)
        viewModel.isRefreshing.first { isRefreshing -> !isRefreshing }
        val after = viewModel.dealState.value
        assertTrue(isSettled(after))
        assertEquals(2, servers.giveawaysRequestCount.value)
    }

    /**
     * 下拉刷新和重试都用设置里当前的地区，不是第一次加载时的地区。
     * Pull to refresh and retry both use the current region from the settings, not the one from the first load.
     */
    @Test
    fun refreshAndRetryUseCurrentRegion() = runTest {
        val settings = SettingsRepository(dataStore = InMemoryPreferencesDataStore())
        val servers = FakeServers()
        val viewModel = servers.makeViewModel(settingsRepository = settings)
        viewModel.loadIfNeeded()
        viewModel.dealState.first { state -> isSettled(state) }
        settings.setRegion(AppRegion.UnitedStates)
        viewModel.dealState.first { state -> isSettled(state) && itadUUIDs(state).contains("US-found") }

        viewModel.refresh()
        viewModel.isRefreshing.first { isRefreshing -> !isRefreshing }
        viewModel.retryDeals()
        servers.dealsCountries.first { countries -> countries.size == 4 }

        assertEquals(listOf("AU", "US", "US", "US"), servers.dealsCountries.value)
    }

    // MARK: - 小工具 / Helpers

    /**
     * [state] 是不是已发布、且每条的 appID 都查完了。
     * Whether [state] is published with every row's appID settled.
     */
    private fun isSettled(state: DealLoadState): Boolean {
        if (state !is DealLoadState.Loaded) {
            return false
        }
        return state.items.none { item -> item.steam == SteamLink.Pending }
    }

    /**
     * [state] 里每条的 Steam 关联状态；不是 `Loaded` 时为空。
     * Each row's Steam link in [state]; empty when it is not `Loaded`.
     */
    private fun steamLinks(state: DealLoadState): List<SteamLink> {
        if (state !is DealLoadState.Loaded) {
            return emptyList()
        }
        return state.items.map { item -> item.steam }
    }

    /**
     * [state] 里每条的 ITAD UUID；不是 `Loaded` 时为空。
     * Each row's ITAD UUID in [state]; empty when it is not `Loaded`.
     */
    private fun itadUUIDs(state: DealLoadState): List<String> {
        if (state !is DealLoadState.Loaded) {
            return emptyList()
        }
        return state.items.map { item -> item.itadUUID }
    }

    // MARK: - 假服务器 / Fake servers

    /**
     * 一次测试里的假 ITAD：按设好的状态码回答，记下收到的请求，还能用闸门把请求卡住、由测试决定何时放行。
     * The fake ITAD for one test: answers with the configured status codes, records the requests it receives, and can
     * hold a request at a gate until the test lets it through.
     *
     * 每个地区返回两条 deal：`<地区>-found` 在 lookup 里有 `app/100`，`<地区>-missing` 没有。
     * Each region returns two deals: `<region>-found` has `app/100` in the lookup, `<region>-missing` has none.
     */
    private class FakeServers {

        /** deals 接口的状态码 / The deals endpoint's status code. */
        var dealsStatus: HttpStatusCode = HttpStatusCode.OK

        /** lookup 接口的状态码 / The lookup endpoint's status code. */
        var lookupStatus: HttpStatusCode = HttpStatusCode.OK

        /** 按地区代码卡住 deals 请求的闸门 / Gates holding deals requests, by region code. */
        val dealsGates = mutableMapOf<String, CompletableDeferred<Unit>>()

        /** 卡住 lookup 请求的闸门，`null` 是马上回答 / The gate holding lookups; `null` answers at once. */
        var lookupGate: CompletableDeferred<Unit>? = null

        /** 收到的 deals 请求里的地区，按顺序 / The region of each deals request received, in order. */
        val dealsCountries = MutableStateFlow<List<String>>(emptyList())

        /** 在闸门上被取消的 deals 请求的地区 / The region of each deals request cancelled at its gate. */
        val cancelledCountries = MutableStateFlow<List<String>>(emptyList())

        /** 收到的 giveaways 请求数 / The number of giveaways requests received. */
        val giveawaysRequestCount = MutableStateFlow(0)

        /**
         * 建一个连着这个假服务器的 [GamesViewModel]，Service 用和正式 App 一样的插件配置；地区从
         * [settingsRepository] 读，不传就是一份没存过东西的设置（澳大利亚）。
         * Builds a [GamesViewModel] wired to this fake server, with services using the app's real plugin setup; the
         * region comes from [settingsRepository], which defaults to empty settings (Australia).
         */
        fun makeViewModel(
            settingsRepository: SettingsRepository = SettingsRepository(dataStore = InMemoryPreferencesDataStore()),
        ): GamesViewModel {
            val engine = MockEngine { request -> answer(request) }
            val client = HttpClient(engine) {
                installAppPlugins(logsRequests = false, cacheStorage = CacheStorage.Unlimited())
            }
            val itadApi = ItadApi(client = client, apiKey = "test-key")
            val steamApi = SteamApi(client = client)
            val resolver = SteamAppIDResolver(itadApi = itadApi, steamApi = steamApi)
            return GamesViewModel(
                itadApi = itadApi,
                steamAppIDResolver = resolver,
                settingsRepository = settingsRepository,
            )
        }

        /**
         * 按路径分给各接口回答。
         * Routes a request to the matching endpoint by path.
         */
        private suspend fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
            val path = request.url.encodedPath
            if (path == "/deals/v2") {
                return answerDeals(request)
            }
            if (path == "/giveaways/v1") {
                giveawaysRequestCount.update { count -> count + 1 }
                return respond(content = GIVEAWAYS_BODY, status = HttpStatusCode.OK, headers = JSON_HEADERS)
            }
            if (path.startsWith("/lookup/shop/")) {
                return answerLookup(request)
            }
            error("没想到的请求 / Unexpected request: ${request.url}")
        }

        /**
         * 回答 deals：记下地区，有闸门就等，然后按地区返回两条 deal。
         * Answers deals: records the region, waits at its gate if any, then returns the region's two deals.
         */
        private suspend fun MockRequestHandleScope.answerDeals(request: HttpRequestData): HttpResponseData {
            val sentBody = request.body.toByteArray().decodeToString()
            val country = Json.parseToJsonElement(sentBody).jsonObject.getValue("country").jsonPrimitive.content
            dealsCountries.update { countries -> countries + country }

            val gate = dealsGates[country]
            if (gate != null) {
                try {
                    gate.await()
                } catch (cancellation: CancellationException) {
                    cancelledCountries.update { countries -> countries + country }
                    throw cancellation
                }
            }

            return respond(content = dealsBody(country = country), status = dealsStatus, headers = JSON_HEADERS)
        }

        /**
         * 回答 lookup：有闸门就等；成功时 `-found` 结尾的 UUID 给 `app/100`，其余给 `null`。
         * Answers a lookup: waits at the gate if any; on success, UUIDs ending in `-found` get `app/100` and the rest
         * get `null`.
         */
        private suspend fun MockRequestHandleScope.answerLookup(request: HttpRequestData): HttpResponseData {
            lookupGate?.await()
            if (lookupStatus != HttpStatusCode.OK) {
                return respond(content = "", status = lookupStatus, headers = JSON_HEADERS)
            }

            val sentBody = request.body.toByteArray().decodeToString()
            val uuids = Json.parseToJsonElement(sentBody).jsonArray.map { element -> element.jsonPrimitive.content }
            val entries = uuids.map { uuid -> lookupEntry(uuid = uuid) }
            val body = entries.joinToString(separator = ",", prefix = "{", postfix = "}")
            return respond(content = body, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        /**
         * lookup 响应里一个 UUID 的那一项。
         * One UUID's entry in the lookup response.
         */
        private fun lookupEntry(uuid: String): String {
            if (uuid.endsWith(FOUND_SUFFIX)) {
                return "\"$uuid\":[\"app/$FOUND_APP_ID\"]"
            }
            return "\"$uuid\":null"
        }

        /**
         * 某个地区的 deals 响应：`<地区>-found` 与 `<地区>-missing` 两条。
         * The deals response for a region: `<region>-found` and `<region>-missing`.
         */
        private fun dealsBody(country: String): String {
            val found = dealJSON(id = "$country$FOUND_SUFFIX")
            val missing = dealJSON(id = "$country-missing")
            return """{"hasMore":false,"list":[$found,$missing]}"""
        }

        /**
         * 一条最小的 deal，可选字段都不出现（同 `ItadDealsTest` 的稀疏响应）。
         * One minimal deal with every optional field absent (as in `ItadDealsTest`'s sparse response).
         */
        private fun dealJSON(id: String): String {
            return """
            {
              "id": "$id", "slug": "$id", "title": "$id", "type": "game", "mature": false, "assets": {},
              "deal": {
                "shop": { "id": 61, "name": "Steam" },
                "price": { "amount": 9.99, "amountInt": 999, "currency": "AUD" },
                "regular": { "amount": 19.99, "amountInt": 1999, "currency": "AUD" },
                "cut": 50,
                "drm": [ { "id": 61, "name": "Steam" } ],
                "platforms": [],
                "timestamp": "2026-11-18T00:00:00+01:00",
                "url": "https://itad.link/example/"
              }
            }
            """
        }
    }

    private companion object {

        /** 能查到 appID 的 deal UUID 的结尾 / The ending of deal UUIDs that resolve to an appID. */
        const val FOUND_SUFFIX = "-found"

        /** 查到的 appID / The appID they resolve to. */
        const val FOUND_APP_ID = 100

        /**
         * 一条 giveaway，取自 `ItadGiveawaysTest` 的真实响应（2026-11-18）。
         * One giveaway, taken from `ItadGiveawaysTest`'s real response (2026-11-18).
         */
        const val GIVEAWAYS_BODY = """
        [
          {
            "id": 7433,
            "title": "Die Young: Prologue - FREE on IndieGala",
            "shop": null,
            "url": "https://freebies.indiegala.com/die-young-prologue?ref=itad",
            "details": "https://isthereanydeal.com/giveaways/7433/",
            "isMature": false,
            "publish": "2021-10-06T11:00:00+02:00",
            "expiry": null,
            "note": "* Giveaway previously offered.",
            "games": [
              {
                "id": "018d937f-1032-72fc-9e19-176d4f377484",
                "slug": "die-young-prologue",
                "title": "Die Young: Prologue",
                "type": "game",
                "mature": false,
                "assets": {},
                "drmFree": true,
                "keys": [],
                "platforms": [ { "id": 1, "name": "Windows" } ]
              }
            ]
          }
        ]
        """

        /** JSON 响应头 / The JSON response header. */
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
