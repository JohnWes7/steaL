package io.github.johnwes7.steal.shared.models.app

import io.github.johnwes7.steal.shared.models.itad.ItadDeal

/**
 * 首页列表里的一条：ITAD 的 deal，加上从 Steam 那边补出来的 appID。
 * One row of the home feed: the ITAD deal plus the Steam appID resolved for it.
 *
 * 两个属性都是 `val`，改关联状态用 `copy(steam = …)` 生成新的一条。Swift 的 struct 是值类型，改一个字段就是换了一个新值；
 * Kotlin 的 data class 是引用类型，原地改的话 `StateFlow` 拿新旧值比较时是同一个对象，界面不会更新。
 * Both properties are `val`; changing the link state makes a new row with `copy(steam = …)`. A Swift struct is a
 * value type, so changing a field yields a new value; a Kotlin data class is a reference type, and mutating it in
 * place would leave `StateFlow` comparing an object with itself, so the UI would not update.
 *
 * [deal] 是 ITAD 的原始条目，界面渲染用的都是它；[steam] 是 Steam 关联状态，刚建出来时还在查。
 * [deal] is the untouched ITAD entry that everything on screen comes from; [steam] is the Steam link state,
 * still resolving when the row is first made.
 */
data class HomeDealItem(
    val deal: ItadDeal,
    val steam: SteamLink = SteamLink.Pending,
) {

    /**
     * 列表主键：ITAD 游戏 UUID。
     * The list identity: the ITAD game UUID.
     */
    val itadUUID: String
        get() = deal.id

    /**
     * 可用的 Steam appID，还没查到或查不到就是 `null`。
     * The Steam appID when there is one, or `null` while resolving or when there is none.
     */
    val steamAppID: Int?
        get() = when (steam) {
            is SteamLink.Resolved -> steam.appID
            else -> null
        }

    /**
     * 这一条在 Steam 上的关联情况，四种互斥。
     * How this row maps onto Steam; the four cases are mutually exclusive.
     */
    sealed interface SteamLink {

        /**
         * 还没查完。
         * Still resolving.
         */
        data object Pending : SteamLink

        /**
         * 查到了 [appID]，可以进详情页。
         * Resolved to [appID]; the detail page can open.
         */
        data class Resolved(val appID: Int) : SteamLink

        /**
         * 查过了，但 ITAD 没有对应的 Steam app；重试也不会变。
         * Looked up, but ITAD has no Steam app for this game; a retry would not help.
         */
        data object NotFound : SteamLink

        /**
         * lookup 请求失败，下拉刷新会重查。
         * The lookup request failed; pull to refresh looks it up again.
         */
        data object Failed : SteamLink
    }
}
