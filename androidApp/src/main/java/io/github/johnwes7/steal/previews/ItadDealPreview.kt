package io.github.johnwes7.steal.previews

import io.github.johnwes7.steal.shared.models.app.HomeDealItem
import io.github.johnwes7.steal.shared.models.itad.ItadAssets
import io.github.johnwes7.steal.shared.models.itad.ItadDeal
import io.github.johnwes7.steal.shared.models.itad.ItadDealInfo
import io.github.johnwes7.steal.shared.models.itad.ItadPlatformRef
import io.github.johnwes7.steal.shared.models.itad.ItadPrice
import io.github.johnwes7.steal.shared.models.itad.ItadShopRef
import kotlin.math.roundToInt

/**
 * 预览用的 5 条澳区 deal，字段形状照真实的 `deals/v2` 响应；第 2 条是史低。
 * Five Australian deals for previews, shaped like a real `deals/v2` response; the second is an all-time low.
 */
internal val previewDeals: List<ItadDeal> = listOf(
    previewDeal(
        id = "01973cf5-c00c-72ad-bc30-2d24c94799f6",
        title = "007 First Light",
        shopName = "GameBillet",
        price = 86.43,
        regular = 104.22,
        cut = 17,
        historyLow = 55.26,
    ),
    previewDeal(
        id = "018e147e-9b32-7025-90af-981a785efddf",
        title = "Ghost of Tsushima DIRECTOR'S CUT",
        shopName = "Fanatical",
        price = 40.43,
        regular = 90.0,
        cut = 55,
        historyLow = 40.43,
    ),
    previewDeal(
        id = "preview-clair-obscur",
        title = "Clair Obscur: Expedition 33",
        shopName = "GreenManGaming",
        price = 50.77,
        regular = 75.0,
        cut = 32,
        historyLow = 45.0,
    ),
    previewDeal(
        id = "preview-no-mans-sky",
        title = "No Man's Sky",
        shopName = "Steam",
        price = 29.45,
        regular = 90.0,
        cut = 67,
        historyLow = 26.99,
    ),
    previewDeal(
        id = "preview-god-of-war",
        title = "God of War",
        shopName = "Fanatical",
        price = 26.93,
        regular = 75.0,
        cut = 64,
        historyLow = 22.5,
    ),
)

/**
 * [previewDeals] 包成首页的条目，appID 都还在查（`Pending`）。
 * [previewDeals] wrapped as home page rows, with every appID still resolving (`Pending`).
 */
internal val previewDealItems: List<HomeDealItem> = previewDeals.map { deal -> HomeDealItem(deal = deal) }

private fun previewDeal(
    id: String,
    title: String,
    shopName: String,
    price: Double,
    regular: Double,
    cut: Int,
    historyLow: Double,
): ItadDeal {
    val assetBase = "https://assets.isthereanydeal.com/$id"
    val assets = ItadAssets(
        boxart = "$assetBase/boxart.jpg",
        banner145 = "$assetBase/banner145.jpg",
        banner300 = "$assetBase/banner300.jpg",
        banner400 = "$assetBase/banner400.jpg",
        banner600 = "$assetBase/banner600.jpg",
    )
    val steam = ItadPlatformRef(id = ItadShopRef.STEAM_ID, name = "Steam")
    val info = ItadDealInfo(
        shop = ItadShopRef(id = 20, name = shopName),
        price = audPrice(amount = price),
        regular = audPrice(amount = regular),
        cut = cut,
        voucher = null,
        storeLow = null,
        historyLow = audPrice(amount = historyLow),
        historyLow1y = null,
        historyLow3m = null,
        flag = null,
        drm = listOf(steam),
        platforms = listOf(ItadPlatformRef(id = 1, name = "Windows")),
        timestamp = "2026-11-18T01:08:29+01:00",
        expiry = null,
        url = "https://itad.link/example/",
    )
    return ItadDeal(
        id = id,
        slug = id,
        title = title,
        type = "game",
        mature = false,
        assets = assets,
        deal = info,
    )
}

private fun audPrice(amount: Double): ItadPrice {
    val amountInt = (amount * 100).roundToInt()
    return ItadPrice(amount = amount, amountInt = amountInt, currency = "AUD")
}
