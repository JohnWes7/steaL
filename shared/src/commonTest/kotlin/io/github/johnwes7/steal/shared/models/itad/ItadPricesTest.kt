package io.github.johnwes7.steal.shared.models.itad

import io.github.johnwes7.steal.shared.services.appNetworkJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `games/prices/v3` 响应的解码和派生属性测试。
 * Decoding and derived-property tests for the `games/prices/v3` response.
 */
class ItadPricesTest {

    /**
     * 照 Service 的做法解码：顶层是数组，直接解成列表。
     * Decodes the way the service will: the root is an array, decoded straight into a list.
     */
    private fun decode(json: String): List<ItadPriceGame> {
        val games = appNetworkJson.decodeFromString<List<ItadPriceGame>>(json)
        return games
    }

    // MARK: - 真实响应 / Real response

    /**
     * 按 UUID 取到 ELDEN RING：三档历史低价和三条报价都解得出来。
     * ELDEN RING is found by UUID, with its three historical-low windows and three offers decoded.
     */
    @Test
    fun decodesGameByUUID() {
        val prices = decode(ELDEN_RING_RESPONSE).game(ELDEN_RING)

        assertNotNull(prices)
        assertEquals(4553, prices.allTimeLow?.amountInt)
        assertEquals(4838, prices.historyLow.y1?.amountInt)
        assertEquals(7320, prices.historyLow.m3?.amountInt)
        assertEquals(listOf("GameBillet", "Steam", "JoyBuggy"), prices.deals.map { deal -> deal.shop.name })

        val firstDeal = prices.deals.first()
        assertEquals(12, firstDeal.cut)
        assertEquals(9109, firstDeal.regular.amountInt)
        assertEquals(4553, firstDeal.storeLow?.amountInt)
        assertNull(firstDeal.flag)
        assertNull(firstDeal.voucher)
        assertNull(firstDeal.expiry)
        assertEquals("https://itad.link/01a15605-496a-708d-bc81-602e9c76ee70/?app=fxbadj", firstDeal.url)
        assertFalse(firstDeal.isHistoricalLow)
        assertFalse(prices.isEmpty)
    }

    /**
     * 在 Steam 买的报价 DRM 是空列表，也算 Steam 激活；三条都是，最便宜的是 GameBillet。
     * An offer bought on Steam has an empty DRM list yet still activates on Steam; all three do, and GameBillet is
     * the cheapest.
     */
    @Test
    fun steamShopCountsAsSteamActivation() {
        val prices = decode(ELDEN_RING_RESPONSE).game(ELDEN_RING)

        assertNotNull(prices)
        val steamShopDeal = prices.deals.first { deal -> deal.shop.id == ItadShopRef.STEAM_ID }
        assertTrue(steamShopDeal.drm.isEmpty())
        assertTrue(steamShopDeal.isSteamActivation)
        assertEquals(3, prices.steamDeals.size)
        assertEquals("GameBillet", prices.bestDeal?.shop?.name)
        assertEquals("GameBillet", prices.bestSteamDeal?.shop?.name)
    }

    /**
     * 稀疏数组：没请求到的 UUID、空数组都取不到，返回 `null`。
     * The sparse array: a UUID that is not there and an empty array both give `null`.
     */
    @Test
    fun missingGameIsNull() {
        assertNull(decode(ELDEN_RING_RESPONSE).game("018d937e-0000-0000-0000-000000000000"))
        assertNull(decode("[]").game(ELDEN_RING))
    }

    // MARK: - 手写的边界情况 / Hand-written edge cases

    /**
     * 只在别的平台激活的报价（GOG）不算 Steam 激活；最便宜的 Steam 报价跳过它。
     * An offer activating only on another platform (GOG) is not a Steam activation, so the cheapest Steam offer
     * skips it.
     */
    @Test
    fun otherDrmIsNotSteam() {
        val json = """
        [{"id":"game","historyLow":{"all":null,"y1":null,"m3":null},"deals":[
        ${dealJson(shopID = 35, price = 500, drm = """[{"id":35,"name":"GOG"}]""", flag = null)},
        ${dealJson(shopID = 20, price = 700, drm = """[{"id":61,"name":"Steam"}]""", flag = null)}
        ]}]
        """
        val prices = decode(json).game("game")

        assertNotNull(prices)
        assertEquals(500, prices.bestDeal?.price?.amountInt)
        assertEquals(1, prices.steamDeals.size)
        assertEquals(700, prices.bestSteamDeal?.price?.amountInt)
    }

    /**
     * `H` 和 `N` 算全史最低价，`S` 不算；认不出的新标记照样解码。
     * `H` and `N` count as all-time lows and `S` does not; an unknown new flag still decodes.
     */
    @Test
    fun flagsDecideHistoricalLow() {
        val json = """
        [{"id":"game","historyLow":{"all":null,"y1":null,"m3":null},"deals":[
        ${dealJson(shopID = 1, price = 100, drm = "[]", flag = "\"H\"")},
        ${dealJson(shopID = 2, price = 100, drm = "[]", flag = "\"N\"")},
        ${dealJson(shopID = 3, price = 100, drm = "[]", flag = "\"S\"")},
        ${dealJson(shopID = 4, price = 100, drm = "[]", flag = "\"X\"")}
        ]}]
        """
        val prices = decode(json).game("game")

        assertNotNull(prices)
        val historicalLows = prices.deals.map { deal -> deal.isHistoricalLow }
        assertEquals(listOf(true, true, false, false), historicalLows)
        assertEquals("X", prices.deals.last().flag)
    }

