package io.github.johnwes7.steal.views.components.shared

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.shared.models.settings.BarBlurSettings
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * tab 栏（连同下面的手势条）的高度，tab 页的内容要在底部让出这么多；没有 tab 栏时（如预览）是 0。
 * The height of the tab bar (with the gesture bar below it), which tab pages leave clear at the bottom; 0 when
 * there is no tab bar, such as in previews.
 *
 * 页面一般不直接读它，而是用 [PageScaffold] 给的 contentPadding。
 * Pages normally don't read it directly; they use the contentPadding [PageScaffold] provides.
 */
val LocalTabBarHeight: ProvidableCompositionLocal<Dp> = compositionLocalOf { 0.dp }

/**
 * 顶栏和 tab 栏的毛玻璃设置，由 App 最外层按用户设置提供；没提供时（如预览）是默认值。
 * The frosted glass settings for the top bar and tab bar, provided at the app's root from the user's settings;
 * the defaults when nothing provides it, such as in previews.
 *
 * 只有画栏的地方读它（[PageScaffold] 的顶栏、`MainTabView` 的 tab 栏），页面不读。
 * Only the code that draws the bars reads it ([PageScaffold]'s top bar and `MainTabView`'s tab bar); pages don't.
 */
val LocalBarBlurSettings: ProvidableCompositionLocal<BarBlurSettings> =
    compositionLocalOf { BarBlurSettings.DEFAULT }

/**
 * 不能模糊时（Android 11 及以下）栏的叠色不透明度，接近实心，保证栏上的字看得清。
 * The bar tint's opacity when blur is unavailable (Android 11 and below): nearly solid, so text on the bar stays
 * legible.
 */
private const val BAR_FALLBACK_ALPHA = 0.95f

/**
 * [PageScaffold] 顶栏的样式。
 * The style of [PageScaffold]'s top bar.
 */
enum class PageTopBarStyle {
    /**
     * 一行，高度随 header 撑开；tab 的根页面用，header 是 `PageHeaderView` 这类自带字号的大头部。
     * One row whose height follows the header; for tab root screens, whose header is a large header with its own
     * type sizes, such as `PageHeaderView`.
     */
    Small,

    /**
     * 两行：标题在返回箭头下面一行，内容往上滑时收成一行；推进来的页面用，header 是普通的 `Text`（字号由顶栏给）。
     * Two rows: the title sits in a row below the back arrow and collapses into one row as content scrolls up; for
     * pushed screens, whose header is a plain `Text` (the bar supplies its type sizes).
     */
    Collapsing,
}

/**
 * 页面的骨架：毛玻璃顶栏叠在内容上，[header] 放在顶栏的标题位，[navigationIcon] 放在标题左边。
 * The skeleton of a page: a frosted top bar over the content, with [header] in the bar's title slot and
 * [navigationIcon] before it.
 *
 * 推进来的页面要自己传返回箭头（Material 的顶栏不会像 iOS 那样自动加）；tab 的根页面不传。
 * Pushed screens pass their own back arrow (Material's top bar doesn't add one the way iOS does); tab root screens
 * pass nothing.
 *
 * [content] 收到的 contentPadding 含顶栏、tab 栏和横屏时左右刘海的留白：会滑动的内容把它加在滚动里面（如列表的
 * contentPadding），内容才会滚到两条栏后面、最后一项也滑得出来；不滑动的内容用外层 `Modifier.padding`。
 * 顶栏在 [scrollState] 离开顶部后换色。
 * The contentPadding [content] receives covers the top bar, the tab bar and side cutouts in landscape. Scrolling
 * content applies it inside the scroll (such as a list's contentPadding) so it passes behind both bars and its
 * last item still scrolls into view; static content applies it with an outer `Modifier.padding`. The top bar
 * changes color once [scrollState] leaves its start.
 *
 * [topBarStyle] 是 [PageTopBarStyle.Collapsing] 时顶栏靠嵌套滚动收起，下拉刷新拉动的距离也会被算进去，所以有下拉刷新的页面用
 * [PageTopBarStyle.Small]。
 * With [PageTopBarStyle.Collapsing] the bar collapses through nested scrolling, which also counts a pull-to-refresh
 * drag, so screens with pull to refresh use [PageTopBarStyle.Small].
 */
