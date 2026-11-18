package io.github.johnwes7.steal.shared.models.itad

import io.github.johnwes7.steal.shared.services.appJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `lookup/shop/61/id/v1` 响应的解码与取 appID 测试。
 * Decoding and appID extraction tests for the `lookup/shop/61/id/v1` response.
 */
class ItadShopIDsTest {

    /**
     * 照 Service 的做法解码：先解成字典，再包进 [ItadShopIDsResponse]。
     * Decodes the way the service will: into a dictionary first, then wrapped in [ItadShopIDsResponse].
     */
    private fun decode(json: String): ItadShopIDsResponse {
        val entries = appJson.decodeFromString<Map<String, List<String>?>>(json)
        val response = ItadShopIDsResponse(entries)
        return response
    }

    /**
     * `app/` 不在第一位时也能找到（ELDEN RING 排在第三个）。
     * An `app/` entry is found even when it is not first (ELDEN RING's is third).
     */
    @Test
    fun findsAppIDAmongSubs() {
        val response = decode(REAL_RESPONSE)

        assertEquals(listOf(1245620), response.appIDs(ELDEN_RING))
        assertEquals(4, response.shopIDs(ELDEN_RING).size)
    }

    /**
     * 多个 `app/` 全部保留，并保持响应里的顺序（Monster Hunter: World）。
     * Several `app/` entries are all kept, in response order (Monster Hunter: World).
     */
    @Test
    fun keepsEveryAppIDInOrder() {
        val response = decode(REAL_RESPONSE)

        assertEquals(listOf(601100, 743980, 582010), response.appIDs(MONSTER_HUNTER_WORLD))
    }

    /**
     * 没收录（值是 `null`）、根本没查（不在字典里）都返回空列表，不是错误。
     * Untracked (a `null` value) and never asked (not in the dictionary) both give an empty list, not an error.
     */
    @Test
    fun untrackedAndMissingGiveEmptyLists() {
        val response = decode(REAL_RESPONSE)

        assertTrue(response.appIDs(UNKNOWN_UUID).isEmpty())
        assertTrue(response.shopIDs(UNKNOWN_UUID).isEmpty())
        assertTrue(response.appIDs("never-asked").isEmpty())
        assertEquals(2, response.trackedCount)
    }

    /**
     * 只有 `sub/`、或 `app/` 后面不是数字时，都拿不到 appID。
     * No appID comes out when there are only `sub/` entries or an `app/` entry is not a number.
     */
    @Test
    fun ignoresSubsAndMalformedAppIDs() {
        val response = decode(
            """
            { "only-subs": ["sub/1", "sub/2"], "bad-app": ["app/abc", "app/42"], "empty": [] }
            """,
        )

        assertTrue(response.appIDs("only-subs").isEmpty())
        assertEquals(listOf(42), response.appIDs("bad-app"))
        assertEquals(2, response.trackedCount)
    }

    private companion object {

        /** ELDEN RING 的 ITAD UUID / ELDEN RING's ITAD UUID. */
        const val ELDEN_RING = "018d937f-590c-728b-ac35-38bcff85f086"

        /** Monster Hunter: World 的 ITAD UUID / Monster Hunter: World's ITAD UUID. */
        const val MONSTER_HUNTER_WORLD = "018d937e-fd94-7238-a99b-14457502670c"

        /** 编出来的 UUID，ITAD 没收录 / A made-up UUID that ITAD does not track. */
        const val UNKNOWN_UUID = "00000000-0000-0000-0000-000000000000"

        /**
         * 2026-11-18 用上面三个 UUID 请求的真实响应，原样保留 `\/` 转义。
         * A real response to the three UUIDs above on 2026-11-18, keeping the `\/` escapes as sent.
         */
        const val REAL_RESPONSE = """
        {"018d937f-590c-728b-ac35-38bcff85f086":["sub\/432247","sub\/440410","app\/1245620","sub\/440408"],"018d937e-fd94-7238-a99b-14457502670c":["app\/601100","app\/743980","sub\/149309","app\/582010","sub\/281619"],"00000000-0000-0000-0000-000000000000":null}
        """
    }
}
