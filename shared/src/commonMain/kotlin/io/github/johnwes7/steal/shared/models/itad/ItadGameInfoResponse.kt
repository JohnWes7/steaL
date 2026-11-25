package io.github.johnwes7.steal.shared.models.itad

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

// MARK: - 根对象 / Root

/**
 * `GET https://api.isthereanydeal.com/games/info/v2?id=<uuid>` 的响应：Steam 没有的评分汇总、标签、热度和厂商。
 * The response of `GET https://api.isthereanydeal.com/games/info/v2?id=<uuid>`: the review summary, tags, popularity
 * and credits Steam does not provide.
 *
 * 只收 ITAD UUID，传 Steam appID 会 400。未发售的游戏也能查，只是 [releaseDate] 是 `null`、[reviews] 是空列表。
 * It only takes an ITAD UUID; a Steam appID gets a 400. Unreleased games work too, with a `null` [releaseDate] and
 * an empty [reviews].
 */
@Serializable
data class ItadGameInfoResponse(

    // 标识 / Identity

    /**
     * ITAD 游戏 UUID。
     * The ITAD game UUID.
     */
    val id: String,

    /**
     * 可读的短标识，如 `elden-ring`。
     * A readable slug, such as `elden-ring`.
     */
    val slug: String,

    /**
     * ITAD 上的标题。
     * The title on ITAD.
     */
    val title: String,

    /**
     * 条目类型，如 `game`；新收录的条目和试玩版可能是 `null`。
     * The item type, such as `game`; may be `null` for newly tracked items and demos.
     */
    val type: String?,

    /**
     * 是否限制级内容。
     * Whether the item is marked mature.
     */
    val mature: Boolean,

    /**
     * ITAD 认为对应的 Steam appID；不一定对（被合并进同一个 UUID 的另一款游戏），只作参考。
     * The Steam appID ITAD considers matching; not always right (another game merged into the same UUID), so it
     * is only a hint.
     */
    @SerialName("appid")
    val appID: Int?,

    // 介绍 / Description

    /**
     * 标签，如 `Souls-like`。
     * Tags, such as `Souls-like`.
     */
    val tags: List<String>?,

    /**
     * 图片地址；没有图时是 `null`。
     * The image URLs, or `null` when there are none.
     */
    @Serializable(with = ItadInfoAssetsSerializer::class)
    val assets: ItadAssets? = null,

    /**
     * 是否抢先体验。
     * Whether the game is in early access.
     */
    val earlyAccess: Boolean?,

    /**
     * 是否有成就。
     * Whether the game has achievements.
     */
    val achievements: Boolean?,

    /**
     * 是否有集换式卡牌。
     * Whether the game has trading cards.
     */
    val tradingCards: Boolean?,

    // 发行 / Release

    /**
     * 发售日期，如 `2022-02-24`；未发售是 `null`。
     * The release date, such as `2022-02-24`, or `null` before release.
     */
    val releaseDate: String?,

    // 厂商 / Credits

    /**
     * 开发商。
     * The developers.
     */
    val developers: List<ItadCompanyRef>?,

    /**
     * 发行商。
     * The publishers.
     */
    val publishers: List<ItadCompanyRef>?,

    // 评分与热度 / Reviews and popularity

    /**
     * 各家评分：Steam、Metascore、Metacritic User Score、OpenCritic，有哪家给哪家。
     * Scores from Steam, Metascore, Metacritic User Score and OpenCritic, whichever exist.
     */
    val reviews: List<ItadReview>?,

    /**
     * ITAD 上的热度。
     * Popularity on ITAD.
     */
    val stats: ItadGameStats?,

    /**
     * 玩家人数；试玩版是 `null`，未发售游戏四个值都是 `0`。
     * Player counts; `null` for demos, and all four are `0` for unreleased games.
     */
    val players: ItadPlayerCounts?,

    /**
     * ITAD 上的页面地址。
     * The page URLs on ITAD.
     */
    val urls: ItadGameUrls?,
)

// MARK: - 嵌套结构 / Nested types

