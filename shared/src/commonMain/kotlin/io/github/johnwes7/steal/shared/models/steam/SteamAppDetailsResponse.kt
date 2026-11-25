package io.github.johnwes7.steal.shared.models.steam

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

// MARK: - 根对象 / Root

/**
 * `appdetails?appids=<一个 appID>&cc=…&l=…`（不带 `filters`）的响应：appID 字符串 → 查询结果。
 * The response of `appdetails?appids=<one appID>&cc=…&l=…` without `filters`: appID string to its result.
 *
 * 响应顶层是字典，和 [SteamAppAvailabilityResponse] 一样由 Service 先解码成字典再包进来，所以这个类不是 `@Serializable`。
 * The response root is a dictionary; as with [SteamAppAvailabilityResponse], the service decodes the dictionary and
 * wraps it, so this class is not `@Serializable`.
 */
data class SteamAppDetailsResponse(
    /**
     * appID 字符串 → 查询结果。
     * AppID string to its result.
     */
    val entries: Map<String, SteamAppDetailsEntry>,
) {

    /**
     * [appID] 的详情；Steam 上没有这个 app（下架、锁区、不存在）时是 `null`。
     * The details of [appID], or `null` when Steam has no such app (delisted, region-locked or unknown).
     */
    fun details(appID: Int): SteamAppDetails? {
        val entry = entries[appID.toString()]
        if (entry == null) {
            return null
        }
        if (!entry.success) {
            return null
        }
        return entry.data
    }
}

/**
 * 一个 appID 的查询结果。
 * One appID's result.
 */
@Serializable
data class SteamAppDetailsEntry(
    /**
     * `false` 表示 Steam 上没有这个 app（下架、锁区、不存在），这时没有 [data]。
     * `false` means Steam has no such app (delisted, region-locked or unknown), and [data] is absent.
     */
    val success: Boolean,

    /**
     * 详情，只在 [success] 为 `true` 时有。
     * The details, present only when [success] is `true`.
     */
    val data: SteamAppDetails?,
)

// MARK: - 游戏详情 / Game details

/**
 * 一个 Steam 商店条目的详情：游戏、试玩版、DLC 等。
 * The details of one Steam store entry: a game, a demo, a DLC and so on.
 *
 * 字段几乎都可能缺：Steam 对不同条目返回的内容差别很大。
 * Almost every field may be missing, because Steam returns very different payloads for different entries.
 */
