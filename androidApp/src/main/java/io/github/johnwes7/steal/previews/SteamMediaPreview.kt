package io.github.johnwes7.steal.previews

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
