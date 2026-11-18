package io.github.johnwes7.steal.shared.models.itad

import kotlinx.serialization.Serializable

/**
 * `POST https://api.isthereanydeal.com/deals/v2` 的请求体。
 * The request body of `POST https://api.isthereanydeal.com/deals/v2`.
 *
 * iOS 用 `[String: Any]` 拼请求体，这里写成类型，字段名和值类型由编译器检查。
 * iOS builds the body as `[String: Any]`; here it is a type, so the compiler checks field names and value types.
 */
@Serializable
data class ItadDealsRequest(
    /**
     * 国家或地区代码，决定货币与可买的商店，取 `AppRegion.apiRegionCode`。
     * The country or region code, which decides the currency and shops; taken from `AppRegion.apiRegionCode`.
     */
    val country: String,

    /**
     * 从第几条开始，第一页是 `0`，下一页用上一页返回的 `nextOffset`。
     * Where the page starts: `0` for the first page, then the previous page's `nextOffset`.
     */
    val offset: Int,

    /**
     * 这一页要几条。
     * How many deals this page should hold.
     */
    val limit: Int,

    /**
     * 排序，如 `-hot`（按热度降序）、`-cut`（按折扣降序）。
     * The sort order, such as `-hot` (most popular first) or `-cut` (biggest discount first).
     */
    val sort: String,

    /**
     * 是否也返回没打折的游戏。
     * Whether games that are not on sale are returned too.
     */
    val nondeals: Boolean,

    /**
     * 筛选条件。
     * The filters.
     */
    val filter: ItadDealsFilter,
)

/**
 * deals 请求的筛选条件。
 * The filters of a deals request.
 */
@Serializable
data class ItadDealsFilter(
    /**
     * 条目类型，`1` 是游戏。
     * The item types; `1` is games.
     */
    val type: List<Int>,

    /**
     * 激活平台，`61` 是 Steam：不限购买商店，卖 Steam key 的第三方商店也会返回。
     * The activation platforms; `61` is Steam. Shops are not limited, so third-party shops selling Steam keys come back too.
     */
    val drm: List<Int>,

    /**
     * 游戏类型标签，如 `Action`；`null` 表示不按类型筛，请求里不会出现这个键。
     * Genre tags, such as `Action`; `null` means no genre filter, and the key is left out of the request.
     */
    val tags: List<String>? = null,
)
