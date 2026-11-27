package io.github.johnwes7.steal.shared.services.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import io.github.johnwes7.steal.shared.models.app.WishlistItem
import kotlinx.coroutines.flow.Flow

/**
 * 读写 `wishlist_items` 表；只给 `WishlistRepository` 用，界面和 ViewModel 不直接碰它。
 * Reads and writes the `wishlist_items` table; only `WishlistRepository` uses it, never the UI or ViewModels.
 */
@Dao
interface WishlistDao {

    /**
     * 整张愿望单，新加的在前；表一改就发出新列表。
     * The whole wishlist, newest first; emits a new list whenever the table changes.
     */
    @Query("SELECT * FROM wishlist_items ORDER BY added_at DESC")
    fun observeItems(): Flow<List<WishlistItem>>

    /**
     * 加入一个游戏；同一个 appID 已经在表里时整行覆盖。
     * Adds a game, replacing the whole row when the appID is already in the table.
     */
    @Upsert
    suspend fun upsert(item: WishlistItem)

    /**
     * 按 appID 移除一个游戏；不在表里时什么也不做。
     * Removes a game by appID; does nothing when it isn't in the table.
     */
    @Query("DELETE FROM wishlist_items WHERE app_id = :appID")
    suspend fun delete(appID: Int)
}
