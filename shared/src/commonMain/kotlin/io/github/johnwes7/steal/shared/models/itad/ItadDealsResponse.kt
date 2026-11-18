package io.github.johnwes7.steal.shared.models.itad

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 根对象 / Root

/**
 * `POST https://api.isthereanydeal.com/deals/v2` 的响应体。
 * The response body of `POST https://api.isthereanydeal.com/deals/v2`.
 */
@Serializable
data class ItadDealsResponse(
    /**
     * 下一页请求要传的偏移量，以服务端返回值为准。
     * The offset to pass for the next page, as reported by the server.
     */
    val nextOffset: Int?,

    /**
     * 是否还有下一页。
     * Whether more pages are available.
     */
    val hasMore: Boolean,

    /**
     * 本页的优惠列表。
     * The deals on this page.
     */
    val list: List<ItadDeal>,
)

// 列表项 / Deal item

/**
 * 一个游戏和它当前的最佳优惠。
 * One game with its current best deal.
 */
@Serializable
data class ItadDeal(
    /**
     * ITAD 游戏 UUID，用作列表主键。
     * The ITAD game UUID, used as the list identity.
     */
    val id: String,

    /**
     * 可读的短标识，如 `hard-reset-redux`。
     * A readable slug, such as `hard-reset-redux`.
     */
    val slug: String,

    /**
     * 游戏标题。
     * The game title.
     */
    val title: String,

    /**
     * 条目类型：`game`、`dlc`、`package` 等。
     * The item type: `game`, `dlc`, `package` and so on.
     */
    val type: String,

    /**
     * 是否限制级内容。
     * Whether the item is marked mature.
     */
    val mature: Boolean,

    /**
     * 图片地址。
     * The image URLs.
     */
    val assets: ItadAssets,

    /**
     * 当前的最佳优惠。
     * The current best deal.
     */
    val deal: ItadDealInfo,
)

// 图片 / Assets

/**
 * 各种封面与横幅的地址，每张都可能没有。
 * Cover and banner URLs, any of which may be missing.
 *
 * `banner300` / `banner400` 与 Steam header 同为 460:215，`banner600` 是 600 × 344，比例不同。
 * `banner300` and `banner400` share Steam's 460:215 header ratio; `banner600` is 600 × 344, a different ratio.
 */
@Serializable
data class ItadAssets(
    /**
     * 竖版封面。
     * Vertical box art.
     */
    val boxart: String?,

    /**
     * 145 宽的横幅。
     * The 145-wide banner.
     */
    val banner145: String?,

    /**
     * 300 宽的横幅。
     * The 300-wide banner.
     */
    val banner300: String?,

    /**
     * 400 宽的横幅。
     * The 400-wide banner.
     */
    val banner400: String?,

    /**
     * 600 宽的横幅。
     * The 600-wide banner.
     */
    val banner600: String?,
)

// 优惠详情 / Deal detail

/**
 * ITAD 为一个游戏选出的最佳优惠，每个游戏只有一条。
 * The best deal ITAD picked for a game; each game has exactly one.
 */
