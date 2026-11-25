package io.github.johnwes7.steal.shared.models.steam

import io.github.johnwes7.steal.shared.models.steam.SteamAppDetails.GameKind
import io.github.johnwes7.steal.shared.services.appNetworkJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `appdetails`（不带 `filters`）响应的解码和派生属性测试。
 * Decoding and derived-property tests for the `appdetails` response without `filters`.
 */
class SteamAppDetailsTest {

    /**
     * 照 Service 的做法解码：先解成字典，再包进 [SteamAppDetailsResponse]。
     * Decodes the way the service will: into a dictionary first, then wrapped in [SteamAppDetailsResponse].
     */
    private fun decode(json: String): SteamAppDetailsResponse {
        val entries = appNetworkJson.decodeFromString<Map<String, SteamAppDetailsEntry>>(json)
        val response = SteamAppDetailsResponse(entries)
        return response
    }

    /**
     * 从真实样例里取一个 app 的详情，取不到就让测试失败。
     * Takes one app's details from the real sample, failing the test when it is missing.
     */
    private fun realDetails(appID: Int): SteamAppDetails {
        val response = decode(REAL_RESPONSES)
        val details = response.details(appID)
        assertNotNull(details)
        return details
    }

    // MARK: - 真实响应 / Real responses

    /**
     * 付费游戏（ELDEN RING）的字段都解得出来；`genres` 的字符串 `"1"` 和 `categories` 的数字 `2` 都成了 `Int`。
     * A paid game (ELDEN RING) decodes every field; `genres`' string `"1"` and `categories`' number `2` both become
     * `Int`.
     */
    @Test
    fun decodesPaidGame() {
        val details = realDetails(ELDEN_RING)

        assertEquals(ELDEN_RING, details.steamAppID)
        assertEquals("ELDEN RING", details.name)
        assertEquals(0, details.requiredAge)
        assertEquals(listOf(1, 3), details.genres?.map { genre -> genre.id })
        assertEquals(listOf(2, 1), details.categories?.map { category -> category.id })
        assertEquals("RPG", details.genres?.get(1)?.description)

        val price = details.priceOverview
        assertNotNull(price)
        assertEquals("AUD", price.currency)
        assertEquals(8995, price.final)
        assertEquals("", price.initialFormatted)

        assertEquals("24 Feb, 2022", details.releaseDate?.date)
        assertEquals(true, details.platforms?.windows)
        assertEquals(listOf("FromSoftware, Inc."), details.developers)
        assertNotNull(details.headerImage)
        assertNotNull(details.detailedDescription)
        assertNull(details.demos)
        assertNull(details.fullGame)

        assertFalse(details.isDemo)
        assertFalse(details.isComingSoon)
        assertEquals(GameKind.Paid, details.gameKind)
    }

    /**
     * 视频和截图：有 HLS 地址的视频能播；截图 ID 是 `0` 也照常解。
     * Movies and screenshots: a movie with an HLS URL is playable, and a screenshot with ID `0` decodes as usual.
     */
    @Test
    fun decodesMoviesAndScreenshots() {
        val details = realDetails(ELDEN_RING)

        val movie = details.movies?.first()
        assertNotNull(movie)
        assertEquals(true, movie.highlight)
        assertTrue(movie.isPlayable)
        assertNotNull(movie.dashAv1)
        assertTrue(movie.hlsH264.orEmpty().contains(".m3u8"))

        val screenshot = details.screenshots?.first()
        assertNotNull(screenshot)
        assertEquals(0, screenshot.id)
        assertNotNull(screenshot.full)
    }

    /**
     * `required_age` 是字符串 `"18"` 时（Cyberpunk 2077）也解成 18。
     * A string `required_age` of `"18"` (Cyberpunk 2077) still decodes to 18.
     */
    @Test
    fun ageAsStringIsRead() {
        val details = realDetails(CYBERPUNK)

        assertEquals(18, details.requiredAge)
    }

    /**
     * 永久免费（Wuthering Waves）：`is_free` 且没有价格，不显示历史低价。
     * Free forever (Wuthering Waves): `is_free` with no price, and the historical lows are hidden.
     */
    @Test
    fun freeToPlayIsFreeForever() {
        val details = realDetails(WUTHERING_WAVES)

        assertNull(details.priceOverview)
        assertEquals(GameKind.FreeForever, details.gameKind)
        assertFalse(details.gameKind.showsPriceHistory)
    }

    /**
     * 开了预购的未发售游戏（Persona 4 Revival）有价格，但算未发售：只看 `coming_soon`。
     * An unreleased game open for pre-order (Persona 4 Revival) has a price but counts as unreleased; only
     * `coming_soon` decides.
     */
    @Test
    fun preOrderIsUnreleased() {
        val details = realDetails(PERSONA_4_REVIVAL)

        assertNotNull(details.priceOverview)
        assertTrue(details.isComingSoon)
        assertEquals(GameKind.Unreleased, details.gameKind)
        assertTrue(details.gameKind.showsPriceHistory)
    }

