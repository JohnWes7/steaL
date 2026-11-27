package io.github.johnwes7.steal.previews

import io.github.johnwes7.steal.shared.models.steam.SteamSearchItem
import io.github.johnwes7.steal.shared.models.steam.SteamSearchPrice

/**
 * 预览用的 4 条搜索结果，取自 2026-11-27 搜「counter-strike」的真实响应：免费游戏、付费游戏、原声，最后一条的名字故意加长、
 * 看折两行。
 * Four search results for previews, taken from a real "counter-strike" search on 2026-11-27: a free game, a paid
 * game, a soundtrack, and a last one whose name is lengthened on purpose to show two lines.
 */
internal val previewSearchItems: List<SteamSearchItem> = listOf(
    SteamSearchItem(
        type = "app",
        name = "Counter-Strike 2",
        id = 730,
        price = null,
        tinyImage = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/730/942c04efaa5bc87afb6f2a97dbf17ac614c8a84d/capsule_231x87.jpg?t=1793054556",
    ),
    SteamSearchItem(
        type = "app",
        name = "Counter-Strike",
        id = 10,
        price = SteamSearchPrice(currency = "AUD", initial = 1415, final = 1415),
        tinyImage = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/10/capsule_231x87.jpg?t=1745368572",
    ),
    SteamSearchItem(
        type = "app",
        name = "Counter-Strike 2 Soundtrack",
        id = 2678630,
        price = null,
        tinyImage = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/2678630/capsule_231x87.jpg?t=1788971306",
    ),
    SteamSearchItem(
        type = "app",
        name = "Counter-Strike: Condition Zero Deleted Scenes and a Much Longer Title",
        id = 80,
        price = SteamSearchPrice(currency = "AUD", initial = 1415, final = 1415),
        tinyImage = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/80/capsule_231x87.jpg?t=1745368574",
    ),
)
