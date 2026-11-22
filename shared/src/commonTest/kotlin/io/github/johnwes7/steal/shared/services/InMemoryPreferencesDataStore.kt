package io.github.johnwes7.steal.shared.services

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 测试用的 DataStore：值放在内存里，不碰文件，两个平台都能跑。
 * A DataStore for tests: values live in memory and no files are touched, so it runs on both platforms.
 *
 * [failsReads] / [failsWrites] 让读或写抛 `IOException`，模拟磁盘出错。
 * [failsReads] / [failsWrites] make reads or writes throw `IOException`, simulating disk errors.
 */
internal class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
    private val failsReads: Boolean = false,
) : DataStore<Preferences> {

    private val current = MutableStateFlow(initial)

    private val writeLock = Mutex()

    /**
     * 为 `true` 时每次写入都抛 `IOException`。
     * When `true`, every write throws `IOException`.
     */
    var failsWrites: Boolean = false

    override val data: Flow<Preferences> = if (failsReads) {
        flow { throw IOException("Simulated read failure", null) }
    } else {
        current
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        // 写入一个接一个做，同真的 DataStore。
        // Writes run one at a time, as in the real DataStore.
        writeLock.withLock {
            if (failsWrites) {
                throw IOException("Simulated write failure", null)
            }
            val updated = transform(current.value)
            current.value = updated
            return updated
        }
    }
}
