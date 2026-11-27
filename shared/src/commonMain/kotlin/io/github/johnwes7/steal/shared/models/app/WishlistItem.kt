package io.github.johnwes7.steal.shared.models.app

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import io.github.johnwes7.steal.shared.models.itad.ItadPrice
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import kotlin.time.Instant

/**
 * 愿望单里的一个游戏：本地数据库 `wishlist_items` 表的一行，只存画列表行和做提醒要的快照字段。
 * One wishlisted game: a row of the local `wishlist_items` table, holding only the snapshot fields a list row and the
 * alerts need.
 *
 * 表名和列名写进了用户手机上的数据库文件，不要改名；属性可以改名，列名由 `@ColumnInfo` 固定。
 * The table and column names are written into the database file on users' phones and must not be renamed; properties
 * may be renamed, since `@ColumnInfo` pins the column names.
 */
@Entity(tableName = "wishlist_items")
data class WishlistItem(

    /**
     * Steam appID，也是主键：同一个游戏再加一次会覆盖原来那行。
     * The Steam appID, also the primary key: adding the same game again replaces its row.
     */
    @PrimaryKey
    @ColumnInfo(name = "app_id")
    val appID: Int,

    /**
     * 列表行标题。
     * The list row title.
     */
    @ColumnInfo(name = "name")
    val name: String,

    /**
     * 列表行封面（460:215 的横图），免得每行都去请求 `appdetails`。
     * The list row cover (a 460:215 header image), so rows don't each fetch `appdetails`.
     */
    @ColumnInfo(name = "header_image_url")
    val headerImageURL: String?,

    /**
     * ITAD 游戏 UUID，查价格时省一次 `lookup/v1`；`null` 表示收藏时还不知道。
     * The ITAD game UUID, saving a `lookup/v1` when fetching prices; `null` when it was unknown at save time.
     */
    @ColumnInfo(name = "itad_id")
    val itadID: String?,

    /**
     * 是否未发售（Steam `release_date.coming_soon`）；`null` 表示不知道。
     * Whether the game is unreleased (Steam `release_date.coming_soon`); `null` when unknown.
     */
    @ColumnInfo(name = "is_coming_soon")
    val isComingSoon: Boolean?,

    /**
     * 上次向 Steam 核对发售状态的时间；`null` 表示没核对过。
     * When the release status was last checked with Steam; `null` when never.
     */
    @ColumnInfo(name = "release_checked_at")
    val releaseCheckedAt: Instant?,

    /**
     * 用户已经知道的最低价；`null` 表示不知道，或回到原价后被清空。
     * The lowest price the user already knows about; `null` when none, or cleared after a return to full price.
     *
     * 降价提醒只在价格低于它时发，用户刚看过的折扣不会再推一次。
     * A price-drop alert fires only below it, so a sale the user just saw is not pushed again.
     */
    @Embedded(prefix = "known_low_")
    val knownLow: KnownPrice?,

    /**
     * 加入愿望单的时间，列表按它倒序排。
     * When the game was added, which the list sorts by, newest first.
     */
    @ColumnInfo(name = "added_at")
    val addedAt: Instant,
) {

    /**
     * 用户已经知道的一个价格，连同它所在的地区。
     * A price the user already knows about, together with its region.
     *
     * 不同地区货币不同，换了地区旧价格就不能拿来比，所以两个值放在一起，要么都有、要么都没有。
     * Regions use different currencies, so an old price can't be compared once the region changes; the two values
     * therefore travel together, either both present or both absent.
     */
    data class KnownPrice(

        /**
         * 以最小货币单位表示的价格，同 `ItadPrice.amountInt`。
         * The price in the smallest currency unit, as in `ItadPrice.amountInt`.
         */
        @ColumnInfo(name = "amount")
        val amount: Int,

        /**
         * 查到这个价格时的地区。
         * The region the price was fetched for.
         */
        @ColumnInfo(name = "region")
        val region: AppRegion,
    ) {

        companion object {

            /**
             * 收藏时用：在打折（[cut] 大于 0）就记下现价 [price]，没打折返回 `null`。
             * For saving: records the current [price] when on sale ([cut] above 0), or returns `null` when not.
             */
            fun fromSaleOrNull(price: ItadPrice, cut: Int, region: AppRegion): KnownPrice? {
                if (cut <= 0) {
                    return null
                }
                val knownPrice = KnownPrice(amount = price.amountInt, region = region)
                return knownPrice
            }
        }
    }
}
