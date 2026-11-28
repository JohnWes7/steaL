package io.github.johnwes7.steal.shared.services

import io.github.johnwes7.steal.shared.models.app.WishlistItem
import io.github.johnwes7.steal.shared.services.database.WishlistDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

/**
 * 测试用的 [WishlistDao]：愿望单放在内存里，不用 Room，两个平台都能跑。
 * A [WishlistDao] for tests: the wishlist lives in memory without Room, so it runs on both platforms.
 *
 * 交给真的 `WishlistRepository`，测 ViewModel 时 Repository 的逻辑照常跑。SQL 和类型转换由
 * `androidDeviceTest` 的 `WishlistRepositoryTest` 连真库测。
 * Hand it to a real `WishlistRepository`, so the repository's logic still runs in view model tests. The SQL and the
 * type converters are tested against a real database by `WishlistRepositoryTest` in `androidDeviceTest`.
 */
internal class InMemoryWishlistDao : WishlistDao {

    /**
     * 表里现在的行，顺序不管；读的时候按加入时间倒序排。
     * The rows currently in the table, in no particular order; sorted newest first when read.
     */
    val rows = MutableStateFlow<List<WishlistItem>>(emptyList())

    /**
     * 收到的写入次数（加入、删除、回写都算）。
     * The number of writes received (adds, removes and write-backs all count).
     */
    val writeCount = MutableStateFlow(0)

    /**
     * 为 `true` 时每次写入都抛异常，模拟磁盘出错。
     * When `true`, every write throws, simulating a disk error.
     */
    var failsWrites: Boolean = false

    /**
     * 卡住下一次写入的闸门；`null` 是马上写。
     * The gate holding the next write; `null` writes at once.
     */
    var writeGate: CompletableDeferred<Unit>? = null

    override fun observeItems(): Flow<List<WishlistItem>> {
        val sortedRows = rows.map { currentRows -> newestFirst(currentRows) }
        return sortedRows
    }

    override suspend fun upsert(item: WishlistItem) {
        startWrite()
        rows.update { currentRows -> rowsReplacing(currentRows, item) }
    }

    override suspend fun delete(appID: Int) {
        startWrite()
        rows.update { currentRows -> rowsWithout(currentRows, appID) }
    }

    override suspend fun updateItadID(appID: Int, itadID: String) {
        startWrite()
        rows.update { currentRows ->
            rowsUpdating(currentRows, appID) { row -> row.copy(itadID = itadID) }
        }
    }

    override suspend fun updateReleaseStatus(appID: Int, isComingSoon: Boolean, checkedAt: Instant) {
        startWrite()
        rows.update { currentRows ->
            rowsUpdating(currentRows, appID) { row ->
                row.copy(isComingSoon = isComingSoon, releaseCheckedAt = checkedAt)
            }
        }
    }

    /**
     * 记一次写入，等闸门，按设置抛错。
     * Counts a write, waits at the gate and throws when set to fail.
     */
    private suspend fun startWrite() {
        writeCount.update { count -> count + 1 }
        writeGate?.await()
        if (failsWrites) {
            error("Simulated write failure")
        }
    }

    private fun newestFirst(rows: List<WishlistItem>): List<WishlistItem> {
        val sortedRows = rows.sortedByDescending { item -> item.addedAt }
        return sortedRows
    }

    private fun rowsReplacing(rows: List<WishlistItem>, item: WishlistItem): List<WishlistItem> {
        val otherRows = rowsWithout(rows, item.appID)
        return otherRows + item
    }

    /**
     * 把 [appID] 那一行换成 [change] 改过的样子；没有这一行就原样返回，同 SQL 的 `UPDATE`。
     * Replaces the [appID] row with [change] applied to it; returns the rows unchanged when it is missing, as an SQL
     * `UPDATE` would.
     */
    private fun rowsUpdating(
        rows: List<WishlistItem>,
        appID: Int,
        change: (WishlistItem) -> WishlistItem,
    ): List<WishlistItem> {
        val updatedRows = rows.map { row ->
            if (row.appID == appID) {
                change(row)
            } else {
                row
            }
        }
        return updatedRows
    }

    private fun rowsWithout(rows: List<WishlistItem>, appID: Int): List<WishlistItem> {
        val keptRows = rows.filter { row -> row.appID != appID }
        return keptRows
    }
}
