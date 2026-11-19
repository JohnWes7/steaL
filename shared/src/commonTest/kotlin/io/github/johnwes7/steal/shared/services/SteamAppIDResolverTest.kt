package io.github.johnwes7.steal.shared.services

import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [SteamAppIDResolver] 的挑选规则测试：一个假引擎同时扮演 ITAD 和 Steam，按请求的主机分别回答。
 * Tests for [SteamAppIDResolver]'s picking rules: one mock engine plays both ITAD and Steam, answering by host.
 */
class SteamAppIDResolverTest {

    /**
     * 一次测试里的假服务器：记下 Steam 收到的请求，并按设好的内容回答。
     * The fake servers for one test: records what Steam received and answers with the configured bodies.
     */
    private class FakeServers(
        val lookupBody: String,
        val lookupStatus: HttpStatusCode = HttpStatusCode.OK,
        val steamBody: String = "{}",
        val steamStatus: HttpStatusCode = HttpStatusCode.OK,
    ) {
        /** 发给 ITAD 的请求数 / Requests sent to ITAD. */
        var itadRequestCount = 0

        /** 发给 Steam 的请求里的 `appids` 参数 / The `appids` parameter of each request sent to Steam. */
        val steamAppIDParameters = mutableListOf<String?>()

        /**
         * 建一个连着这两个假服务器的 [SteamAppIDResolver]。
         * Builds a [SteamAppIDResolver] wired to these two fake servers.
         */
        fun makeResolver(): SteamAppIDResolver {
            val engine = MockEngine { request ->
                if (request.url.host == "api.isthereanydeal.com") {
                    itadRequestCount += 1
                    respond(content = lookupBody, status = lookupStatus, headers = JSON_HEADERS)
                } else {
                    steamAppIDParameters.add(request.url.parameters["appids"])
                    respond(content = steamBody, status = steamStatus, headers = JSON_HEADERS)
                }
            }
            val client = HttpClient(engine) {
                installAppPlugins(logsRequests = false)
            }
            val itadApi = ItadApi(client = client, apiKey = "test-key")
            val steamApi = SteamApi(client = client)
            val resolver = SteamAppIDResolver(itadApi = itadApi, steamApi = steamApi)
            return resolver
        }
    }

    /**
     * 只有一个候选的游戏直接用，不去问 Steam。
     * A game with a single candidate is used directly, without asking Steam.
     */
    @Test
    fun singleCandidateSkipsSteam() = runTest {
        val servers = FakeServers(lookupBody = """{"single":["sub/1","app/100"]}""")

        val result = servers.makeResolver().resolveAppIDs(listOf("single"), AppRegion.Australia)

        assertEquals(mapOf("single" to 100), result)
        assertTrue(servers.steamAppIDParameters.isEmpty())
    }

    /**
     * Monster Hunter: World：三个候选里挑中有价格的 582010；只有多候选游戏的 appID 发给 Steam。
     * Monster Hunter: World: the priced 582010 is picked among three candidates; only the multi-candidate game's
     * appIDs go to Steam.
     */
    @Test
    fun multipleCandidatesPickThePricedOne() = runTest {
        val servers = FakeServers(
            lookupBody = """{"single":["app/100"],"mhw":["app/601100","app/743980","sub/149309","app/582010"]}""",
            steamBody = MHW_AVAILABILITY,
        )

        val result = servers.makeResolver().resolveAppIDs(listOf("single", "mhw"), AppRegion.Australia)

        assertEquals(mapOf("single" to 100, "mhw" to 582010), result)
        assertEquals(listOf<String?>("601100,743980,582010"), servers.steamAppIDParameters)
    }

    /**
     * 候选都没有价格时，取第一个还存在的（跳过已不存在的）。
     * When no candidate has a price, the first one that still exists is taken (skipping ones that are gone).
     */
    @Test
    fun noPriceFallsBackToFirstAvailable() = runTest {
        val servers = FakeServers(
            lookupBody = """{"game":["app/1","app/2","app/3"]}""",
            steamBody = """{"1":{"success":false},"2":{"success":true,"data":[]},"3":{"success":true,"data":[]}}""",
        )

        val result = servers.makeResolver().resolveAppIDs(listOf("game"), AppRegion.Australia)

        assertEquals(mapOf("game" to 2), result)
    }

    /**
     * 候选都不存在、或 ITAD 没收录的游戏，都不在结果里。
     * Games whose candidates are all gone, or that ITAD does not track, are left out of the result.
     */
    @Test
    fun noUsableCandidateIsLeftOut() = runTest {
        val servers = FakeServers(
            lookupBody = """{"gone":["app/1","app/2"],"untracked":null}""",
            steamBody = """{"1":{"success":false},"2":{"success":false}}""",
        )

        val result = servers.makeResolver().resolveAppIDs(listOf("gone", "untracked"), AppRegion.Australia)

        assertTrue(result.isEmpty())
    }

    /**
     * Steam 校验失败时不抛错，退回第一个候选。
     * When the Steam check fails, nothing is thrown and the first candidate is used.
     */
    @Test
    fun steamFailureFallsBackToFirstCandidate() = runTest {
        val servers = FakeServers(
            lookupBody = """{"mhw":["app/601100","app/743980","app/582010"]}""",
            steamBody = "Internal Server Error",
            steamStatus = HttpStatusCode.InternalServerError,
        )

        val result = servers.makeResolver().resolveAppIDs(listOf("mhw"), AppRegion.Australia)

        assertEquals(mapOf("mhw" to 601100), result)
    }

    /**
     * ITAD 的 lookup 失败时抛出原异常，由调用方把整批标成失败。
     * When the ITAD lookup fails, its original exception is thrown for the caller to mark the batch as failed.
     */
    @Test
    fun itadFailureIsThrown() = runTest {
        val servers = FakeServers(lookupBody = """{"status_code":403}""", lookupStatus = HttpStatusCode.Forbidden)

        val error = assertFailsWith<ItadApiException> {
            servers.makeResolver().resolveAppIDs(listOf("game"), AppRegion.Australia)
        }

        assertEquals(403, error.statusCode)
    }

    /**
     * 空列表一个请求都不发。
     * An empty list sends no request at all.
     */
    @Test
    fun emptyListSendsNothing() = runTest {
        val servers = FakeServers(lookupBody = "{}")

        val result = servers.makeResolver().resolveAppIDs(emptyList(), AppRegion.Australia)

        assertTrue(result.isEmpty())
        assertEquals(0, servers.itadRequestCount)
        assertTrue(servers.steamAppIDParameters.isEmpty())
    }

    private companion object {

        /**
         * Monster Hunter: World 三个候选的真实 Steam 响应（2026-11-19）。
         * The real Steam response for Monster Hunter: World's three candidates (2026-11-19).
         */
        const val MHW_AVAILABILITY =
            """{"601100":{"success":false},"743980":{"success":false},"582010":{"success":true,"data":{"price_overview":{"currency":"AUD","initial":4494,"final":4494,"discount_percent":0,"initial_formatted":"","final_formatted":"A$ 44.94"}}}}"""

        /** JSON 响应头 / The JSON response header. */
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