@Composable
fun PageScaffold(
    header: @Composable () -> Unit,
    scrollState: ScrollableState,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    topBarStyle: PageTopBarStyle = PageTopBarStyle.Small,
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    // 1. 模糊的来源和顶栏的样式
    // 1. The blur source and the top bar's style
    val hazeState = rememberHazeState()
    val barBlurSettings = LocalBarBlurSettings.current
    val topBarTint = rememberTopBarTint(scrollState = scrollState)
    val topBarBlurStyle = rememberBarBlurStyle(tint = topBarTint, settings = barBlurSettings)

    // 2. 内容要让开的：tab 栏和横屏时左右的刘海；顶栏的高度 Scaffold 自己会算进去
    // 2. What content must clear: the tab bar and side cutouts in landscape; Scaffold adds the top bar itself
    val tabBarHeight = LocalTabBarHeight.current
    val sideInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    val contentInsets = sideInsets.add(WindowInsets(bottom = tabBarHeight))

    // 3. 两行的顶栏要一个收起行为，并经嵌套滚动收到内容的滚动（官方 exitUntilCollapsed 示例的写法）
    // 3. The two-row bar needs a collapse behavior that receives the content's scrolling through nested scroll
    //    (as in the official exitUntilCollapsed sample)
    val collapseBehavior = if (topBarStyle == PageTopBarStyle.Collapsing) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    } else {
        null
    }
    val scaffoldModifier = if (collapseBehavior != null) {
        modifier.nestedScroll(collapseBehavior.nestedScrollConnection)
    } else {
        modifier
    }

    // 4. 内容铺满整页、顶栏叠在上面；没有 bottomBar 的 Scaffold 会把 contentInsets 的底部也算进给内容的 padding
    // 4. Content fills the page under the top bar; a Scaffold without a bottomBar adds the bottom of contentInsets
    //    to the padding it hands to content
    Scaffold(
        modifier = scaffoldModifier,
        topBar = {
            // 底色由 barBackground 画（模糊层或实心色），顶栏自己透明；两行的顶栏收起时也会换容器色，所以两种都设透明。
            // barBackground draws the background (blur or a solid color), so the bar itself is transparent; the
            // two-row bar also switches container color as it collapses, so both colors are transparent.
            val barModifier = Modifier.barBackground(
                isBlurEnabled = barBlurSettings.isEnabled,
                hazeState = hazeState,
                blurStyle = topBarBlurStyle,
                tint = topBarTint,
            )
            val barColors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            )
            when (topBarStyle) {
                PageTopBarStyle.Small -> TopAppBar(
                    title = header,
                    navigationIcon = navigationIcon,
                    modifier = barModifier,
                    colors = barColors,
                )
                PageTopBarStyle.Collapsing -> MediumFlexibleTopAppBar(
                    title = header,
                    navigationIcon = navigationIcon,
                    modifier = barModifier,
                    colors = barColors,
                    scrollBehavior = collapseBehavior,
                )
            }
        },
        contentWindowInsets = contentInsets,
    ) { innerPadding ->
        // 模糊的来源挂在内容外面，页面不用知道 Haze。
        // The blur source wraps the content, so pages don't need to know about Haze.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            content(innerPadding)
        }
    }
}

/**
 * 推进来的页面顶栏左边的返回箭头，传给 [PageScaffold] 的 navigationIcon。
 * The back arrow at the start of a pushed screen's top bar, passed to [PageScaffold]'s navigationIcon.
 */
@Composable
fun PageBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.arrow_back_24px),
            contentDescription = stringResource(R.string.action_back),
        )
    }
}

/**
 * 列表里占满一屏、内容居中的一项，放加载中、失败和空状态；出现和消失时淡入淡出。
 * A list item that fills one screen with its content centered, for loading, failure and empty states; it fades
 * in and out.
 *
 * 一屏是列表的可见区域减去 contentPadding，所以内容居中在两条栏之间。有列表的页面让列表一直在、把这些状态做成这一项，
 * 不按状态换整页。
 * One screen is the list's viewport minus its contentPadding, so the content centers between the two bars. Pages
 * with a list keep the list and show these states as this item instead of swapping the whole page by state.
 */