@Serializable
data class SteamAppDetails(

    // 标识 / Identity

    /**
     * Steam appID。
     * The Steam appID.
     */
    @SerialName("steam_appid")
    val steamAppID: Int?,

    /**
     * 名字，按请求的 `l=` 本地化。
     * The name, localised by the request's `l=`.
     */
    val name: String?,

    /**
     * 条目类型：`game`、`demo`、`dlc`、`music` 等。
     * The entry type: `game`, `demo`, `dlc`, `music` and so on.
     */
    val type: String?,

    /**
     * 年龄限制；接口里有数字 `0` 也有字符串 `"18"`，两种都收，认不出是 `null`。
     * The age gate; the API sends both the number `0` and the string `"18"`, both are accepted, and anything else
     * is `null`.
     */
    @SerialName("required_age")
    @Serializable(with = FlexibleIntSerializer::class)
    val requiredAge: Int? = null,

    // 介绍 / Description

    /**
     * 简短介绍，纯文本。
     * The short description, plain text.
     */
    @SerialName("short_description")
    val shortDescription: String?,

    /**
     * 详细介绍，带 HTML 标记。
     * The detailed description, with HTML markup.
     */
    @SerialName("detailed_description")
    val detailedDescription: String?,

    /**
     * 「关于这款游戏」一段，带 HTML 标记。
     * The "About this game" block, with HTML markup.
     */
    @SerialName("about_the_game")
    val aboutTheGame: String?,

    // 媒体 / Media

    /**
     * 商店主图，比例 460:215。
     * The store header image, in a 460:215 ratio.
     */
    @SerialName("header_image")
    val headerImage: String?,

    /**
     * 小横版宣传图（231 × 87）。
     * The small landscape capsule image (231 × 87).
     */
    @SerialName("capsule_image")
    val capsuleImage: String?,

    /**
     * 截图。
     * The screenshots.
     */
    val screenshots: List<SteamScreenshot>?,

    /**
     * 视频（预告片等）。
     * The movies (trailers and the like).
     */
    val movies: List<SteamMovie>?,

    // 发行 / Release

    /**
     * 发行日期和是否未发售。
     * The release date and whether it is still unreleased.
     */
    @SerialName("release_date")
    val releaseDate: SteamReleaseDate?,

    /**
     * Steam 标的「免费」：永久免费、试玩版都是 `true`。
     * Steam's "free" flag: `true` for free-to-play titles and demos alike.
     */
    @SerialName("is_free")
    val isFree: Boolean?,

    /**
     * 本体，只有试玩版这类条目有。
     * The full game, present only on entries such as demos.
     */
    @SerialName("fullgame")
    val fullGame: SteamFullGame?,

    /**
     * 这个游戏的试玩版，只有本体有；没有试玩版时是 `null`，不是空列表。
     * This game's demos, present only on the full game; `null` rather than an empty list when there are none.
     */
    val demos: List<SteamDemo>?,

    // 价格 / Price

    /**
     * Steam 自己的价格和折扣；未开预购、已下架、永久免费、试玩版都没有。
     * Steam's own price and discount; absent before pre-orders open, after delisting, for free-to-play titles and
     * for demos.
     */
    @SerialName("price_overview")
    val priceOverview: SteamPriceOverview?,

    // 分类与厂商 / Classification and credits

    /**
     * 玩法分类，如「单人」「Steam 成就」。
     * Feature categories, such as Single-player or Steam Achievements.
     */
    val categories: List<SteamDescriptor>?,

    /**
     * 类型，如「动作」「角色扮演」。
     * Genres, such as Action or RPG.
     */
    val genres: List<SteamDescriptor>?,

    /**
     * 支持的系统。
     * The supported operating systems.
     */
    val platforms: SteamPlatforms?,

    /**
     * 开发商。
     * The developers.
     */
    val developers: List<String>?,

    /**
     * 发行商。
     * The publishers.
     */
    val publishers: List<String>?,
) {

    /**
     * 是不是试玩版条目。
     * Whether the entry is a demo.
     */
    val isDemo: Boolean
        get() {
            val entryType = type
            if (entryType == null) {
                return false
            }
            return entryType.equals("demo", ignoreCase = true)
        }

    /**
     * 是不是还没发售。
     * Whether the game is not released yet.
     *
     * 只能看 `release_date.coming_soon`：开了预购的未发售游戏有价格，已发售的永久免费、下架游戏没有价格，不能用价格反推。
     * Only `release_date.coming_soon` tells: an unreleased game open for pre-order has a price, while released
     * free-to-play or delisted games have none, so the price cannot be used to infer it.
     */
    val isComingSoon: Boolean
        get() {
            val comingSoon = releaseDate?.comingSoon
            return comingSoon == true
        }

    /**
     * 这个条目按定价算是哪一类，决定详情页查不查比价、显不显示历史低价。
     * Which pricing kind the entry is, deciding whether the detail page asks for prices and shows the historical
     * lows.
     *
     * 几个条件并不互斥（试玩版和限时免费的 `is_free` 都是 `true`），按顺序判断：试玩版 → 未发售 → 永久免费 → 付费。
     * 永久免费是 `is_free` 且没有 `price_overview`；限时免费还有原价，算付费。
     * The flags overlap (demos and limited-time giveaways both have `is_free` set), so they are checked in order:
     * demo, unreleased, free forever, paid. Free forever means `is_free` with no `price_overview`; a limited-time
     * giveaway still has its regular price and counts as paid.
     */
    val gameKind: GameKind
        get() {
            if (isDemo) {
                return GameKind.Demo
            }
            if (isComingSoon) {
                return GameKind.Unreleased
            }
            val isFreeForever = isFree == true && priceOverview == null
            if (isFreeForever) {
                return GameKind.FreeForever
            }
            return GameKind.Paid
        }

    /**
     * 按定价分的条目类型。
     * The kind of entry by pricing.
     */
    enum class GameKind {
        /**
         * 试玩版，不单独卖。
         * A demo, never sold on its own.
         */
        Demo,

        /**
         * 永久免费，从来没有卖过。
         * Free forever; it has never been sold.
         */
        FreeForever,

        /**
         * 还没发售；开了预购的有价格。
         * Not released yet; one open for pre-order has a price.
         */
        Unreleased,

        /**
         * 正常售卖，包括限时免费（100% 折扣，原价还在）。
         * Sold normally, including limited-time giveaways (100% off, with the regular price still there).
         */
        Paid,
        ;

        /**
         * 要不要显示历史低价：永久免费的历史低价是它免费之前的价格，不显示。
         * Whether to show the historical lows; a free-forever title's lows date from before it went free, so they
         * are hidden.
         */
        val showsPriceHistory: Boolean
            get() = this != FreeForever
    }
}

