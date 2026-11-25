package io.github.johnwes7.steal.shared.models.itad

import io.github.johnwes7.steal.shared.services.appNetworkJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `games/info/v2` 响应的解码测试，重点是 `assets` 的几种形状。
 * Decoding tests for the `games/info/v2` response, focused on the shapes of `assets`.
 */
class ItadGameInfoTest {

    /**
     * 完整的游戏（ELDEN RING）：评分、热度、厂商、图片都解得出来。
     * A full game (ELDEN RING): reviews, popularity, credits and images all decode.
     */
    @Test
    fun decodesFullGame() {
        val info = appNetworkJson.decodeFromString<ItadGameInfoResponse>(ELDEN_RING_RESPONSE)

        assertEquals("018d937f-590c-728b-ac35-38bcff85f086", info.id)
        assertEquals("game", info.type)
        assertEquals(1245620, info.appID)
        assertEquals("2022-02-24", info.releaseDate)
        assertEquals(listOf("Souls-like", "Open World", "Dark Fantasy", "RPG", "Difficult"), info.tags)
        assertEquals("From Software", info.developers?.first()?.name)
        assertEquals(8, info.publishers?.size)

        val reviewSources = info.reviews?.map { review -> review.source }
        assertEquals(listOf("Steam", "Metascore", "Metacritic User Score", "OpenCritic"), reviewSources)
        assertEquals(93, info.reviews?.first()?.score)

        assertEquals(3, info.stats?.rank)
        assertEquals(940453, info.players?.peak)
        assertEquals("https://isthereanydeal.com/game/elden-ring/", info.urls?.game)

        val assets = info.assets
        assertNotNull(assets)
        assertNotNull(assets.boxart)
        assertNotNull(assets.banner400)
    }

    /**
     * 试玩版（Half-Life 2: Demo）：`assets` 是 `{}`，解出全是空的图片；`type`、`appid`、`players` 都是 `null`。
     * A demo (Half-Life 2: Demo): `assets` is `{}` and decodes to all-empty images; `type`, `appid` and `players`
     * are all `null`.
     */
    @Test
    fun decodesDemoWithEmptyObjectAssets() {
        val info = appNetworkJson.decodeFromString<ItadGameInfoResponse>(HALF_LIFE_2_DEMO_RESPONSE)

        assertNull(info.type)
        assertNull(info.appID)
        assertNull(info.releaseDate)
        assertNull(info.players)
        assertTrue(info.reviews.orEmpty().isEmpty())
        assertTrue(info.tags.orEmpty().isEmpty())
        assertEquals(3456, info.stats?.rank)

        val assets = info.assets
        assertNotNull(assets)
        assertNull(assets.boxart)
        assertNull(assets.banner400)
    }

    /**
     * 以前记录到的 `[]` 形状当没图（`null`），不让整条解码失败。
     * The `[]` shape recorded earlier counts as no images (`null`) instead of failing the whole entry.
     */
    @Test
    fun emptyArrayAssetsIsNull() {
        val json = HALF_LIFE_2_DEMO_RESPONSE.replace("\"assets\":{}", "\"assets\":[]")
        val info = appNetworkJson.decodeFromString<ItadGameInfoResponse>(json)

        assertNull(info.assets)
        assertEquals("half-life-2-demo", info.slug)
    }

    /**
     * 没有 `assets` 字段时也是 `null`。
     * A missing `assets` field is `null` too.
     */
    @Test
    fun missingAssetsIsNull() {
        val json = HALF_LIFE_2_DEMO_RESPONSE.replace("\"assets\":{},", "")
        val info = appNetworkJson.decodeFromString<ItadGameInfoResponse>(json)

        assertNull(info.assets)
    }

    private companion object {

        /**
         * 2026-11-26 请求 `info/v2?id=018d937f-590c-728b-ac35-38bcff85f086`（ELDEN RING）的真实响应。
         * A real response to `info/v2?id=018d937f-590c-728b-ac35-38bcff85f086` (ELDEN RING) on 2026-11-26.
         */
        const val ELDEN_RING_RESPONSE = """
        {"id":"018d937f-590c-728b-ac35-38bcff85f086","slug":"elden-ring","title":"Elden Ring","type":"game","mature":false,"assets":{"boxart":"https://assets.isthereanydeal.com/018d937f-590c-728b-ac35-38bcff85f086/boxart.jpg?t=1790295614","banner145":"https://assets.isthereanydeal.com/018d937f-590c-728b-ac35-38bcff85f086/banner145.jpg?t=1790295614","banner300":"https://assets.isthereanydeal.com/018d937f-590c-728b-ac35-38bcff85f086/banner300.jpg?t=1790295614","banner400":"https://assets.isthereanydeal.com/018d937f-590c-728b-ac35-38bcff85f086/banner400.jpg?t=1790295614","banner600":"https://assets.isthereanydeal.com/018d937f-590c-728b-ac35-38bcff85f086/banner600.jpg?t=1790295614"},"earlyAccess":false,"achievements":true,"tradingCards":true,"appid":1245620,"tags":["Souls-like","Open World","Dark Fantasy","RPG","Difficult"],"releaseDate":"2022-02-24","developers":[{"id":403,"name":"From Software"},{"id":113435,"name":"FromSoftware, Inc."}],"publishers":[{"id":81,"name":"BANDAI NAMCO Entertainment"},{"id":5413,"name":"BANDAI NAMCO Entertainment US"},{"id":92612,"name":"FromSoftware, Inc., Bandai Namco Entertainment"},{"id":175,"name":"Bandai Namco Entertainment Inc."},{"id":6186,"name":"Bandai Namco US"},{"id":113435,"name":"FromSoftware, Inc."},{"id":5630,"name":"FromSoftware"},{"id":11216,"name":"BANDAI NAMCO Entertainment Europe"}],"reviews":[{"score":93,"source":"Steam","count":832261,"url":"https://store.steampowered.com/app/1245620/"},{"score":94,"source":"Metascore","count":60,"url":"https://metacritic.com/game/elden-ring/critic-reviews/?platform=pc"},{"score":84,"source":"Metacritic User Score","count":24971,"url":"https://metacritic.com/game/elden-ring/user-reviews/?platform=pc"},{"score":95,"source":"OpenCritic","count":219,"url":"https://opencritic.com/game/12090/elden-ring"}],"stats":{"rank":3,"waitlisted":41223,"collected":32591},"players":{"recent":19698,"day":25613,"week":32657,"peak":940453},"urls":{"game":"https://isthereanydeal.com/game/elden-ring/"}}
        """

        /**
         * 2026-11-26 请求 `info/v2?id=018d937f-2957-7148-8d7c-8ac72686175c`（Half-Life 2: Demo）的真实响应。
         * A real response to `info/v2?id=018d937f-2957-7148-8d7c-8ac72686175c` (Half-Life 2: Demo) on 2026-11-26.
         */
        const val HALF_LIFE_2_DEMO_RESPONSE = """
        {"id":"018d937f-2957-7148-8d7c-8ac72686175c","slug":"half-life-2-demo","title":"Half-Life 2: Demo","type":null,"mature":false,"assets":{},"earlyAccess":false,"achievements":false,"tradingCards":false,"appid":null,"tags":[],"releaseDate":null,"developers":[],"publishers":[],"reviews":[],"stats":{"rank":3456,"waitlisted":0,"collected":31},"players":null,"urls":{"game":"https://isthereanydeal.com/game/half-life-2-demo/"}}
        """
    }
}
