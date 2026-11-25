package io.github.johnwes7.steal.shared.models.itad

import io.github.johnwes7.steal.shared.services.appNetworkJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `games/lookup/v1` 响应的解码测试。
 * Decoding tests for the `games/lookup/v1` response.
 */
class ItadGameLookupTest {

    /**
     * 找到时有 UUID 和标识；`mature`、`assets` 这类 DTO 不收的字段被忽略。
     * A match carries the UUID and identity; fields the DTO ignores, such as `mature` and `assets`, are skipped.
     */
    @Test
    fun decodesMatch() {
        val response = appNetworkJson.decodeFromString<ItadGameLookupResponse>(ELDEN_RING_RESPONSE)

        assertTrue(response.found)
        val game = response.game
        assertNotNull(game)
        assertEquals("018d937f-590c-728b-ac35-38bcff85f086", game.id)
        assertEquals("elden-ring", game.slug)
        assertEquals("Elden Ring", game.title)
        assertEquals("game", game.type)
    }

    /**
     * 试玩版的 `type` 是 `null`，`assets` 是 `{}`，照样解得出来。
     * A demo's `null` `type` and `{}` `assets` still decode.
     */
    @Test
    fun decodesDemoWithNullType() {
        val response = appNetworkJson.decodeFromString<ItadGameLookupResponse>(HALF_LIFE_2_DEMO_RESPONSE)

        assertTrue(response.found)
        assertEquals("018d937f-2957-7148-8d7c-8ac72686175c", response.game?.id)
        assertNull(response.game?.type)
    }

    /**
     * 没收录时只有 `found: false`，没有 `game`；这不是错误。
     * An untracked game gives only `found: false` with no `game`; that is not an error.
     */
    @Test
    fun notFoundHasNoGame() {
        val response = appNetworkJson.decodeFromString<ItadGameLookupResponse>(NOT_FOUND_RESPONSE)

        assertFalse(response.found)
        assertNull(response.game)
    }

    private companion object {

        /**
         * 2026-11-26 请求 `lookup/v1?appid=1245620`（ELDEN RING）的真实响应。
         * A real response to `lookup/v1?appid=1245620` (ELDEN RING) on 2026-11-26.
         */
        const val ELDEN_RING_RESPONSE = """
        {"found":true,"game":{"id":"018d937f-590c-728b-ac35-38bcff85f086","slug":"elden-ring","title":"Elden Ring","type":"game","mature":false,"assets":{"boxart":"https:\/\/assets.isthereanydeal.com\/018d937f-590c-728b-ac35-38bcff85f086\/boxart.jpg?t=1790295614","banner145":"https:\/\/assets.isthereanydeal.com\/018d937f-590c-728b-ac35-38bcff85f086\/banner145.jpg?t=1790295614","banner300":"https:\/\/assets.isthereanydeal.com\/018d937f-590c-728b-ac35-38bcff85f086\/banner300.jpg?t=1790295614","banner400":"https:\/\/assets.isthereanydeal.com\/018d937f-590c-728b-ac35-38bcff85f086\/banner400.jpg?t=1790295614","banner600":"https:\/\/assets.isthereanydeal.com\/018d937f-590c-728b-ac35-38bcff85f086\/banner600.jpg?t=1790295614"}}}
        """

        /**
         * 2026-11-26 请求 `lookup/v1?appid=219`（Half-Life 2: Demo）的真实响应。
         * A real response to `lookup/v1?appid=219` (Half-Life 2: Demo) on 2026-11-26.
         */
        const val HALF_LIFE_2_DEMO_RESPONSE = """
        {"found":true,"game":{"id":"018d937f-2957-7148-8d7c-8ac72686175c","slug":"half-life-2-demo","title":"Half-Life 2: Demo","type":null,"mature":false,"assets":{}}}
        """

        /**
         * 2026-11-26 请求 `lookup/v1?appid=4349200`（ITAD 没收录的试玩版）的真实响应。
         * A real response to `lookup/v1?appid=4349200` (a demo ITAD does not track) on 2026-11-26.
         */
        const val NOT_FOUND_RESPONSE = """
        {"found":false}
        """
    }
}
