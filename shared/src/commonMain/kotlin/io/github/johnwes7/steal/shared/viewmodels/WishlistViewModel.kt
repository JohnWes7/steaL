package io.github.johnwes7.steal.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.johnwes7.steal.shared.models.app.WishlistItem
import io.github.johnwes7.steal.shared.models.itad.ItadPriceDeal
import io.github.johnwes7.steal.shared.services.WishlistRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Wishlist tab 根页面的状态：本机愿望单，新加的在前。
 * The state of the Wishlist tab's root screen: the on-device wishlist, newest first.
 */
class WishlistViewModel(wishlistRepository: WishlistRepository) : ViewModel() {

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

    // MARK: - 对外状态 / Exposed state

    /**
     * 整张愿望单；`null` 表示还没读到（只在页面第一次出现的一瞬间），空列表是真的没有收藏。
     * The whole wishlist; `null` until it is read (only for a moment as the screen first appears), while an empty
     * list means nothing is saved.
     */
    val items: StateFlow<List<WishlistItem>?> = wishlistRepository.items.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )
}
