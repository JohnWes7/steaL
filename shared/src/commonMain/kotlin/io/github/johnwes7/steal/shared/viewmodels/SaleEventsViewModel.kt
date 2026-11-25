package io.github.johnwes7.steal.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.johnwes7.steal.shared.config.AppLog
import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.shared.models.saleevents.SaleEventList
import io.github.johnwes7.steal.shared.services.SaleEventsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Sales 页的状态：加载所有商店的促销活动。
 * The Sales page's state: loads every store's sale events.
 *
 * 只存接口给的列表；分段由页面用当前时间调 [SaleEventList.sections] 算，所以不在这里存「现在」。
 * It keeps only the list from the API; the page computes the sections with [SaleEventList.sections] at the current
 * time, so "now" is not stored here.
 */
class SaleEventsViewModel(
    private val saleEventsApi: SaleEventsApi,
) : ViewModel() {

    // MARK: - 状态类型 / State types

    /**
     * 活动列表的加载状态。
     * The loading state of the event list.
     */
    sealed interface SaleEventsLoadState {

        /**
         * 第一次加载或重试中。
         * The first load or a retry.
         */
        data object Loading : SaleEventsLoadState

        /**
         * 请求成功，[eventList] 的活动可能为空（没有活动不是错误）。
         * The request succeeded; [eventList] may have no events (no events is not an error).
         */
        data class Loaded(val eventList: SaleEventList) : SaleEventsLoadState

        /**
         * 请求失败，原因在 [error]。
         * The request failed; [error] says why.
         */
        data class Failed(val error: LoadError) : SaleEventsLoadState
    }

    // MARK: - 对外状态 / Exposed state

    private val _state = MutableStateFlow<SaleEventsLoadState>(SaleEventsLoadState.Loading)

    /**
     * 活动列表当前的状态，界面按它显示。
     * The event list's current state, which the UI renders.
     */
    val state: StateFlow<SaleEventsLoadState> = _state.asStateFlow()

    // MARK: - 内部记录 / Internal bookkeeping

    /**
     * 是否已经开始过第一次加载，保证 [loadIfNeeded] 只请求一次。
     * Whether the first load has started, so [loadIfNeeded] requests only once.
     */
    private var hasStartedLoading: Boolean = false

    private var loadJob: Job? = null

    // MARK: - 界面调用 / Called by the UI

    /**
     * 开始第一次加载，之后再调用什么也不做。
     * Starts the first load; later calls do nothing.
     *
     * 页面每次出现都会调用（切 tab 回来、转屏），所以要幂等；请求跑在 `viewModelScope`，切 tab 不会中断。
     * The page calls this every time it appears (back from another tab, after rotation), so it is idempotent;
     * the request runs in `viewModelScope` and survives tab switches.
     */
    fun loadIfNeeded() {
        if (hasStartedLoading) {
            return
        }
        hasStartedLoading = true
        loadEvents()
    }

    /**
     * 失败后的重试：切回 Loading 重新请求；一小时内有成功的响应时由 HTTP 缓存直接给。
     * Retries after a failure, showing Loading again; a successful response from within the hour comes straight
     * from the HTTP cache.
     */
    fun retry() {
        loadEvents()
    }

    // MARK: - 请求 / Request
    // load… 管协程、马上返回；update… 在协程里取数据、写状态。
    // load… manages the coroutine and returns at once; update… fetches inside it and writes the state.

    private fun loadEvents() {
        // 1. 取消旧请求：旧结果不会盖掉新结果
        // 1. Cancel the old request: a stale result never overwrites a fresh one
        loadJob?.cancel()

        // 2. 启动新请求，记下来供下次取消
        // 2. Launch the new one and keep it for the next cancel
        val job = viewModelScope.launch {
            updateEvents()
        }
        loadJob = job
    }

    private suspend fun updateEvents() {
        // 1. 切到 Loading
        // 1. Show Loading
        _state.value = SaleEventsLoadState.Loading

        // 2. 请求活动并发布
        // 2. Fetch and publish the events
        try {
            val eventList = saleEventsApi.fetchSaleEvents()
            _state.value = SaleEventsLoadState.Loaded(eventList = eventList)
        } catch (cancellation: CancellationException) {
            // 取消不是失败，要原样抛出，协程才会结束。
            // A cancellation is no failure; rethrow it so the coroutine ends.
            throw cancellation
        } catch (error: Exception) {
            AppLog.saleEvents.e(error) { "活动加载失败 / Loading sale events failed" }
            _state.value = SaleEventsLoadState.Failed(error = LoadError.from(error))
        }
    }
}
