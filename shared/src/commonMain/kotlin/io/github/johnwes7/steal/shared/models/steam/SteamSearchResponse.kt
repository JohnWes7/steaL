package io.github.johnwes7.steal.shared.models.steam

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - 根对象 / Root

/**
 * `storesearch?term=…&cc=…&l=…` 的响应：按名字搜 Steam 商店的结果。
 * The response of `storesearch?term=…&cc=…&l=…`: Steam store results for a name search.
 */
@Serializable
data class SteamSearchResponse(
    /**
     * 这次返回的条目数，最多 10。
     * The number of items returned, at most 10.
     */
    val total: Int,

    /**
     * 搜到的条目，按 Steam 的相关度排序；没搜到时是空列表。
     * The matching items in Steam's relevance order; an empty list when nothing matches.
     */
    val items: List<SteamSearchItem>,
) {

    companion object {

        /**
         * 不发请求时用的空结果。
         * The empty result used when no request is sent.
         */
        val Empty: SteamSearchResponse = SteamSearchResponse(total = 0, items = emptyList())
    }
}

// MARK: - 搜索条目 / Search item

/**
 * 搜索列表用的一个商店条目，只有名字、图和价格；完整资料还是要查 `appdetails`。
 * One store item for the search list, with only its name, image and price; full details still come from
 * `appdetails`.
 */
@Serializable
data class SteamSearchItem(
    /**
     * 条目类型，实测都是 `app`（原声、demo 也是）。
     * The item type; always `app` in practice (soundtracks and demos too).
     */
    val type: String,

    /**
     * 按 `l=` 语言本地化的名字。
     * The name, localised by the `l=` language.
     */
    val name: String,

    /**
     * Steam appID。
     * The Steam appID.
     */
    val id: Int,

    /**
     * 当前地区的价格；免费游戏、原声等没有价格的条目是 `null`。
     * The price in the current region; `null` for items without one, such as free games and soundtracks.
     */
    val price: SteamSearchPrice? = null,

    /**
     * 列表用的横图（231 × 87 的 capsule）。
     * The list's wide capsule image (231 × 87).
     */
    @SerialName("tiny_image")
    val tinyImage: String? = null,
)

/**
 * 搜索结果里的价格，金额以最小货币单位表示；没有 `appdetails` 那样的折扣百分比和排好的文字。
 * A search result's price in minor units; unlike `appdetails` it has no discount percent or formatted text.
 */
@Serializable
data class SteamSearchPrice(
    /**
     * ISO 4217 货币代码，如 `AUD`。
     * The ISO 4217 currency code, such as `AUD`.
     */
    val currency: String,

    /**
     * 原价（`1415` 表示 14.15）。
     * The regular price (`1415` for 14.15).
     */
    val initial: Int,

    /**
     * 现价；没打折时等于 [initial]。
     * The current price; equal to [initial] when there is no discount.
     */
    val final: Int,
)
