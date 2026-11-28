package io.github.johnwes7.steal.shared.services

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.github.johnwes7.steal.shared.models.app.WishlistItem
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.services.database.AppDatabase
import io.github.johnwes7.steal.shared.services.database.buildAppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * [WishlistRepository] 连真的 Room 数据库（内存里、和 App 同一个 SQLite 驱动）的测试：排序、覆盖、移除、
 * 已收藏 appID，以及每个字段存进去再读出来不变。
 * Tests for [WishlistRepository] against a real Room database (in memory, with the app's SQLite driver): ordering,
 * replacing, removing, saved appIDs, and every field surviving a write and a read.
 *
 * Room 在 Android 上建库要 `Context`，所以放在设备测试里，在模拟器上跑。
 * Room needs a `Context` to build a database on Android, so these are device tests that run on an emulator.
 */
class WishlistRepositoryTest {

    /**
     * 测试自己控制的时钟：[now] 返回 [currentTime]，测试改它来决定加入的先后。
     * A clock the test controls: [now] returns [currentTime], which tests change to decide the order of adds.
     */
    private class TestClock(var currentTime: Instant) : Clock {
        override fun now(): Instant {
            return currentTime
        }
    }

    private lateinit var database: AppDatabase
    private lateinit var clock: TestClock
    private lateinit var repository: WishlistRepository

    @BeforeTest
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val builder = Room.inMemoryDatabaseBuilder<AppDatabase>(context = context)
        database = buildAppDatabase(builder)
        clock = TestClock(currentTime = START_TIME)
        repository = WishlistRepository(wishlistDao = database.wishlistDao(), clock = clock)
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    /**
     * 只填必需字段地加入一个游戏，其余留空。
     * Adds a game with only the required fields, leaving the rest empty.
     */
    private suspend fun addGame(appID: Int, name: String) {
        repository.add(
            appID = appID,
            name = name,
            headerImageURL = null,
            itadID = null,
            isComingSoon = null,
            knownLow = null,
        )
    }

    /**
     * 当前愿望单里各项的 appID，按列表顺序。
     * The appIDs in the current wishlist, in list order.
     */
    private suspend fun currentAppIDs(): List<Int> {
        val items = repository.items.first()
        val appIDs = items.map { item -> item.appID }
        return appIDs
    }

    /**
     * 新加的排在前面。
     * The newest game comes first.
     */
    @Test
    fun itemsAreNewestFirst() = runTest {
        addGame(appID = 1, name = "First")
        clock.currentTime = START_TIME + ONE_MINUTE
        addGame(appID = 2, name = "Second")
        clock.currentTime = START_TIME + ONE_MINUTE + ONE_MINUTE
        addGame(appID = 3, name = "Third")

        assertEquals(listOf(3, 2, 1), currentAppIDs())
    }

    /**
     * 同一个 appID 再加一次：整行覆盖成新内容并排到最前面，不会多出一行。
     * Adding the same appID again replaces the row with the new content and moves it to the top, without a second row.
     */
    @Test
    fun addingSameAppIDReplacesAndMovesToTop() = runTest {
        addGame(appID = 1, name = "Old name")
        clock.currentTime = START_TIME + ONE_MINUTE
        addGame(appID = 2, name = "Other")
        clock.currentTime = START_TIME + ONE_MINUTE + ONE_MINUTE
        addGame(appID = 1, name = "New name")

        val items = repository.items.first()
        val firstItem = items.first()

        assertEquals(listOf(1, 2), currentAppIDs())
        assertEquals("New name", firstItem.name)
        assertEquals(clock.currentTime, firstItem.addedAt)
    }

    /**
     * 移除只删那一个；移除不在愿望单里的 appID 什么也不做。
     * Removing deletes only that game; removing an appID that isn't saved does nothing.
     */
    @Test
    fun removeDeletesOnlyThatGame() = runTest {
        addGame(appID = 1, name = "Keep")
        clock.currentTime = START_TIME + ONE_MINUTE
        addGame(appID = 2, name = "Remove")

        repository.remove(appID = 2)
        repository.remove(appID = 999)

        assertEquals(listOf(1), currentAppIDs())
    }

    /**
     * 已收藏的 appID 跟着加入和移除变。
     * The saved appIDs follow adds and removes.
     */
    @Test
    fun savedAppIDsFollowAddsAndRemoves() = runTest {
        addGame(appID = 1, name = "One")
        addGame(appID = 2, name = "Two")
        val afterAdding = repository.savedAppIDs.first()

        repository.remove(appID = 1)
        val afterRemoving = repository.savedAppIDs.first()

        assertEquals(setOf(1, 2), afterAdding)
        assertEquals(setOf(2), afterRemoving)
    }

    /**
     * 所有字段存进去再读出来不变（时间、嵌入的最低价、地区枚举都经过类型转换）；给了发售状态时核对时间就是加入时间。
     * Every field survives a write and a read (the times, the embedded known low and the region enum all go through
     * conversions); a given release status sets the check time to the added time.
     */
    @Test
    fun allFieldsSurviveAWriteAndARead() = runTest {
        val knownLow = WishlistItem.KnownPrice(amount = 4799, region = AppRegion.Europe)
        repository.add(
            appID = 1245620,
            name = "ELDEN RING",
            headerImageURL = "https://example.com/header.jpg",
            itadID = "018d937f-21e1-728e-86d7-9acb3c59f2bb",
            isComingSoon = false,
            knownLow = knownLow,
        )

        val items = repository.items.first()
        val savedItem = items.single()
        val expectedItem = WishlistItem(
            appID = 1245620,
            name = "ELDEN RING",
            headerImageURL = "https://example.com/header.jpg",
            itadID = "018d937f-21e1-728e-86d7-9acb3c59f2bb",
            isComingSoon = false,
            releaseCheckedAt = START_TIME,
            knownLow = knownLow,
            addedAt = START_TIME,
        )

        assertEquals(expectedItem, savedItem)
    }

    /**
     * 不知道发售状态时核对时间留空；没有最低价时读回来也是 `null`（两列都是 NULL）。
     * An unknown release status leaves the check time empty; a missing known low reads back as `null` (both columns
     * NULL).
     */
    @Test
    fun unknownValuesStayEmpty() = runTest {
        addGame(appID = 1, name = "Unknown")

        val items = repository.items.first()
        val savedItem = items.single()

        assertNull(savedItem.isComingSoon)
        assertNull(savedItem.releaseCheckedAt)
        assertNull(savedItem.knownLow)
    }

    /**
     * 回写 ITAD UUID 只改那一列，加入时间和顺序不变。
     * Writing back the ITAD UUID changes only that column, leaving the added time and the order alone.
     */
    @Test
    fun updateItadIDChangesOnlyThatColumn() = runTest {
        addGame(appID = 1, name = "Older")
        clock.currentTime = START_TIME + ONE_MINUTE
        addGame(appID = 2, name = "Newer")
        val itemsBefore = repository.items.first()

        clock.currentTime = START_TIME + ONE_MINUTE + ONE_MINUTE
        repository.updateItadID(appID = 1, itadID = "018d937f-21e1-728e-86d7-9acb3c59f2bb")

        val itemsAfter = repository.items.first()
        val olderBefore = itemsBefore.last()
        val olderAfter = itemsAfter.last()
        val expectedOlder = olderBefore.copy(itadID = "018d937f-21e1-728e-86d7-9acb3c59f2bb")

        assertEquals(listOf(2, 1), currentAppIDs())
        assertEquals(expectedOlder, olderAfter)
    }

    /**
     * 回写发售状态时核对时间记为回写那一刻，不是加入时间；已经知道的状态也能改。
     * Writing back the release status records the moment of the write as the check time, not the added time; a known
     * status can be changed too.
     */
    @Test
    fun updateReleaseStatusRecordsCheckTime() = runTest {
        repository.add(
            appID = 1,
            name = "Upcoming",
            headerImageURL = null,
            itadID = null,
            isComingSoon = true,
            knownLow = null,
        )

        val checkTime = START_TIME + ONE_MINUTE
        clock.currentTime = checkTime
        repository.updateReleaseStatus(appID = 1, isComingSoon = false)

        val items = repository.items.first()
        val savedItem = items.single()

        assertEquals(false, savedItem.isComingSoon)
        assertEquals(checkTime, savedItem.releaseCheckedAt)
        assertEquals(START_TIME, savedItem.addedAt)
    }

    /**
     * 回写一个已经移出的游戏什么也不做，不会把它重新加回来。
     * Writing back a game that has been removed does nothing and does not add it back.
     */
    @Test
    fun updatesOnRemovedGameDoNothing() = runTest {
        addGame(appID = 1, name = "Removed")
        repository.remove(appID = 1)

        repository.updateItadID(appID = 1, itadID = "018d937f-21e1-728e-86d7-9acb3c59f2bb")
        repository.updateReleaseStatus(appID = 1, isComingSoon = false)

        assertEquals(emptyList(), currentAppIDs())
    }

    private companion object {
        /**
         * 测试时钟的起点；用整毫秒，存成 Unix 毫秒再读回来才会完全相等。
         * The test clock's starting point; whole milliseconds, so it reads back exactly after being stored as Unix
         * epoch milliseconds.
         */
        val START_TIME: Instant = Instant.fromEpochMilliseconds(1_764_230_000_000)

        /**
         * 两次加入之间隔的时间。
         * The gap between two adds.
         */
        val ONE_MINUTE: Duration = 1.minutes
    }
}
