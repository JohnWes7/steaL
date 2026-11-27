package io.github.johnwes7.steal.shared.services

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.ColumnTypeConverter
import androidx.room3.ColumnTypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.johnwes7.steal.shared.models.app.WishlistItem
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import kotlin.time.Instant

/**
 * App 的本地数据库（Room）：目前只有愿望单一张表。
 * The app's local database (Room), holding only the wishlist table for now.
 *
 * 改了任何实体的表结构都要把 [version] 加一并写迁移，否则老用户升级后打不开数据库；导出的 schema 在 `shared/schemas/`，要提交。
 * Any change to an entity's table needs a [version] bump and a migration, or existing users can't open the database
 * after upgrading; the exported schema lives in `shared/schemas/` and must be committed.
 *
 * 各平台只负责给出文件路径和 `Room.databaseBuilder`，再交给 [buildAppDatabase] 统一配置。
 * Each platform only supplies the file path and `Room.databaseBuilder`, then hands it to [buildAppDatabase] for the shared
 * configuration.
 */
@Database(
    entities = [WishlistItem::class],
    version = 1,
)
@ConstructedBy(AppDatabaseConstructor::class)
@ColumnTypeConverters(AppDatabaseConverters::class)
abstract class AppDatabase : RoomDatabase()

/**
 * Room 在每个平台生成 `actual`，这里只声明。
 * Room generates the `actual` on each platform; this only declares it.
 */
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/**
 * 给平台建好的 builder 配上所有平台共用的设置，建出数据库。
 * Applies the settings every platform shares to a platform-made builder and builds the database.
 *
 * 用 [BundledSQLiteDriver]：App 自带同一版本的 SQLite，Android 和 iOS 行为一致，不受系统版本影响。
 * Uses [BundledSQLiteDriver]: the app ships one SQLite version, so Android and iOS behave the same regardless of the OS
 * version.
 */
fun buildAppDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    val driver = BundledSQLiteDriver()
    val configuredBuilder = builder.setDriver(driver)
    return configuredBuilder.build()
}

/**
 * Room 不会自己存的类型怎么转成列值。
 * How to turn types Room can't store by itself into column values.
 *
 * 转出来的值写进了用户手机上的数据库文件，换算方式不能改。
 * The converted values are written into the database file on users' phones, so the conversions must not change.
 */
class AppDatabaseConverters {

    /**
     * 时间存成 Unix 毫秒。
     * Stores a time as Unix epoch milliseconds.
     */
    @ColumnTypeConverter
    fun instantToEpochMilliseconds(instant: Instant): Long {
        return instant.toEpochMilliseconds()
    }

    /**
     * 从 Unix 毫秒读回时间。
     * Reads a time back from Unix epoch milliseconds.
     */
    @ColumnTypeConverter
    fun epochMillisecondsToInstant(epochMilliseconds: Long): Instant {
        return Instant.fromEpochMilliseconds(epochMilliseconds)
    }

    /**
     * 地区存成枚举名（如 `Australia`），和设置里存地区的方式一样；所以 [AppRegion] 的 case 不能改名或删除。
     * Stores a region by its enum name (such as `Australia`), as the settings do; so [AppRegion] cases must not be
     * renamed or removed.
     */
    @ColumnTypeConverter
    fun regionToName(region: AppRegion): String {
        return region.name
    }

    /**
     * 从枚举名读回地区。
     * Reads a region back from its enum name.
     */
    @ColumnTypeConverter
    fun nameToRegion(name: String): AppRegion {
        return AppRegion.valueOf(name)
    }
}
