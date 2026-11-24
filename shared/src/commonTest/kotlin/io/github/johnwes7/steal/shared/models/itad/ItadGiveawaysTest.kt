package io.github.johnwes7.steal.shared.models.itad

import io.github.johnwes7.steal.shared.services.appNetworkJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * giveaways 接口响应 DTO 的解码、派生属性与倒计时规则测试。
 * Tests for the giveaways endpoint's response DTOs: decoding, derived properties and the countdown rules.
 */
class ItadGiveawaysTest {

    // MARK: - 解码 / Decoding

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

    // MARK: - 派生属性 / Derived properties

    /**
     * 真实样例：标题用游戏名，缩略图取 banner300，截止时间带上 `+01:00` 偏移换算成同一时刻。
     * The real sample: the title is the game name, the thumbnail is banner300, and the deadline's `+01:00` offset
     * resolves to the same instant.
     */
    @Test
    fun derivesDisplayFieldsFromRealResponse() {
        val giveaways = appNetworkJson.decodeFromString<List<ItadGiveaway>>(REAL_RESPONSE)
        val epic = giveaways[0]
        val indieGala = giveaways[1]

        assertEquals("Knightica", epic.displayTitle)
        assertEquals(epic.games[0].assets.banner300, epic.thumbnailUrl)
        assertEquals(Instant.parse("2026-11-19T16:00:00Z"), epic.expiryInstant)

        assertEquals("Die Young: Prologue", indieGala.displayTitle)
        assertNull(indieGala.expiryInstant)
    }

    /**
     * 没有游戏时，标题退回 giveaway 自己的标题，没有缩略图。
     * Without games, the title falls back to the giveaway's own title and there is no thumbnail.
     */
    @Test
    fun fallsBackWithoutGames() {
        val withoutGames = knightica().copy(games = emptyList())

        assertEquals("Knightica - FREE on Epic Games", withoutGames.displayTitle)
        assertNull(withoutGames.thumbnailUrl)
    }

    /**
     * 缩略图缺小横幅时按顺序往后退，竖版封面排最后；一张图都没有是 `null`。
     * The thumbnail falls back in order when small banners are missing, box art last; `null` without any image.
     */
    @Test
    fun thumbnailFallsBackInOrder() {
        val epic = knightica()
        val fullAssets = epic.games[0].assets
        val withoutBanner300 = fullAssets.copy(banner300 = null)
        val boxartOnly = ItadAssets(
            boxart = "https://example.com/boxart.jpg",
            banner145 = null,
            banner300 = null,
            banner400 = null,
            banner600 = null,
        )
        val noImages = boxartOnly.copy(boxart = null)

        assertEquals(fullAssets.banner400, epic.withAssets(withoutBanner300).thumbnailUrl)
        assertEquals("https://example.com/boxart.jpg", epic.withAssets(boxartOnly).thumbnailUrl)
        assertNull(epic.withAssets(noImages).thumbnailUrl)
    }

    /**
     * 解析不了的截止时间当作没有：不崩，也不显示倒计时。
     * An unparsable deadline counts as missing: no crash and no countdown.
     */
    @Test
    fun unparsableExpiryIsTreatedAsMissing() {
        val giveaway = knightica().copy(expiry = "next Tuesday")
        val now = Instant.parse("2026-11-19T00:00:00Z")

        assertNull(giveaway.expiryInstant)
        assertNull(giveaway.deadline(now = now))
        assertFalse(giveaway.isExpiringSoon(now = now))
    }

    // MARK: - 倒计时 / Countdown

    /**
     * 不到一整天、已经过期都是 Today。
     * Less than a full day left, or already past, is Today.
     */
    @Test
    fun deadlineIsTodayWithinAFullDay() {
        val giveaway = knightica()
        val twentyThreeHoursLeft = giveaway.nowWithTimeLeft(23.hours)
        val anHourPast = giveaway.nowWithTimeLeft((-1).hours)

        assertEquals(ItadGiveaway.Deadline.Today, giveaway.deadline(now = twentyThreeHoursLeft))
        assertEquals(ItadGiveaway.Deadline.Today, giveaway.deadline(now = anHourPast))
    }

