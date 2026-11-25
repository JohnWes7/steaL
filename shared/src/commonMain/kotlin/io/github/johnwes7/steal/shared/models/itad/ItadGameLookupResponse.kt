package io.github.johnwes7.steal.shared.models.itad

import kotlinx.serialization.Serializable

/**
 * `GET https://api.isthereanydeal.com/games/lookup/v1?appid=<appID>` 的响应：按 Steam appID 找 ITAD 游戏。
 * The response of `GET https://api.isthereanydeal.com/games/lookup/v1?appid=<appID>`: finds the ITAD game for a
 * Steam appID.
 *
 * `appid` 传裸数字，不加 `app/` 前缀。ITAD 没收录时只有 `found: false`，没有 [game]；这不是错误。
 * `appid` takes the bare number without the `app/` prefix. When ITAD does not list the game the body is just
 * `found: false` with no [game]; that is not an error.
 */
@Serializable
data class ItadGameLookupResponse(
    /**
     * 有没有找到对应的 ITAD 游戏。
     * Whether a matching ITAD game was found.
     */
    val found: Boolean,

    /**
     * 找到的游戏，没找到是 `null`。
     * The game found, or `null` when there is none.
     */
    val game: ItadLookupGame?,
)

/**
 * lookup 找到的游戏，只带标识；评分、标签等要另外查 `info/v2`。
 * The game a lookup found, identity only; reviews, tags and the like need a separate `info/v2` call.
 */
@Serializable
data class ItadLookupGame(
    /**
     * ITAD 游戏 UUID，`info/v2` 和 `prices/v3` 都要它。
     * The ITAD game UUID, which `info/v2` and `prices/v3` both need.
     */
    val id: String,

    /**
     * 可读的短标识，如 `elden-ring`。
     * A readable slug, such as `elden-ring`.
     */
    val slug: String?,

    /**
     * ITAD 上的标题。
     * The title on ITAD.
     */
    val title: String?,

    /**
     * 条目类型，如 `game`；新收录的条目和试玩版可能是 `null`。
     * The item type, such as `game`; may be `null` for newly tracked items and demos.
     */
    val type: String?,
)
