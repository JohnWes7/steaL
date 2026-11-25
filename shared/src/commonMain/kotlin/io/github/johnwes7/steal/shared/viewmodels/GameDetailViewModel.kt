package io.github.johnwes7.steal.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.johnwes7.steal.shared.config.AppLog
import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.itad.ItadGameInfoResponse
import io.github.johnwes7.steal.shared.models.itad.ItadPriceGame
import io.github.johnwes7.steal.shared.models.itad.game
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.models.steam.SteamAppDetails
import io.github.johnwes7.steal.shared.models.steam.SteamAppDetails.GameKind
import io.github.johnwes7.steal.shared.services.ItadApi
import io.github.johnwes7.steal.shared.services.SettingsRepository
import io.github.johnwes7.steal.shared.services.SteamApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 游戏详情页的状态：Steam 详情是硬门槛，ITAD 的比价和游戏信息各自加载，失败只少一块。
 * The game detail page's state: the Steam details are the hard requirement, while the ITAD prices and game info
 * load on their own, so a failure only costs one section.
 *
 * 每次推入详情页建一个，[appID]、ITAD UUID、[initialTitle] 来自导航。地区由 ViewModel 自己订阅设置，语言由页面经
 * [loadIfNeeded] 传进来（界面实际显示的那种翻译里的 Steam 代码）；两者有一个变了就整页重新加载。请求跑在
 * `viewModelScope` 里，推页返回、切 tab 都不会中断。
 * One is created for each pushed detail page, with [appID], the ITAD UUID and [initialTitle] from navigation. The
 * view model subscribes to the region in the settings itself, while the page passes the language in through
 * [loadIfNeeded] (the Steam code of the translation the UI actually shows); the whole page reloads when either
 * changes. Requests run in `viewModelScope`, so returning from another screen or switching tabs does not stop them.
 */
