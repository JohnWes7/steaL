package io.github.johnwes7.steal.shared.models.itad

import io.github.johnwes7.steal.shared.services.appNetworkJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * giveaways 接口响应 DTO 的解码测试。
 * Decoding tests for the giveaways endpoint's response DTOs.
 */
class ItadGiveawaysTest {

    /**
     * 真实响应（2026-11-18 的第一条与最后一条）能完整解码：一条有商店和备注，一条商店是 `null`、可激活商店为空。
     * A real response (first and last entries on 2026-11-18) decodes in full: one has a shop and a note,
     * the other has a `null` shop and no activation shops.
     */
    @Test
    fun decodesRealResponse() {
        val giveaways = appNetworkJson.decodeFromString<List<ItadGiveaway>>(REAL_RESPONSE)

        assertEquals(2, giveaways.size)

        val epic = giveaways[0]
        assertEquals(16791, epic.id)
        assertEquals("Epic Game Store", epic.shop?.name)
        assertEquals("2026-11-19T17:00:00+01:00", epic.expiry)
        assertTrue(epic.note?.startsWith("Also available on mobile:\n") == true)
        assertEquals("Knightica", epic.games[0].title)
        assertEquals(16, epic.games[0].keys[0].id)
        assertFalse(epic.games[0].drmFree)

        val indieGala = giveaways[1]
        assertNull(indieGala.shop)
        assertNull(indieGala.expiry)
        assertTrue(indieGala.games[0].keys.isEmpty())
        assertTrue(indieGala.games[0].drmFree)
    }

    /**
     * 可选字段整个缺掉时仍能解码，缺的是 `null`。
     * Decoding still works when optional fields are absent entirely; they come back as `null`.
     */
    @Test
    fun decodesMissingOptionals() {
        val giveaways = appNetworkJson.decodeFromString<List<ItadGiveaway>>(SPARSE_RESPONSE)

        val giveaway = giveaways[0]
        assertNull(giveaway.shop)
        assertNull(giveaway.expiry)
        assertNull(giveaway.note)
        assertNull(giveaway.games[0].assets.banner300)
    }

    /**
     * 空数组是「现在没有免费游戏」，不是错误。
     * An empty array means "no giveaways right now", not an error.
     */
    @Test
    fun decodesEmptyList() {
        val giveaways = appNetworkJson.decodeFromString<List<ItadGiveaway>>("[]")

        assertTrue(giveaways.isEmpty())
    }

    private companion object {

        /**
         * 2026-11-18 请求 `giveaways/v1?limit=20&expired=false&mature=false&sort=-publish` 的真实响应，留第一条与最后一条。
         * A real response to `giveaways/v1?limit=20&expired=false&mature=false&sort=-publish` on 2026-11-18,
         * keeping the first and last entries.
         */
        const val REAL_RESPONSE = """
        [
          {
            "id": 16791,
            "title": "Knightica - FREE on Epic Games",
            "shop": { "id": 16, "name": "Epic Game Store" },
            "url": "https://store.epicgames.com/p/knightica-3be45c?epic_creator_id=585fd9faadb3406ab9a0475bc1d24125",
            "details": "https://isthereanydeal.com/giveaways/16791/",
            "isMature": false,
            "publish": "2026-11-12T04:01:49+01:00",
            "expiry": "2026-11-19T17:00:00+01:00",
            "note": "Also available on mobile:\n\n🍎 https://store.epicgames.com/p/knightica-ios-b4550a\n\n🤖 https://store.epicgames.com/p/knightica-android-ad8285",
            "games": [
              {
                "id": "0190bb85-dac6-73dc-bd6b-3fa5ad9e0573",
                "slug": "knightica",
                "title": "Knightica",
                "type": "game",
                "mature": false,
                "assets": {
                  "boxart": "https://assets.isthereanydeal.com/0190bb85-dac6-73dc-bd6b-3fa5ad9e0573/boxart.jpg?t=1794937529",
                  "banner145": "https://assets.isthereanydeal.com/0190bb85-dac6-73dc-bd6b-3fa5ad9e0573/banner145.jpg?t=1794937529",
                  "banner300": "https://assets.isthereanydeal.com/0190bb85-dac6-73dc-bd6b-3fa5ad9e0573/banner300.jpg?t=1794937529",
                  "banner400": "https://assets.isthereanydeal.com/0190bb85-dac6-73dc-bd6b-3fa5ad9e0573/banner400.jpg?t=1794937529",
                  "banner600": "https://assets.isthereanydeal.com/0190bb85-dac6-73dc-bd6b-3fa5ad9e0573/banner600.jpg?t=1794937529"
                },
                "drmFree": false,
                "keys": [ { "id": 16, "name": "Epic Game Store" } ],
                "platforms": [ { "id": 1, "name": "Windows" } ]
              }
            ]
          },
          {
            "id": 7433,
            "title": "Die Young: Prologue - FREE on IndieGala",
            "shop": null,
            "url": "https://freebies.indiegala.com/die-young-prologue?ref=itad",
            "details": "https://isthereanydeal.com/giveaways/7433/",
            "isMature": false,
            "publish": "2021-10-06T11:00:00+02:00",
            "expiry": null,
            "note": "* Giveaway previously offered.",
            "games": [
              {
                "id": "018d937f-1032-72fc-9e19-176d4f377484",
                "slug": "die-young-prologue",
                "title": "Die Young: Prologue",
                "type": "game",
                "mature": false,
                "assets": {
                  "boxart": "https://assets.isthereanydeal.com/018d937f-1032-72fc-9e19-176d4f377484/boxart.png?t=1732071332",
                  "banner145": "https://assets.isthereanydeal.com/018d937f-1032-72fc-9e19-176d4f377484/banner145.jpg?t=1732071333",
                  "banner300": "https://assets.isthereanydeal.com/018d937f-1032-72fc-9e19-176d4f377484/banner300.jpg?t=1732071333",
                  "banner400": "https://assets.isthereanydeal.com/018d937f-1032-72fc-9e19-176d4f377484/banner400.jpg?t=1732071333",
                  "banner600": "https://assets.isthereanydeal.com/018d937f-1032-72fc-9e19-176d4f377484/banner600.jpg?t=1732071333"
                },
                "drmFree": true,
                "keys": [],
                "platforms": [ { "id": 1, "name": "Windows" } ]
              }
            ]
          }
        ]
        """

        /**
         * 手写的稀疏响应：`shop` / `expiry` / `note` 与所有图片都不出现。
         * A hand-written sparse response: `shop`, `expiry`, `note` and every image are absent.
         */
        const val SPARSE_RESPONSE = """
        [
          {
            "id": 1,
            "title": "Sparse Giveaway",
            "url": "https://example.com/claim",
            "details": "https://isthereanydeal.com/giveaways/1/",
            "isMature": false,
            "publish": "2026-11-18T00:00:00+01:00",
            "games": [
              {
                "id": "uuid-1",
                "slug": "sparse-game",
                "title": "Sparse Game",
                "type": "game",
                "mature": false,
                "assets": {},
                "drmFree": false,
                "keys": [],
                "platforms": []
              }
            ]
          }
        ]
        """
    }
}
