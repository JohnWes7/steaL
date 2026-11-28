package io.github.johnwes7.steal.previews

import io.github.johnwes7.steal.shared.models.app.WishlistItem
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.viewmodels.WishlistViewModel.PriceLoadState
import kotlin.time.Instant

/**
 * 预览用的愿望单：第一条从详情页收藏（字段齐全、有已知低价），第二条从首页收藏（发售状态不知道、没有封面），其余几条凑齐
 * [previewWishlistPrices] 的每种价格状态。
 * A wishlist for previews: the first saved from the detail page (every field, with a known low), the second from the
 * home page (release status unknown, no artwork), and the rest to cover every price state in [previewWishlistPrices].
 */
internal val previewWishlistItems: List<WishlistItem> = listOf(
    WishlistItem(
        appID = 1091500,
        name = "Cyberpunk 2077",
        headerImageURL = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1091500/header.jpg",
        itadID = "018d937f-21e1-728e-86d7-9acb3c59f2bb",
        isComingSoon = false,
        releaseCheckedAt = Instant.fromEpochMilliseconds(1_795_000_000_000),
        knownLow = WishlistItem.KnownPrice(amount = 2698, region = AppRegion.Australia),
        addedAt = Instant.fromEpochMilliseconds(1_795_000_000_000),
    ),
    WishlistItem(
        appID = 620,
        name = "Portal 2",
        headerImageURL = null,
        itadID = null,
        isComingSoon = null,
        releaseCheckedAt = null,
        knownLow = null,
        addedAt = Instant.fromEpochMilliseconds(1_794_900_000_000),
    ),
    WishlistItem(
        appID = 1030300,
        name = "Hollow Knight: Silksong",
        headerImageURL = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1030300/header.jpg",
        itadID = null,
        isComingSoon = true,
        releaseCheckedAt = Instant.fromEpochMilliseconds(1_794_800_000_000),
        knownLow = null,
        addedAt = Instant.fromEpochMilliseconds(1_794_800_000_000),
    ),
    WishlistItem(
        appID = 1145360,
        name = "Hades",
        headerImageURL = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1145360/header.jpg",
        itadID = null,
        isComingSoon = false,
        releaseCheckedAt = Instant.fromEpochMilliseconds(1_794_700_000_000),
        knownLow = null,
        addedAt = Instant.fromEpochMilliseconds(1_794_700_000_000),
    ),
    WishlistItem(
        appID = 3357650,
        name = "PRAGMATA",
        headerImageURL = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/3357650/header.jpg",
        itadID = "01973dd3-e247-712c-aaee-acff181a3175",
        isComingSoon = false,
        releaseCheckedAt = Instant.fromEpochMilliseconds(1_794_600_000_000),
        knownLow = null,
        addedAt = Instant.fromEpochMilliseconds(1_794_600_000_000),
    ),
)

/**
 * 预览用的价格状态，和 [previewWishlistItems] 按顺序配对：打折、还在查、没价格、失败、原价。
 * Price states for previews, paired in order with [previewWishlistItems]: on sale, still fetching, no price, failed,
 * and full price.
 */
internal val previewWishlistPrices: List<PriceLoadState> = listOf(
    // 样例比价的第二条是 GameBillet 的 Steam key，打 32 折。
    // The sample prices' second offer is a GameBillet Steam key, 32% off.
    PriceLoadState.Loaded(deal = previewPriceGame.deals[1]),
    PriceLoadState.Loading,
    PriceLoadState.NoPrice,
    PriceLoadState.Failed,
    PriceLoadState.Loaded(deal = previewPriceGame.deals[0]),
)