    /**
     * 本体（PRAGMATA）列出试玩版，`demos[].appid` 是数字。
     * The full game (PRAGMATA) lists its demo, with `demos[].appid` as a number.
     */
    @Test
    fun fullGameListsItsDemo() {
        val details = realDetails(PRAGMATA)

        assertEquals(PRAGMATA_DEMO, details.demos?.first()?.appID)
        assertEquals("", details.demos?.first()?.description)
        assertEquals(GameKind.Paid, details.gameKind)
    }

    /**
     * 试玩版指回本体，`fullgame.appid` 是字符串；`is_free` 虽然是 `true`，也算试玩版。
     * A demo points back to its full game with `fullgame.appid` as a string; though `is_free` is `true`, it counts
     * as a demo.
     */
    @Test
    fun demoPointsBackToFullGame() {
        val details = realDetails(PRAGMATA_DEMO)

        assertTrue(details.isDemo)
        assertEquals(GameKind.Demo, details.gameKind)
        assertEquals("3357650", details.fullGame?.appID)
        assertEquals("PRAGMATA", details.fullGame?.name)
        assertEquals(10, details.categories?.first()?.id)
        assertNull(details.genres)
    }

    /**
     * Steam 上没有（`success:false`）和响应里根本没有的 appID 都是 `null`，不是错误。
     * An appID Steam does not have (`success:false`) and one missing from the response are both `null`, not errors.
     */
    @Test
    fun missingAppIsNull() {
        val response = decode(REAL_RESPONSES)

        assertNull(response.details(MISSING_APP))
        assertNull(response.details(1))
    }

    // MARK: - 手写的边界情况 / Hand-written edge cases

    /**
     * 认不出的数字（不是数字的字符串、对象、`null`、缺字段）都是 `null`，不让整个条目解码失败。
     * Unreadable numbers (a non-numeric string, an object, `null`, a missing field) all become `null` instead of
     * failing the whole entry.
     */
    @Test
    fun unreadableNumbersBecomeNull() {
        val json = """
        {
        "1":{"success":true,"data":{"type":"game","required_age":"abc","genres":[{"id":{},"description":"Odd"}]}},
        "2":{"success":true,"data":{"type":"game","required_age":null,"genres":[{"id":null,"description":"Null"}]}},
        "3":{"success":true,"data":{"type":"game","genres":[{"description":"Missing"}]}}
        }
        """
        val response = decode(json)

        val oddValues = response.details(1)
        assertNotNull(oddValues)
        assertNull(oddValues.requiredAge)
        assertNull(oddValues.genres?.first()?.id)
        assertEquals("Odd", oddValues.genres?.first()?.description)

        val nullValues = response.details(2)
        assertNotNull(nullValues)
        assertNull(nullValues.requiredAge)
        assertNull(nullValues.genres?.first()?.id)

        val missingValues = response.details(3)
        assertNotNull(missingValues)
        assertNull(missingValues.requiredAge)
        assertNull(missingValues.genres?.first()?.id)
    }

    /**
     * 限时免费（`is_free` 但还有原价）算付费，不算永久免费。
     * A limited-time giveaway (`is_free` with a regular price still there) counts as paid, not free forever.
     */
    @Test
    fun limitedTimeFreeCountsAsPaid() {
        val json = """
        {"1":{"success":true,"data":{"type":"game","is_free":true,"price_overview":{"currency":"AUD","initial":2995,"final":0,"discount_percent":100}}}}
        """
        val details = decode(json).details(1)

        assertNotNull(details)
        assertEquals(GameKind.Paid, details.gameKind)
    }

    /**
     * 未发售的试玩版算试玩版：试玩版的判断排在未发售前面；`type` 不分大小写。
     * An unreleased demo counts as a demo, because the demo check comes before the unreleased one; `type` ignores
     * case.
     */
    @Test
    fun demoWinsOverUnreleased() {
        val json = """
        {"1":{"success":true,"data":{"type":"Demo","is_free":true,"release_date":{"coming_soon":true,"date":"Coming soon"}}}}
        """
        val details = decode(json).details(1)

        assertNotNull(details)
        assertTrue(details.isComingSoon)
        assertEquals(GameKind.Demo, details.gameKind)
    }

    /**
     * 只有 DASH 地址的视频不算能播。
     * A movie with only DASH URLs does not count as playable.
     */
    @Test
    fun movieWithoutHlsIsNotPlayable() {
        val json = """
        {"1":{"success":true,"data":{"type":"game","movies":[{"id":7,"dash_h264":"https://example.com/dash_h264.mpd"}]}}}
        """
        val details = decode(json).details(1)

        assertNotNull(details)
        val movie = details.movies?.first()
        assertNotNull(movie)
        assertFalse(movie.isPlayable)
    }