    /**
     * 天数按整 24 小时往下取，最多 7 天。
     * Days count whole 24-hour spans, rounded down, up to 7.
     */
    @Test
    fun deadlineCountsWholeDaysUpToAWeek() {
        val giveaway = knightica()
        val oneDayAndAnHourLeft = giveaway.nowWithTimeLeft(25.hours)
        val exactlyTwoDaysLeft = giveaway.nowWithTimeLeft(48.hours)
        val almostEightDaysLeft = giveaway.nowWithTimeLeft(7.days + 23.hours)

        assertEquals(ItadGiveaway.Deadline.DaysLeft(days = 1), giveaway.deadline(now = oneDayAndAnHourLeft))
        assertEquals(ItadGiveaway.Deadline.DaysLeft(days = 2), giveaway.deadline(now = exactlyTwoDaysLeft))
        assertEquals(ItadGiveaway.Deadline.DaysLeft(days = 7), giveaway.deadline(now = almostEightDaysLeft))
    }

    /**
     * 满 8 天显示截止日期。
     * Eight full days or more shows the deadline's date.
     */
    @Test
    fun deadlineShowsDateBeyondAWeek() {
        val giveaway = knightica()
        val expiry = assertNotNull(giveaway.expiryInstant)
        val eightDaysLeft = giveaway.nowWithTimeLeft(8.days)

        assertEquals(ItadGiveaway.Deadline.OnDate(expiry = expiry), giveaway.deadline(now = eightDaysLeft))
    }

    /**
     * 48 小时以内（含正好 48 小时、已过期）算快截止，多一分钟就不算。
     * Within 48 hours (including exactly 48 hours and already past) is expiring soon; a minute more is not.
     */
    @Test
    fun expiringSoonWithin48Hours() {
        val giveaway = knightica()
        val anHourPast = giveaway.nowWithTimeLeft((-1).hours)
        val twentyThreeHoursLeft = giveaway.nowWithTimeLeft(23.hours)
        val exactly48HoursLeft = giveaway.nowWithTimeLeft(48.hours)
        val justOver48HoursLeft = giveaway.nowWithTimeLeft(48.hours + 1.minutes)
        val eightDaysLeft = giveaway.nowWithTimeLeft(8.days)

        assertTrue(giveaway.isExpiringSoon(now = anHourPast))
        assertTrue(giveaway.isExpiringSoon(now = twentyThreeHoursLeft))
        assertTrue(giveaway.isExpiringSoon(now = exactly48HoursLeft))
        assertFalse(giveaway.isExpiringSoon(now = justOver48HoursLeft))
        assertFalse(giveaway.isExpiringSoon(now = eightDaysLeft))
    }

    /**
     * 没有截止时间：不显示倒计时，也不算快截止。
     * No deadline: no countdown, and never expiring soon.
     */
    @Test
    fun noDeadlineWithoutExpiry() {
        val giveaways = appNetworkJson.decodeFromString<List<ItadGiveaway>>(REAL_RESPONSE)
        val indieGala = giveaways[1]
        val now = Instant.parse("2026-11-19T00:00:00Z")

        assertNull(indieGala.deadline(now = now))
        assertFalse(indieGala.isExpiringSoon(now = now))
    }

    // MARK: - 辅助 / Helpers

    /**
     * 真实样例里的 Epic 那条（有截止时间、有图）。
     * The Epic entry from the real sample, which has a deadline and images.
     */
    private fun knightica(): ItadGiveaway {
        val giveaways = appNetworkJson.decodeFromString<List<ItadGiveaway>>(REAL_RESPONSE)
        return giveaways[0]
    }

    /**
     * 把第一款游戏的图换成 [assets]。
     * Replaces the first game's images with [assets].
     */
    private fun ItadGiveaway.withAssets(assets: ItadAssets): ItadGiveaway {
        val firstGame = games[0].copy(assets = assets)
        return copy(games = listOf(firstGame))
    }

    /**
     * 离截止还剩 [timeLeft] 的那个「现在」；负数表示已经过期。
     * The "now" that leaves [timeLeft] until the deadline; negative means already past.
     */
    private fun ItadGiveaway.nowWithTimeLeft(timeLeft: Duration): Instant {
        val expiry = assertNotNull(expiryInstant)
        return expiry - timeLeft
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
