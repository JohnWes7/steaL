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
 * 对应 iOS `GamesViewModel`。地区不存在这里，每次由页面传入（同 iOS）。公开方法都由界面在主线程调用，
 * 请求在 `viewModelScope` 里跑：页面暂时离开（切 tab）不会中断，ViewModel 被清掉（页面出栈）时才一起取消。
 * 每个区块同时只有一个请求：新请求先取消同区块的旧请求，旧结果不会盖掉新结果。
 * The counterpart of iOS `GamesViewModel`. The region is not stored here; the page passes it in every time (as on
 * iOS). The UI calls the public methods on the main thread, and requests run in `viewModelScope`: leaving the page
 * for a while (switching tabs) does not stop them, and they are cancelled together only when the view model is
 * cleared (the page is popped). Each section has one request at a time: a new one cancels the section's old one
 * first, so a stale result never overwrites a fresh one.
 *
 * [itadApi] 取 deals 与 giveaways；[steamAppIDResolver] 给每条 deal 补 Steam appID。
 * [itadApi] fetches deals and giveaways; [steamAppIDResolver] resolves each deal's Steam appID.
 */
class GamesViewModel(
    private val itadApi: ItadApi,
    private val steamAppIDResolver: SteamAppIDResolver,
) : ViewModel() {

    // 状态类型 / State types

    /**
     * deals 区块的状态。
     * The state of the deals section.
     */
    sealed interface DealLoadState {

        /**
         * 第一次加载、换了地区或重试中。
         * The first load, a region change, or a retry in progress.
         */
        data object Loading : DealLoadState

        /**
         * 请求成功，[items] 可能为空；每条的 Steam appID 可能还在补。
         * The request succeeded; [items] may be empty, and each row's Steam appID may still be resolving.
         */
        data class Loaded(val items: List<HomeDealItem>) : DealLoadState

        /**
         * 请求失败，[error] 是归一后的原因。
         * The request failed; [error] is the normalised reason.
         */
        data class Failed(val error: LoadError) : DealLoadState
    }

    /**
     * giveaways 区块的状态，和 deals 互不影响。
     * The state of the giveaways section, independent of the deals.
     */
    sealed interface GiveawaysLoadState {

        /**
         * 第一次加载或重试中。
         * The first load, or a retry in progress.
         */
        data object Loading : GiveawaysLoadState

        /**
         * 请求成功，[giveaways] 可能为空。
         * The request succeeded; [giveaways] may be empty.
         */
        data class Loaded(val giveaways: List<ItadGiveaway>) : GiveawaysLoadState

        /**
         * 请求失败，[error] 是归一后的原因。
         * The request failed; [error] is the normalised reason.
         */
        data class Failed(val error: LoadError) : GiveawaysLoadState
    }

    // 对外状态 / Exposed state

    /**
     * deals 区块的状态，可写的那份只在 ViewModel 内部。
     * The deals section's state; the writable copy stays inside the view model.
     */
    private val _dealState = MutableStateFlow<DealLoadState>(DealLoadState.Loading)

    /**
     * deals 区块的状态，界面只读。
     * The deals section's state, read-only for the UI.
     */
    val dealState: StateFlow<DealLoadState> = _dealState.asStateFlow()

    /**
     * giveaways 区块的状态，可写的那份只在 ViewModel 内部。
     * The giveaways section's state; the writable copy stays inside the view model.
     */
    private val _giveawaysState = MutableStateFlow<GiveawaysLoadState>(GiveawaysLoadState.Loading)

    /**
     * giveaways 区块的状态，界面只读。
     * The giveaways section's state, read-only for the UI.
     */
    val giveawaysState: StateFlow<GiveawaysLoadState> = _giveawaysState.asStateFlow()

    /**
     * 是否正在下拉刷新，可写的那份只在 ViewModel 内部。
     * Whether a pull to refresh is running; the writable copy stays inside the view model.
     */
    private val _isRefreshing = MutableStateFlow(false)

    /**
     * 是否正在下拉刷新，交给 `PullToRefreshBox(isRefreshing = …)` 显示转圈。
     * Whether a pull to refresh is running, for `PullToRefreshBox(isRefreshing = …)` to show its spinner.
     *
     * iOS 的 `.refreshable` 自己等 `await` 结束收起转圈；Compose 的 `PullToRefreshBox` 要调用方告诉它刷新完没有。
     * iOS `.refreshable` hides its spinner when the `await` returns; Compose's `PullToRefreshBox` needs the caller to
     * say when refreshing has finished.
     */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // 内部记录 / Internal bookkeeping

    /**
     * 当前内容属于哪个地区；`null` 表示还没加载过。
     * The region the current content belongs to; `null` before the first load.
     */
    private var loadedRegion: AppRegion? = null

    /**
     * deals 区块正在跑的请求（含补 appID）。
     * The deals section's running request, appID resolution included.
     */
    private var dealsJob: Job? = null

    /**
     * giveaways 区块正在跑的请求。
     * The giveaways section's running request.
     */
    private var giveawaysJob: Job? = null

    // 界面调用 / Called by the UI

    /**
     * 第一次显示或地区变了时加载两个区块；这个地区已经加载过就什么也不做。
     * Loads both sections on first display or when the region changes; does nothing for a region already loaded.
     *
     * 页面每次出现都会调用（`LaunchedEffect`），所以要幂等：切 tab、从详情页返回都不重新请求（同 iOS `hasStarted`），
     * 只有 [region] 变了才重新加载（同 iOS `.onChange(of: region)`）。不放在 `init` 里：Android 官方
     * 「UI State production」不建议在 ViewModel 构造时发起异步操作，要写成由界面调用的幂等函数。
     * The page calls this every time it appears (`LaunchedEffect`), so it is idempotent: switching tabs or coming
     * back from the detail page does not refetch (like iOS `hasStarted`), and only a new [region] reloads (like iOS
     * `.onChange(of: region)`). It is not in `init`: Android's "UI State production" guide advises against starting
     * async work while a view model is being constructed and suggests an idempotent function the UI calls instead.
     */
    fun loadIfNeeded(region: AppRegion) {
        if (region == loadedRegion) {
            return
        }
        loadedRegion = region
        loadDeals(region = region, showsLoading = true)
        loadGiveaways(showsLoading = true)
    }

    /**
     * 下拉刷新：两个区块都重查，旧内容留到新数据回来；appID 也一起重查，所以它也是补 appID 失败后的重试入口。
     * Pull to refresh: refetches both sections and keeps the old content until new data arrives; appIDs are resolved
     * again too, which makes this the retry for a failed appID lookup.
     *
     * [isRefreshing] 在两个区块都结束后变回 `false`；已经在刷新时再调用不做事。
     * [isRefreshing] turns back to `false` once both sections finish; calling this while refreshing does nothing.
     */
    fun refresh(region: AppRegion) {
        if (_isRefreshing.value) {
            return
        }
        loadedRegion = region
        _isRefreshing.value = true

        val deals = loadDeals(region = region, showsLoading = false)
        val giveaways = loadGiveaways(showsLoading = false)
        viewModelScope.launch {
            joinAll(deals, giveaways)
            _isRefreshing.value = false
        }
    }

    /**
     * 重试 deals，只影响这一个区块。
     * Retries the deals; only this section is affected.
     */
    fun retryDeals(region: AppRegion) {
        loadDeals(region = region, showsLoading = true)
    }

    /**
     * 重试 giveaways，只影响这一个区块。
     * Retries the giveaways; only this section is affected.
     */
    fun retryGiveaways() {
        loadGiveaways(showsLoading = true)
    }

    // 每个区块的请求：load 启动协程、马上返回，fetch 是协程里真正做事的 suspend 函数。
    // Per-section requests: load starts a coroutine and returns at once; fetch is the suspend function doing the work.

    /**
     * 取消 deals 区块的旧请求，启动新的一次，返回它的 [Job]。
     * Cancels the deals section's old request, starts a new one, and returns its [Job].
     *
     * [showsLoading] 为 `false` 时（下拉刷新）不切到 `Loading`，旧列表留着。
     * When [showsLoading] is `false` (pull to refresh), the state does not switch to `Loading` and the old list stays.
     */
    private fun loadDeals(region: AppRegion, showsLoading: Boolean): Job {
        dealsJob?.cancel()
        val job = viewModelScope.launch {
            fetchDeals(region = region, showsLoading = showsLoading)
        }
        dealsJob = job
        return job
    }

    /**
     * 取消 giveaways 区块的旧请求，启动新的一次，返回它的 [Job]。
     * Cancels the giveaways section's old request, starts a new one, and returns its [Job].
     *
     * [showsLoading] 为 `false` 时（下拉刷新）不切到 `Loading`，旧列表留着。
     * When [showsLoading] is `false` (pull to refresh), the state does not switch to `Loading` and the old list stays.
     */
    private fun loadGiveaways(showsLoading: Boolean): Job {
        giveawaysJob?.cancel()
        val job = viewModelScope.launch {
            fetchGiveaways(showsLoading = showsLoading)
        }
        giveawaysJob = job
        return job
    }

    // deals

    /**
     * 取首页的 deals，先发布列表，再补 Steam appID。
     * Fetches the home deals, publishes the list first, then resolves the Steam appIDs.
     */
    private suspend fun fetchDeals(region: AppRegion, showsLoading: Boolean) {
        if (showsLoading) {
            _dealState.value = DealLoadState.Loading
        }

        try {
            val response = itadApi.fetchDeals(request = homeDealsRequest(region = region))
            val items = response.list.map { deal -> HomeDealItem(deal = deal) }
            // 先发布，卡片马上显示，appID 还是 Pending；补 appID 不挡首屏。
            // Publish first so the cards show right away with appIDs still Pending; resolving never delays them.
            _dealState.value = DealLoadState.Loaded(items = items)
        } catch (cancellation: CancellationException) {
            // 取消（换地区、刷新或重试接手、ViewModel 清掉）不是失败，原样抛出去。
            // A cancellation (new region, a refresh or retry taking over, the view model cleared) is no failure; rethrow it.
            throw cancellation
        } catch (error: Exception) {
            // 完整错误只进日志，界面只看到 LoadError。
            // The full error goes to the log only; the UI sees just a LoadError.
            AppLog.games.e(error) { "deals 加载失败 / Loading deals failed" }
            _dealState.value = DealLoadState.Failed(error = LoadError.from(error))
            return
        }

        resolveAppIDs(region = region)
    }

    /**
     * 首页 deals 的请求体：这个地区的 5 条热门优惠，只要游戏、只要 Steam 激活的。
     * The home deals request body: five hot deals for the region, games only, Steam activation only.
     */
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

    // Steam appID

    /**
     * 给当前列表里的每条 deal 批量补 Steam appID。
     * Resolves the Steam appID for every deal in the current list, in one batch.
     *
     * 失败不改列表本身：列表是好的，只是还进不了详情页，所以只把还在等的条目标成 `Failed`。
     * A failure leaves the list itself alone: it is fine, the rows just cannot open the detail page yet, so only the
     * rows still waiting become `Failed`.
     */
    private suspend fun resolveAppIDs(region: AppRegion) {
        val state = _dealState.value
        if (state !is DealLoadState.Loaded) {
            return
        }
        if (state.items.isEmpty()) {
            return
        }

        val uuids = state.items.map { item -> item.itadUUID }
        val appIDByUUID: Map<String, Int>
        try {
            appIDByUUID = steamAppIDResolver.resolveAppIDs(uuids = uuids, region = region)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.games.e(error) { "Steam appID 补全失败 / Resolving Steam appIDs failed" }
            settlePendingAsFailed()
            return
        }

        applyAppIDs(appIDByUUID = appIDByUUID)
    }

    /**
     * 按查到的 appID 整批更新列表，不在 [appIDByUUID] 里的标成 `NotFound`。
     * Updates the whole list with the resolved appIDs; rows missing from [appIDByUUID] become `NotFound`.
     *
     * 只在查询成功后调用，所以不在字典里就是真的没有；请求失败走 [settlePendingAsFailed]。
     * Only called after a successful lookup, so a missing row really has none; a failed request goes to
     * [settlePendingAsFailed].
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

    /**
     * 查询请求失败后，把还在等的条目标成 `Failed`，否则卡片会一直点不动。
     * After the lookup request fails, marks the rows still waiting as `Failed`; otherwise those cards never respond.
     */
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

    // giveaways

    /**
     * 取限时免费领取列表。
     * Fetches the giveaways list.
     */
    private suspend fun fetchGiveaways(showsLoading: Boolean) {
        if (showsLoading) {
            _giveawaysState.value = GiveawaysLoadState.Loading
        }

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