class GameDetailViewModel(
    /**
     * Steam appID，详情页的硬门槛。
     * The Steam appID, the detail page's hard requirement.
     */
    val appID: Int,

    uuid: String?,

    /**
     * 导航传进来的标题，Steam 详情回来之前（或没有名字时）显示它；之后不会变。
     * The title passed in by navigation, shown until the Steam details arrive (or when they have no name); it never
     * changes.
     */
    val initialTitle: String,

    private val steamApi: SteamApi,
    private val itadApi: ItadApi,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    // MARK: - 状态类型 / State types

    /**
     * 整页的状态：Steam 详情。
     * The whole page's state: the Steam details.
     */
    sealed interface DetailLoadState {

        /**
         * 第一次加载、换了地区或语言、或重试中。
         * The first load, a region or language change, or a retry.
         */
        data object Loading : DetailLoadState

        /**
         * Steam 详情到了。
         * The Steam details are in.
         */
        data class Loaded(val details: SteamAppDetails) : DetailLoadState

        /**
         * 请求成功，但 Steam 上没有这款游戏（下架或这个地区不卖）；不是失败，重试也不会变。
         * The request worked, but Steam has no such game (delisted or not sold in this region); not a failure, and a
         * retry would not help.
         */
        data object NotFound : DetailLoadState

        /**
         * 请求失败，原因在 [error]。
         * The request failed; [error] says why.
         */
        data class Failed(val error: LoadError) : DetailLoadState

        /**
         * 页面标题：有 Steam 的名字就用它，否则（还没加载完、没找到、失败、名字为空）用 [fallback]。
         * The page title: Steam's name when there is one, or else [fallback] (still loading, not found, failed or an
         * empty name).
         */
        fun title(fallback: String): String {
            if (this !is Loaded) {
                return fallback
            }
            val name = details.name
            if (name.isNullOrEmpty()) {
                return fallback
            }
            return name
        }
    }

    /**
     * ITAD 比价区块的状态。
     * The state of the ITAD prices section.
     */
    sealed interface PricesLoadState {

        /**
         * 这个区块不显示：Steam 详情还没到，或者是试玩版、没开预购的未发售游戏。
         * The section is not shown: the Steam details are not in yet, or it is a demo or an unreleased game not open
         * for pre-order.
         */
        data object Hidden : PricesLoadState

        /**
         * 正在查。
         * Fetching.
         */
        data object Loading : PricesLoadState

        /**
         * 比价到了，包括历史低价。
         * The prices are in, historical lows included.
         */
        data class Loaded(val prices: ItadPriceGame) : PricesLoadState

        /**
         * 比价到了，但不显示历史低价（永久免费：它的历史低价是免费之前的价格）。
         * The prices are in, minus the historical lows (free forever: its lows date from before it went free).
         */
        data class LoadedWithoutHistory(val prices: ItadPriceGame) : PricesLoadState

        /**
         * 请求成功，但 ITAD 没收录这款游戏，或这个地区没有报价；重试也不会变。
         * The request worked, but ITAD does not list the game or has no offers in this region; a retry would not help.
         */
        data object NotFound : PricesLoadState

        /**
         * 请求失败（查 UUID 或比价），给重试。
         * The request failed (the UUID lookup or the prices); offers a retry.
         */
        data object Failed : PricesLoadState

        companion object {

            /**
             * 把请求成功拿到的比价落成状态，[prices] 为 `null` 表示查不到；请求失败不走这里。
             * Turns the prices from a successful request into a state, a `null` [prices] meaning none were found;
             * failures never come here.
             *
             * 查不到时看 [kind]：未发售是还没开预购，藏起来；其他是「没有比价」。
             * When nothing is found, [kind] decides: unreleased means pre-orders have not opened, so it is hidden;
             * anything else is "no prices".
             */
            fun from(prices: ItadPriceGame?, kind: GameKind): PricesLoadState {
                if (prices == null) {
                    return missingPricesState(kind = kind)
                }
                if (prices.isEmpty) {
                    return missingPricesState(kind = kind)
                }
                if (kind.showsPriceHistory) {
                    return Loaded(prices = prices)
                }
                return LoadedWithoutHistory(prices = prices)
            }

            private fun missingPricesState(kind: GameKind): PricesLoadState {
                if (kind == GameKind.Unreleased) {
                    return Hidden
                }
                return NotFound
            }
        }
    }

    /**
     * ITAD 游戏信息区块的状态。
     * The state of the ITAD game info section.
     */
    sealed interface InfoLoadState {

        /**
         * 这个区块不显示：Steam 详情还没到，或者是试玩版。
         * The section is not shown: the Steam details are not in yet, or it is a demo.
         */
        data object Hidden : InfoLoadState

        /**
         * 正在查。
         * Fetching.
         */
        data object Loading : InfoLoadState

        /**
         * 游戏信息到了。
         * The game info is in.
         */
        data class Loaded(val info: ItadGameInfoResponse) : InfoLoadState

        /**
         * ITAD 没收录这款游戏；重试也不会变。
         * ITAD does not list this game; a retry would not help.
         */
        data object NotFound : InfoLoadState

        /**
         * 请求失败（查 UUID 或游戏信息），给重试。
         * The request failed (the UUID lookup or the game info); offers a retry.
         */
        data object Failed : InfoLoadState
    }

    // MARK: - 对外状态 / Exposed state

    private val _detailState = MutableStateFlow<DetailLoadState>(DetailLoadState.Loading)

    /**
     * 整页当前的状态，界面按它显示。
     * The whole page's current state, which the UI renders.
     */
    val detailState: StateFlow<DetailLoadState> = _detailState.asStateFlow()

    private val _pricesState = MutableStateFlow<PricesLoadState>(PricesLoadState.Hidden)

    /**
     * 比价区块当前的状态；Steam 详情到之前是 `Hidden`。
     * The prices section's current state; `Hidden` until the Steam details arrive.
     */
    val pricesState: StateFlow<PricesLoadState> = _pricesState.asStateFlow()

    private val _infoState = MutableStateFlow<InfoLoadState>(InfoLoadState.Hidden)

    /**
     * 游戏信息区块当前的状态；Steam 详情到之前是 `Hidden`。
     * The game info section's current state; `Hidden` until the Steam details arrive.
     */
    val infoState: StateFlow<InfoLoadState> = _infoState.asStateFlow()

    // MARK: - 内部记录 / Internal bookkeeping

    /**
     * 一次整页加载用的地区和语言，两者一起比较是否变了。
     * The region and language of one whole-page load, compared together to tell whether either changed.
     */
    private data class DetailRequest(val region: AppRegion, val steamLanguage: String)

    /**
     * ITAD 游戏 UUID：导航带来的，或查到后记下的；`null` 表示还没有（从搜索进来、或 ITAD 没收录）。
     * The ITAD game UUID: brought by navigation or remembered once looked up; `null` while there is none (entered
     * from search, or not listed by ITAD).
     */
    private var uuid: String? = uuid

    /**
     * 页面最近交进来的语言；`null` 表示还没交过。
     * The language the page last passed in; `null` until it has.
     */
    private val requestedLanguage = MutableStateFlow<String?>(null)

    /**
     * 是否已经开始订阅地区和语言，保证只订阅一次。
     * Whether the region and language subscription has started, so it starts only once.
     */
    private var isObservingRequests: Boolean = false

    /**
     * 最近一次整页加载用的地区和语言，重试用它；`null` 表示还没开始加载。
     * The region and language of the latest whole-page load, used by retries; `null` before any load.
     */
    private var loadedRequest: DetailRequest? = null

    private var detailJob: Job? = null

    private var pricesJob: Job? = null

    private var infoJob: Job? = null

    // MARK: - 界面调用 / Called by the UI

    /**
     * 开始加载，或交进新的语言：第一次调用时订阅设置里的地区；之后语言变了就整页重新加载，没变什么也不做。
     * Starts loading or hands in a new language: the first call subscribes to the region in the settings; after that
     * the whole page reloads when the language changes and nothing happens when it does not.
     *
     * 页面每次出现、界面语言每次变化都会调用，所以要幂等。[steamLanguage] 是 Steam `l=` 的代码，如 `schinese`。
     * The page calls this every time it appears and whenever the UI language changes, so it is idempotent.
     * [steamLanguage] is Steam's `l=` code, such as `schinese`.
     */
    fun loadIfNeeded(steamLanguage: String) {
        // 1. 交进最新的语言：和上次一样时 StateFlow 不会再发
        // 1. Hand in the latest language: StateFlow emits nothing when it equals the last one
        requestedLanguage.value = steamLanguage

        // 2. 只订阅一次
        // 2. Subscribe only once
        if (isObservingRequests) {
            return
        }
        isObservingRequests = true

        viewModelScope.launch {
            observeRequests()
        }
    }

    // MARK: - 订阅地区和语言 / Region and language subscription

    /**
     * 一直收集「地区 + 语言」，每来一组和上次不同的就整页加载一次；`viewModelScope` 取消时才结束。
     * Collects the region and language until `viewModelScope` is cancelled, loading the whole page once for each
     * pair that differs from the last.
     */
    private suspend fun observeRequests() {
        val regions = settingsRepository.settings.map { settings -> settings.region }
        val languages = requestedLanguage.filterNotNull()
        val requests = combine(regions, languages) { region, steamLanguage ->
            DetailRequest(region = region, steamLanguage = steamLanguage)
        }
        // 改深浅这类别的设置时整份设置会再发一次，地区和语言都没变就跳过。
        // Changing another setting (such as the theme) emits the whole settings again; skip when neither changed.
        val changedRequests = requests.distinctUntilChanged()
        changedRequests.collect { request -> loadDetail(request = request) }
    }

    // MARK: - 整页 / Whole page
    // load… 管协程、马上返回；update… 在协程里取数据、写状态。
    // load… manages the coroutine and returns at once; update… fetches inside it and writes the state.

    private fun loadDetail(request: DetailRequest) {
        // 1. 记下这次的地区和语言：重试用它
        // 1. Record this region and language: retries use them
        loadedRequest = request

        // 2. 三块的旧请求一起取消：旧地区、旧语言的结果不会盖掉新的
        // 2. Cancel all three old requests: results for the old region or language never overwrite new ones
        detailJob?.cancel()
        pricesJob?.cancel()
        infoJob?.cancel()

        // 3. 启动新的整页加载
        // 3. Launch the new whole-page load
        detailJob = viewModelScope.launch {
            updateDetail(request = request)
        }
    }

    private suspend fun updateDetail(request: DetailRequest) {
        // 1. 整页切到 Loading，两块 ITAD 先藏起来
        // 1. Show Loading for the page and hide both ITAD sections for now
        _detailState.value = DetailLoadState.Loading
        _pricesState.value = PricesLoadState.Hidden
        _infoState.value = InfoLoadState.Hidden

        // 2. 取 Steam 详情：拿不到就没有详情页；查不到不是失败
        // 2. Fetch the Steam details: no details, no page; a miss is not a failure
        val details: SteamAppDetails?
        try {
            details = steamApi.fetchAppDetails(
                appID = appID,
                region = request.region,
                steamLanguage = request.steamLanguage,
            )
        } catch (cancellation: CancellationException) {
            // 取消不是失败，要原样抛出，协程才会结束。
            // A cancellation is no failure; rethrow it so the coroutine ends.
            throw cancellation
        } catch (error: Exception) {
            AppLog.gameDetail.e(error) { "Steam 详情加载失败 / Loading the Steam details failed: $appID" }
            _detailState.value = DetailLoadState.Failed(error = LoadError.from(error))
            return
        }
        if (details == null) {
            _detailState.value = DetailLoadState.NotFound
            return
        }
        _detailState.value = DetailLoadState.Loaded(details = details)

        // 3. 按类型决定 ITAD 两块：试玩版的比价恒为空、ITAD 也很少收录，两块都不查
        // 3. Let the kind decide the ITAD sections: a demo's prices are always empty and ITAD rarely lists demos, so
        //    neither is loaded
        val kind = details.gameKind
        if (kind == GameKind.Demo) {
            return
        }
        _pricesState.value = PricesLoadState.Loading
        _infoState.value = InfoLoadState.Loading

        // 4. 两块共用一次 UUID 查找：先在这里查好，两块再各自用记下的那个
        // 4. Both sections share one UUID lookup: done here first, so each section then uses the remembered one
        val resolvedUUID: String?
        try {
            resolvedUUID = resolveUUID()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.gameDetail.e(error) { "ITAD UUID 查找失败 / Looking up the ITAD UUID failed: $appID" }
            _pricesState.value = PricesLoadState.Failed
            _infoState.value = InfoLoadState.Failed
            return
        }
        if (resolvedUUID == null) {
            _pricesState.value = PricesLoadState.from(prices = null, kind = kind)
            _infoState.value = InfoLoadState.NotFound
            return
        }

        // 5. 两块各开一个协程，谁先回来谁先显示
        // 5. One coroutine per section, each shown as soon as it arrives
        loadPrices(region = request.region, kind = kind)
        loadInfo()
    }

    // MARK: - 比价 / Prices

    private fun loadPrices(region: AppRegion, kind: GameKind) {
        // 1. 取消旧请求
        // 1. Cancel the old request
        pricesJob?.cancel()

        // 2. 启动新请求，记下来供下次取消
        // 2. Launch the new one and keep it for the next cancel
        pricesJob = viewModelScope.launch {
            updatePrices(region = region, kind = kind)
        }
    }

    private suspend fun updatePrices(region: AppRegion, kind: GameKind) {
        // 1. 切到 Loading
        // 1. Show Loading
        _pricesState.value = PricesLoadState.Loading

        // 2. 拿 UUID：已经有就直接用，没有再查一次
        // 2. Get the UUID: use the remembered one, or look it up once
        val resolvedUUID: String?
        try {
            resolvedUUID = resolveUUID()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.gameDetail.e(error) { "ITAD UUID 查找失败 / Looking up the ITAD UUID failed: $appID" }
            _pricesState.value = PricesLoadState.Failed
            return
        }
        if (resolvedUUID == null) {
            _pricesState.value = PricesLoadState.from(prices = null, kind = kind)
            return
        }

        // 3. 查比价：响应是稀疏的，按 UUID 取
        // 3. Fetch the prices: the response is sparse, so look the game up by UUID
        try {
            val priceGames = itadApi.fetchPrices(uuids = listOf(resolvedUUID), region = region)
            val prices = priceGames.game(uuid = resolvedUUID)
            _pricesState.value = PricesLoadState.from(prices = prices, kind = kind)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.gameDetail.e(error) { "比价加载失败 / Loading the prices failed: $appID" }
            _pricesState.value = PricesLoadState.Failed
        }
    }

    // MARK: - 游戏信息 / Game info

    private fun loadInfo() {
        // 1. 取消旧请求
        // 1. Cancel the old request
        infoJob?.cancel()

        // 2. 启动新请求，记下来供下次取消
        // 2. Launch the new one and keep it for the next cancel
        infoJob = viewModelScope.launch {
            updateInfo()
        }
    }

    private suspend fun updateInfo() {
        // 1. 切到 Loading
        // 1. Show Loading
        _infoState.value = InfoLoadState.Loading

        // 2. 拿 UUID：已经有就直接用，没有再查一次
        // 2. Get the UUID: use the remembered one, or look it up once
        val resolvedUUID: String?
        try {
            resolvedUUID = resolveUUID()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.gameDetail.e(error) { "ITAD UUID 查找失败 / Looking up the ITAD UUID failed: $appID" }
            _infoState.value = InfoLoadState.Failed
            return
        }
        if (resolvedUUID == null) {
            _infoState.value = InfoLoadState.NotFound
            return
        }

        // 3. 查游戏信息：UUID 已确认收录，只有成功或失败
        // 3. Fetch the game info: the UUID is known to be listed, so it either loads or fails
        try {
            val info = itadApi.fetchGameInfo(uuid = resolvedUUID)
            _infoState.value = InfoLoadState.Loaded(info = info)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.gameDetail.e(error) { "游戏信息加载失败 / Loading the game info failed: $appID" }
            _infoState.value = InfoLoadState.Failed
        }
    }

    // MARK: - ITAD UUID

    /**
     * 返回 ITAD UUID：已经有（导航带来或查过）就直接用，否则按 [appID] 查一次并记下；ITAD 没收录返回 `null`。
     * Returns the ITAD UUID: the one already known (from navigation or an earlier lookup), or else one looked up by
     * [appID] and remembered; `null` when ITAD does not list the game.
     *
     * 查询请求失败时把异常抛给调用方，由它决定哪块显示失败。
     * A failed lookup request throws to the caller, which decides which section shows the failure.
     */
    private suspend fun resolveUUID(): String? {
        val knownUUID = uuid
        if (knownUUID != null) {
            return knownUUID
        }

        val lookup = itadApi.lookupGame(appID = appID)
        if (!lookup.found) {
            return null
        }
        val foundUUID = lookup.game?.id
        uuid = foundUUID
        return foundUUID
    }
}
