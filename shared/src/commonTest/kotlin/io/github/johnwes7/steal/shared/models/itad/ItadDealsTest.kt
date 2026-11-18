package io.github.johnwes7.steal.shared.models.itad

import io.github.johnwes7.steal.shared.services.appJson
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * deals 接口的请求体与响应 DTO 的编解码测试。
 * Encoding and decoding tests for the deals endpoint's request body and response DTOs.
 */
class ItadDealsTest {

    /**
     * 真实响应（2026-11-18 AU 区 `-hot` 的第一条）能完整解码，带下划线的键也对得上。
     * A real response (AU, `-hot`, first entry on 2026-11-18) decodes in full, including the underscored keys.
     */
    @Test
    fun decodesRealResponse() {
        val response = appJson.decodeFromString<ItadDealsResponse>(REAL_RESPONSE)

        assertEquals(3, response.nextOffset)
        assertTrue(response.hasMore)
        assertEquals(1, response.list.size)

        val deal = response.list[0]
        assertEquals("01973cf5-c00c-72ad-bc30-2d24c94799f6", deal.id)
        assertEquals("007 First Light", deal.title)
        assertEquals("GameBillet", deal.deal.shop.name)
        assertEquals(8643, deal.deal.price.amountInt)
        assertEquals(17, deal.deal.cut)
        assertEquals(5526, deal.deal.historyLow1y?.amountInt)
        assertEquals(5526, deal.deal.historyLow3m?.amountInt)
        assertNull(deal.deal.voucher)
        assertNull(deal.deal.expiry)
        assertTrue(deal.deal.isSteamActivation)
        assertFalse(deal.deal.isHistoricalLow)
    }

    /**
     * 可选字段整个缺掉、外加不认识的字段时，仍能解码：缺的是 `null`，多的被忽略。
     * Decoding still works when optional fields are missing and unknown ones are added: missing ones are `null`, extras are ignored.
     */
    @Test
    fun decodesMissingOptionalsAndUnknownFields() {
        val response = appJson.decodeFromString<ItadDealsResponse>(SPARSE_RESPONSE)

        val deal = response.list[0]
        assertNull(response.nextOffset)
        assertNull(deal.assets.boxart)
        assertNull(deal.deal.storeLow)
        assertNull(deal.deal.historyLow)
        assertNull(deal.deal.flag)
        assertFalse(deal.deal.isHistoricalLow)
    }

    /**
     * 当前价不高于历史最低价时算史低。
     * A price at or below the all-time low counts as a historical low.
     */
    @Test
    fun historicalLowComparesMinorUnits() {
        val response = appJson.decodeFromString<ItadDealsResponse>(SPARSE_RESPONSE)
        val sparseDeal = response.list[0].deal

        val atLow = sparseDeal.copy(historyLow = sparseDeal.price)
        assertTrue(atLow.isHistoricalLow)
    }

    /**
     * 请求体编码出来和 iOS 首页发的一样；没有类型标签时不出现 `tags` 键。
     * The encoded body matches what the iOS home page sends; without genre tags there is no `tags` key.
     */
    @Test
    fun encodesHomeRequestWithoutTags() {
        val request = ItadDealsRequest(
            country = "AU",
            offset = 0,
            limit = 5,
            sort = "-hot",
            nondeals = false,
            filter = ItadDealsFilter(type = listOf(1), drm = listOf(61)),
        )

        val encoded = appJson.encodeToJsonElement(ItadDealsRequest.serializer(), request)
        val expected = Json.parseToJsonElement(
            """
            {"country":"AU","offset":0,"limit":5,"sort":"-hot","nondeals":false,
             "filter":{"type":[1],"drm":[61]}}
            """,
        )
        assertEquals(expected, encoded)
    }

