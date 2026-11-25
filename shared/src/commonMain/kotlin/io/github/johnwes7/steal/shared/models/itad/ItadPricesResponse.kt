package io.github.johnwes7.steal.shared.models.itad

import kotlinx.serialization.Serializable

// MARK: - 按 UUID 取 / Looking up by UUID

// `POST https://api.isthereanydeal.com/games/prices/v3?country=<CC>&deals=false` 的响应顶层是 [ItadPriceGame] 的数组，
// Service 直接解成 `List<ItadPriceGame>`。数组是稀疏的：没有价格数据的游戏整条不返回，传 2 个 UUID 可能只回 1 条。
// The response root of `POST https://api.isthereanydeal.com/games/prices/v3?country=<CC>&deals=false` is an array of
// [ItadPriceGame], decoded by the service straight into `List<ItadPriceGame>`. The array is sparse: a game without
// price data is left out entirely, so asking for 2 UUIDs may return 1 entry.

/**
 * 按 [uuid] 取一个游戏的价格；没有这个游戏的价格数据时是 `null`，这不是错误。
 * The prices of the game with [uuid], or `null` when there is no price data for it; that is not an error.
 *
 * 数组是稀疏的，只能按 `id` 找，不能按下标对应请求里的顺序。
 * The array is sparse, so entries must be found by `id`, never matched to the request order by index.
 */
fun List<ItadPriceGame>.game(uuid: String): ItadPriceGame? {
    val matchingGame = firstOrNull { priceGame -> priceGame.id == uuid }
    return matchingGame
}

// MARK: - 单个游戏 / One game

/**
 * 一个游戏的价格总览：三档历史低价 + 每家商店当前的报价。
 * One game's price overview: three historical-low windows plus every shop's current offer.
 */
@Serializable
data class ItadPriceGame(
    /**
     * ITAD 游戏 UUID。
     * The ITAD game UUID.
     */
    val id: String,

    /**
     * 三档历史低价。
     * The three historical-low windows.
     */
    val historyLow: ItadPriceHistory,

    /**
     * 每家商店当前的报价，可能是空列表。
     * Every shop's current offer; may be empty.
     */
    val deals: List<ItadPriceDeal>,
) {

    /**
     * 全史最低价；没有价格历史时是 `null`。
     * The all-time low, or `null` without price history.
     */
    val allTimeLow: ItadPrice?
        get() = historyLow.all

    /**
     * 最便宜的报价；没有报价时是 `null`。
     * The cheapest offer, or `null` when there are none.
     */
    val bestDeal: ItadPriceDeal?
        get() {
            val cheapestDeal = deals.minByOrNull { deal -> deal.price.amountInt }
            return cheapestDeal
        }

    /**
     * 能在 Steam 激活的报价。
     * The offers whose keys activate on Steam.
     */
    val steamDeals: List<ItadPriceDeal>
        get() {
            val steamOnlyDeals = deals.filter { deal -> deal.isSteamActivation }
            return steamOnlyDeals
        }

    /**
     * 最便宜的 Steam 激活报价；没有时是 `null`。
     * The cheapest offer that activates on Steam, or `null` when there is none.
     */
    val bestSteamDeal: ItadPriceDeal?
        get() {
            val cheapestSteamDeal = steamDeals.minByOrNull { deal -> deal.price.amountInt }
            return cheapestSteamDeal
        }

    /**
     * 什么都没有：没有报价，也没有全史最低价。
     * Nothing usable: no offers and no all-time low.
     */
    val isEmpty: Boolean
        get() = deals.isEmpty() && allTimeLow == null
}

// MARK: - 历史低价 / Historical lows

/**
 * 三档时间窗口里的最低价，每档都可能没有。
 * The lowest price within three time windows, any of which may be missing.
 *
 * 和 `deals/v2` 里 [ItadDealInfo] 的扁平历史低价字段结构不同，不能共用。
 * Structured differently from the flat historical-low fields of `deals/v2`'s [ItadDealInfo], so they cannot be
 * shared.
 */
@Serializable
data class ItadPriceHistory(
    /**
     * 全史最低价。
     * The all-time low.
     */
    val all: ItadPrice?,

    /**
     * 近一年最低价。
     * The lowest price in the last year.
     */
    val y1: ItadPrice?,

    /**
     * 近三个月最低价。
     * The lowest price in the last three months.
     */
    val m3: ItadPrice?,
)

// MARK: - 单条报价 / One offer

/**
 * 一家商店当前的报价。
 * One shop's current offer.
 */
@Serializable
data class ItadPriceDeal(
    /**
     * 购买的商店。
     * The shop the key is bought from.
     */
    val shop: ItadShopRef,

    /**
     * 现价。
     * The current price.
     */
    val price: ItadPrice,

    /**
     * 原价。
     * The regular price.
     */
    val regular: ItadPrice,

    /**
     * 折扣百分比，`12` 表示 -12%。
     * The discount in percent; `12` means -12%.
     */
    val cut: Int,

    /**
     * 优惠码，一般是 `null`。
     * A voucher code, usually `null`.
     */
    val voucher: String?,

    /**
     * 这家商店的历史最低价。
     * This shop's lowest price ever.
     */
    val storeLow: ItadPrice?,

    /**
     * ITAD 的价格标记：`H` 历史最低、`N` 新的历史最低、`S` 商店最低，没有就是 `null`。
     * ITAD's price flag: `H` historical low, `N` new historical low, `S` store low, or `null`.
     *
     * 存成字符串：ITAD 以后加了新标记也不会让整条价格解码失败。
     * Kept as a string, so a flag ITAD adds later cannot fail decoding the whole price entry.
     */
    val flag: String?,

    /**
     * 激活平台（不是购买商店），`61` 是 Steam。
     * The activation platforms (not the shop); `61` is Steam.
     */
    val drm: List<ItadPlatformRef>,

    /**
     * 支持的系统。
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
     * When the offer ends, as an ISO 8601 string; `null` when the end is unknown.
     */
    val expiry: String?,

    /**
     * ITAD 的跳转链接，带 affiliate 参数，要原样打开。
     * ITAD's redirect link, carrying affiliate parameters; open it as is.
     */
    val url: String,
) {

    /**
     * 能不能在 Steam 激活：在 Steam 买的，或 DRM 里明确有 Steam。
     * Whether the key activates on Steam: bought from Steam, or Steam listed explicitly as the DRM.
     */
    val isSteamActivation: Boolean
        get() {
            val isBoughtOnSteam = shop.id == ItadShopRef.STEAM_ID
            if (isBoughtOnSteam) {
                return true
            }
            val steamDrm = drm.firstOrNull { platform -> platform.id == ItadShopRef.STEAM_ID }
            return steamDrm != null
        }

    /**
     * 是不是全史最低价：ITAD 标了 `H` 或 `N`。
     * Whether it is an all-time low: ITAD flagged it `H` or `N`.
     */
    val isHistoricalLow: Boolean
        get() = flag == "H" || flag == "N"
}
