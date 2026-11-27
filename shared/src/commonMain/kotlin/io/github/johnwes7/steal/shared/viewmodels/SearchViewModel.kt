package io.github.johnwes7.steal.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.johnwes7.steal.shared.config.AppLog
import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.models.steam.SteamSearchItem
import io.github.johnwes7.steal.shared.models.steam.SteamSearchResponse
import io.github.johnwes7.steal.shared.services.SettingsRepository
import io.github.johnwes7.steal.shared.services.SteamApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Search 页的状态：按页面交进来的搜索词搜 Steam 商店。
 * The Search page's state: searches the Steam store for the query the page hands in.
 *
 * 搜索词由页面经 [applyQuery] 交进来，停止输入 500ms 后才搜，清空时马上回到 [SearchState.Idle]；地区由 ViewModel
 * 自己订阅设置，语言由页面经 [loadIfNeeded] 交进来。三者有一个变了就搜一次，新的一次会取消还在途中的旧请求。
 * 搜下一个词时，正在显示的结果留着（标上 `isSearching`），直到新结果回来。
 * The page hands the query in through [applyQuery]; a search starts 500 ms after typing stops, and clearing the
 * query returns to [SearchState.Idle] at once. The view model subscribes to the region in the settings itself,
 * while the page hands the language in through [loadIfNeeded]. A change to any of the three searches again, and the
 * new search cancels an old request still in flight. While the next query is being searched, the results on screen
 * stay (marked with `isSearching`) until the new ones arrive.
 */
