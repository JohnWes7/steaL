package io.github.johnwes7.steal.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.johnwes7.steal.shared.models.app.WishlistFilter
import io.github.johnwes7.steal.shared.models.app.WishlistItem
import io.github.johnwes7.steal.shared.models.itad.ItadPriceDeal
import io.github.johnwes7.steal.shared.services.ItadApi
import io.github.johnwes7.steal.shared.services.SettingsRepository
import io.github.johnwes7.steal.shared.services.SteamApi
import io.github.johnwes7.steal.shared.services.WishlistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Wishlist tab 根页面的状态：本机愿望单（新加的在前）、每行的价格、选中的筛选。
 * The state of the Wishlist tab's root screen: the on-device wishlist (newest first), each row's price and the
 * selected filter.
 *
 * 收藏本身只存在数据库里，这里只读；价格只存在内存里、不过期。
 * The saved games live only in the database and are only read here; prices live in memory and never expire.
 */
class WishlistViewModel(
    private val wishlistRepository: WishlistRepository,
    private val itadApi: ItadApi,
    private val steamApi: SteamApi,
    private val settingsRepository: SettingsRepository,
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

    // MARK: - 界面调用 / Called by the UI

    /**
     * 换筛选项。
     * Switches the filter.
     */
    fun selectFilter(filter: WishlistFilter) {
        _selectedFilter.value = filter
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
}
