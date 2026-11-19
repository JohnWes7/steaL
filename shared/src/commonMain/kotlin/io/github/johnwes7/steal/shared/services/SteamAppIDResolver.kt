package io.github.johnwes7.steal.shared.services

import io.github.johnwes7.steal.shared.config.AppLog
import io.github.johnwes7.steal.shared.models.settings.AppRegion
import io.github.johnwes7.steal.shared.models.steam.SteamAppAvailabilityResponse
import kotlin.coroutines.cancellation.CancellationException

/**
 * 把一批 ITAD 游戏 UUID 换成 Steam appID，供首页与 Hot Deals 列表共用。
 * Resolves a batch of ITAD game UUIDs into Steam appIDs, shared by the home page and the Hot Deals list.
 *
 * ITAD 只给候选，一个游戏可能有多个 `app/`，第一个不一定还存在，所以多候选时要再向 Steam 校验一次。
 * 这件事只依赖 [itadApi] 与 [steamApi]、不依赖页面状态，所以放 Service；写回列表状态由各自的 ViewModel 做。
 * ITAD only offers candidates: a game may have several `app/` entries and the first may be gone, so several
 * candidates are checked with Steam. This depends only on [itadApi] and [steamApi], not on page state, so it
 * lives in a service; each view model writes the result back into its own list.
 */
class SteamAppIDResolver(
    private val itadApi: ItadApi,
    private val steamApi: SteamApi,
) {

    /**
     * 批量查出 [uuids] 里每个游戏的 Steam appID，返回 UUID → appID；不在结果里就是查了没有。
     * Looks up the Steam appID of every game in [uuids], returning UUID to appID; a game missing from the result
     * has none.
     *
     * 只把多候选游戏的 appID 合成一次请求拿去 Steam 校验（按 [region]），一个都没有就不发；校验失败不抛错，退回第一个候选。
     * ITAD 的 lookup 请求失败时抛出原异常（`ItadApiException` 或 Ktor 的网络异常），由调用方把整批标成失败。
     * Only appIDs from games with several candidates go to Steam, in one request (for [region]), and none is sent
     * when there are none; a failed check does not throw and falls back to the first candidate. When the ITAD
     * lookup fails, its original exception (`ItadApiException` or a Ktor network exception) is thrown, and the
     * caller marks the whole batch as failed.
     */
    suspend fun resolveAppIDs(uuids: List<String>, region: AppRegion): Map<String, Int> {
        if (uuids.isEmpty()) {
            return emptyMap()
        }

        val itadShopIDResp = itadApi.lookupShopIDs(uuids)

        // 1. 每个游戏的候选 appID，可能 0 个、1 个或多个。
        // 1. Each game's candidate appIDs: zero, one or several.
        val candidatesByUUID = uuids.associateWith { uuid -> itadShopIDResp.appIDs(uuid) }

        // 2. 只把多候选游戏的 appID 拿去 Steam 校验。
        // 2. Only appIDs from games with several candidates go to Steam.
        val multiCandidateLists = candidatesByUUID.values.filter { candidates -> candidates.size > 1 }
        val appIDsToCheckOnSteam = multiCandidateLists.flatten()

        var steamAvailabilityRespon: SteamAppAvailabilityResponse? = null
        if (appIDsToCheckOnSteam.isNotEmpty()) {
            steamAvailabilityRespon = fetchAvailability(appIDsToCheckOnSteam, region)
        }

        // 3. 每个游戏定一个 appID，选不出的不放进结果。
        // 3. Pick one appID per game; games without one are left out.
        val appIDByUUID = mutableMapOf<String, Int>()
        for ((uuid, candidates) in candidatesByUUID) {
            val appID = pickAppID(candidates, steamAvailabilityRespon)
            if (appID != null) {
                appIDByUUID[uuid] = appID
            }
        }

        AppLog.itad.d { "Steam appID 解析 / resolve: ${itadShopIDResp.trackedCount}/${uuids.size} 个被 ITAD 收录 / tracked by ITAD" }
        return appIDByUUID
    }

    /**
     * 向 Steam 校验候选 [appIDs]，请求失败返回 `null`。
     * Checks the candidate [appIDs] with Steam, returning `null` when the request fails.
     *
     * 失败不往外抛：校验只是为了在多个候选里挑对的，挑不了就退回第一个候选。取消不是失败，要原样抛出去。
     * A failure is not thrown: the check only helps pick among candidates, and without it the first candidate is
     * used. Cancellation is not a failure and is rethrown as-is.
     */
    private suspend fun fetchAvailability(appIDs: List<Int>, region: AppRegion): SteamAppAvailabilityResponse? {
        try {
            val availability = steamApi.fetchAvailability(appIDs = appIDs, region = region)
            return availability
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.steam.e(error) { "appID 校验失败，退回第一个候选 / check failed, using first candidate" }
            return null
        }
    }

    /**
     * 从 [candidates] 里挑一个 appID，`null` 表示选不出。
     * Picks one appID from [candidates], or `null` when none fits.
     *
     * 先挑有价格的：调用方都是在打折的 deal 列表，对的那个一定有价格；只看「存在」会选到 demo 或无价格的旧页。
     * [availability] 为 `null` 表示没校验或校验失败。
     * The first priced one wins: every caller lists deals on sale, so the right one always has a price; checking
     * only existence picks a demo or an old unpriced page. A `null` [availability] means unchecked or failed.
     */
    private fun pickAppID(candidates: List<Int>, availability: SteamAppAvailabilityResponse?): Int? {
        // 没有候选：ITAD 没收录，或只有 `sub/`。
        // No candidates: untracked, or only `sub/` entries.
        if (candidates.isEmpty()) {
            return null
        }

        // 只有一个：直接用，它没被拿去 Steam 校验。
        // A single candidate is used as-is; it was never sent to Steam.
        if (candidates.size == 1) {
            return candidates[0]
        }

        // 多个但校验失败：退回第一个。
        // Several but the check failed: the first one.
        if (availability == null) {
            return candidates[0]
        }

        // 多个：先取第一个有价格的。
        // Several: the first priced one.
        val pricedAppID = candidates.firstOrNull { appID -> availability.hasPrice(appID) }
        if (pricedAppID != null) {
            return pricedAppID
        }

        // 都没价格：取第一个还存在的；都不存在就是 null，点进去也只会报错。
        // None priced: the first one that still exists; null when none exists, since the detail page would only fail.
        val availableAppID = candidates.firstOrNull { appID -> availability.isAvailable(appID) }
        return availableAppID
    }
}
