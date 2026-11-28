package io.github.johnwes7.steal.shared.viewmodels

import io.github.johnwes7.steal.shared.models.app.WishlistFilter
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
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [WishlistViewModel] 的测试：读到之前是 `null`，别处（详情页的心形）加入、移出时跟着变。
 * Tests for [WishlistViewModel]: `null` until read, and following adds and removes made elsewhere (the detail page's
 * heart).
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
     * 建一个读 [repository] 的 ViewModel；接口一律回 404，这些测试不该发请求。
     * Builds a view model reading [repository]; every API call gets a 404, since these tests should send none.
     */
    private fun makeViewModel(repository: WishlistRepository): WishlistViewModel {
        val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.NotFound) }
        val client = HttpClient(engine) {
            installAppPlugins(logsRequests = false, cacheStorage = CacheStorage.Unlimited())
        }
        return WishlistViewModel(
            wishlistRepository = repository,
            itadApi = ItadApi(client = client, apiKey = "test-key"),
            steamApi = SteamApi(client = client),
            settingsRepository = SettingsRepository(dataStore = InMemoryPreferencesDataStore()),
        )
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
}