@Composable
fun LazyItemScope.CenteredPageItem(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .animateItem()
            .fillParentMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * 栏（顶栏、tab 栏）的毛玻璃样式：页面底色打底，模糊的内容上半透明地叠一层 [tint]；半径、叠色、噪点来自 [settings]。
 * A bar's frosted style (top bar or tab bar): the page background underneath, with [tint] laid translucently over
 * the blurred content; the radius, tint opacity and noise come from [settings].
 *
 * 样式里读的是 [tint] 的当前值，颜色变化时 Haze 直接重画、不用重组；不能模糊时（Android 11 及以下）用接近实心的 [tint]。
 * The style reads [tint]'s current value, so Haze redraws on color changes without recomposing; without blur
 * (Android 11 and below) it uses a nearly solid [tint].
 */
@Composable
fun rememberBarBlurStyle(tint: State<Color>, settings: BarBlurSettings): HazeBlurStyle {
    val backgroundColor = MaterialTheme.colorScheme.background
    return remember(backgroundColor, tint, settings) {
        // 设置里存的是整数（dp 和百分数），换成 Haze 要的单位。
        // Settings store whole numbers (dp and percentages); convert them to the units Haze takes.
        val blurRadius = settings.radiusDp.dp
        val tintAlpha = settings.tintPercent / 100f
        val noiseFactor = settings.noisePercent / 100f
        HazeBlurStyle {
            backgroundColor(backgroundColor)
            colorEffects(listOf(HazeColorEffect.tint(tint.value.copy(alpha = tintAlpha))))
            fallbackColorEffect(HazeColorEffect.tint(tint.value.copy(alpha = BAR_FALLBACK_ALPHA)))
            blurRadius(blurRadius)
            noiseFactor(noiseFactor)
        }
    }
}

/**
 * 栏的背景：[isBlurEnabled] 时模糊 [hazeState] 里的内容（样式 [blurStyle]），关掉时画实心的 [tint]。
 * A bar's background: blurs the content in [hazeState] with [blurStyle] when [isBlurEnabled], or draws a solid
 * [tint] when it is off.
 *
 * 实心色在绘制阶段读 [tint]，颜色渐变时只重画、不重组。
 * The solid color reads [tint] in the draw phase, so a color fade only redraws without recomposing.
 */
fun Modifier.barBackground(
    isBlurEnabled: Boolean,
    hazeState: HazeState,
    blurStyle: HazeBlurStyle,
    tint: State<Color>,
): Modifier {
    if (isBlurEnabled) {
        return this.hazeBlur(input = HazeInput.Backdrop(hazeState), style = blurStyle)
    }
    return this.drawBehind { drawRect(color = tint.value) }
}

/**
 * 顶栏的叠色：内容在顶部时和页面底色一样，滚到顶栏下面后换成导航栏那一层的灰。
 * The top bar's tint: the page background while content is at its start, then the navigation bar's gray once
 * content scrolls under the bar.
 *
 * 「滚到下面」直接看 [scrollState] 能不能往回滚，不用 `TopAppBarDefaults.pinnedScrollBehavior`：
 * 它把下拉刷新拉动的距离也算成滚动，松手后顶栏一直停在滚动后的颜色。
 * "Scrolled under" reads whether [scrollState] can scroll backward instead of using
 * `TopAppBarDefaults.pinnedScrollBehavior`, which counts a pull-to-refresh drag as scrolling and leaves the bar in
 * its scrolled color after release.
 */
@Composable
private fun rememberTopBarTint(scrollState: ScrollableState): State<Color> {
    // 1. 内容有没有滚到顶栏下面
    // 1. Whether content has scrolled under the top bar
    val isScrolledUnder = scrollState.canScrollBackward

    // 2. 叠色，换色时渐变（同 Material TopAppBar 自己换色的动画）
    // 2. The tint, fading between colors (the same animation Material TopAppBar uses for its own color change)
    val colorScheme = MaterialTheme.colorScheme
    val targetTint = if (isScrolledUnder) colorScheme.surfaceContainer else colorScheme.background
    return animateColorAsState(
        targetValue = targetTint,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "topBarTint",
    )
}

@Preview
@Composable
private fun PageScaffoldPreview() {
    SteaLTheme(darkTheme = true) {
        val listState = rememberLazyListState()
        PageScaffold(
            header = { Text(text = "Header") },
            scrollState = listState,
        ) { contentPadding ->
            LazyColumn(state = listState, contentPadding = contentPadding) {
                items(count = 30) { index ->
                    Text(text = "Row $index", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
