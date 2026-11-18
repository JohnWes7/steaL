package io.github.johnwes7.steal.shared.models.settings

/**
 * 商店区域：决定 Steam / ITAD 返回的价格、货币与可买的游戏。
 * The store region, which decides the prices, currency and available games Steam and ITAD return.
 *
 * 只放请求要用的 [apiRegionCode]；iOS 上的显示名是界面文字（Android 放 `strings.xml`），货币代码与符号做价格显示时再加。
 * Only [apiRegionCode], which requests need, lives here; iOS's display name is UI text (Android keeps it in
 * `strings.xml`), and the currency code and symbol come with price formatting.
 */
enum class AppRegion(
    /**
     * 拼 Steam / ITAD 请求时用的国家或地区代码，如 `AU`。
     * The country or region code used in Steam and ITAD requests, such as `AU`.
     */
    val apiRegionCode: String,
) {
    Australia(apiRegionCode = "AU"),
    China(apiRegionCode = "CN"),
    UnitedStates(apiRegionCode = "US"),
    UnitedKingdom(apiRegionCode = "GB"),
    Japan(apiRegionCode = "JP"),
    Korea(apiRegionCode = "KR"),

    // EU 不是正式的国家代码，照 iOS 原样搬；Steam / ITAD 怎么处理还没查过。
    // EU is not a real country code; copied from iOS as-is, and how Steam and ITAD treat it has not been checked.
    Europe(apiRegionCode = "EU"),
}