/**
 * 一个厂商（开发商或发行商）。
 * A company (developer or publisher).
 */
@Serializable
data class ItadCompanyRef(
    /**
     * 厂商 ID。
     * The company ID.
     */
    val id: Int,

    /**
     * 厂商名字。
     * The company name.
     */
    val name: String,
)

/**
 * 一家的评分。
 * One source's score.
 */
@Serializable
data class ItadReview(
    /**
     * 分数，0–100；Steam 是好评百分比。
     * The score, 0–100; for Steam, the share of positive reviews.
     */
    val score: Int?,

    /**
     * 来源，如 `Steam`、`Metascore`、`Metacritic User Score`、`OpenCritic`。
     * The source, such as `Steam`, `Metascore`, `Metacritic User Score` or `OpenCritic`.
     */
    val source: String?,

    /**
     * 评分人数。
     * How many ratings the score is based on.
     */
    val count: Int?,

    /**
     * 来源页面地址；有的来源没有。
     * The source page URL; some sources have none.
     */
    val url: String?,
)

/**
 * ITAD 上的热度。
 * Popularity on ITAD.
 */
@Serializable
data class ItadGameStats(
    /**
     * 热度排名，越小越热门。
     * The popularity rank; smaller is more popular.
     */
    val rank: Int?,

    /**
     * 加进等降价列表的人数。
     * How many users waitlisted the game.
     */
    val waitlisted: Int?,

    /**
     * 加进收藏的人数。
     * How many users collected the game.
     */
    val collected: Int?,
)

/**
 * 玩家人数。
 * Player counts.
 */
@Serializable
data class ItadPlayerCounts(
    /**
     * 最近一次统计的在线人数。
     * The most recent online count.
     */
    val recent: Int?,

    /**
     * 过去一天的最高人数。
     * The highest count in the past day.
     */
    val day: Int?,

    /**
     * 过去一周的最高人数。
     * The highest count in the past week.
     */
    val week: Int?,

    /**
     * 历史最高人数。
     * The all-time peak.
     */
    val peak: Int?,
)

/**
 * ITAD 上的页面地址。
 * The page URLs on ITAD.
 */
@Serializable
data class ItadGameUrls(
    /**
     * 游戏页。
     * The game page.
     */
    val game: String?,
)

// MARK: - 图片的两种形状 / The two shapes of assets

/**
 * 解 `info/v2` 的 `assets`：有图是对象，没图可能是 `{}` 也可能是 `[]`；不是对象的都当没图（`null`）。
 * Decodes `info/v2`'s `assets`: an object when there are images, and either `{}` or `[]` when there are none;
 * anything that is not an object counts as no images (`null`).
 *
 * 只能用在 JSON 上。用它的属性要给默认值 `= null`：字段缺失时直接取默认值，不会调用它。
 * public 是因为序列化插件要求自定义序列化器至少和用它的类型一样可见。
 * JSON only. Properties using it need a `= null` default, so a missing field takes the default without calling it.
 * It is public because the serialization plugin wants a custom serializer at least as visible as its users.
 */
object ItadInfoAssetsSerializer : KSerializer<ItadAssets?> {

    override val descriptor: SerialDescriptor = ItadAssets.serializer().descriptor.nullable

    override fun deserialize(decoder: Decoder): ItadAssets? {
        // 先原样拿到 JSON 值看形状，是对象才按 ItadAssets 解。
        // Take the raw JSON value to check its shape, and decode it as ItadAssets only when it is an object.
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        val assetsObject = element as? JsonObject
        if (assetsObject == null) {
            return null
        }
        val assets = jsonDecoder.json.decodeFromJsonElement(ItadAssets.serializer(), assetsObject)
        return assets
    }

    override fun serialize(encoder: Encoder, value: ItadAssets?) {
        val jsonEncoder = encoder as JsonEncoder
        if (value == null) {
            jsonEncoder.encodeJsonElement(JsonNull)
            return
        }
        val element = jsonEncoder.json.encodeToJsonElement(ItadAssets.serializer(), value)
        jsonEncoder.encodeJsonElement(element)
    }
}
