package io.github.johnwes7.steal.shared.models.itad

import kotlinx.serialization.Serializable

// `GET https://api.isthereanydeal.com/giveaways/v1` 的响应顶层直接是数组，没有 `nextOffset` / `hasMore` 这种分页外层，
// 所以解码成 `List<ItadGiveaway>`。
// The response of `GET https://api.isthereanydeal.com/giveaways/v1` is a bare array with no paging wrapper such as
// `nextOffset` / `hasMore`, so it decodes as `List<ItadGiveaway>`.

// 列表项 / Giveaway item

/**
 * 一条限时免费领取。
 * One limited-time giveaway.
 */
@Serializable
data class ItadGiveaway(
    /**
     * ITAD 的 giveaway ID，不是游戏 UUID。
     * ITAD's giveaway ID, not a game UUID.
     */
    val id: Int,

    /**
     * 展示标题，如 `Knightica - FREE on Epic Games`。
     * The display title, such as `Knightica - FREE on Epic Games`.
     */
    val title: String,

    /**
     * 提供领取的商店；`null` 表示没有对应商店（如游戏内兑换码活动）。
     * The shop offering the giveaway; `null` when no shop is involved (such as in-game code promotions).
     */
    val shop: ItadShopRef?,

    /**
     * 领取链接，可能带 affiliate 参数，必须原样打开。
     * The claim link, possibly with affiliate parameters; it must be opened as-is.
     */
    val url: String,

    /**
     * ITAD 上这条 giveaway 的详情页。
     * This giveaway's page on ITAD.
     */
    val details: String,

    /**
     * 是否限制级内容。
     * Whether the giveaway is marked mature.
     */
    val isMature: Boolean,

    /**
     * 开始时间，ISO 8601 字符串。
     * When the giveaway started, as an ISO 8601 string.
     */
    val publish: String,

    /**
     * 领取截止时间，ISO 8601 字符串；`null` 表示不知道什么时候截止。
     * The claim deadline, as an ISO 8601 string; `null` when it is unknown.
     */
    val expiry: String?,

    /**
     * 运营备注，可能含 Markdown 与换行，常常没有。
     * An editorial note that may contain Markdown and line breaks; often absent.
     */
    val note: String?,

    /**
     * 这条 giveaway 包含的游戏，可能不止一个。
     * The games in this giveaway, possibly more than one.
     */
    val games: List<ItadGiveawayGame>,
)

// 游戏 / Game

/**
 * giveaway 里的一个游戏。
 * One game in a giveaway.
 */
@Serializable
data class ItadGiveawayGame(
    /**
     * ITAD 游戏 UUID。
     * The ITAD game UUID.
     */
    val id: String,

    /**
     * 可读的短标识，如 `knightica`。
     * A readable slug, such as `knightica`.
     */
    val slug: String,

    /**
     * 游戏标题。
     * The game title.
     */
    val title: String,

    /**
     * 条目类型，如 `game`、`dlc`。
     * The item type, such as `game` or `dlc`.
     */
    val type: String,

    /**
     * 是否限制级内容。
     * Whether the game is marked mature.
     */
    val mature: Boolean,

    /**
     * 图片地址。
     * The image URLs.
     */
    val assets: ItadAssets,

    /**
     * 是否无 DRM。
     * Whether the game is DRM-free.
     */
    val drmFree: Boolean,

    /**
     * 可以激活这个游戏的商店，可能是空列表。
     * The shops where the game can be activated, possibly empty.
     */
    val keys: List<ItadShopRef>,

    /**
     * 支持的操作系统。
     * The supported operating systems.
     */
    val platforms: List<ItadPlatformRef>,
)
