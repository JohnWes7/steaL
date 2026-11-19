package io.github.johnwes7.steal.shared.models.steam

import io.github.johnwes7.steal.shared.services.appNetworkJson
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 批量 `appdetails`（`filters=price_overview`）响应的解码测试。
 * Decoding tests for the batched `appdetails` (`filters=price_overview`) response.
 */
class SteamAppAvailabilityTest {

    /**
     * 照 Service 的做法解码：先解成字典，再包进 [SteamAppAvailabilityResponse]。
     * Decodes the way the service will: into a dictionary first, then wrapped in [SteamAppAvailabilityResponse].
     */
    private fun decode(json: String): SteamAppAvailabilityResponse {
        val entries = appNetworkJson.decodeFromString<Map<String, SteamAppAvailabilityEntry>>(json)
        val response = SteamAppAvailabilityResponse(entries)
        return response
    }

    /**
     * Monster Hunter: World 的三个候选：前两个已不存在，只有 582010 存在且有价格。
     * Monster Hunter: World's three candidates: the first two are gone, and only 582010 exists with a price.
     */
    @Test
    fun picksOutTheLiveCandidate() {
        val response = decode(REAL_RESPONSE)

        assertFalse(response.isAvailable(601100))
        assertFalse(response.hasPrice(601100))
        assertFalse(response.isAvailable(743980))
        assertTrue(response.isAvailable(582010))
        assertTrue(response.hasPrice(582010))
    }

    /**
     * 免费游戏（Dota 2）和 demo（FORM 的 408530）存在但 `data` 是 `[]`，算没有价格。
     * A free game (Dota 2) and a demo (FORM's 408530) exist but their `data` is `[]`, so they have no price.
     */
    @Test
    fun emptyArrayDataMeansNoPrice() {
        val response = decode(REAL_RESPONSE)

        assertTrue(response.isAvailable(570))
        assertFalse(response.hasPrice(570))
        assertTrue(response.isAvailable(408530))
        assertFalse(response.hasPrice(408530))
    }

    /**
     * 响应里没有的 appID 既不存在也没有价格。
     * An appID missing from the response neither exists nor has a price.
     */
    @Test
    fun missingAppIDIsUnavailable() {
        val response = decode(REAL_RESPONSE)

        assertFalse(response.isAvailable(1))
        assertFalse(response.hasPrice(1))
    }

    private companion object {

        /**
         * 2026-11-19 请求 `appids=601100,743980,582010,570,408530&cc=AU&filters=price_overview` 的真实响应。
         * A real response to `appids=601100,743980,582010,570,408530&cc=AU&filters=price_overview` on 2026-11-19.
         */
        const val REAL_RESPONSE = """
        {"601100":{"success":false},"743980":{"success":false},"582010":{"success":true,"data":{"price_overview":{"currency":"AUD","initial":4494,"final":4494,"discount_percent":0,"initial_formatted":"","final_formatted":"A$ 44.94"}}},"570":{"success":true,"data":[]},"408530":{"success":true,"data":[]}}
        """
    }
}