    /**
     * 有类型标签时编码进 `filter.tags`（Hot Deals 按类型筛选）。
     * Genre tags are encoded into `filter.tags` (Hot Deals' genre filter).
     */
    @Test
    fun encodesTagsWhenPresent() {
        val request = ItadDealsRequest(
            country = "AU",
            offset = 20,
            limit = 20,
            sort = "-hot",
            nondeals = false,
            filter = ItadDealsFilter(type = listOf(1), drm = listOf(61), tags = listOf("Action")),
        )

        val encoded = appJson.encodeToJsonElement(ItadDealsRequest.serializer(), request)
        val expected = Json.parseToJsonElement(
            """
            {"country":"AU","offset":20,"limit":20,"sort":"-hot","nondeals":false,
             "filter":{"type":[1],"drm":[61],"tags":["Action"]}}
            """,
        )
        assertEquals(expected, encoded)
    }

    private companion object {

        /**
         * 2026-11-18 用 `country: AU, sort: -hot, limit: 3` 请求的真实响应，只留第一条。
         * A real response to `country: AU, sort: -hot, limit: 3` on 2026-11-18, keeping only the first entry.
         */
        const val REAL_RESPONSE = """
        {
          "nextOffset": 3,
          "hasMore": true,
          "list": [
            {
              "id": "01973cf5-c00c-72ad-bc30-2d24c94799f6",
              "slug": "007-first-light",
              "title": "007 First Light",
              "type": "game",
              "mature": false,
              "assets": {
                "boxart": "https://assets.isthereanydeal.com/01973cf5-c00c-72ad-bc30-2d24c94799f6/boxart.jpg?t=1794995714",
                "banner145": "https://assets.isthereanydeal.com/01973cf5-c00c-72ad-bc30-2d24c94799f6/banner145.jpg?t=1794995714",
                "banner300": "https://assets.isthereanydeal.com/01973cf5-c00c-72ad-bc30-2d24c94799f6/banner300.jpg?t=1794995714",
                "banner400": "https://assets.isthereanydeal.com/01973cf5-c00c-72ad-bc30-2d24c94799f6/banner400.jpg?t=1794995714",
                "banner600": "https://assets.isthereanydeal.com/01973cf5-c00c-72ad-bc30-2d24c94799f6/banner600.jpg?t=1794995714"
              },
              "deal": {
                "shop": { "id": 20, "name": "GameBillet" },
                "price": { "amount": 86.43, "amountInt": 8643, "currency": "AUD" },
                "regular": { "amount": 104.22, "amountInt": 10422, "currency": "AUD" },
                "cut": 17,
                "voucher": null,
                "storeLow": { "amount": 55.26, "amountInt": 5526, "currency": "AUD" },
                "historyLow": { "amount": 55.26, "amountInt": 5526, "currency": "AUD" },
                "historyLow_1y": { "amount": 55.26, "amountInt": 5526, "currency": "AUD" },
                "historyLow_3m": { "amount": 55.26, "amountInt": 5526, "currency": "AUD" },
                "flag": null,
                "drm": [ { "id": 61, "name": "Steam" } ],
                "platforms": [ { "id": 1, "name": "Windows" } ],
                "timestamp": "2026-11-18T01:08:29+01:00",
                "expiry": null,
                "url": "https://itad.link/01a15605-675c-720f-87f6-dbb59f058a9a/?app=fxbadj"
              }
            }
          ]
        }
        """

        /**
         * 手写的稀疏响应：可选字段整个不出现，另加一个不认识的字段 `extra`。
         * A hand-written sparse response: optional fields are absent entirely, plus an unknown field `extra`.
         */
        const val SPARSE_RESPONSE = """
        {
          "hasMore": false,
          "extra": "ignored",
          "list": [
            {
              "id": "uuid-1",
              "slug": "sparse-game",
              "title": "Sparse Game",
              "type": "game",
              "mature": false,
              "assets": {},
              "deal": {
                "shop": { "id": 61, "name": "Steam" },
                "price": { "amount": 9.99, "amountInt": 999, "currency": "AUD" },
                "regular": { "amount": 19.99, "amountInt": 1999, "currency": "AUD" },
                "cut": 50,
                "drm": [ { "id": 61, "name": "Steam" } ],
                "platforms": [],
                "timestamp": "2026-11-18T00:00:00+01:00",
                "url": "https://itad.link/example/"
              }
            }
          ]
        }
        """
    }
}