class SearchViewModel(
    private val steamApi: SteamApi,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    // MARK: - 状态类型 / State types

    /**
     * 搜索结果区的状态。
     * The state of the search results.
     */
    sealed interface SearchState {

        /**
         * 搜索词是空的，还没搜。
         * The query is empty, so nothing has been searched.
         */
        data object Idle : SearchState

        /**
         * 正在搜，而且屏幕上没有上一次的结果可以留着。
         * A search is running and there are no earlier results on screen to keep.
         */
        data object Loading : SearchState

        /**
         * 搜到了：[query] 是这批结果对应的搜索词（去掉了首尾空白），[items] 不为空；[isSearching] 为 `true` 时这批结果
         * 留在屏幕上，同时正在搜下一个词。
         * Results arrived: [query] is the query they belong to (trimmed), and [items] is not empty; when [isSearching]
         * is `true` these results stay on screen while the next query is being searched.
         */
        data class Loaded(
            val query: String,
            val items: List<SteamSearchItem>,
            val isSearching: Boolean = false,
        ) : SearchState

        /**
         * 请求成功，但 [query] 什么都没搜到；不是失败，换个词再搜。[isSearching] 同 [Loaded]。
         * The request worked, but [query] matched nothing; not a failure, so try another query. [isSearching] works
         * as in [Loaded].
         */
        data class NoResults(val query: String, val isSearching: Boolean = false) : SearchState

        /**
         * 请求失败，原因在 [error]；不给重试，改一下搜索词就会再搜。
         * The request failed; [error] says why. There is no retry: editing the query searches again.
         */
        data class Failed(val error: LoadError) : SearchState
    }

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)

    /**
     * 搜索结果区的状态。
     * The state of the search results.
     */
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    // MARK: - 内部记录 / Internal bookkeeping

    /**
     * 一次搜索用的搜索词（已去掉首尾空白）、地区和语言，三者一起比较是否变了。
     * The trimmed query, region and language of one search, compared together to tell whether any changed.
     */
    private data class SearchRequest(val query: String, val region: AppRegion, val steamLanguage: String)

    /**
     * 页面最近交进来的搜索词，原样存（去空白在订阅里做）。
     * The query the page last handed in, stored as is (trimming happens in the subscription).
     */
    private val requestedQuery = MutableStateFlow("")

    /**
     * 页面最近交进来的语言；`null` 表示还没交过。
     * The language the page last handed in; `null` until it has.
     */
    private val requestedLanguage = MutableStateFlow<String?>(null)

    /**
     * 是否已经开始订阅，保证只订阅一次。
     * Whether the subscription has started, so it starts only once.
     */
    private var isObservingRequests: Boolean = false

    /**
     * 最近一次搜成功的请求；同一个请求再来时不重发。失败、清空时设回 `null`，所以失败后再输入同一个词会重搜。
     * The latest request that succeeded; the same request coming again is not sent twice. It goes back to `null`
     * after a failure or when the query is cleared, so typing the same query after a failure searches again.
     */
    private var loadedRequest: SearchRequest? = null

    // MARK: - 界面调用 / Called by the UI

    /**
     * 开始订阅，或交进新的语言：第一次调用时订阅搜索词、地区和语言；之后语言变了就重搜，没变什么也不做。
     * Starts the subscription or hands in a new language: the first call subscribes to the query, the region and
     * the language; after that a changed language searches again and an unchanged one does nothing.
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

    /**
     * 交进输入框里现在的文字 [query]；页面每次文字变化都调用。
     * Hands in the input field's current text [query]; the page calls this on every text change.
     */
    fun applyQuery(query: String) {
        requestedQuery.value = query
    }

    // MARK: - 订阅搜索词、地区和语言 / Query, region and language subscription

    /**
     * 一直收集「搜索词 + 地区 + 语言」，每来一组就搜一次，新的一组来了取消旧的；`viewModelScope` 取消时才结束。
     * Collects the query, region and language until `viewModelScope` is cancelled, searching once for each set and
     * cancelling the old search when a new set arrives.
     */
    @OptIn(FlowPreview::class)
    private suspend fun observeRequests() {
        // 1. 搜索词：去掉首尾空白，只多打了空格不算变；停止输入 500ms 后才发，清空马上发
        // 1. Query: trimmed, so extra spaces are no change; sent 500 ms after typing stops, at once when cleared
        val trimmedQueries = requestedQuery.map { query -> query.trim() }
        val changedQueries = trimmedQueries.distinctUntilChanged()
        val debouncedQueries = changedQueries.debounce { query -> debounceTimeout(query = query) }

        // 2. 地区：改深浅这类别的设置时整份设置会再发一次，地区没变就跳过
        // 2. Region: changing another setting (such as the theme) emits the whole settings again; skip an unchanged
        //    region
        val regions = settingsRepository.settings.map { settings -> settings.region }
        val changedRegions = regions.distinctUntilChanged()

        // 3. 语言：等页面交进来
        // 3. Language: wait for the page to hand it in
        val languages = requestedLanguage.filterNotNull()

        // 4. 合成一个请求，新的来了取消旧的
        // 4. Combine into one request and cancel the old search when a new one arrives
        val requests = combine(debouncedQueries, changedRegions, languages) { query, region, steamLanguage ->
            SearchRequest(query = query, region = region, steamLanguage = steamLanguage)
        }
        requests.collectLatest { request -> updateResults(request = request) }
    }

    /**
     * 搜索词 [query] 要等多久才发：空的马上发（马上回到 Idle），其余等 500ms。
     * How long [query] waits before it is sent: at once when empty (back to Idle at once), 500 ms otherwise.
     */
    private fun debounceTimeout(query: String): Duration {
        if (query.isEmpty()) {
            return Duration.ZERO
        }
        return SEARCH_DEBOUNCE
    }

    // MARK: - 搜索 / Search

    private suspend fun updateResults(request: SearchRequest) {
        // 1. 空的搜索词：回到 Idle，不发请求
        // 1. Empty query: back to Idle without a request
        if (request.query.isEmpty()) {
            loadedRequest = null
            _searchState.value = SearchState.Idle
            return
        }

        // 2. 和上次搜成功的一样（删一个字又打回来）：不重发；被取消的那次可能已经标了「正在搜」，要清掉
        // 2. Same as the last successful search (a letter deleted and typed back): don't send it again; the cancelled
        //    search may have marked "searching", so clear it
        if (request == loadedRequest) {
            applySearchingFlag(isSearching = false)
            return
        }

        // 3. 屏幕上有上一次的结果就留着、标上「正在搜」，没有才显示加载中
        // 3. Keep earlier results on screen marked as "searching"; show Loading only when there are none
        val isShowingResults = applySearchingFlag(isSearching = true)
        if (!isShowingResults) {
            _searchState.value = SearchState.Loading
        }

        // 4. 搜 Steam 商店
        // 4. Search the Steam store
        val response: SteamSearchResponse
        try {
            response = steamApi.fetchSearchResults(
                query = request.query,
                region = request.region,
                steamLanguage = request.steamLanguage,
            )
        } catch (cancellation: CancellationException) {
            // 新的搜索取消了这一次，不是失败，要原样抛出。
            // A newer search cancelled this one; no failure, so rethrow it.
            throw cancellation
        } catch (error: Exception) {
            AppLog.search.e(error) { "搜索失败 / Search failed: ${request.query}" }
            loadedRequest = null
            _searchState.value = SearchState.Failed(error = LoadError.from(error))
            return
        }

        // 5. 写结果：没搜到不是失败
        // 5. Write the results: no match is not a failure
        loadedRequest = request
        if (response.items.isEmpty()) {
            _searchState.value = SearchState.NoResults(query = request.query)
            return
        }
        _searchState.value = SearchState.Loaded(query = request.query, items = response.items)
    }

    /**
     * 屏幕上是结果（`Loaded` / `NoResults`）时把它的 `isSearching` 设成 [isSearching]，返回 `true`；别的状态不动，返回
     * `false`。
     * Sets `isSearching` to [isSearching] when results are on screen (`Loaded` / `NoResults`) and returns `true`;
     * leaves other states alone and returns `false`.
     */
    private fun applySearchingFlag(isSearching: Boolean): Boolean {
        val currentState = _searchState.value
        if (currentState is SearchState.Loaded) {
            _searchState.value = currentState.copy(isSearching = isSearching)
            return true
        }
        if (currentState is SearchState.NoResults) {
            _searchState.value = currentState.copy(isSearching = isSearching)
            return true
        }
        return false
    }

    private companion object {

        /**
         * 停止输入多久后才搜。
         * How long after typing stops a search starts.
         */
        val SEARCH_DEBOUNCE: Duration = 500.milliseconds
    }
}
