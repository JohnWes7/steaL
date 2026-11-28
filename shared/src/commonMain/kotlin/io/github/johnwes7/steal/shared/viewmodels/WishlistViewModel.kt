package io.github.johnwes7.steal.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.johnwes7.steal.shared.config.AppLog
import io.github.johnwes7.steal.shared.models.app.WishlistFilter
import io.github.johnwes7.steal.shared.models.app.WishlistItem
import io.github.johnwes7.steal.shared.models.itad.ItadPriceDeal
import io.github.johnwes7.steal.shared.models.itad.ItadPriceGame
import io.github.johnwes7.steal.shared.models.itad.game
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.models.steam.SteamAppDetails
import io.github.johnwes7.steal.shared.services.ItadApi
import io.github.johnwes7.steal.shared.services.SettingsRepository
import io.github.johnwes7.steal.shared.services.SteamApi
import io.github.johnwes7.steal.shared.services.WishlistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Wishlist tab 根页面的状态：本机愿望单（新加的在前）、每行的价格、选中的筛选。
 * The state of the Wishlist tab's root screen: the on-device wishlist (newest first), each row's price and the
 * selected filter.
 *
 * 收藏本身只存在数据库里：这里读它，只回写查到的 UUID 和发售状态。价格只存在内存里、不过期。请求跑在 `viewModelScope`
 * 里，切 tab 不会中断。
 * The saved games live only in the database: this reads them and only writes back the UUIDs and release statuses
 * it finds. Prices live in memory and never expire. Requests run in `viewModelScope`, so switching tabs does not
 * stop them.
 *
 * @param clock 判断「一天内核对过」用的时钟；测试传固定时钟。
 * The clock for "checked within a day"; tests pass a fixed one.
 */
