package io.github.johnwes7.steal.shared.models.itad

/**
 * `POST .../lookup/shop/61/id/v1` 的响应：ITAD 游戏 UUID → 这个游戏在 Steam 上的商店 ID。
 * The response of `POST .../lookup/shop/61/id/v1`: ITAD game UUID to the game's shop IDs on Steam.
 *
 * 响应顶层是字典，值是 `null` 表示 ITAD 没收录这个 UUID；不在字典里表示根本没查。
 * 它本身不参与 JSON 解码：Service 先把响应解码成 `Map<String, List<String>?>` 再包进来，这样不用写自定义解码器。
 * The response root is a dictionary; a `null` value means ITAD does not track that UUID, and a missing key means
 * it was never asked. This class takes no part in JSON decoding: the service decodes the body into
 * `Map<String, List<String>?>` and wraps it, which avoids a custom serializer.
 */
data class ItadShopIDsResponse(
    /**
     * UUID → 商店 ID 列表，如 `["sub/432247", "app/1245620"]`。
     * UUID to its shop IDs, such as `["sub/432247", "app/1245620"]`.
     */
    val entries: Map<String, List<String>?>,
) {

    /**
     * 某个游戏的全部 Steam appID 候选，保持响应里的顺序；没收录、值是 `null` 或只有 `sub/` 都返回空列表。
     * Every Steam appID candidate for one game, in response order; an empty list when the game is untracked,
     * the value is `null`, or there are only `sub/` entries.
     *
     * 可能不止一个，第一个也不一定还存在（Monster Hunter: World 给了三个 `app/`，只有最后一个能用），
     * 所以全部交给调用方去 Steam 校验。`app/` 的位置也不固定，只能按前缀找。
     * There can be several and the first may be gone (Monster Hunter: World lists three `app/` entries and only the
     * last one works), so all of them go back to the caller to check with Steam. `app/` entries can sit anywhere,
     * so they are found by prefix.
     */
    fun appIDs(uuid: String): List<Int> {
        val shopIDs = shopIDs(uuid)
        // 获取以 app/ 开头
        val prefixAppID = shopIDs.filter { shopID -> shopID.startsWith(APP_PREFIX) }
        // 移除前缀并转换为 Int
        val appIDs = prefixAppID.mapNotNull { shopID -> shopID.removePrefix(APP_PREFIX).toIntOrNull() }
        return appIDs
    }

    /**
     * 某个游戏在 Steam 上的全部商店 ID，`app/` 与 `sub/` 都在；没有就是空列表。
     * Every Steam shop ID for one game, both `app/` and `sub/`; an empty list when there are none.
     */
    fun shopIDs(uuid: String): List<String> {
        val shopIDs = entries[uuid]
        return shopIDs ?: emptyList()
    }

    /**
     * 这次响应里有几个 UUID 被 ITAD 收录了（有至少一个商店 ID）。
     * How many UUIDs in this response ITAD tracks (with at least one shop ID).
     */
    val trackedCount: Int
        get() {
            val tracked = entries.values.count { shopIDs -> !shopIDs.isNullOrEmpty() }
            return tracked
        }

    companion object {

        /**
         * Steam 应用 ID 的前缀，如 `app/1245620`。
         * The prefix of a Steam application ID, such as `app/1245620`.
         */
        const val APP_PREFIX: String = "app/"

        /**
         * Steam 礼包（订阅）ID 的前缀，如 `sub/432247`；一个游戏通常有好几个，不能当 appID 用。
         * The prefix of a Steam package (subscription) ID, such as `sub/432247`; a game usually has several,
         * and they can never be used as an appID.
         */
        const val SUB_PREFIX: String = "sub/"
    }
}
