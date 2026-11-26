package io.github.johnwes7.steal.previews

import io.github.johnwes7.steal.shared.models.itad.ItadGameInfoResponse
import io.github.johnwes7.steal.shared.models.itad.ItadGameStats
import io.github.johnwes7.steal.shared.models.itad.ItadPlatformRef
import io.github.johnwes7.steal.shared.models.itad.ItadPlayerCounts
import io.github.johnwes7.steal.shared.models.itad.ItadPrice
import io.github.johnwes7.steal.shared.models.itad.ItadPriceDeal
import io.github.johnwes7.steal.shared.models.itad.ItadPriceGame
import io.github.johnwes7.steal.shared.models.itad.ItadPriceHistory
import io.github.johnwes7.steal.shared.models.itad.ItadReview
import io.github.johnwes7.steal.shared.models.itad.ItadShopRef
import io.github.johnwes7.steal.shared.models.steam.SteamAppDetails
import io.github.johnwes7.steal.shared.models.steam.SteamDemo
import io.github.johnwes7.steal.shared.models.steam.SteamDescriptor
import io.github.johnwes7.steal.shared.models.steam.SteamPlatforms
import io.github.johnwes7.steal.shared.models.steam.SteamReleaseDate
import kotlin.math.roundToInt

/**
 * Steam 的 DRM；写在用到它的顶层属性前面（顶层属性按书写顺序初始化）。
 * The Steam DRM; declared before the top-level properties that use it, which initialize in order.
 */
private val previewSteamDrm = ItadPlatformRef(id = ItadShopRef.STEAM_ID, name = "Steam")

/**
 * 预览用的 Steam 详情：照 PRAGMATA 的真实数据（2026-11-26），有试玩版。
 * Steam details for previews, after PRAGMATA's real data (2026-11-26), with a demo.
 */
internal val previewAppDetails: SteamAppDetails = SteamAppDetails(
    steamAppID = 3357650,
    name = "PRAGMATA",
    type = "game",
    shortDescription = "Pragmata is a unique, sci-fi action-adventure game from Capcom. Follow Hugh, a member of an " +
        "ill-fated investigation team, and Diana, a young android, as they navigate a lunar facility taken over " +
        "by rogue AI in search of a way to Earth.",
    detailedDescription = null,
    aboutTheGame = null,
    headerImage = PREVIEW_HEADER_IMAGE_URL,
    capsuleImage = null,
    screenshots = null,
    movies = null,
    releaseDate = SteamReleaseDate(comingSoon = false, date = "16 Apr, 2026"),
    isFree = false,
    fullGame = null,
    demos = listOf(SteamDemo(appID = 4003800, description = "")),
    priceOverview = null,
    categories = listOf(
        SteamDescriptor(id = 2, description = "Single-player"),
        SteamDescriptor(id = 22, description = "Steam Achievements"),
        SteamDescriptor(id = 28, description = "Full controller support"),
        SteamDescriptor(id = 23, description = "Steam Cloud"),
    ),
    genres = listOf(
        SteamDescriptor(description = "Action"),
        SteamDescriptor(description = "Adventure"),
    ),
    platforms = SteamPlatforms(windows = true, mac = false, linux = false),
    developers = listOf("CAPCOM Co., Ltd."),
    publishers = listOf("CAPCOM Co., Ltd."),
)

/**
 * 预览用的澳区比价：Steam 原价、GameBillet 打折（最低）、Epic 原价（不能在 Steam 激活）。
 * Australian prices for previews: Steam at full price, GameBillet on sale (cheapest), and Epic at full price (not a
 * Steam activation).
 */
internal val previewPriceGame: ItadPriceGame = ItadPriceGame(
    id = "01973dd3-e247-712c-aaee-acff181a3175",
    historyLow = ItadPriceHistory(
        all = previewAudPrice(amount = 56.45),
        y1 = previewAudPrice(amount = 56.45),
        m3 = previewAudPrice(amount = 59.99),
    ),
    deals = listOf(
        previewPriceDeal(shop = ItadShopRef(id = ItadShopRef.STEAM_ID, name = "Steam"), price = 99.95, cut = 0, drm = emptyList()),
        previewPriceDeal(shop = ItadShopRef(id = 37, name = "GameBillet"), price = 61.38, cut = 32, drm = listOf(previewSteamDrm)),
        previewPriceDeal(
            shop = ItadShopRef(id = 16, name = "Epic Game Store"),
            price = 99.95,
            cut = 0,
            drm = listOf(ItadPlatformRef(id = 16, name = "Epic")),
        ),
    ),
)

/**
 * 预览用的 ITAD 信息：照 PRAGMATA 的真实数据，四个评分来源、在线人数和排名都有。
 * ITAD info for previews, after PRAGMATA's real data, with four review sources, players online and a rank.
 */
internal val previewGameInfo: ItadGameInfoResponse = ItadGameInfoResponse(
    id = "01973dd3-e247-712c-aaee-acff181a3175",
    slug = "pragmata",
    title = "PRAGMATA",
    type = "game",
    mature = false,
    appID = 3357650,
    tags = listOf("Action", "Adventure"),
    earlyAccess = false,
    achievements = true,
    tradingCards = true,
    releaseDate = "2026-04-16",
    developers = null,
    publishers = null,
    reviews = listOf(
        ItadReview(score = 96, source = "Steam", count = 34476, url = null),
        ItadReview(score = 88, source = "Metascore", count = 45, url = null),
        ItadReview(score = 87, source = "Metacritic User Score", count = 3043, url = null),
        ItadReview(score = 86, source = "OpenCritic", count = null, url = null),
    ),
    stats = ItadGameStats(rank = 182, waitlisted = null, collected = null),
    players = ItadPlayerCounts(recent = 486, day = null, week = null, peak = null),
    urls = null,
)

private fun previewPriceDeal(shop: ItadShopRef, price: Double, cut: Int, drm: List<ItadPlatformRef>): ItadPriceDeal {
    val regularAmount = if (cut > 0) price * 100 / (100 - cut) else price
    return ItadPriceDeal(
        shop = shop,
        price = previewAudPrice(amount = price),
        regular = previewAudPrice(amount = regularAmount),
        cut = cut,
        voucher = null,
        storeLow = null,
        flag = null,
        drm = drm,
        platforms = listOf(ItadPlatformRef(id = 1, name = "Windows")),
        timestamp = "2026-11-26T01:08:29+01:00",
        expiry = null,
        url = "https://itad.link/example/",
    )
}

private fun previewAudPrice(amount: Double): ItadPrice {
    val amountInt = (amount * 100).roundToInt()
    return ItadPrice(amount = amount, amountInt = amountInt, currency = "AUD")
}