// MARK: - 嵌套结构 / Nested types

/**
 * 一张截图。
 * One screenshot.
 */
@Serializable
data class SteamScreenshot(
    /**
     * Steam 内部的 ID，`0` 也是合法值。
     * Steam's internal ID; `0` is a valid value.
     */
    val id: Int?,

    /**
     * 缩略图地址。
     * The thumbnail URL.
     */
    @SerialName("path_thumbnail")
    val thumbnail: String?,

    /**
     * 原图地址。
     * The full-size image URL.
     */
    @SerialName("path_full")
    val full: String?,
)

/**
 * 一个视频。
 * One movie.
 */
@Serializable
data class SteamMovie(
    /**
     * Steam 内部的视频 ID。
     * Steam's internal movie ID.
     */
    val id: Int?,

    /**
     * 视频名字，按请求的 `l=` 本地化。
     * The movie name, localised by the request's `l=`.
     */
    val name: String?,

    /**
     * 是不是主预告片。
     * Whether this is the highlighted trailer.
     */
    val highlight: Boolean?,

    /**
     * 封面图地址。
     * The poster image URL.
     */
    val thumbnail: String?,

    /**
     * HLS 播放列表（`.m3u8`）地址。
     * The HLS playlist (`.m3u8`) URL.
     */
    @SerialName("hls_h264")
    val hlsH264: String?,

    /**
     * DASH 地址（AV1 编码）。
     * The DASH URL, AV1-encoded.
     */
    @SerialName("dash_av1")
    val dashAv1: String?,

    /**
     * DASH 地址（H.264 编码）。
     * The DASH URL, H.264-encoded.
     */
    @SerialName("dash_h264")
    val dashH264: String?,
) {

    /**
     * 能不能播：有 HLS 地址才算。
     * Whether it can be played: only a movie with an HLS URL counts.
     */
    val isPlayable: Boolean
        get() = hlsH264 != null
}

/**
 * 发行日期。
 * The release date.
 */
@Serializable
data class SteamReleaseDate(
    /**
     * 是否还没发售；判断未发售只能看它（见 [SteamAppDetails.isComingSoon]）。
     * Whether the game is unreleased; this is the only reliable signal (see [SteamAppDetails.isComingSoon]).
     */
    @SerialName("coming_soon")
    val comingSoon: Boolean,

    /**
     * 显示用的日期文字，按请求的 `l=` 本地化；可能是「2026」「Q1 2027」这类不标准的写法。
     * The display date text, localised by the request's `l=`; it may be non-standard, such as "2026" or "Q1 2027".
     */
    val date: String?,
)

/**
 * Steam 自己的价格和折扣。
 * Steam's own price and discount.
 */