@Serializable
data class ItadDealInfo(
    /**
     * 卖这个 key 的商店，如 Steam、Fanatical。
     * The shop selling the key, such as Steam or Fanatical.
     */
    val shop: ItadShopRef,

    /**
     * 当前价格。
     * The current price.
     */
    val price: ItadPrice,

    /**
     * 原价。
     * The regular price.
     */
    val regular: ItadPrice,

    /**
     * 折扣百分比，`30` 表示 -30%。
     * The discount in percent; `30` means -30%.
     */
    val cut: Int,

    /**
     * 优惠码，通常没有。
     * The voucher code, usually absent.
     */
    val voucher: String?,

    /**
     * 这家商店的历史最低价。
     * This shop's lowest price ever.
     */
    val storeLow: ItadPrice?,

    /**
     * 所有商店的历史最低价。
     * The all-time low across shops.
     */
    val historyLow: ItadPrice?,

    /**
     * 近一年最低价。
     * The lowest price in the last year.
     */
    @SerialName("historyLow_1y")
    val historyLow1y: ItadPrice?,

    /**
     * 近三个月最低价。
     * The lowest price in the last three months.
     */
    @SerialName("historyLow_3m")
    val historyLow3m: ItadPrice?,

    /**
     * ITAD 的价格标记：`H` 历史最低、`N` 新的历史最低、`S` 商店最低，没有就是 `null`。
     * ITAD's price flag: `H` historical low, `N` new historical low, `S` store low, or `null`.
     */
    val flag: String?,

    /**
     * 激活平台（不是购买商店），`61` 是 Steam。
     * The activation platforms (not the shop); `61` is Steam.
     */
    val drm: List<ItadPlatformRef>,

    /**
     * 支持的操作系统。
     * The supported operating systems.
     */
    val platforms: List<ItadPlatformRef>,

    /**
     * 价格采集时间，ISO 8601 字符串。
     * When the price was seen, as an ISO 8601 string.
     */
    val timestamp: String,

    /**
     * 优惠结束时间，ISO 8601 字符串；`null` 表示不知道什么时候结束。
     * When the deal ends, as an ISO 8601 string; `null` when the end is unknown.
     */
    val expiry: String?,

    /**
     * ITAD 的跳转链接，带 affiliate 参数，必须原样打开。
     * ITAD's redirect link, with affiliate parameters; it must be opened as-is.
     */
    val url: String,
) {

    /**
     * 这个 key 是否在 Steam 激活。
     * Whether the key activates on Steam.
     */
    val isSteamActivation: Boolean
        get() {
            val steamDrm = drm.firstOrNull { platform -> platform.id == ItadShopRef.STEAM_ID }
            return steamDrm != null
        }

    /**
     * 当前价是否等于或低于历史最低价。
     * Whether the current price matches or beats the all-time low.
     *
     * 用最小货币单位的整数比较，避免小数误差。
     * Compares whole minor units to avoid floating-point error.
     */
    val isHistoricalLow: Boolean
        get() {
            val allTimeLow = historyLow ?: return false
            return price.amountInt <= allTimeLow.amountInt
        }
}

// 值类型 / Value types

/**
 * 商店（购买来源）。
 * A shop (where the key is bought).
 */
@Serializable
data class ItadShopRef(
    /**
     * 商店 ID，`61` 是 Steam。
     * The shop ID; `61` is Steam.
     */
    val id: Int,

    /**
     * 商店名称。
     * The shop name.
     */
    val name: String,
) {
    companion object {

        /**
         * Steam 在 ITAD 里的商店 ID，也是 DRM ID。
         * Steam's shop ID in ITAD, also its DRM ID.
         */
        const val STEAM_ID: Int = 61
    }
}

/**
 * 平台（DRM 或操作系统）。
 * A platform (a DRM or an operating system).
 */
@Serializable
data class ItadPlatformRef(
    /**
     * 平台 ID，如 `1` 是 Windows。
     * The platform ID, such as `1` for Windows.
     */
    val id: Int,

    /**
     * 平台名称。
     * The platform name.
     */
    val name: String,
)

/**
 * 一个金额。
 * A money amount.
 *
 * 显示用 [amount] 和 [currency]，计算和比较用 [amountInt]，避免小数误差。
 * Display uses [amount] and [currency]; calculations and comparisons use [amountInt] to avoid floating-point error.
 */
@Serializable
data class ItadPrice(
    /**
     * 以主币单位表示的金额，如 `11.37`。
     * The amount in major units, such as `11.37`.
     */
    val amount: Double,

    /**
     * 以最小货币单位表示的金额，如 `1137` 表示 11.37。
     * The amount in minor units, such as `1137` for 11.37.
     */
    val amountInt: Int,

    /**
     * ISO 4217 货币代码，如 `AUD`。
     * The ISO 4217 currency code, such as `AUD`.
     */
    val currency: String,
)
