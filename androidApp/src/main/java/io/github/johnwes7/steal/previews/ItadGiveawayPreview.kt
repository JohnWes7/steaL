package io.github.johnwes7.steal.previews

import io.github.johnwes7.steal.shared.models.itad.ItadAssets
import io.github.johnwes7.steal.shared.models.itad.ItadGiveaway
import io.github.johnwes7.steal.shared.models.itad.ItadGiveawayGame
import io.github.johnwes7.steal.shared.models.itad.ItadPlatformRef
import io.github.johnwes7.steal.shared.models.itad.ItadShopRef
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * 预览里的「现在」，倒计时按它算。
 * The "now" for previews, which the countdowns are measured from.
 *
 * 截止时间都是相对它定的，预览不会过几天就全变成「Today」。
 * Every deadline is set relative to it, so previews don't all turn into "Today" a few days later.
 */
internal val previewNow: Instant = Instant.parse("2026-11-24T00:00:00Z")

// 要写在 previewGiveaways 前面：同一文件的顶层属性按书写顺序初始化，写在后面的话那时还是 null。
// Must come before previewGiveaways: top-level properties in a file initialize in order, so it would still be null.
private val EPIC_GAME_STORE = ItadShopRef(id = 16, name = "Epic Game Store")

/**
 * 预览用的 4 条 giveaway，依次是今天截止（快截止）、剩 3 天、一周以后（标题长、折两行）、没有截止时间也没有商店。
 * Four giveaways for previews: ending today (expiring soon), 3 days left, more than a week away (with a long,
 * two-line title), and one with neither a deadline nor a shop.
 */
internal val previewGiveaways: List<ItadGiveaway> = listOf(
    previewGiveaway(
        id = 16791,
        title = "Knightica - FREE on Epic Games",
        gameId = "0190bb85-dac6-73dc-bd6b-3fa5ad9e0573",
        gameTitle = "Knightica",
        shop = EPIC_GAME_STORE,
        timeLeft = 20.hours,
    ),
    previewGiveaway(
        id = 16800,
        title = "Mark of the Deep - FREE on Epic Games",
        gameId = "preview-mark-of-the-deep",
        gameTitle = "Mark of the Deep",
        shop = EPIC_GAME_STORE,
        timeLeft = 3.days,
    ),
    previewGiveaway(
        id = 16801,
        title = "Contractor: The Clean Up Part II - FREE on Epic Games",
        gameId = "preview-contractor-the-clean-up-part-ii",
        gameTitle = "Contractor: The Clean Up Part II",
        shop = EPIC_GAME_STORE,
        timeLeft = 8.days,
    ),
    previewGiveaway(
        id = 7433,
        title = "Die Young: Prologue - FREE on IndieGala",
        gameId = "018d937f-1032-72fc-9e19-176d4f377484",
        gameTitle = "Die Young: Prologue",
        shop = null,
        timeLeft = null,
    ),
)

/**
 * 拼一条只含一款游戏的 giveaway；[timeLeft] 是离 [previewNow] 还有多久截止，`null` 表示没有截止时间。
 * Builds a giveaway with a single game; [timeLeft] is how long after [previewNow] it ends, `null` for no deadline.
 */
private fun previewGiveaway(
    id: Int,
    title: String,
    gameId: String,
    gameTitle: String,
    shop: ItadShopRef?,
    timeLeft: Duration?,
): ItadGiveaway {
    val assetBase = "https://assets.isthereanydeal.com/$gameId"
    val assets = ItadAssets(
        boxart = "$assetBase/boxart.jpg",
        banner145 = "$assetBase/banner145.jpg",
        banner300 = "$assetBase/banner300.jpg",
        banner400 = "$assetBase/banner400.jpg",
        banner600 = "$assetBase/banner600.jpg",
    )
    val game = ItadGiveawayGame(
        id = gameId,
        slug = gameId,
        title = gameTitle,
        type = "game",
        mature = false,
        assets = assets,
        drmFree = false,
        keys = listOfNotNull(shop),
        platforms = listOf(ItadPlatformRef(id = 1, name = "Windows")),
    )
    val expiry = if (timeLeft == null) null else (previewNow + timeLeft).toString()

    return ItadGiveaway(
        id = id,
        title = title,
        shop = shop,
        url = "https://example.com/claim/$id",
        details = "https://isthereanydeal.com/giveaways/$id/",
        isMature = false,
        publish = "2026-11-20T17:00:00+01:00",
        expiry = expiry,
        note = null,
        games = listOf(game),
    )
}
