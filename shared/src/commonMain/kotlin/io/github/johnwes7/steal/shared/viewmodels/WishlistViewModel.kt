package io.github.johnwes7.steal.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.johnwes7.steal.shared.models.app.WishlistItem
import io.github.johnwes7.steal.shared.services.WishlistRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Wishlist tab 根页面的状态：本机愿望单，新加的在前。
 * The state of the Wishlist tab's root screen: the on-device wishlist, newest first.
 */
class WishlistViewModel(wishlistRepository: WishlistRepository) : ViewModel() {

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
