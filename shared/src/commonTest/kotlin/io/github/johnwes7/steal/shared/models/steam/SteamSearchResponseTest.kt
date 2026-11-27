package io.github.johnwes7.steal.shared.models.steam

import io.github.johnwes7.steal.shared.services.appNetworkJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `storesearch` 响应的解码测试。
 * Decoding tests for the `storesearch` response.
 */
class SteamSearchResponseTest {

    /**
     * 真实响应解出 10 条，顺序、名字、appID 和横图地址都对。
     * The real response decodes into 10 items with the right order, names, appIDs and image URLs.
     */
    @Test
    fun decodesRealResponse() {
        val response = appNetworkJson.decodeFromString<SteamSearchResponse>(REAL_RESPONSE)

        assertEquals(10, response.total)
        assertEquals(10, response.items.size)

        val first = response.items[0]
        assertEquals("app", first.type)
        assertEquals("Counter-Strike 2", first.name)
        assertEquals(730, first.id)
        assertEquals(
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/730/942c04efaa5bc87afb6f2a97dbf17ac614c8a84d/capsule_231x87.jpg?t=1793054556",
            first.tinyImage,
        )
    }

    /**
     * 有价格的条目解出货币和金额；免费游戏（Counter-Strike 2）和原声没有 `price`，解成 `null`。
     * Priced items decode their currency and amounts; a free game (Counter-Strike 2) and a soundtrack have no
     * `price` and decode as `null`.
     */
    @Test
    fun priceIsOptional() {
        val response = appNetworkJson.decodeFromString<SteamSearchResponse>(REAL_RESPONSE)

        val counterStrike = response.items[1]
        val price = counterStrike.price
        assertNotNull(price)
        assertEquals("AUD", price.currency)
        assertEquals(1415, price.initial)
        assertEquals(1415, price.final)

        val counterStrike2 = response.items[0]
        assertNull(counterStrike2.price)
        val soundtrack = response.items[2]
        assertNull(soundtrack.price)
    }

    /**
     * 没搜到时 `items` 是空列表，不是错误。
     * No match gives an empty `items` list, not an error.
     */
    @Test
    fun decodesEmptyResult() {
        val response = appNetworkJson.decodeFromString<SteamSearchResponse>("""{"total":0,"items":[]}""")

        assertEquals(0, response.total)
        assertTrue(response.items.isEmpty())
    }

    private companion object {

        /**
         * 2026-11-27 请求 `storesearch?term=counter-strike&cc=AU&l=english` 的真实响应。
         * A real response to `storesearch?term=counter-strike&cc=AU&l=english` on 2026-11-27.
         */
        const val REAL_RESPONSE = """
        {"total":10,"items":[{"type":"app","name":"Counter-Strike 2","id":730,"tiny_image":"https:\/\/shared.akamai.steamstatic.com\/store_item_assets\/steam\/apps\/730\/942c04efaa5bc87afb6f2a97dbf17ac614c8a84d\/capsule_231x87.jpg?t=1793054556","metascore":"","platforms":{"windows":true,"mac":false,"linux":true},"streamingvideo":false},{"type":"app","name":"Counter-Strike","id":10,"price":{"currency":"AUD","initial":1415,"final":1415},"tiny_image":"https:\/\/shared.akamai.steamstatic.com\/store_item_assets\/steam\/apps\/10\/capsule_231x87.jpg?t=1745368572","metascore":"88","platforms":{"windows":true,"mac":true,"linux":true},"streamingvideo":false},{"type":"app","name":"Counter-Strike 2 Soundtrack","id":2678630,"tiny_image":"https:\/\/shared.akamai.steamstatic.com\/store_item_assets\/steam\/apps\/2678630\/capsule_231x87.jpg?t=1788971306","metascore":"","platforms":{"windows":true,"mac":false,"linux":true},"streamingvideo":false},{"type":"app","name":"Counter-Strike: Source","id":240,"price":{"currency":"AUD","initial":1415,"final":1415},"tiny_image":"https:\/\/shared.akamai.steamstatic.com\/store_item_assets\/steam\/apps\/240\/capsule_231x87.jpg?t=1745368575","metascore":"88","platforms":{"windows":true,"mac":false,"linux":true},"streamingvideo":false},{"type":"app","name":"Counter-Strike: Condition Zero","id":80,"price":{"currency":"AUD","initial":1415,"final":1415},"tiny_image":"https:\/\/shared.akamai.steamstatic.com\/store_item_assets\/steam\/apps\/80\/capsule_231x87.jpg?t=1745368574","metascore":"65","platforms":{"windows":true,"mac":true,"linux":true},"streamingvideo":false},{"type":"app","name":"CS Manager","id":3418500,"price":{"currency":"AUD","initial":3495,"final":3495},"tiny_image":"https:\/\/shared.akamai.steamstatic.com\/store_item_assets\/steam\/apps\/3418500\/a70a25c69ebbc000a8e78a18171664d2f3501c52\/capsule_231x87.jpg?t=1794522647","metascore":"","platforms":{"windows":true,"mac":false,"linux":false},"streamingvideo":false},{"type":"app","name":"CS2D","id":666220,"tiny_image":"https:\/\/shared.akamai.steamstatic.com\/store_item_assets\/steam\/apps\/666220\/capsule_231x87.jpg?t=1787393976","metascore":"","platforms":{"windows":true,"mac":false,"linux":true},"streamingvideo":false},{"type":"app","name":"CS:GO Player Profiles","id":413850,"tiny_image":"https:\/\/shared.akamai.steamstatic.com\/store_item_assets\/steam\/apps\/413850\/capsule_231x87.jpg?t=1485215623","metascore":"","platforms":{"windows":true,"mac":true,"linux":true},"streamingvideo":true,"controller_support":"full"},{"type":"app","name":"CS Pipas: Legend","id":3715230,"tiny_image":"https:\/\/shared.akamai.steamstatic.com\/store_item_assets\/steam\/apps\/3715230\/664419eecb0b349b552c6a58bc4d4b7e8179163a\/capsule_231x87.jpg?t=1778318461","metascore":"","platforms":{"windows":true,"mac":false,"linux":false},"streamingvideo":false},{"type":"app","name":"CS Pipas: Legend Demo","id":4423450,"tiny_image":"https:\/\/shared.akamai.steamstatic.com\/store_item_assets\/steam\/apps\/4423450\/ca6289b33f3762775b2fb3948ae9e784cef81606\/capsule_231x87.jpg?t=1771813666","metascore":"","platforms":{"windows":true,"mac":false,"linux":false},"streamingvideo":false}]}
        """
    }
}