    private companion object {

        const val ELDEN_RING = 1245620
        const val CYBERPUNK = 1091500
        const val WUTHERING_WAVES = 3513350
        const val PERSONA_4_REVIVAL = 2963950
        const val PRAGMATA = 3357650
        const val PRAGMATA_DEMO = 4003800
        const val MISSING_APP = 9999999

        /**
         * 2026-11-26 用 `appdetails?appids=<一个>&cc=AU&l=english` 一个个请求的真实响应，合进一个字典。
         * 每条只留测试用到的字段，长文字截短、列表只留前一两项；ELDEN RING 多留了 `website` 这类 DTO 不收的字段。
         * Real responses fetched one by one with `appdetails?appids=<one>&cc=AU&l=english` on 2026-11-26, merged into
         * one dictionary. Each keeps only the fields the tests use, with long text cut and lists cut to their first
         * one or two items; ELDEN RING also keeps fields the DTO ignores, such as `website`.
         */
        const val REAL_RESPONSES = """
{
"1245620":{"success":true,"data":{"type":"game","name":"ELDEN RING","steam_appid":1245620,"required_age":0,"is_free":false,"short_description":"THE CRITICALLY ACCLAIMED FANTASY ACTION ","detailed_description":"<h1>ELDEN RING Shadow of the Erdtree Edi","header_image":"https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1245620/header.jpg?t=1790290043","capsule_image":"https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1245620/capsule_231x87.jpg?t=1790290043","website":null,"release_date":{"coming_soon":false,"date":"24 Feb, 2022"},"price_overview":{"currency":"AUD","initial":8995,"final":8995,"discount_percent":0,"initial_formatted":"","final_formatted":"A$ 89.95"},"genres":[{"id":"1","description":"Action"},{"id":"3","description":"RPG"}],"categories":[{"id":2,"description":"Single-player"},{"id":1,"description":"Multi-player"}],"platforms":{"windows":true,"mac":false,"linux":false},"developers":["FromSoftware, Inc."],"publishers":["FromSoftware, Inc.","Bandai Namco Entertainment"],"movies":[{"id":256889456,"name":"ELDEN RING Accolades Trailer - UNRATED","thumbnail":"https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/256889456/movie.293x165.jpg?t=1654109241","dash_av1":"https://video.akamai.steamstatic.com/store_trailers/1245620/468149/7d588828f80907ca97efdcb1e6ef72b4ce2b1d0d/1750650511/dash_av1.mpd?t=1654109241","dash_h264":"https://video.akamai.steamstatic.com/store_trailers/1245620/468149/7d588828f80907ca97efdcb1e6ef72b4ce2b1d0d/1750650511/dash_h264.mpd?t=1654109241","hls_h264":"https://video.akamai.steamstatic.com/store_trailers/1245620/468149/7d588828f80907ca97efdcb1e6ef72b4ce2b1d0d/1750650511/hls_264_master.m3u8?t=1654109241","highlight":true}],"screenshots":[{"id":0,"path_thumbnail":"https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1245620/ss_943bf6fe62352757d9070c1d33e50b92fe8539f1.600x338.jpg?t=1790290043","path_full":"https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1245620/ss_943bf6fe62352757d9070c1d33e50b92fe8539f1.1920x1080.jpg?t=1790290043"}]}},
"1091500":{"success":true,"data":{"type":"game","name":"Cyberpunk 2077","steam_appid":1091500,"required_age":"18","is_free":false,"release_date":{"coming_soon":false,"date":"9 Dec, 2020"},"price_overview":{"currency":"AUD","initial":8995,"final":8995,"discount_percent":0,"initial_formatted":"","final_formatted":"A$ 89.95"},"genres":[{"id":"3","description":"RPG"}]}},
"3513350":{"success":true,"data":{"type":"game","name":"Wuthering Waves","steam_appid":3513350,"required_age":0,"is_free":true,"release_date":{"coming_soon":false,"date":"28 Apr, 2025"}}},
"2963950":{"success":true,"data":{"type":"game","name":"Persona 4 Revival ","steam_appid":2963950,"required_age":0,"is_free":false,"release_date":{"coming_soon":true,"date":"17 Feb, 2027"},"price_overview":{"currency":"AUD","initial":10795,"final":10795,"discount_percent":0,"initial_formatted":"","final_formatted":"A$ 107.95"}}},
"3357650":{"success":true,"data":{"type":"game","name":"PRAGMATA","steam_appid":3357650,"required_age":0,"is_free":false,"release_date":{"coming_soon":false,"date":"16 Apr, 2026"},"price_overview":{"currency":"AUD","initial":9295,"final":9295,"discount_percent":0,"initial_formatted":"","final_formatted":"A$ 92.95"},"demos":[{"appid":4003800,"description":""}]}},
"4003800":{"success":true,"data":{"type":"demo","name":"PRAGMATA SKETCHBOOK - DEMO","steam_appid":4003800,"required_age":0,"is_free":true,"release_date":{"coming_soon":false,"date":"11 Dec, 2025"},"categories":[{"id":10,"description":"Game demo"}],"fullgame":{"appid":"3357650","name":"PRAGMATA"}}},
"9999999":{"success":false}
}
"""
    }
}
