package io.github.johnwes7.steal.shared.models.itad

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
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
) {

    /**
     * 列表里显示的标题：第一款游戏的名字，没有游戏时用 [title]。
     * The title shown in lists: the first game's name, or [title] when there are no games.
     *
     * [title] 带「- FREE on …」这类后缀，游戏名更干净。
     * [title] carries suffixes such as "- FREE on …", so the game name reads cleaner.
     */
    val displayTitle: String
        get() {
            val firstGame = games.firstOrNull() ?: return title
            return firstGame.title
        }

    /**
     * 行缩略图：第一款游戏的小横幅优先，最后才退到竖版封面；没有游戏或一张图都没有是 `null`。
     * The row thumbnail: the first game's small banners first, the box art only as a last resort; `null` when
     * there are no games or no images.
     */
    val thumbnailUrl: String?
        get() {
            val assets = games.firstOrNull()?.assets ?: return null
            val candidates = listOfNotNull(
                assets.banner300,
                assets.banner400,
                assets.banner145,
                assets.banner600,
                assets.boxart,
            )
            return candidates.firstOrNull()
        }

    /**
     * 领取截止时间；[expiry] 没有或解析不了时是 `null`。
     * The claim deadline; `null` when [expiry] is missing or cannot be parsed.
     */
    val expiryInstant: Instant?
        get() {
            val expiryText = expiry ?: return null
            return Instant.parseOrNull(expiryText)
        }

    /**
     * 截止时间的显示方式：今天、剩几天，或具体日期。
     * How the deadline is shown: today, days left, or the exact date.
     */
    sealed interface Deadline {

        /**
         * 不到一整天就截止，已经过了截止时间也算。
         * Ends in less than a full day, including deadlines already passed.
         */
        data object Today : Deadline

        /**
         * 还剩 [days] 天，1 到 7。
         * [days] days left, from 1 to 7.
         */
        data class DaysLeft(val days: Int) : Deadline

        /**
         * 一周以后才截止，显示 [expiry] 的日期。
         * Ends more than a week away, shown as the date of [expiry].
         */
        data class OnDate(val expiry: Instant) : Deadline
    }

    /**
     * 按 [now] 算截止时间怎么显示；没有截止时间是 `null`。
     * Works out how the deadline is shown as of [now]; `null` when there is no deadline.
     *
     * 天数按整 24 小时算、不按日历日：明天凌晨截止、现在还不到 24 小时，算 [Deadline.Today]。
     * Days count whole 24-hour spans rather than calendar days, so a deadline early tomorrow that is under 24 hours
     * away is [Deadline.Today].
     */
    fun deadline(now: Instant): Deadline? {
        val expiry = expiryInstant ?: return null
        val remaining = expiry - now
        val daysLeft = remaining.inWholeDays

        return when {
            daysLeft <= 0 -> Deadline.Today
            daysLeft <= COUNTDOWN_MAX_DAYS -> Deadline.DaysLeft(days = daysLeft.toInt())
            else -> Deadline.OnDate(expiry = expiry)
        }
    }

    /**
     * 按 [now] 是否 48 小时内截止（含已经过了），界面用它加强调色；没有截止时间是 `false`。
     * Whether the deadline is within 48 hours of [now] (or already passed), for the UI's emphasis; `false` when there
     * is no deadline.
     */
    fun isExpiringSoon(now: Instant): Boolean {
        val expiry = expiryInstant ?: return false
        val remaining = expiry - now
        return remaining <= EXPIRING_SOON_WITHIN
    }
}

/**
 * 剩几天以内显示倒计时，更远显示日期。
 * The most days left that still show as a countdown; farther deadlines show the date.
 */
private const val COUNTDOWN_MAX_DAYS = 7

/**
 * 剩多久以内算快截止。
 * How close a deadline has to be to count as expiring soon.
 */
private val EXPIRING_SOON_WITHIN = 48.hours

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