class WishlistViewModel(
    private val wishlistRepository: WishlistRepository,
    private val itadApi: ItadApi,
    private val steamApi: SteamApi,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    // MARK: - 状态类型 / State types

    /**
     * 一行的价格状态。
     * One row's price state.
     */
    sealed interface PriceLoadState {

        /**
         * 还在查。
         * Still fetching.
         */
        data object Loading : PriceLoadState

        /**
         * 查到了：能在 Steam 激活的报价里最便宜的那条。
         * Found: the cheapest offer that activates on Steam.
         */
        data class Loaded(val deal: ItadPriceDeal) : PriceLoadState

        /**
         * 请求成功，但 ITAD 没收录，或没有能在 Steam 激活的报价；重试也不会变。
         * The request worked, but ITAD does not list the game or has no offer that activates on Steam; a retry would
         * not help.
         */
        data object NoPrice : PriceLoadState

        /**
         * 查 UUID 或查价失败，下拉刷新重试。
         * The UUID lookup or the price request failed; pull to refresh retries.
         */
        data object Failed : PriceLoadState
    }

    /**
     * 列表里的一行：收藏的游戏和它现在的价格。
     * One row of the list: a saved game and its current price.
     */
    data class Row(val item: WishlistItem, val price: PriceLoadState)

    // MARK: - 对外状态 / Exposed state

    /**
     * 整张愿望单，不看筛选；`null` 表示还没读到（只在页面第一次出现的一瞬间），空列表是真的没有收藏。
     * The whole wishlist, ignoring the filter; `null` until it is read (only for a moment as the screen first
     * appears), while an empty list means nothing is saved.
     */
    val items: StateFlow<List<WishlistItem>?> = wishlistRepository.items.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    private val _selectedFilter = MutableStateFlow(WishlistFilter.All)

    /**
     * 顶部选中的筛选项；切 tab 回来还在。
     * The filter selected at the top; it survives switching tabs.
     */
    val selectedFilter: StateFlow<WishlistFilter> = _selectedFilter.asStateFlow()

    /**
     * 每行的价格，按 appID；字典里没有的行就是还在查。
     * Each row's price by appID; a row missing from the map is still being fetched.
     *
     * 不要往里写 `Loading`：查价被取消时字典里不留东西，下次加载才知道要重查这一行；写进去就会一直转圈。
     * Never store `Loading` here: a cancelled fetch then leaves nothing behind and the next load knows to refetch the
     * row, while a stored one would spin forever.
     */
    private val _priceStates = MutableStateFlow<Map<Int, PriceLoadState>>(emptyMap())

    /**
     * 是否正在向 Steam 核对发售状态；只用来算 [isFilterPending]。
     * Whether release statuses are being checked with Steam; only used to work out [isFilterPending].
     */
    private val _isCheckingReleases = MutableStateFlow(false)

    /**
     * 筛选后要显示的行，顺序同 [items]；`null` 表示愿望单还没读到。
     * The rows to show after filtering, in the order of [items]; `null` until the wishlist is read.
     */
    val visibleRows: StateFlow<List<Row>?> = combine(
        items,
        _priceStates,
        _selectedFilter,
    ) { currentItems, priceStates, filter ->
        visibleRowsOf(items = currentItems, priceStates = priceStates, filter = filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    /**
     * 这个筛选要的数据是否还有行在等；筛选结果为空时，界面用它分开「还在查」和「真的没有」。
     * Whether any row is still waiting for the data this filter needs; when the filter comes up empty, the UI uses
     * it to tell "still checking" from "none".
     */
    val isFilterPending: StateFlow<Boolean> = combine(
        items,
        _priceStates,
        _selectedFilter,
        _isCheckingReleases,
    ) { currentItems, priceStates, filter, isCheckingReleases ->
        isFilterPendingFor(
            items = currentItems.orEmpty(),
            priceStates = priceStates,
            filter = filter,
            isCheckingReleases = isCheckingReleases,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = false,
    )

    private val _isRefreshing = MutableStateFlow(false)

    /**
     * 是否正在下拉刷新，给 `PullToRefreshBox` 显示转圈。
     * Whether a pull to refresh is running, for `PullToRefreshBox`'s spinner.
     */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // MARK: - 内部记录 / Internal bookkeeping

    /**
     * 一次加载看的东西：收藏了哪些 appID、在哪个地区，两者一起比较是否变了。
     * What one load depends on: which appIDs are saved and in which region, compared together to tell whether either
     * changed.
     *
     * 只看 appID、不看整行：回写 UUID 和发售状态会改行，但不该打断正在查的价格。
     * Only the appIDs, not the whole rows: writing back UUIDs and release statuses changes the rows, which must not
     * interrupt a price fetch in progress.
     */
    private data class LoadRequest(val appIDs: Set<Int>, val region: AppRegion)

    /**
     * 一次 UUID lookup 的结果。
     * The outcome of one UUID lookup.
     */
    private sealed interface LookupResult {
        data class Found(val uuid: String) : LookupResult
        data object NotFound : LookupResult
        data object Failed : LookupResult
    }

    /**
     * 是否已经开始订阅，保证只订阅一次。
     * Whether the subscription has started, so it starts only once.
     */
    private var isObservingRequests: Boolean = false

    /**
     * 页面最近交进来的 Steam 语言，核对发售状态时用；[loadIfNeeded] 第一次调用时就有值，订阅在那之后才开始。
     * The Steam language the page last passed in, used for release checks; set by the first [loadIfNeeded] call,
     * before the subscription starts.
     */
    private lateinit var requestedLanguage: String

    /**
     * 当前价格属于的地区；`null` 表示还没加载过。
     * The region the current prices belong to; `null` before any load.
     */
    private var loadedRegion: AppRegion? = null

    private var pricesJob: Job? = null

    private var releasesJob: Job? = null

    // MARK: - 界面调用 / Called by the UI

    /**
     * 开始加载，或交进新的语言：第一次调用时订阅收藏的 appID 和设置里的地区，之后再调用只更新语言。
     * Starts loading or hands in a new language: the first call subscribes to the saved appIDs and the region in
     * the settings; later calls only update the language.
     *
     * 页面每次出现、界面语言每次变化都会调用，所以要幂等。[steamLanguage] 是 Steam `l=` 的代码（如 `schinese`），只用来核对
     * 发售状态：结果和语言无关，所以换语言不重新加载；传界面语言是为了和详情页共用 HTTP 缓存。
     * The page calls this every time it appears and whenever the UI language changes, so it is idempotent.
     * [steamLanguage] is Steam's `l=` code (such as `schinese`) and is only used for release checks: the result does
     * not depend on it, so a language change reloads nothing; the UI language is passed to share the HTTP cache with
     * the detail page.
     */
    fun loadIfNeeded(steamLanguage: String) {
        // 1. 记下最新的语言
        // 1. Record the latest language
        requestedLanguage = steamLanguage

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

    /**
     * 换筛选项。
     * Switches the filter.
     */
    fun selectFilter(filter: WishlistFilter) {
        _selectedFilter.value = filter
    }

    // MARK: - 订阅 / Subscription

    /**
     * 一直收集「收藏的 appID + 地区」，每来一组和上次不同的就加载一次；`viewModelScope` 取消时才结束。
     * Collects the saved appIDs and the region until `viewModelScope` is cancelled, loading once for each pair that
     * differs from the last.
     */
    private suspend fun observeRequests() {
        val regions = settingsRepository.settings.map { settings -> settings.region }
        val requests = combine(wishlistRepository.savedAppIDs, regions) { appIDs, region ->
            LoadRequest(appIDs = appIDs, region = region)
        }
        // 回写、改别的设置都会再发一次，appID 和地区都没变就跳过。
        // Write-backs and other settings changes emit again; skip when neither the appIDs nor the region changed.
        val changedRequests = requests.distinctUntilChanged()
        changedRequests.collect { request -> loadSections(request = request) }
    }

    // MARK: - 加载 / Loading
    // load… 管协程、马上返回；update… 在协程里取数据、写状态。
    // load… manages the coroutines and returns at once; update… fetches inside them and writes the state.

    private fun loadSections(request: LoadRequest) {
        // 1. 两块的旧任务都取消：被打断的行没留下状态，新任务会重查
        // 1. Cancel both old tasks: interrupted rows left no state behind, so the new tasks refetch them
        pricesJob?.cancel()
        releasesJob?.cancel()

        // 2. 换了地区就清空价格：币种不同
        // 2. Clear the prices on a region change: the currency differs
        if (loadedRegion != request.region) {
            _priceStates.value = emptyMap()
            loadedRegion = request.region
        }

        // 3. 查价和核对发售状态同时开始，互不等待
        // 3. Start the prices and the release checks together, neither waiting for the other
        pricesJob = viewModelScope.launch {
            updatePrices(region = request.region)
        }
        releasesJob = viewModelScope.launch {
            updateReleases(region = request.region, steamLanguage = requestedLanguage)
        }
    }

    // MARK: - 筛选 / Filtering

    private fun visibleRowsOf(
        items: List<WishlistItem>?,
        priceStates: Map<Int, PriceLoadState>,
        filter: WishlistFilter,
    ): List<Row>? {
        if (items == null) {
            return null
        }
        val rows = items.map { item -> Row(item = item, price = priceStateOf(item = item, priceStates = priceStates)) }
        val matchingRows = rows.filter { row -> matches(row = row, filter = filter) }
        return matchingRows
    }

    /**
     * 一行是否符合 [filter]；价格没查到（还在查、没价格、失败）的行只在 All 里出现。
     * Whether a row passes [filter]; rows without a loaded price (still fetching, no price, failed) only show under
     * All.
     */
    private fun matches(row: Row, filter: WishlistFilter): Boolean {
        val price = row.price
        val deal = if (price is PriceLoadState.Loaded) price.deal else null
        when (filter) {
            WishlistFilter.All -> return true
            WishlistFilter.OnSale -> {
                if (deal == null) {
                    return false
                }
                return deal.cut > 0
            }
            WishlistFilter.AllTimeLow -> {
                if (deal == null) {
                    return false
                }
                // ITAD 的 H / N 标记，和首页「ALL-TIME LOW」标签同一个判断。
                // ITAD's H / N flag, the same test as the home page's "ALL-TIME LOW" tag.
                return deal.isHistoricalLow
            }
            WishlistFilter.Upcoming -> return row.item.isComingSoon == true
        }
    }

    private fun isFilterPendingFor(
        items: List<WishlistItem>,
        priceStates: Map<Int, PriceLoadState>,
        filter: WishlistFilter,
        isCheckingReleases: Boolean,
    ): Boolean {
        when (filter) {
            WishlistFilter.All -> return false
            WishlistFilter.OnSale, WishlistFilter.AllTimeLow -> {
                val hasLoadingPrice = items.any { item ->
                    priceStateOf(item = item, priceStates = priceStates) == PriceLoadState.Loading
                }
                return hasLoadingPrice
            }
            WishlistFilter.Upcoming -> {
                // 核对完还是 `null`（请求失败）就不再算在等。
                // Still `null` after the check (it failed) no longer counts as waiting.
                if (!isCheckingReleases) {
                    return false
                }
                val hasUncheckedRelease = items.any { item -> item.isComingSoon == null }
                return hasUncheckedRelease
            }
        }
    }

    /**
     * [item] 现在的价格状态：字典里没有就是还在查。
     * [item]'s current price state: missing from the map means still fetching.
     */
    private fun priceStateOf(item: WishlistItem, priceStates: Map<Int, PriceLoadState>): PriceLoadState {
        val state = priceStates[item.appID]
        if (state == null) {
            return PriceLoadState.Loading
        }
        return state
    }

    // MARK: - 价格 / Prices

    /**
     * 查还没有价格状态的行，结果整批写一次。
     * Fetches the rows that have no price state yet and writes the results in one go.
     *
     * 被取消（appID 或地区又变了）时什么也不写：没查完的行留在「没有状态」，下一次加载会重查。
     * When cancelled (the appIDs or the region changed again) nothing is written: unfinished rows stay without a
     * state, so the next load refetches them.
     */
    private suspend fun updatePrices(region: AppRegion) {
        // 1. 读现在的收藏，丢掉已删掉的行：重新加回来时会再查
        // 1. Read the current wishlist and drop removed rows, so a re-added game is fetched again
        val items = wishlistRepository.items.first()
        val appIDList = items.map { item -> item.appID }
        val currentAppIDs = appIDList.toSet()
        val keptStates = _priceStates.value.filterKeys { appID -> appID in currentAppIDs }
        _priceStates.value = keptStates

        // 2. 只查还没有状态的行：删一行不会重查别的
        // 2. Only fetch rows without a state, so removing one refetches nothing else
        val newItems = items.filter { item -> item.appID !in keptStates }
        if (newItems.isEmpty()) {
            return
        }

        // 3. 查完整批写回：界面只重算一次
        // 3. Write the whole batch back at once, so the UI recomputes once
        val newStates = fetchPriceStates(items = newItems, region = region)
        _priceStates.value = _priceStates.value + newStates
    }

    /**
     * 查 [items] 的价格状态：缺 UUID 的先 lookup（查到就回写），再批量查价。
     * Fetches the price states of [items]: rows missing a UUID are looked up first (hits are written back), then
     * the prices are fetched in batches.
     */
    private suspend fun fetchPriceStates(items: List<WishlistItem>, region: AppRegion): Map<Int, PriceLoadState> {
        // 1. 存着 UUID 的直接用，其余要 lookup
        // 1. Use stored UUIDs; the rest need a lookup
        val uuidByAppID = mutableMapOf<Int, String>()
        val appIDsToLookUp = mutableListOf<Int>()
        for (item in items) {
            val itadID = item.itadID
            if (itadID != null) {
                uuidByAppID[item.appID] = itadID
            } else {
                appIDsToLookUp.add(item.appID)
            }
        }

        // 2. 并行 lookup：查不到、失败的行直接定下状态，查到的回写数据库
        // 2. Parallel lookups: misses and failures settle their rows at once, hits are written to the database
        val states = mutableMapOf<Int, PriceLoadState>()
        val lookupResults = lookUpUUIDs(appIDs = appIDsToLookUp)
        for ((appID, result) in lookupResults) {
            when (result) {
                is LookupResult.Found -> {
                    uuidByAppID[appID] = result.uuid
                    saveItadID(appID = appID, itadID = result.uuid)
                }
                LookupResult.NotFound -> states[appID] = PriceLoadState.NoPrice
                LookupResult.Failed -> states[appID] = PriceLoadState.Failed
            }
        }

        // 3. 有 UUID 的批量查价
        // 3. Batch-fetch the prices of rows with a UUID
        val pricedStates = fetchPriceStatesByUUID(uuidByAppID = uuidByAppID, region = region)
        states.putAll(pricedStates)
        return states
    }

    /**
     * 按 UUID 批量查价；任何一批请求失败，这次的行全部 `Failed`。
     * Fetches prices by UUID in batches; if any batch fails, every row of this fetch is `Failed`.
     *
     * `prices/v3` 没有 DRM 筛选，所以拿全部商店，再在客户端取能在 Steam 激活的最便宜报价（`bestSteamDeal`）。
     * `prices/v3` has no DRM filter, so every shop is fetched and the cheapest Steam-activated offer
     * (`bestSteamDeal`) is picked on the client.
     */
    private suspend fun fetchPriceStatesByUUID(
        uuidByAppID: Map<Int, String>,
        region: AppRegion,
    ): Map<Int, PriceLoadState> {
        if (uuidByAppID.isEmpty()) {
            return emptyMap()
        }

        // 1. 两个 appID 可能对应同一个 UUID：去重，每批最多 200 个
        // 1. Two appIDs can share a UUID: dedupe, at most 200 per batch
        val uuids = uuidByAppID.values.distinct()
        val batches = uuids.chunked(PRICES_BATCH_LIMIT)

        // 2. 一批一批请求
        // 2. Request batch by batch
        val games = mutableListOf<ItadPriceGame>()
        try {
            for (batch in batches) {
                val batchGames = itadApi.fetchPrices(uuids = batch, region = region)
                games.addAll(batchGames)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.wishlist.e(error) { "愿望单查价失败 / Fetching wishlist prices failed" }
            val failedStates = uuidByAppID.mapValues { _ -> PriceLoadState.Failed }
            return failedStates
        }

        // 3. 按 UUID 取每行的报价：响应是稀疏的，没有条目或没有 Steam 激活的报价都是没价格
        // 3. Find each row's offer by UUID: the response is sparse, so no entry or no Steam offer means no price
        val states = mutableMapOf<Int, PriceLoadState>()
        for ((appID, uuid) in uuidByAppID) {
            val game = games.game(uuid = uuid)
            val deal = game?.bestSteamDeal
            if (deal == null) {
                states[appID] = PriceLoadState.NoPrice
            } else {
                states[appID] = PriceLoadState.Loaded(deal = deal)
            }
        }
        return states
    }

    // MARK: - UUID lookup

    /**
     * 并行 lookup [appIDs]，按 appID 返回结果；没有要查的直接返回空。
     * Looks up [appIDs] in parallel and returns the results by appID; empty when there is nothing to look up.
     */
    private suspend fun lookUpUUIDs(appIDs: List<Int>): Map<Int, LookupResult> {
        if (appIDs.isEmpty()) {
            return emptyMap()
        }

        val results = coroutineScope {
            val lookups = mutableListOf<Deferred<LookupResult>>()
            for (appID in appIDs) {
                val lookup = async { lookUpUUID(appID = appID) }
                lookups.add(lookup)
            }
            lookups.awaitAll()
        }
        // awaitAll 按启动顺序返回，和 appIDs 一一对应。
        // awaitAll returns in launch order, matching appIDs one to one.
        val appIDResultPairs = appIDs.zip(results)
        val resultByAppID = appIDResultPairs.toMap()
        return resultByAppID
    }

    private suspend fun lookUpUUID(appID: Int): LookupResult {
        try {
            val lookup = itadApi.lookupGame(appID = appID)
            if (!lookup.found) {
                return LookupResult.NotFound
            }
            val game = lookup.game
            if (game == null) {
                return LookupResult.NotFound
            }
            return LookupResult.Found(uuid = game.id)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.wishlist.e(error) { "ITAD UUID 查询失败 / Looking up the ITAD UUID failed: $appID" }
            return LookupResult.Failed
        }
    }

    /**
     * 把 lookup 到的 UUID 回写数据库；写失败只记日志，这次查价照常用它。
     * Writes a looked-up UUID to the database; a failed write is only logged, and this fetch still uses the UUID.
     */
    private suspend fun saveItadID(appID: Int, itadID: String) {
        try {
            wishlistRepository.updateItadID(appID = appID, itadID = itadID)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.wishlist.e(error) { "回写 ITAD UUID 失败 / Writing back the ITAD UUID failed: $appID" }
        }
    }

    // MARK: - 发售状态 / Release status

    private suspend fun updateReleases(region: AppRegion, steamLanguage: String) {
        // 1. 读现在的收藏，挑出要核对的行
        // 1. Read the current wishlist and pick the rows to check
        val items = wishlistRepository.items.first()
        val now = clock.now()
        val itemsToCheck = items.filter { item -> needsReleaseCheck(item = item, now = now) }

        // 2. 向 Steam 核对并回写
        // 2. Check with Steam and write back
        checkReleases(items = itemsToCheck, region = region, steamLanguage = steamLanguage, bypassesCache = false)
    }

    /**
     * 一行要不要核对：已发售的不会再变回未发售；未发售或还不知道的，一天内核对过就跳过。
     * Whether a row needs a check: a released game never turns unreleased again; others are skipped when checked
     * within a day.
     */
    private fun needsReleaseCheck(item: WishlistItem, now: Instant): Boolean {
        if (item.isComingSoon == false) {
            return false
        }
        val checkedAt = item.releaseCheckedAt
        if (checkedAt == null) {
            return true
        }
        val elapsed = now - checkedAt
        return elapsed > RELEASE_CHECK_INTERVAL
    }

    /**
     * 并行向 Steam 核对 [items] 的发售状态，查到一个回写一个；核对期间 [_isCheckingReleases] 为 `true`。
     * Checks [items]' release status with Steam in parallel, writing each result back as it arrives;
     * [_isCheckingReleases] is `true` meanwhile.
     *
     * Steam `appdetails` 一次只能查一个游戏的发售状态（多个 appid 只支持 `filters=price_overview`）。被取消时不动
     * [_isCheckingReleases]：接着启动的任务会重新设置。
     * Steam's `appdetails` gives release status for one game per request (several appids only work with
     * `filters=price_overview`). A cancellation leaves [_isCheckingReleases] alone: the task that follows sets it
     * again.
     */
    private suspend fun checkReleases(
        items: List<WishlistItem>,
        region: AppRegion,
        steamLanguage: String,
        bypassesCache: Boolean,
    ) {
        if (items.isEmpty()) {
            _isCheckingReleases.value = false
            return
        }
        _isCheckingReleases.value = true

        coroutineScope {
            for (item in items) {
                launch {
                    checkRelease(
                        appID = item.appID,
                        region = region,
                        steamLanguage = steamLanguage,
                        bypassesCache = bypassesCache,
                    )
                }
            }
        }

        _isCheckingReleases.value = false
    }

    private suspend fun checkRelease(appID: Int, region: AppRegion, steamLanguage: String, bypassesCache: Boolean) {
        // 1. 向 Steam 查：失败只记日志、不回写，下次加载再核对
        // 1. Ask Steam: a failure is only logged and not written, so the next load checks again
        val details: SteamAppDetails?
        try {
            details = steamApi.fetchAppDetails(
                appID = appID,
                region = region,
                steamLanguage = steamLanguage,
                bypassesCache = bypassesCache,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.wishlist.e(error) { "核对发售状态失败 / Checking the release status failed: $appID" }
            return
        }

        // 2. Steam 上已经没有了（下架）也不回写
        // 2. Write nothing when Steam no longer has it (delisted)
        if (details == null) {
            return
        }

        // 3. 回写发售状态和核对时间
        // 3. Write back the release status and the check time
        try {
            wishlistRepository.updateReleaseStatus(appID = appID, isComingSoon = details.isComingSoon)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.wishlist.e(error) { "回写发售状态失败 / Writing back the release status failed: $appID" }
        }
    }

    private companion object {

        /**
         * `prices/v3` 一次最多 200 个 UUID。
         * `prices/v3` takes at most 200 UUIDs per request.
         */
        const val PRICES_BATCH_LIMIT: Int = 200

        /**
         * 未发售的游戏多久向 Steam 核对一次。
         * How often an unreleased game is checked with Steam again.
         */
        val RELEASE_CHECK_INTERVAL: Duration = 24.hours
    }
}
