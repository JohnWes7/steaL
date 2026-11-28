package io.github.johnwes7.steal.shared.viewmodels

import io.github.johnwes7.steal.shared.models.app.WishlistFilter
import io.github.johnwes7.steal.shared.models.app.WishlistItem
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.models.settings.AppTheme
import io.github.johnwes7.steal.shared.services.InMemoryPreferencesDataStore
import io.github.johnwes7.steal.shared.services.InMemoryWishlistDao
import io.github.johnwes7.steal.shared.services.ItadApi
import io.github.johnwes7.steal.shared.services.SettingsRepository
import io.github.johnwes7.steal.shared.services.SteamApi
import io.github.johnwes7.steal.shared.services.WishlistRepository
import io.github.johnwes7.steal.shared.services.installAppPlugins
import io.github.johnwes7.steal.shared.viewmodels.WishlistViewModel.PriceLoadState
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * [WishlistViewModel] 的测试：读到之前是 `null`，别处（详情页的心形）加入、移出时跟着变；筛选；查价、核对发售状态和回写。
 * Tests for [WishlistViewModel]: `null` until read, following adds and removes made elsewhere (the detail page's
 * heart), the filters, and fetching prices, checking release status and writing back.
 *
 * [WishlistViewModel.items] 只在有人收集时才读（`WhileSubscribed`），测试在 `backgroundScope` 里收集它，代替界面。
 * [WishlistViewModel.items] only reads while collected (`WhileSubscribed`), so tests collect it in
 * `backgroundScope` in place of the UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WishlistViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 读到之前是 `null`，读到后是空列表。
     * `null` until read, then an empty list.
     */
    @Test
    fun itemsAreNullUntilRead() = runTest {
        val repository = WishlistRepository(wishlistDao = InMemoryWishlistDao())
        val viewModel = makeViewModel(repository = repository)
        val initialItems = viewModel.items.value

        collectItems(viewModel)
        advanceUntilIdle()

        assertNull(initialItems)
        assertEquals(emptyList(), viewModel.items.value)
    }

    /**
     * 别处加入、移出后，列表跟着变。
     * Adds and removes made elsewhere show up in the list.
     */
    @Test
    fun followsAddsAndRemoves() = runTest {
        val repository = WishlistRepository(wishlistDao = InMemoryWishlistDao())
        val viewModel = makeViewModel(repository = repository)
        collectItems(viewModel)
        advanceUntilIdle()

        repository.add(
            appID = 620,
            name = "Portal 2",
            headerImageURL = null,
            itadID = null,
            isComingSoon = null,
            knownLow = null,
        )
        advanceUntilIdle()
        val appIDsAfterAdd = appIDsOf(viewModel)

        repository.remove(appID = 620)
        advanceUntilIdle()
        val appIDsAfterRemove = appIDsOf(viewModel)

        assertEquals(listOf(620), appIDsAfterAdd)
        assertEquals(emptyList(), appIDsAfterRemove)
    }

    /**
     * 价格还没查时：All 每行都是 `Loading`；Upcoming 只留存着未发售的；On Sale 为空且算在等。
     * Before any price arrives: All shows every row as `Loading`; Upcoming keeps only the ones stored as
     * unreleased; On Sale is empty and counts as pending.
     */
    @Test
    fun filtersBeforePricesArrive() = runTest {
        val repository = WishlistRepository(wishlistDao = InMemoryWishlistDao())
        repository.add(
            appID = 620,
            name = "Portal 2",
            headerImageURL = null,
            itadID = null,
            isComingSoon = false,
            knownLow = null,
        )
        repository.add(
            appID = 1_030_300,
            name = "Hollow Knight: Silksong",
            headerImageURL = null,
            itadID = null,
            isComingSoon = true,
            knownLow = null,
        )
        val viewModel = makeViewModel(repository = repository)
        backgroundScope.launch {
            viewModel.visibleRows.collect { _ -> }
        }
        backgroundScope.launch {
            viewModel.isFilterPending.collect { _ -> }
        }
        advanceUntilIdle()

        val allRows = viewModel.visibleRows.value.orEmpty()
        val allPrices = allRows.map { row -> row.price }
        val allPending = viewModel.isFilterPending.value

        viewModel.selectFilter(WishlistFilter.Upcoming)
        advanceUntilIdle()
        val upcomingAppIDs = rowAppIDsOf(viewModel)
        val upcomingPending = viewModel.isFilterPending.value

        viewModel.selectFilter(WishlistFilter.OnSale)
        advanceUntilIdle()
        val onSaleAppIDs = rowAppIDsOf(viewModel)
        val onSalePending = viewModel.isFilterPending.value

        assertEquals(listOf(PriceLoadState.Loading, PriceLoadState.Loading), allPrices)
        assertFalse(allPending)
        assertEquals(listOf(1_030_300), upcomingAppIDs)
        assertFalse(upcomingPending)
        assertEquals(emptyList(), onSaleAppIDs)
        assertTrue(onSalePending)
    }

    /**
     * 存着 UUID 的直接查价；缺 UUID 的 lookup，查到回写、查不到是 `NoPrice`；回写不会让价格再查一次。
     * 同时核对发售状态并回写，Steam 查不到的不写。
     * Rows with a stored UUID go straight to the prices; the others are looked up, a hit written back and a miss
     * `NoPrice`; the write-back does not refetch the prices. Release statuses are checked and written back at the
     * same time, except for games Steam does not have.
     */
    @Test
    fun loadsPricesAndWritesBack() = runTest {
        val repository = WishlistRepository(wishlistDao = InMemoryWishlistDao())
        addGame(repository = repository, appID = PORTAL_ID, itadID = PORTAL_UUID, isComingSoon = null)
        addGame(repository = repository, appID = SILKSONG_ID, itadID = null, isComingSoon = null)
        addGame(repository = repository, appID = UNLISTED_ID, itadID = null, isComingSoon = null)
        val servers = FakeServers()
        servers.uuidByAppID = mapOf(SILKSONG_ID to SILKSONG_UUID)
        servers.pricedUUIDs = setOf(PORTAL_UUID)
        servers.comingSoonByAppID = mapOf(PORTAL_ID to false, SILKSONG_ID to true)
        val viewModel = servers.makeViewModel(repository = repository)
        collectRows(viewModel)

        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.visibleRows.first { rows -> rows != null && hasSettledPrices(rows = rows) }
        val savedItems = viewModel.items.first { items -> items != null && hasCheckedReleases(items = items) }
        advanceUntilIdle()

        val priceByAppID = priceByAppIDOf(viewModel)
        val portalPrice = priceByAppID.getValue(PORTAL_ID) as PriceLoadState.Loaded
        val savedByAppID = savedItems.orEmpty().associateBy { item -> item.appID }
        assertEquals(50, portalPrice.deal.cut)
        assertEquals(PriceLoadState.NoPrice, priceByAppID.getValue(SILKSONG_ID))
        assertEquals(PriceLoadState.NoPrice, priceByAppID.getValue(UNLISTED_ID))
        assertEquals(SILKSONG_UUID, savedByAppID.getValue(SILKSONG_ID).itadID)
        assertNull(savedByAppID.getValue(UNLISTED_ID).itadID)
        assertEquals(2, servers.lookupCount.value)
        assertEquals(1, servers.pricesCount)
        assertEquals(false, savedByAppID.getValue(PORTAL_ID).isComingSoon)
        assertEquals(true, savedByAppID.getValue(SILKSONG_ID).isComingSoon)
        assertNull(savedByAppID.getValue(UNLISTED_ID).isComingSoon)
    }

    /**
     * 一天内核对过的、已发售（`false`）的不向 Steam 核对；超过一天的才核对。
     * Games checked within a day and released ones (`false`) are not checked with Steam; only those older than a
     * day are.
     */
    @Test
    fun releaseChecksSkipRecentAndReleased() = runTest {
        val checkedAt = Instant.parse("2026-11-28T00:00:00Z")
        val repository = WishlistRepository(wishlistDao = InMemoryWishlistDao(), clock = FixedClock(now = checkedAt))
        addGame(repository = repository, appID = PORTAL_ID, itadID = PORTAL_UUID, isComingSoon = false)
        addGame(repository = repository, appID = SILKSONG_ID, itadID = SILKSONG_UUID, isComingSoon = true)

        // 一小时后：两行都不用核对，等价格落定再确认没发 Steam 请求。
        // An hour later: neither row needs a check; wait for the prices, then confirm no Steam request went out.
        val soonServers = FakeServers()
        val soonClock = FixedClock(now = checkedAt + 1.hours)
        val soonViewModel = soonServers.makeViewModel(repository = repository, clock = soonClock)
        collectRows(soonViewModel)
        soonViewModel.loadIfNeeded(steamLanguage = "english")
        soonViewModel.visibleRows.first { rows -> rows != null && rows.isNotEmpty() && hasSettledPrices(rows = rows) }
        advanceUntilIdle()
        val soonSteamAppIDs = soonServers.steamAppIDs.value

        // 25 小时后：只有未发售的那行要核对。
        // 25 hours later: only the unreleased row needs a check.
        val laterServers = FakeServers()
        val laterClock = FixedClock(now = checkedAt + 25.hours)
        val laterViewModel = laterServers.makeViewModel(repository = repository, clock = laterClock)
        laterViewModel.loadIfNeeded(steamLanguage = "english")
        laterServers.steamAppIDs.first { appIDs -> appIDs.isNotEmpty() }
        advanceUntilIdle()
        val laterSteamAppIDs = laterServers.steamAppIDs.value

        assertEquals(emptyList(), soonSteamAppIDs)
        assertEquals(listOf(SILKSONG_ID), laterSteamAppIDs)
    }

    /**
     * lookup 失败的行是 `Failed`；查价失败时这次查的行全部 `Failed`。
     * A row whose lookup fails is `Failed`; when the price request fails, every row of that fetch is `Failed`.
     */
    @Test
    fun failedRequestsBecomeFailed() = runTest {
        val repository = WishlistRepository(wishlistDao = InMemoryWishlistDao())
        addGame(repository = repository, appID = PORTAL_ID, itadID = PORTAL_UUID, isComingSoon = false)
        addGame(repository = repository, appID = SILKSONG_ID, itadID = null, isComingSoon = false)

        // lookup 失败、查价成功
        // The lookup fails, the prices work
        val lookupFailing = FakeServers()
        lookupFailing.lookupStatus = HttpStatusCode.InternalServerError
        lookupFailing.pricedUUIDs = setOf(PORTAL_UUID)
        val firstViewModel = lookupFailing.makeViewModel(repository = repository)
        collectRows(firstViewModel)
        firstViewModel.loadIfNeeded(steamLanguage = "english")
        firstViewModel.visibleRows.first { rows -> rows != null && rows.isNotEmpty() && hasSettledPrices(rows = rows) }
        val firstPrices = priceByAppIDOf(firstViewModel)

        // 查价失败
        // The prices fail
        val pricesFailing = FakeServers()
        pricesFailing.uuidByAppID = mapOf(SILKSONG_ID to SILKSONG_UUID)
        pricesFailing.pricesStatus = HttpStatusCode.InternalServerError
        val secondViewModel = pricesFailing.makeViewModel(repository = repository)
        collectRows(secondViewModel)
        secondViewModel.loadIfNeeded(steamLanguage = "english")
        secondViewModel.visibleRows.first { rows -> rows != null && rows.isNotEmpty() && hasSettledPrices(rows = rows) }
        val secondPrices = priceByAppIDOf(secondViewModel)

        assertTrue(firstPrices.getValue(PORTAL_ID) is PriceLoadState.Loaded)
        assertEquals(PriceLoadState.Failed, firstPrices.getValue(SILKSONG_ID))
        assertEquals(PriceLoadState.Failed, secondPrices.getValue(PORTAL_ID))
        assertEquals(PriceLoadState.Failed, secondPrices.getValue(SILKSONG_ID))
    }

    /**
     * 删一行不会重查别的行，留下的行价格不变。
     * Removing a row refetches nothing, and the remaining row keeps its price.
     */
    @Test
    fun removingRowRefetchesNothing() = runTest {
        val repository = WishlistRepository(wishlistDao = InMemoryWishlistDao())
        addGame(repository = repository, appID = PORTAL_ID, itadID = PORTAL_UUID, isComingSoon = false)
        addGame(repository = repository, appID = SILKSONG_ID, itadID = SILKSONG_UUID, isComingSoon = false)
        val servers = FakeServers()
        servers.pricedUUIDs = setOf(PORTAL_UUID, SILKSONG_UUID)
        val viewModel = servers.makeViewModel(repository = repository)
        collectRows(viewModel)
        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.visibleRows.first { rows -> rows != null && rows.size == 2 && hasSettledPrices(rows = rows) }
        val silksong = viewModel.items.value.orEmpty().first { item -> item.appID == SILKSONG_ID }

        viewModel.remove(item = silksong)
        viewModel.visibleRows.first { rows -> rows != null && rows.size == 1 }
        advanceUntilIdle()
        val remainingPrices = priceByAppIDOf(viewModel)

        assertEquals(1, servers.pricesCount)
        assertEquals(setOf(PORTAL_ID), remainingPrices.keys)
        assertTrue(remainingPrices.getValue(PORTAL_ID) is PriceLoadState.Loaded)
    }

    /**
     * 换地区会清空价格，按新地区重查全部行；改别的设置不会重查。
     * A region change clears the prices and refetches every row for the new region; other settings refetch nothing.
     */
    @Test
    fun regionChangeRefetchesPrices() = runTest {
        val repository = WishlistRepository(wishlistDao = InMemoryWishlistDao())
        addGame(repository = repository, appID = PORTAL_ID, itadID = PORTAL_UUID, isComingSoon = false)
        val settings = SettingsRepository(dataStore = InMemoryPreferencesDataStore())
        val servers = FakeServers()
        servers.pricedUUIDs = setOf(PORTAL_UUID)
        val viewModel = servers.makeViewModel(repository = repository, settingsRepository = settings)
        collectRows(viewModel)
        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.visibleRows.first { rows -> rows != null && rows.isNotEmpty() && hasSettledPrices(rows = rows) }

        settings.setTheme(AppTheme.Light)
        advanceUntilIdle()
        val countriesAfterTheme = servers.pricesCountries.value

        val gate = CompletableDeferred<Unit>()
        servers.pricesGate = gate
        settings.setRegion(AppRegion.UnitedStates)
        servers.pricesCountries.first { countries -> countries.size == 2 }
        advanceUntilIdle()
        val priceWhileRefetching = priceByAppIDOf(viewModel).getValue(PORTAL_ID)
        gate.complete(Unit)
        viewModel.visibleRows.first { rows -> rows != null && rows.isNotEmpty() && hasSettledPrices(rows = rows) }

        assertEquals(listOf("AU"), countriesAfterTheme)
        assertEquals(listOf("AU", "US"), servers.pricesCountries.value)
        assertEquals(PriceLoadState.Loading, priceWhileRefetching)
    }

    /**
     * 价格到了以后：On Sale 只留打折的，Lowest 只留史低的（含没打折的），没价格的只在 All 里；都不再算在等。
     * Once prices arrive: On Sale keeps only discounted rows, Lowest only all-time lows (undiscounted ones
     * included), rows without a price only show under All; none counts as pending any more.
     */
    @Test
    fun filtersUseLoadedPrices() = runTest {
        val repository = WishlistRepository(wishlistDao = InMemoryWishlistDao())
        addGame(repository = repository, appID = PORTAL_ID, itadID = PORTAL_UUID, isComingSoon = false)
        addGame(repository = repository, appID = HADES_ID, itadID = HADES_UUID, isComingSoon = false)
        addGame(repository = repository, appID = SILKSONG_ID, itadID = SILKSONG_UUID, isComingSoon = false)
        val servers = FakeServers()
        servers.pricedUUIDs = setOf(PORTAL_UUID)
        servers.lowUUIDs = setOf(HADES_UUID)
        val viewModel = servers.makeViewModel(repository = repository)
        collectRows(viewModel)
        backgroundScope.launch {
            viewModel.isFilterPending.collect { _ -> }
        }
        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.visibleRows.first { rows -> rows != null && rows.size == 3 && hasSettledPrices(rows = rows) }

        viewModel.selectFilter(WishlistFilter.OnSale)
        advanceUntilIdle()
        val onSaleAppIDs = rowAppIDsOf(viewModel)
        val onSalePending = viewModel.isFilterPending.value

        viewModel.selectFilter(WishlistFilter.AllTimeLow)
        advanceUntilIdle()
        val lowestAppIDs = rowAppIDsOf(viewModel)
        val lowestPending = viewModel.isFilterPending.value

        assertEquals(listOf(PORTAL_ID), onSaleAppIDs)
        assertFalse(onSalePending)
        assertEquals(listOf(HADES_ID), lowestAppIDs)
        assertFalse(lowestPending)
    }

    /**
     * 下拉刷新：还没加载时不做事；刷新时旧价格留到新结果回来，发售状态绕过缓存核对，结束后停止转圈。
     * Pull to refresh: nothing happens before the first load; while refreshing the old price stays until the new
     * one lands, release statuses are checked bypassing the cache, and the spinner stops at the end.
     */
    @Test
    fun refreshKeepsOldPricesAndBypassesCache() = runTest {
        val repository = WishlistRepository(wishlistDao = InMemoryWishlistDao())
        addGame(repository = repository, appID = PORTAL_ID, itadID = PORTAL_UUID, isComingSoon = null)
        val servers = FakeServers()
        servers.pricedUUIDs = setOf(PORTAL_UUID)
        servers.comingSoonByAppID = mapOf(PORTAL_ID to true)
        val viewModel = servers.makeViewModel(repository = repository)
        collectRows(viewModel)

        viewModel.refresh()
        advanceUntilIdle()
        val refreshingBeforeLoad = viewModel.isRefreshing.value
        val pricesBeforeLoad = servers.pricesCount

        viewModel.loadIfNeeded(steamLanguage = "english")
        viewModel.visibleRows.first { rows -> rows != null && rows.isNotEmpty() && hasSettledPrices(rows = rows) }
        viewModel.items.first { items -> items != null && items.first().isComingSoon == true }

        // 刷新时价格没了，查价卡住，看旧价格还在不在
        // The price is gone on refresh and the request is held; check whether the old price is still shown
        val gate = CompletableDeferred<Unit>()
        servers.pricesGate = gate
        servers.pricedUUIDs = emptySet()
        viewModel.refresh()
        servers.pricesCountries.first { countries -> countries.size == 2 }
        servers.steamCacheControls.first { headers -> headers.size == 2 }
        val refreshingDuring = viewModel.isRefreshing.value
        val priceDuring = priceByAppIDOf(viewModel).getValue(PORTAL_ID)
        gate.complete(Unit)
        viewModel.isRefreshing.first { isRefreshing -> !isRefreshing }
        val priceAfter = priceByAppIDOf(viewModel).getValue(PORTAL_ID)

        assertFalse(refreshingBeforeLoad)
        assertEquals(0, pricesBeforeLoad)
        assertTrue(refreshingDuring)
        assertTrue(priceDuring is PriceLoadState.Loaded)
        assertEquals(PriceLoadState.NoPrice, priceAfter)
        assertEquals(listOf(null, "no-cache"), servers.steamCacheControls.value)
    }

    /**
     * 代替界面一直收集 [WishlistViewModel.visibleRows]，测试结束时自动停止。
     * Keeps collecting [WishlistViewModel.visibleRows] in place of the UI, stopping when the test ends.
     */
    private fun TestScope.collectRows(viewModel: WishlistViewModel) {
        backgroundScope.launch {
            viewModel.visibleRows.collect { _ -> }
        }
        backgroundScope.launch {
            viewModel.items.collect { _ -> }
        }
    }

    /**
     * 每行现在的价格，按 appID。
     * Each row's current price by appID.
     */
    private fun priceByAppIDOf(viewModel: WishlistViewModel): Map<Int, PriceLoadState> {
        val rows = viewModel.visibleRows.value.orEmpty()
        val pairs = rows.map { row -> row.item.appID to row.price }
        val priceByAppID = pairs.toMap()
        return priceByAppID
    }

    /**
     * 每行的价格都落定了（不是 `Loading`）。
     * Whether every row's price has settled (none is `Loading`).
     */
    private fun hasSettledPrices(rows: List<WishlistViewModel.Row>): Boolean {
        val hasLoading = rows.any { row -> row.price == PriceLoadState.Loading }
        return !hasLoading
    }

    /**
     * Portal 和 Silksong 的发售状态都回写了（测试里 Steam 只认识这两个）。
     * Whether Portal's and Silksong's release statuses are written back (the only two Steam knows in the tests).
     */
    private fun hasCheckedReleases(items: List<WishlistItem>): Boolean {
        val checkedItems = items.filter { item -> item.isComingSoon != null }
        return checkedItems.size == 2
    }

    private suspend fun addGame(repository: WishlistRepository, appID: Int, itadID: String?, isComingSoon: Boolean?) {
        repository.add(
            appID = appID,
            name = "Game $appID",
            headerImageURL = null,
            itadID = itadID,
            isComingSoon = isComingSoon,
            knownLow = null,
        )
    }

    /**
     * 建一个读 [repository] 的 ViewModel，接口都由一个默认的 [FakeServers] 回答（什么都查不到）。
     * Builds a view model reading [repository], with every API answered by a default [FakeServers] (nothing found).
     */
    private fun makeViewModel(repository: WishlistRepository): WishlistViewModel {
        val servers = FakeServers()
        return servers.makeViewModel(repository = repository)
    }

    /**
     * 固定在 [now] 的时钟。
     * A clock stopped at [now].
     */
    private class FixedClock(private val now: Instant) : Clock {
        override fun now(): Instant = now
    }

    /**
     * 扮演 ITAD（lookup、查价）和 Steam（`appdetails`）的假服务器，记下收到的请求。
     * A fake server playing ITAD (lookups, prices) and Steam (`appdetails`), recording the requests it gets.
     */
    private class FakeServers {

        /** lookup 查得到的 UUID，按 appID；不在里面的回 `found: false` / UUIDs found by lookup, by appID; others get `found: false`. */
        var uuidByAppID: Map<Int, String> = emptyMap()

        /** 有 Steam 半价报价（不是史低）的 UUID / UUIDs with a half-price Steam offer that is not an all-time low. */
        var pricedUUIDs: Set<String> = emptySet()

        /** 现价是史低（原价、标 `H`）的 UUID；两个集合都不在的不进响应 / UUIDs whose undiscounted price is flagged `H`; UUIDs in neither set are left out. */
        var lowUUIDs: Set<String> = emptySet()

        /** Steam 的 `coming_soon`，按 appID；不在里面的回 `success: false` / Steam's `coming_soon` by appID; others get `success: false`. */
        var comingSoonByAppID: Map<Int, Boolean> = emptyMap()

        /** lookup 回的状态码 / The status code lookups answer with. */
        var lookupStatus: HttpStatusCode = HttpStatusCode.OK

        /** 查价回的状态码 / The status code price requests answer with. */
        var pricesStatus: HttpStatusCode = HttpStatusCode.OK

        /** 不为 `null` 时，查价请求等它完成才回答 / When not `null`, price requests wait for it before answering. */
        var pricesGate: CompletableDeferred<Unit>? = null

        /** 收到的 lookup 请求数 / The lookup requests received. */
        val lookupCount = MutableStateFlow(0)

        /** 收到的查价请求的 `country`，按到达顺序 / The `country` of each price request received, in order. */
        val pricesCountries = MutableStateFlow<List<String>>(emptyList())

        /** 收到的 `appdetails` 请求的 appID，按到达顺序 / The appIDs of the `appdetails` requests received, in order. */
        val steamAppIDs = MutableStateFlow<List<Int>>(emptyList())

        /** 每个 `appdetails` 请求的 `Cache-Control` 头，没有是 `null` / Each `appdetails` request's `Cache-Control` header, `null` when absent. */
        val steamCacheControls = MutableStateFlow<List<String?>>(emptyList())

        /** 收到的查价请求数 / The price requests received. */
        val pricesCount: Int
            get() = pricesCountries.value.size

        fun makeViewModel(
            repository: WishlistRepository,
            clock: Clock = Clock.System,
            settingsRepository: SettingsRepository = SettingsRepository(dataStore = InMemoryPreferencesDataStore()),
        ): WishlistViewModel {
            val engine = MockEngine { request -> answer(request) }
            val client = HttpClient(engine) {
                installAppPlugins(logsRequests = false, cacheStorage = CacheStorage.Unlimited())
            }
            return WishlistViewModel(
                wishlistRepository = repository,
                itadApi = ItadApi(client = client, apiKey = "test-key"),
                steamApi = SteamApi(client = client),
                settingsRepository = settingsRepository,
                clock = clock,
            )
        }

        private suspend fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
            val path = request.url.encodedPath
            if (path == "/games/lookup/v1") {
                return answerLookup(request)
            }
            if (path == "/games/prices/v3") {
                return answerPrices(request)
            }
            if (path == "/api/appdetails") {
                return answerDetails(request)
            }
            error("没想到的请求 / Unexpected request: ${request.url}")
        }

        private fun MockRequestHandleScope.answerLookup(request: HttpRequestData): HttpResponseData {
            lookupCount.update { count -> count + 1 }
            if (lookupStatus != HttpStatusCode.OK) {
                return respond(content = "", status = lookupStatus, headers = JSON_HEADERS)
            }
            val appID = request.url.parameters["appid"]?.toInt()
            val uuid = uuidByAppID[appID]
            val body = if (uuid == null) {
                """{"found":false}"""
            } else {
                """{"found":true,"game":{"id":"$uuid","slug":"game","title":"Game","type":"game"}}"""
            }
            return respond(content = body, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        private suspend fun MockRequestHandleScope.answerPrices(request: HttpRequestData): HttpResponseData {
            val country = request.url.parameters["country"].orEmpty()
            pricesCountries.update { countries -> countries + country }
            pricesGate?.await()
            if (pricesStatus != HttpStatusCode.OK) {
                return respond(content = "", status = pricesStatus, headers = JSON_HEADERS)
            }

            val sentBody = request.body.toByteArray().decodeToString()
            val sentUUIDs = Json.parseToJsonElement(sentBody).jsonArray
            val entries = mutableListOf<String>()
            for (element in sentUUIDs) {
                val uuid = element.jsonPrimitive.content
                if (uuid in pricedUUIDs) {
                    entries.add(priceEntry(uuid = uuid, price = SALE_PRICE, cut = 50, flag = null))
                }
                if (uuid in lowUUIDs) {
                    entries.add(priceEntry(uuid = uuid, price = REGULAR_PRICE, cut = 0, flag = "H"))
                }
            }
            val body = entries.joinToString(separator = ",", prefix = "[", postfix = "]")
            return respond(content = body, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        private fun MockRequestHandleScope.answerDetails(request: HttpRequestData): HttpResponseData {
            val appID = request.url.parameters["appids"]?.toInt() ?: error("没有 appids / No appids")
            val cacheControl = request.headers[HttpHeaders.CacheControl]
            steamCacheControls.update { headers -> headers + cacheControl }
            steamAppIDs.update { appIDs -> appIDs + appID }
            val comingSoon = comingSoonByAppID[appID]
            val body = if (comingSoon == null) {
                """{"$appID":{"success":false}}"""
            } else {
                """{"$appID":{"success":true,"data":{"type":"game","name":"Game","is_free":false,"release_date":{"coming_soon":$comingSoon,"date":""}}}}"""
            }
            return respond(content = body, status = HttpStatusCode.OK, headers = JSON_HEADERS)
        }

        /**
         * 一个游戏的 `prices/v3` 条目：只有一条 Steam 报价，原价 9.99。
         * One game's `prices/v3` entry: a single Steam offer with a regular price of 9.99.
         */
        private fun priceEntry(uuid: String, price: String, cut: Int, flag: String?): String {
            val flagJSON = if (flag == null) "null" else "\"$flag\""
            val deal = """{"shop":{"id":61,"name":"Steam"},"price":$price,"regular":$REGULAR_PRICE,"cut":$cut,""" +
                """"voucher":null,"storeLow":null,"flag":$flagJSON,"drm":[],"platforms":[],""" +
                """"timestamp":"2026-11-28T00:00:00+00:00","expiry":null,"url":"https://example.com/steam"}"""
            return """{"id":"$uuid","historyLow":{"all":$price,"y1":null,"m3":null},"deals":[$deal]}"""
        }
    }

    /**
     * 代替界面一直收集 [WishlistViewModel.items]，测试结束时自动停止。
     * Keeps collecting [WishlistViewModel.items] in place of the UI, stopping when the test ends.
     */
    private fun TestScope.collectItems(viewModel: WishlistViewModel) {
        backgroundScope.launch {
            viewModel.items.collect { _ -> }
        }
    }

    /**
     * 列表里现在的 appID，还没读到时是空列表。
     * The appIDs currently in the list, or an empty list before it is read.
     */
    private fun appIDsOf(viewModel: WishlistViewModel): List<Int> {
        val items = viewModel.items.value.orEmpty()
        val appIDs = items.map { item -> item.appID }
        return appIDs
    }

    /**
     * 筛选后各行的 appID，还没读到时是空列表。
     * The appIDs of the filtered rows, or an empty list before they are read.
     */
    private fun rowAppIDsOf(viewModel: WishlistViewModel): List<Int> {
        val rows = viewModel.visibleRows.value.orEmpty()
        val appIDs = rows.map { row -> row.item.appID }
        return appIDs
    }

    private companion object {

        const val PORTAL_ID = 620

        const val PORTAL_UUID = "portal-uuid"

        const val SILKSONG_ID = 1_030_300

        const val SILKSONG_UUID = "silksong-uuid"

        const val HADES_ID = 1_145_360

        const val HADES_UUID = "hades-uuid"

        /** ITAD 没收录、Steam 也查不到的游戏 / A game neither ITAD nor Steam has. */
        const val UNLISTED_ID = 999

        /** 半价 / Half price. */
        const val SALE_PRICE = """{"amount":4.99,"amountInt":499,"currency":"AUD"}"""

        /** 原价 / The regular price. */
        const val REGULAR_PRICE = """{"amount":9.99,"amountInt":999,"currency":"AUD"}"""

        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}