@Serializable
data class SteamPriceOverview(
    /**
     * ISO 4217 货币代码，如 `AUD`。
     * The ISO 4217 currency code, such as `AUD`.
     */
    val currency: String,

    /**
     * 原价，以最小货币单位表示（`8995` 表示 89.95）。
     * The regular price in minor units (`8995` for 89.95).
     */
    val initial: Int,

    /**
     * 现价，以最小货币单位表示；没打折时等于 [initial]。
     * The current price in minor units; equal to [initial] when there is no discount.
     */
    val final: Int,

    /**
     * 折扣百分比，`0` 表示没打折。
     * The discount in percent; `0` when there is none.
     */
    @SerialName("discount_percent")
    val discountPercent: Int,

    /**
     * Steam 排好的原价文字；可能是空字符串。
     * Steam's formatted regular price; may be an empty string.
     */
    @SerialName("initial_formatted")
    val initialFormatted: String?,

    /**
     * Steam 排好的现价文字，如 `A$ 89.95`。
     * Steam's formatted current price, such as `A$ 89.95`.
     */
    @SerialName("final_formatted")
    val finalFormatted: String?,
)

/**
 * 试玩版条目指回的本体。
 * The full game a demo entry points back to.
 */
@Serializable
data class SteamFullGame(
    /**
     * 本体的 appID；接口给的是**字符串**，要转成数字才能用。
     * The full game's appID; the API sends it as a **string**, so it must be converted before use.
     */
    @SerialName("appid")
    val appID: String?,

    /**
     * 本体的名字。
     * The full game's name.
     */
    val name: String?,
)

/**
 * 本体列出的一个试玩版。
 * One demo listed by the full game.
 */
@Serializable
data class SteamDemo(
    /**
     * 试玩版自己的 appID（数字）。
     * The demo's own appID, as a number.
     */
    @SerialName("appid")
    val appID: Int?,

    /**
     * 说明；实测几乎都是空字符串，不能当名字用。
     * The description; almost always an empty string in practice, so it cannot serve as a name.
     */
    val description: String?,
)

/**
 * 一个有名字的分类项，`categories` 和 `genres` 共用。
 * A named descriptor, shared by `categories` and `genres`.
 */
@Serializable
data class SteamDescriptor(
    /**
     * 分类 ID；`genres` 给的是字符串 `"1"`、`categories` 给的是数字 `2`，两种都收。
     * The descriptor ID; `genres` sends the string `"1"` and `categories` the number `2`, and both are accepted.
     */
    @Serializable(with = FlexibleIntSerializer::class)
    val id: Int? = null,

    /**
     * 显示名字，按请求的 `l=` 本地化。
     * The display name, localised by the request's `l=`.
     */
    val description: String?,
)

/**
 * 支持的系统。
 * The supported operating systems.
 */
@Serializable
data class SteamPlatforms(
    /**
     * 支持 Windows。
     * Windows is supported.
     */
    val windows: Boolean?,

    /**
     * 支持 macOS。
     * macOS is supported.
     */
    val mac: Boolean?,

    /**
     * 支持 Linux。
     * Linux is supported.
     */
    val linux: Boolean?,
)

// MARK: - 宽容数字解码 / Lenient number decoding

/**
 * 把数字或字符串形式的整数都解成 `Int`；都不是（或字符串不是数字）解成 `null`，不让整个条目解码失败。
 * Decodes an integer sent either as a number or as a string into an `Int`; anything else, including a
 * non-numeric string, becomes `null` instead of failing the whole entry.
 *
 * 只能用在 JSON 上。用它的属性要给默认值 `= null`：字段缺失时直接取默认值，不会调用它。
 * public 是因为序列化插件要求自定义序列化器至少和用它的类型一样可见。
 * JSON only. Properties using it need a `= null` default, so a missing field takes the default without calling it.
 * It is public because the serialization plugin wants a custom serializer at least as visible as its users.
 */
object FlexibleIntSerializer : KSerializer<Int?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.github.johnwes7.steal.shared.models.steam.FlexibleInt", PrimitiveKind.INT).nullable

    override fun deserialize(decoder: Decoder): Int? {
        // 先原样拿到 JSON 值，才分得清是数字还是字符串；两种的 content 都是数字的文字。
        // Take the raw JSON value first to tell numbers from strings; both carry the digits as their content.
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        val primitive = element as? JsonPrimitive
        if (primitive == null) {
            return null
        }
        val number = primitive.content.toIntOrNull()
        return number
    }

    override fun serialize(encoder: Encoder, value: Int?) {
        val jsonEncoder = encoder as JsonEncoder
        val element = JsonPrimitive(value)
        jsonEncoder.encodeJsonElement(element)
    }
}
