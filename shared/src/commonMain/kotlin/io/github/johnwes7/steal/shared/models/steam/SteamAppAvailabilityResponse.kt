package io.github.johnwes7.steal.shared.models.steam

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * `appdetails?appids=a,b,c&filters=price_overview` 的响应：每个 appID 只看「存不存在」和「有没有价格」。
 * The response of a batched `appdetails` call filtered to `price_overview`: each appID only tells whether it
 * exists and whether it has a price.
 *
 * 用来在 ITAD 给的多个候选 appID 里挑对的那个。响应顶层是字典（appID 字符串 → 结果），
 * 和 `ItadShopIDsResponse` 一样由 Service 先解码成字典再包进来，所以这个类不是 `@Serializable`。
 * Used to pick the right one among ITAD's candidate appIDs. The response root is a dictionary (appID string to
 * result); as with `ItadShopIDsResponse`, the service decodes the dictionary and wraps it, so this class is not
 * `@Serializable`.
 */
data class SteamAppAvailabilityResponse(
    /**
     * appID 字符串 → 查询结果。
     * AppID string to its result.
     */
    val entries: Map<String, SteamAppAvailabilityEntry>,
) {

    /**
     * [appID] 在 Steam 上是否存在。
     * Whether [appID] exists on Steam.
     */
    fun isAvailable(appID: Int): Boolean {
        val entry = entries[appID.toString()]
        return entry?.success == true
    }

    /**
     * [appID] 是否存在且有价格。
     * Whether [appID] exists and has a price.
     */
    fun hasPrice(appID: Int): Boolean {
        val entry = entries[appID.toString()] ?: return false
        return entry.success && entry.hasPrice
    }
}

/**
 * 一个 appID 的查询结果。
 * One appID's result.
 *
 * `data` 有三种形状：有价格时是带 `price_overview` 的对象，免费游戏和 demo 是空数组 `[]`，不存在时没有这个字段。
 * 所以先按任意 JSON 收下，判断有没有价格时再看它是不是对象。
 * `data` comes in three shapes: an object with `price_overview` when there is a price, an empty array `[]` for
 * free games and demos, and absent when the app does not exist. So it is taken as any JSON first, and checked
 * for an object only when asking about the price.
 */
@Serializable
data class SteamAppAvailabilityEntry(
    /**
     * `false` 表示已下架、废弃或在这个区域锁区。
     * `false` means delisted, retired or region-locked.
     */
    val success: Boolean,

    /**
     * 原样收下的 `data` 字段，形状不定，只在本类里读。
     * The `data` field as received, of no fixed shape, read only inside this class.
     */
    private val data: JsonElement? = null,
) {

    /**
     * 是否有 `price_overview`，即能买（限时免费也有）；demo、没有价格的旧页、永久免费都没有。
     * Whether there is a `price_overview`, meaning it can be bought (limited-time free counts); demos, old
     * price-less pages and free-to-play games have none.
     */
    val hasPrice: Boolean
        get() {
            val dataObject = data as? JsonObject
            if (dataObject == null) {
                return false
            }
            return dataObject.containsKey("price_overview")
        }
}
