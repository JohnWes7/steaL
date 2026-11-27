package io.github.johnwes7.steal.shared.services

import io.github.johnwes7.steal.shared.models.app.WishlistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * 愿望单的唯一入口：ViewModel 只通过它读写，不直接拿 [WishlistDao]。本机数据库就是愿望单唯一的数据源。
 * The single entry point to the wishlist: ViewModels read and write through it, never through [WishlistDao] directly.
 * The on-device database is the wishlist's only source of truth.
 *
 * 写入失败（磁盘满、文件损坏）时抛 Room / SQLite 的异常，由调用方处理。
 * Writes throw Room / SQLite exceptions on failure (full disk, damaged file), which callers handle.
 *
 * @param clock 取「现在」的时钟；测试传固定时钟，好控制加入顺序。
 * The clock that supplies "now"; tests pass a fixed one to control the order items were added in.
 */
class WishlistRepository(
    private val wishlistDao: WishlistDao,
    private val clock: Clock = Clock.System,
) {

    /**
     * 整张愿望单，新加的在前；加入、移除后都会再发一次。
     * The whole wishlist, newest first, emitted again after every add or remove.
     */
    val items: Flow<List<WishlistItem>> = wishlistDao.observeItems()

    /**
     * 已收藏的 appID，给心形按钮判断实心还是空心。
     * The saved appIDs, which heart buttons use to decide between filled and outlined.
     */
    val savedAppIDs: Flow<Set<Int>> = items.map { currentItems -> appIDsOf(currentItems) }

    /**
     * 加入一个游戏，加入时间记为现在；同一个 appID 已经在愿望单里时整行覆盖，并排到最前面。
     * Adds a game with the current time as its added time; an appID already in the wishlist is replaced and moves to
     * the top.
     *
     * 给出了发售状态（[isComingSoon] 不是 `null`）就说明调用方刚向 Steam 拿到详情，核对时间也记为现在；
     * 不知道就留空，等愿望单页核对后回写。
     * A given release status ([isComingSoon] not `null`) means the caller just got Steam's details, so the check time
     * is also now; an unknown one stays empty until the wishlist page checks and writes it back.
     */
    suspend fun add(
        appID: Int,
        name: String,
        headerImageURL: String?,
        itadID: String?,
        isComingSoon: Boolean?,
        knownLow: WishlistItem.KnownPrice?,
    ) {
        val now = clock.now()
        val releaseCheckedAt = if (isComingSoon == null) {
            null
        } else {
            now
        }
        val item = WishlistItem(
            appID = appID,
            name = name,
            headerImageURL = headerImageURL,
            itadID = itadID,
            isComingSoon = isComingSoon,
            releaseCheckedAt = releaseCheckedAt,
            knownLow = knownLow,
            addedAt = now,
        )
        wishlistDao.upsert(item)
    }

    /**
     * 移出一个游戏；不在愿望单里时什么也不做。
     * Removes a game; does nothing when it isn't in the wishlist.
     */
    suspend fun remove(appID: Int) {
        wishlistDao.delete(appID)
    }

    /**
     * 从一份愿望单取出所有 appID。
     * Collects every appID from a wishlist.
     */
    private fun appIDsOf(items: List<WishlistItem>): Set<Int> {
        val appIDs = items.map { item -> item.appID }
        return appIDs.toSet()
    }
}
