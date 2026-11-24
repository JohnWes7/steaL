package io.github.johnwes7.steal.shared.models.settings

/**
 * 用户的全部设置，由 `SettingsRepository` 从存储读出来；没存过的项是 [DEFAULT] 里的值。
 * All of the user's settings, read from storage by `SettingsRepository`; items never saved take their value from
 * [DEFAULT].
 */
data class AppSettings(
    /**
     * 浅色、深色或跟随系统。
     * Light, dark, or following the system.
     */
    val theme: AppTheme,
    /**
     * 是否用按壁纸生成的动态配色；只有 Android 12 以上支持，开了时 [palette] 不起作用。
     * Whether to use the wallpaper-based dynamic colors; Android 12 and later only, and [palette] has no effect
     * while it is on.
     */
    val useDynamicColor: Boolean,
    /**
     * 选用的配色方案。
     * The chosen color palette.
     */
    val palette: ColorPalette,
    /**
     * 顶栏和 tab 栏的毛玻璃。
     * The frosted glass of the top bar and tab bar.
     */
    val barBlur: BarBlurSettings,
    /**
     * 商店地区：请求价格时用哪个国家或地区的价格和货币。
     * The store region: whose prices and currency the requests ask for.
     */
    val region: AppRegion,
) {
    companion object {
        /**
         * 默认设置：深色、不用动态配色、Theme Builder 配色、毛玻璃开、地区澳大利亚。
         * The default settings: dark, no dynamic color, the Theme Builder palette, frosted bars on, and the
         * Australia region.
         */
        val DEFAULT: AppSettings = AppSettings(
            theme = AppTheme.Dark,
            useDynamicColor = false,
            palette = ColorPalette.ThemeBuilder,
            barBlur = BarBlurSettings.DEFAULT,
            region = AppRegion.Australia,
        )
    }
}

/**
 * App 的深浅色：跟随系统、浅色或深色。
 * The app's light or dark appearance: following the system, light, or dark.
 */
enum class AppTheme {
    /**
     * 跟随系统的深色模式开关。
     * Follows the system's dark theme setting.
     */
    System,

    /**
     * 一直是浅色。
     * Always light.
     */
    Light,

    /**
     * 一直是深色。
     * Always dark.
     */
    Dark,
}

/**
 * 可选的配色方案，都由品牌绿（Primary）和品牌蓝（Secondary）生成。
 * The selectable color palettes, all generated from brand green (primary) and brand blue (secondary).
 *
 * 这里只有名字，每套的色值由各平台自己定义（Android 在 `ColorPalettes.kt`）。
 * Only the names live here; each platform defines the colors itself (`ColorPalettes.kt` on Android).
 */
enum class ColorPalette {
    /**
     * Theme Builder 导出的配色（开 Color Match），表面带一点绿；默认用它。
     * The Theme Builder export (Color Match on), with slightly green surfaces; the default.
     */
    ThemeBuilder,

    /**
     * 同 [ThemeBuilder]，表面和文字换成 iOS 那种偏蓝的中性灰。
     * Same as [ThemeBuilder], with the surfaces and text in iOS's bluish neutral gray.
     */
    ThemeBuilderIosGray,

    /**
     * 容器直接用品牌色、容器上的字接近黑（对比度最高），表面的绿比 [ThemeBuilder] 淡。
     * Brand colors used as-is for containers with near-black content on them (the highest contrast), and surfaces
     * less green than [ThemeBuilder].
     */
    SimulatedC,
}

/**
 * 顶栏和 tab 栏的毛玻璃设置；数值都是整数，界面上显示的就是存的值。
 * The frosted glass settings for the top bar and tab bar; every value is a whole number, so what the UI shows is
 * exactly what is stored.
 */
data class BarBlurSettings(
    /**
     * 是否模糊；关掉时两条栏是实心的。
     * Whether the bars blur; with it off both bars are solid.
     */
    val isEnabled: Boolean,
    /**
     * 模糊半径，单位 dp，范围 [RADIUS_RANGE_DP]。
     * The blur radius in dp, within [RADIUS_RANGE_DP].
     */
    val radiusDp: Int,
    /**
     * 模糊层上叠色的不透明度，百分数（10 = 10%），范围 [PERCENT_RANGE]。
     * The opacity of the tint over the blur as a percentage (10 means 10%), within [PERCENT_RANGE].
     */
    val tintPercent: Int,
    /**
     * 噪点的不透明度，百分数，范围 [PERCENT_RANGE]。
     * The opacity of the noise as a percentage, within [PERCENT_RANGE].
     */
    val noisePercent: Int,
) {
    companion object {
        /**
         * 默认值：开，半径 20 dp、叠色 10%、噪点 15%。
         * The defaults: on, with a 20 dp radius, a 10% tint and 15% noise.
         */
        val DEFAULT: BarBlurSettings = BarBlurSettings(
            isEnabled = true,
            radiusDp = 20,
            tintPercent = 10,
            noisePercent = 15,
        )

        /**
         * 模糊半径能取的范围（dp）。
         * The range the blur radius can take, in dp.
         */
        val RADIUS_RANGE_DP: IntRange = 0..40

        /**
         * 两个百分数能取的范围。
         * The range both percentages can take.
         */
        val PERCENT_RANGE: IntRange = 0..100
    }
}
