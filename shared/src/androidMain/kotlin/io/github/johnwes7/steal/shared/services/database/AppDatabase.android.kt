package io.github.johnwes7.steal.shared.services.database

import android.content.Context
import androidx.room3.Room

/**
 * 数据库文件的名字。写进了用户手机，不要改名，否则老用户的愿望单会读不到。
 * The database file's name. It is on users' phones, so don't rename it, or existing users lose their wishlist.
 */
private const val DATABASE_FILE_NAME = "steal.db"

/**
 * 建 App 的本地数据库，文件放在系统给 App 的数据库目录里。
 * Creates the app's local database, in the database directory the system gives the app.
 *
 * 全 App 只能建一份（`SteaLApplication` 建好传给 `AppContainer`）：每份都很占资源，而且一份写入时只通知自己的 `Flow`，
 * 别的那份观察的列表不会更新。
 * The app must create only one (`SteaLApplication` creates it and passes it to `AppContainer`): each instance is
 * costly, and a write through one only notifies its own `Flow`s, so lists observed through another don't update.
 */
fun createAppDatabase(context: Context): AppDatabase {
    val applicationContext = context.applicationContext
    val databaseFile = applicationContext.getDatabasePath(DATABASE_FILE_NAME)
    val builder = Room.databaseBuilder<AppDatabase>(
        context = applicationContext,
        name = databaseFile.absolutePath,
    )
    return buildAppDatabase(builder)
}
