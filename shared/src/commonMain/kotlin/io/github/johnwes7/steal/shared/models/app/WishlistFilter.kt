package io.github.johnwes7.steal.shared.models.app

/**
 * 愿望单顶部的筛选项。
 * The filters at the top of the wishlist.
 *
 * 打折、史低看每行查到的价格，未发售看存着的 `isComingSoon`（会向 Steam 核对）；界面文字由界面层按这个值取。
 * On sale and all-time low come from each row's fetched price, upcoming from the stored `isComingSoon` (checked
 * against Steam); the UI picks its own label for each value.
 */
enum class WishlistFilter {

    /**
     * 全部收藏。
     * Every saved game.
     */
    All,

    /**
     * 正在打折。
     * On sale right now.
     */
    OnSale,

    /**
     * 现价是史低（ITAD 标记为 H / N，含没打折的）。
     * The current price is the all-time low (ITAD flags it H / N, undiscounted ones included).
     */
    AllTimeLow,

    /**
     * 还没发售。
     * Not released yet.
     */
    Upcoming,
}
