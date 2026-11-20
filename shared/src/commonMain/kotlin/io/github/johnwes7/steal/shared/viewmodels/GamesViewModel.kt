package io.github.johnwes7.steal.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.johnwes7.steal.shared.config.AppLog
import io.github.johnwes7.steal.shared.models.app.HomeDealItem
import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.itad.ItadDealsFilter
import io.github.johnwes7.steal.shared.models.itad.ItadDealsRequest
import io.github.johnwes7.steal.shared.models.itad.ItadGiveaway
import io.github.johnwes7.steal.shared.models.itad.ItadShopRef
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.services.ItadApi
import io.github.johnwes7.steal.shared.services.SteamAppIDResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * 首页的状态：Hot Deals 前 5 条与 Giveaways 两个区块，各自加载、失败、重试，互不影响。
 * The home page's state: the first five Hot Deals and the Giveaways, each loading, failing and retrying on its own.
 *
 * 请求跑在 `viewModelScope` 里，切 tab 不会中断，页面出栈（ViewModel 被清掉）时才取消。
 * Requests run in `viewModelScope`: switching tabs does not stop them; they are cancelled when the page is popped.
 */
class GamesViewModel(
    private val itadApi: ItadApi,
    private val steamAppIDResolver: SteamAppIDResolver,
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

    // MARK: - 内部记录 / Internal bookkeeping

    /**
     * 当前内容属于哪个地区；`null` 表示还没加载过。
     * The region the current content belongs to; `null` before the first load.
     */
    private var loadedRegion: AppRegion? = null

    private var dealsJob: Job? = null

    private var giveawaysJob: Job? = null

    // MARK: - 界面调用 / Called by the UI

    /**
     * 第一次调用时加载两个区块，地区变了只重新加载 deals；这个地区已经加载过就什么也不做。
     * Loads both sections on the first call and only the deals when the region changes; does nothing for a region
     * already loaded.
     *
     * 页面每次出现都会调用，所以要幂等：切 tab、从详情页返回都不重新请求。
     * The page calls this every time it appears, so it is idempotent: switching tabs or coming back does not refetch.
     */
    fun loadIfNeeded(region: AppRegion) {
        // 1. 这个地区加载过就跳过，没有就记下
        // 1. Skip a region already loaded; otherwise record it
        if (region == loadedRegion) {
            return
        }
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

    /**
     * 下拉刷新：两个区块连同 appID 都重查，旧内容留到新数据回来；已经在刷新时再调用不做事。
     * Pull to refresh: refetches both sections and the appIDs, keeping the old content until new data arrives; does
     * nothing while already refreshing.
     *
     * 补 appID 失败的卡片也靠它重试。
     * It is also how cards whose appID lookup failed get retried.
     */
    fun refresh(region: AppRegion) {
        // 1. 正在刷新就跳过，否则记下地区、开始转圈
        // 1. Skip if already refreshing; otherwise record the region and start the spinner
        if (_isRefreshing.value) {
            return
        }
        loadedRegion = region
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
     * deals 失败后的重试：切回 Loading 重新请求。
     * Retries the deals after a failure, showing Loading again.
     */
    fun retryDeals(region: AppRegion) {
        loadDeals(region = region, showsLoading = true)
    }

    /**
     * giveaways 失败后的重试：切回 Loading 重新请求。
     * Retries the giveaways after a failure, showing Loading again.
     */
    fun retryGiveaways() {
        loadGiveaways(showsLoading = true)
    }

    // MARK: - 每个区块的请求 / Per-section requests
    // load… 管协程、马上返回；update… 在协程里取数据、写状态。
    // load… manages the coroutine and returns at once; update… fetches inside it and writes the state.

    private fun loadDeals(region: AppRegion, showsLoading: Boolean): Job {
        // 1. 取消旧请求：旧结果不会盖掉新结果
        // 1. Cancel the old request: a stale result never overwrites a fresh one
        dealsJob?.cancel()

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