    /**
     * 没有报价也没有全史最低价才算空；只要有全史最低价就不算。
     * Empty means no offers and no all-time low; an all-time low alone is enough to be non-empty.
     */
    @Test
    fun emptyNeedsNoOffersAndNoHistory() {
        val nothing = """
        [{"id":"game","historyLow":{"all":null,"y1":null,"m3":null},"deals":[]}]
        """
        val historyOnly = """
        [{"id":"game","historyLow":{"all":{"amount":1.0,"amountInt":100,"currency":"AUD"},"y1":null,"m3":null},"deals":[]}]
        """

        val emptyPrices = decode(nothing).game("game")
        assertNotNull(emptyPrices)
        assertTrue(emptyPrices.isEmpty)
        assertNull(emptyPrices.bestDeal)
        assertNull(emptyPrices.bestSteamDeal)

        val historyPrices = decode(historyOnly).game("game")
        assertNotNull(historyPrices)
        assertFalse(historyPrices.isEmpty)
    }

    /**
     * 拼一条手写的报价，只有商店、现价、DRM、标记会变。
     * Builds one hand-written offer where only the shop, the price, the DRM and the flag vary.
     */
    private fun dealJson(shopID: Int, price: Int, drm: String, flag: String?): String {
        val flagJson = flag ?: "null"
        val priceJson = """{"amount":${price / 100.0},"amountInt":$price,"currency":"AUD"}"""
        val deal = """{"shop":{"id":$shopID,"name":"Shop $shopID"},"price":$priceJson,"regular":$priceJson,"cut":0,""" +
            """"voucher":null,"storeLow":null,"flag":$flagJson,"drm":$drm,"platforms":[],""" +
            """"timestamp":"2026-11-26T00:00:00+00:00","expiry":null,"url":"https://itad.link/$shopID/"}"""
        return deal
    }

    private companion object {

        const val ELDEN_RING = "018d937f-590c-728b-ac35-38bcff85f086"

        /**
         * 2026-11-26 请求 `prices/v3?country=AU&deals=false`（ELDEN RING）的真实响应。
         * 10 条报价只留 3 条：最便宜的 GameBillet、Steam 自己（DRM 是空列表）、最贵的 JoyBuggy，顺序照原样。
         * A real response to `prices/v3?country=AU&deals=false` (ELDEN RING) on 2026-11-26. Of its 10 offers only 3
         * are kept, in their original order: the cheapest (GameBillet), Steam itself (with an empty DRM list) and the
         * dearest (JoyBuggy).
         */
        const val ELDEN_RING_RESPONSE = """
        [{"id":"018d937f-590c-728b-ac35-38bcff85f086","historyLow":{"all":{"amount":45.53,"amountInt":4553,"currency":"AUD"},"y1":{"amount":48.38,"amountInt":4838,"currency":"AUD"},"m3":{"amount":73.2,"amountInt":7320,"currency":"AUD"}},"deals":[{"shop":{"id":20,"name":"GameBillet"},"price":{"amount":80.01,"amountInt":8001,"currency":"AUD"},"regular":{"amount":91.09,"amountInt":9109,"currency":"AUD"},"cut":12,"voucher":null,"storeLow":{"amount":45.53,"amountInt":4553,"currency":"AUD"},"flag":null,"drm":[{"id":61,"name":"Steam"}],"platforms":[{"id":1,"name":"Windows"}],"timestamp":"2026-11-07T14:21:31+01:00","expiry":null,"url":"https://itad.link/01a15605-496a-708d-bc81-602e9c76ee70/?app=fxbadj"},{"shop":{"id":61,"name":"Steam"},"price":{"amount":89.95,"amountInt":8995,"currency":"AUD"},"regular":{"amount":89.95,"amountInt":8995,"currency":"AUD"},"cut":0,"voucher":null,"storeLow":{"amount":53.97,"amountInt":5397,"currency":"AUD"},"flag":null,"drm":[],"platforms":[{"id":1,"name":"Windows"}],"timestamp":"2026-03-26T19:05:41+01:00","expiry":null,"url":"https://itad.link/018d9386-5633-7086-a2fa-f903c6afc45b/?app=fxbadj"},{"shop":{"id":65,"name":"JoyBuggy"},"price":{"amount":99.09,"amountInt":9909,"currency":"AUD"},"regular":{"amount":99.09,"amountInt":9909,"currency":"AUD"},"cut":0,"voucher":null,"storeLow":{"amount":47.95,"amountInt":4795,"currency":"AUD"},"flag":null,"drm":[{"id":61,"name":"Steam"}],"platforms":[],"timestamp":"2026-11-20T14:28:09+01:00","expiry":null,"url":"https://itad.link/01a1f922-e060-703b-b663-c7bd67538f41/?app=fxbadj"}]}]
        """
    }
}
