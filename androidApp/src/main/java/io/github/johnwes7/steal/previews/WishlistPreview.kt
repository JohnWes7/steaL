package io.github.johnwes7.steal.previews

import io.github.johnwes7.steal.shared.models.app.WishlistItem
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import kotlin.time.Instant

/**
 * 预览用的愿望单：一条从详情页收藏（字段齐全、有已知低价），一条从首页收藏（发售状态不知道）。
 * A wishlist for previews: one saved from the detail page (every field, with a known low) and one from the home page
 * (release status unknown).
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
)
