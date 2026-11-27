package io.github.johnwes7.steal.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.johnwes7.steal.shared.config.AppLog
import io.github.johnwes7.steal.shared.models.app.HomeDealItem
import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.app.WishlistItem
import io.github.johnwes7.steal.shared.models.itad.ItadDealsFilter
import io.github.johnwes7.steal.shared.models.itad.ItadDealsRequest
import io.github.johnwes7.steal.shared.models.itad.ItadGiveaway
import io.github.johnwes7.steal.shared.models.itad.ItadShopRef
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.services.ItadApi
import io.github.johnwes7.steal.shared.services.SettingsRepository
import io.github.johnwes7.steal.shared.services.SteamAppIDResolver
import io.github.johnwes7.steal.shared.services.WishlistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * 首页的状态：Hot Deals 前 5 条与 Giveaways 两个区块，各自加载、失败、重试，互不影响。
 * The home page's state: the first five Hot Deals and the Giveaways, each loading, failing and retrying on its own.
 *
 * 请求跑在 `viewModelScope` 里，切 tab 不会中断，页面出栈（ViewModel 被清掉）时才取消。地区由 ViewModel 自己订阅
 * 设置，所以在设置页改了地区，首页不在屏幕上也会重新加载。
 * Requests run in `viewModelScope`: switching tabs does not stop them; they are cancelled when the page is popped.
 * The view model subscribes to the region in the settings itself, so changing it on the settings screen reloads
 * the home page even while it is off screen.
 */
