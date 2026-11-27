package io.github.johnwes7.steal.shared.viewmodels

import io.github.johnwes7.steal.shared.services.InMemoryWishlistDao
import io.github.johnwes7.steal.shared.services.WishlistRepository
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
import kotlin.test.assertNull

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
        val viewModel = WishlistViewModel(wishlistRepository = repository)
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
        val viewModel = WishlistViewModel(wishlistRepository = repository)
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
}
