package io.github.johnwes7.steal.previews

import io.github.johnwes7.steal.shared.models.steam.SteamMovie

/**
 * 预览用的 Steam 主图地址（Portal 2）。
 * A Steam header image URL for previews (Portal 2).
 */
internal const val PREVIEW_HEADER_IMAGE_URL = "https://cdn.cloudflare.steamstatic.com/steam/apps/620/header.jpg"

/**
 * 预览用的截图地址：两张就够看出翻页和页点。
 * Screenshot URLs for previews: two are enough to show paging and the dots.
 */
internal val previewScreenshotUrls: List<String> = listOf(
    "https://cdn.cloudflare.steamstatic.com/steam/apps/620/capsule_616x353.jpg",
    "https://cdn.cloudflare.steamstatic.com/steam/apps/620/library_hero.jpg",
)

/**
 * 预览用的一长串截图地址：页数超过页点一次能显示的个数。
 * A long list of screenshot URLs for previews: more pages than the dots show at once.
 */
internal val previewManyScreenshotUrls: List<String> = List(24) { index ->
    "https://cdn.cloudflare.steamstatic.com/steam/apps/620/ss_preview_$index.jpg"
}

/**
 * 预览用的能播的预告片（Portal 2，和 iOS 的 `SteamMovie.previewSample` 同一条）。
 * A playable trailer for previews (Portal 2, the same one as iOS's `SteamMovie.previewSample`).
 */
internal val previewMovie = SteamMovie(
    id = 5787,
    name = "Portal 2 Trailer",
    highlight = true,
    thumbnail = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/5787/movie.293x165.jpg?t=1682715616",
    hlsH264 = "https://video.akamai.steamstatic.com/store_trailers/620/3746/0f4bee66d490b637707ac4fca3e850f2cc2d3b2a/1750108721/hls_264_master.m3u8?t=1682715616",
    dashAv1 = null,
    dashH264 = null,
)