class GamesViewModel(
    private val itadApi: ItadApi,
    private val steamAppIDResolver: SteamAppIDResolver,
    private val settingsRepository: SettingsRepository,
    private val wishlistRepository: WishlistRepository,
) : ViewModel() {

    // MARK: - 状态类型 / State types

    /**
     * deals 区块的状态。
     * The state of the deals section.
     */
    sealed interface DealLoadState {

        /**
         * 第一次加载、换了地区或重试中；下拉刷新不切到这里，旧列表留着。
         * The first load, a region change or a retry; pull to refresh never comes here and keeps the old list.
         */
        data object Loading : DealLoadState

        /**
         * 请求成功，[items] 可能为空；每条的 Steam appID 可能还在补。
         * The request succeeded; [items] may be empty, and each row's Steam appID may still be resolving.
         */
        data class Loaded(val items: List<HomeDealItem>) : DealLoadState

        /**
         * 请求失败，原因在 [error]。
         * The request failed; [error] says why.
         */
        data class Failed(val error: LoadError) : DealLoadState
    }

    /**
     * giveaways 区块的状态，和 deals 互不影响。
     * The state of the giveaways section, independent of the deals.
     */
    sealed interface GiveawaysLoadState {

        /**
         * 第一次加载或重试中；下拉刷新不切到这里。
         * The first load or a retry; pull to refresh never comes here.
         */
        data object Loading : GiveawaysLoadState

        /**
         * 请求成功，[giveaways] 可能为空。
         * The request succeeded; [giveaways] may be empty.
         */
        data class Loaded(val giveaways: List<ItadGiveaway>) : GiveawaysLoadState

        /**
         * 请求失败，原因在 [error]。
         * The request failed; [error] says why.
         */
        data class Failed(val error: LoadError) : GiveawaysLoadState
    }

    // MARK: - 对外状态 / Exposed state

    private val _dealState = MutableStateFlow<DealLoadState>(DealLoadState.Loading)

    /**
     * deals 区块当前的状态，界面按它显示。
     * The deals section's current state, which the UI renders.
     */
    val dealState: StateFlow<DealLoadState> = _dealState.asStateFlow()

    private val _giveawaysState = MutableStateFlow<GiveawaysLoadState>(GiveawaysLoadState.Loading)

    /**
     * giveaways 区块当前的状态。
     * The giveaways section's current state.
     */
    val giveawaysState: StateFlow<GiveawaysLoadState> = _giveawaysState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)

    /**
     * 是否正在下拉刷新，给 `PullToRefreshBox` 显示转圈；两个区块都结束才变回 `false`。
     * Whether a pull to refresh is running, for `PullToRefreshBox`'s spinner; `false` again once both sections finish.
     */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * 已收藏的 appID，给卡片上的心形判断实心还是空心。
     * The saved appIDs, which the cards' hearts use to decide between filled and outlined.
     *
     * 初始是空集合，不用 `null` 表示「还没读到」：卡片要等网络上的 deals 回来才出现，那时本机数据库早就读完了。
     * It starts as an empty set rather than `null` for "not read yet": the cards only appear once the deals arrive
     * over the network, long after the on-device database has been read.
     */
    val savedAppIDs: StateFlow<Set<Int>> = wishlistRepository.savedAppIDs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = emptySet(),
    )

    // MARK: - 内部记录 / Internal bookkeeping

    /**
     * 是否已经开始订阅地区，保证只订阅一次。
     * Whether the region subscription has started, so it starts only once.
     */
    private var isObservingRegion: Boolean = false

    /**
     * 最近从设置收到的地区，当前（或正在加载的）deals 属于它；`null` 表示还没收到。
     * The region last received from the settings, which the current (or loading) deals belong to; `null` until the
     * first one arrives.
     */
    private var loadedRegion: AppRegion? = null

    private var dealsJob: Job? = null

    private var giveawaysJob: Job? = null

    /**
     * 补 appID 的重试；加载 deals 时接着补的那次跑在 [dealsJob] 里，不在这里。
     * The appID lookup retry; the lookup that follows a deals load runs inside [dealsJob], not here.
     */
    private var appIDsJob: Job? = null

    /**
     * 正在收藏或取消收藏的 appID，写完前同一张卡的点击不算；按 appID 分开记，写一张时点别的卡照常。
     * The appIDs being added or removed; taps on the same card are ignored until the write lands. Kept per appID, so
     * other cards still respond meanwhile.
     */
    private val writingAppIDs = mutableSetOf<Int>()

    // MARK: - 界面调用 / Called by the UI

    /**
     * 开始加载：第一次调用时订阅设置里的地区，之后再调用什么也不做。
     * Starts loading: the first call subscribes to the region in the settings, and later calls do nothing.
     *
     * 页面每次出现都会调用（切 tab 回来、转屏、从别的页面返回），所以要幂等。
     * The page calls this every time it appears (back from another tab, after rotation, back from another screen),
     * so it is idempotent.
     */
    fun loadIfNeeded() {
        if (isObservingRegion) {
            return
        }
        isObservingRegion = true

        viewModelScope.launch {
            observeRegion()
        }
    }

    /**
     * 下拉刷新：按当前地区，两个区块连同 appID 都重查，旧内容留到新数据回来；还没收到地区、或已经在刷新时不做事。
     * Pull to refresh: refetches both sections and the appIDs for the current region, keeping the old content until
     * new data arrives; does nothing before the region arrives or while already refreshing.
     */
    fun refresh() {
        // 1. 还没收到地区、或正在刷新就跳过，否则开始转圈
        // 1. Skip before the region arrives or while refreshing; otherwise start the spinner
        val region = loadedRegion
        if (region == null) {
            return
        }
        if (_isRefreshing.value) {
            return
        }
        _isRefreshing.value = true

        // 2. 两个区块都重查，不切 Loading：旧内容留着
        // 2. Refetch both sections without Loading: the old content stays
        val deals = loadDeals(region = region, showsLoading = false)
        val giveaways = loadGiveaways(showsLoading = false)

        // 3. 两个都结束才停止转圈
        // 3. Stop the spinner once both finish
        viewModelScope.launch {
            joinAll(deals, giveaways)
            _isRefreshing.value = false
        }
    }

    /**
     * deals 失败后的重试：按当前地区切回 Loading 重新请求。
     * Retries the deals after a failure for the current region, showing Loading again.
     */
    fun retryDeals() {
        val region = loadedRegion
        if (region == null) {
            return
        }
        loadDeals(region = region, showsLoading = true)
    }

    /**
     * giveaways 失败后的重试：切回 Loading 重新请求。
     * Retries the giveaways after a failure, showing Loading again.
     */
    fun retryGiveaways() {
        loadGiveaways(showsLoading = true)
    }

    /**
     * 补 appID 失败后的重试：只重查当前列表的 appID，不重新请求 deals；还没收到地区、deals 还在加载、或没有失败的卡片时
     * 不做事。
     * Retries the appID lookup after a failure: re-resolves only the current list's appIDs without refetching the
     * deals; does nothing before the region arrives, while the deals are loading, or when no card has failed.
     *
     * 重查期间失败的卡片回到 `Pending`；deals 重新加载时会取消还没回来的重试。
     * The failed cards go back to `Pending` meanwhile; reloading the deals cancels a retry that hasn't returned.
     */
    fun retryAppIDs() {
        // 1. 不该重试就跳过：deals 在加载时，加载完会自己补 appID
        // 1. Skip when there is nothing to retry: a running deals load resolves the appIDs itself
        val region = loadedRegion
        if (region == null) {
            return
        }
        val runningDealsJob = dealsJob
        if (runningDealsJob != null && runningDealsJob.isActive) {
            return
        }
        val state = _dealState.value
        if (state !is DealLoadState.Loaded) {
            return
        }
        val hasFailedItems = state.items.any { item -> item.steam == HomeDealItem.SteamLink.Failed }
        if (!hasFailedItems) {
            return
        }

        // 2. 失败的卡片切回 Pending：查询期间点它们先不做事
        // 2. Put the failed cards back to Pending: taps on them do nothing during the lookup
        appIDsJob?.cancel()
        settleFailedAsPending()

        // 3. 启动重查，记下来供 deals 重新加载时取消
        // 3. Launch the lookup and keep it so a deals reload can cancel it
        appIDsJob = viewModelScope.launch {
            resolveAppIDs(region = region)
        }
    }

    /**
     * 卡片上的心形被点了：在愿望单里就移出，不在就加入；appID 还没查到、或这张卡上一次点击还没写完时不做事。
     * A card's heart was tapped: removes the game when it is on the wishlist and adds it otherwise; does nothing
     * while the appID is unresolved or this card's previous tap is still being written.
     */
    fun toggleWishlist(item: HomeDealItem) {
        val appID = item.steamAppID
        if (appID == null) {
            return
        }
        if (appID in writingAppIDs) {
            return
        }
        writingAppIDs.add(appID)

        viewModelScope.launch {
            try {
                updateWishlist(item = item, appID = appID)
            } finally {
                writingAppIDs.remove(appID)
            }
        }
    }

    // MARK: - 愿望单 / Wishlist

    private suspend fun updateWishlist(item: HomeDealItem, appID: Int) {
        try {
            // 1. 从数据库读现在在不在：不用 savedAppIDs，它没人订阅时不更新
            // 1. Read from the database whether it is saved: not savedAppIDs, which stops updating with no subscriber
            val currentAppIDs = wishlistRepository.savedAppIDs.first()
            if (appID in currentAppIDs) {
                wishlistRepository.remove(appID = appID)
                return
            }

            // 2. 不在就加入：首页只有 ITAD 的 deal，拿不到 Steam 的发售状态，愿望单页核对后回写
            // 2. Otherwise add it: the home page only has ITAD's deal, without Steam's release status, which the
            //    wishlist page checks and writes back
            wishlistRepository.add(
                appID = appID,
                name = item.deal.title,
                // 要和详情页存的 Steam header 同比例，愿望单的行才能完整显示。
                // The same ratio as the Steam header the detail page saves, so wishlist rows show it whole.
                headerImageURL = item.deal.assets.headerRatioArtworkUrl,
                itadID = item.itadUUID,
                isComingSoon = null,
                knownLow = knownLowOf(item = item),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.games.e(error) { "愿望单写入失败 / Writing the wishlist failed: $appID" }
        }
    }

    /**
     * 用户正看着的这个折扣，作为已知低价：没打折或还没收到地区时是 `null`。
     * The sale the user is looking at, as the known low: `null` when it isn't on sale or before the region arrives.
     *
     * 首页只请求 Steam 能激活的 deal，所以这个价格可以拿来和以后的 Steam 价格比。
     * The home page only requests Steam-activated deals, so this price compares with later Steam prices.
     */
    private fun knownLowOf(item: HomeDealItem): WishlistItem.KnownPrice? {
        val region = loadedRegion
        if (region == null) {
            return null
        }
        val info = item.deal.deal
        val knownLow = WishlistItem.KnownPrice.fromSaleOrNull(price = info.price, cut = info.cut, region = region)
        return knownLow
    }

    // MARK: - 订阅地区 / Region subscription

    /**
     * 一直收集设置里的地区，每来一个和上次不同的地区就加载一次；`viewModelScope` 取消时才结束。
     * Collects the region from the settings until `viewModelScope` is cancelled, loading once for each region that
     * differs from the last.
     */
    private suspend fun observeRegion() {
        val regions = settingsRepository.settings.map { settings -> settings.region }
        // 改深浅、毛玻璃这些别的设置时整份设置会再发一次，地区没变就跳过。
        // Changing another setting (theme, frosted bars) emits the whole settings again; skip an unchanged region.
        val changedRegions = regions.distinctUntilChanged()
        changedRegions.collect { region -> loadSections(region = region) }
    }

    /**
     * 按 [region] 加载：deals 每次都重新加载，giveaways 只在第一次加载。
     * Loads for [region]: the deals every time, the giveaways only the first time.
     */
    private fun loadSections(region: AppRegion) {
        // 1. 记下地区：刷新和重试用它
        // 1. Record the region: refresh and retry use it
        val isFirstLoad = loadedRegion == null
        loadedRegion = region

        // 2. 加载 deals
        // 2. Load the deals
        loadDeals(region = region, showsLoading = true)

        // 3. giveaways 只在第一次加载：请求不分地区
        // 3. Giveaways only on the first load: the request has no region
        if (isFirstLoad) {
            loadGiveaways(showsLoading = true)
        }
    }

    // MARK: - 每个区块的请求 / Per-section requests
    // load… 管协程、马上返回；update… 在协程里取数据、写状态。
    // load… manages the coroutine and returns at once; update… fetches inside it and writes the state.

    private fun loadDeals(region: AppRegion, showsLoading: Boolean): Job {
        // 1. 取消旧请求和补 appID 的重试：旧结果不会盖掉新结果，旧列表的 appID 也不会写到新列表上
        // 1. Cancel the old request and any appID retry: a stale result never overwrites a fresh one, and the old
        //    list's appIDs never land on the new list
        dealsJob?.cancel()
        appIDsJob?.cancel()

        // 2. 启动新请求，记下来供下次取消
        // 2. Launch the new one and keep it for the next cancel
        val job = viewModelScope.launch {
            updateDeals(region = region, showsLoading = showsLoading)
        }
        dealsJob = job
        return job
    }

    private fun loadGiveaways(showsLoading: Boolean): Job {
        // 1. 取消旧请求
        // 1. Cancel the old request
        giveawaysJob?.cancel()

        // 2. 启动新请求，记下来供下次取消
        // 2. Launch the new one and keep it for the next cancel
        val job = viewModelScope.launch {
            updateGiveaways(showsLoading = showsLoading)
        }
        giveawaysJob = job
        return job
    }

    // MARK: - deals 区块 / Deals

    private suspend fun updateDeals(region: AppRegion, showsLoading: Boolean) {
        // 1. 按需切到 Loading：下拉刷新时旧列表留着
        // 1. Show Loading if asked: pull to refresh keeps the old list
        if (showsLoading) {
            _dealState.value = DealLoadState.Loading
        }

        // 2. 请求 deals，马上发布：appID 还是 Pending
        // 2. Fetch and publish the deals at once: appIDs still Pending
        try {
            val response = itadApi.fetchDeals(request = homeDealsRequest(region = region))
            val items = response.list.map { deal -> HomeDealItem(deal = deal) }
            _dealState.value = DealLoadState.Loaded(items = items)
        } catch (cancellation: CancellationException) {
            // 取消不是失败，要原样抛出，协程才会结束。
            // A cancellation is no failure; rethrow it so the coroutine ends.
            throw cancellation
        } catch (error: Exception) {
            AppLog.games.e(error) { "deals 加载失败 / Loading deals failed" }
            _dealState.value = DealLoadState.Failed(error = LoadError.from(error))
            return
        }

        // 3. 补 Steam appID：不挡首屏
        // 3. Resolve the Steam appIDs: never delays the cards
        resolveAppIDs(region = region)
    }

    private fun homeDealsRequest(region: AppRegion): ItadDealsRequest {
        val filter = ItadDealsFilter(
            // 1 是游戏。
            // 1 means games.
            type = listOf(1),
            drm = listOf(ItadShopRef.STEAM_ID),
        )
        return ItadDealsRequest(
            country = region.apiRegionCode,
            offset = 0,
            // 首页只放 5 条：1 张大卡 + 2 行网格，下面的 Giveaways 不被挤到很远。
            // The home page shows five: one featured card plus two grid rows, keeping Giveaways close.
            limit = 5,
            sort = "-hot",
            nondeals = false,
            filter = filter,
        )
    }

    // MARK: - 补 Steam appID / Steam appID resolution

    private suspend fun resolveAppIDs(region: AppRegion) {
        val state = _dealState.value
        if (state !is DealLoadState.Loaded) {
            return
        }
        if (state.items.isEmpty()) {
            return
        }

        // 1. 一次查完整个列表的 appID
        // 1. Look up the whole list's appIDs in one batch
        val uuids = state.items.map { item -> item.itadUUID }
        val appIDByUUID: Map<String, Int>
        try {
            appIDByUUID = steamAppIDResolver.resolveAppIDs(uuids = uuids, region = region)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.games.e(error) { "Steam appID 补全失败 / Resolving Steam appIDs failed" }
            // 列表本身没问题，只把还在等的条目标成 Failed，不然这些卡片一直点不动。
            // The list itself is fine; only the rows still waiting become Failed, or their cards would never respond.
            settlePendingAsFailed()
            return
        }

        // 2. 写回列表
        // 2. Write them back into the list
        applyAppIDs(appIDByUUID = appIDByUUID)
    }

    /**
     * 按查到的 appID 更新整个列表；只在查询成功后调用，所以不在 [appIDByUUID] 里的就是真的没有（`NotFound`）。
     * Updates the whole list with the resolved appIDs; only called after a successful lookup, so a row missing from
     * [appIDByUUID] really has none (`NotFound`).
     */
    private fun applyAppIDs(appIDByUUID: Map<String, Int>) {
        val state = _dealState.value
        if (state !is DealLoadState.Loaded) {
            return
        }

        val updatedItems = state.items.map { item ->
            val appID = appIDByUUID[item.itadUUID]
            if (appID != null) {
                item.copy(steam = HomeDealItem.SteamLink.Resolved(appID = appID))
            } else {
                item.copy(steam = HomeDealItem.SteamLink.NotFound)
            }
        }
        _dealState.value = DealLoadState.Loaded(items = updatedItems)
    }

    private fun settlePendingAsFailed() {
        val state = _dealState.value
        if (state !is DealLoadState.Loaded) {
            return
        }

        val updatedItems = state.items.map { item ->
            if (item.steam == HomeDealItem.SteamLink.Pending) {
                item.copy(steam = HomeDealItem.SteamLink.Failed)
            } else {
                item
            }
        }
        _dealState.value = DealLoadState.Loaded(items = updatedItems)
    }

    private fun settleFailedAsPending() {
        val state = _dealState.value
        if (state !is DealLoadState.Loaded) {
            return
        }

        val updatedItems = state.items.map { item ->
            if (item.steam == HomeDealItem.SteamLink.Failed) {
                item.copy(steam = HomeDealItem.SteamLink.Pending)
            } else {
                item
            }
        }
        _dealState.value = DealLoadState.Loaded(items = updatedItems)
    }

    // MARK: - giveaways 区块 / Giveaways

    private suspend fun updateGiveaways(showsLoading: Boolean) {
        // 1. 按需切到 Loading
        // 1. Show Loading if asked
        if (showsLoading) {
            _giveawaysState.value = GiveawaysLoadState.Loading
        }

        // 2. 请求 giveaways 并发布
        // 2. Fetch and publish the giveaways
        try {
            val giveaways = itadApi.fetchGiveaways()
            _giveawaysState.value = GiveawaysLoadState.Loaded(giveaways = giveaways)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.games.e(error) { "giveaways 加载失败 / Loading giveaways failed" }
            _giveawaysState.value = GiveawaysLoadState.Failed(error = LoadError.from(error))
        }
    }
}
