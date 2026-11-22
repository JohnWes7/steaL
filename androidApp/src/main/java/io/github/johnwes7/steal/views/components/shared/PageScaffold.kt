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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
 * 栏的模糊层上叠色的不透明度：越高越接近实心的栏，越低后面的内容颜色透得越多。
 * The opacity of the color over a bar's blur: higher looks closer to a solid bar, lower lets more of the content
 * behind show through.
 */
private const val BAR_TINT_ALPHA = 0.1f

/**
 * 不能模糊时（Android 11 及以下）栏的叠色不透明度，接近实心，保证栏上的字看得清。
 * The bar tint's opacity when blur is unavailable (Android 11 and below): nearly solid, so text on the bar stays
 * legible.
 */
private const val BAR_FALLBACK_ALPHA = 0.95f

/**
 * 页面的骨架：毛玻璃顶栏叠在内容上，[header] 放在顶栏的标题位。
 * The skeleton of a page: a frosted top bar over the content, with [header] in the bar's title slot.
 *
 * [content] 收到的 contentPadding 含顶栏、tab 栏和横屏时左右刘海的留白：会滑动的内容把它加在滚动里面（如列表的
 * contentPadding），内容才会滚到两条栏后面、最后一项也滑得出来；不滑动的内容用外层 `Modifier.padding`。
 * 顶栏在 [scrollState] 离开顶部后换色。
 * The contentPadding [content] receives covers the top bar, the tab bar and side cutouts in landscape. Scrolling
 * content applies it inside the scroll (such as a list's contentPadding) so it passes behind both bars and its
 * last item still scrolls into view; static content applies it with an outer `Modifier.padding`. The top bar
 * changes color once [scrollState] leaves its start.
 */
@Composable
fun PageScaffold(
    header: @Composable () -> Unit,
    scrollState: ScrollableState,
    modifier: Modifier = Modifier,
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    // 1. 模糊的来源和顶栏的样式
    // 1. The blur source and the top bar's style
    val hazeState = rememberHazeState()
    val topBarBlurStyle = rememberTopBarBlurStyle(scrollState = scrollState)

    // 2. 内容要让开的：tab 栏和横屏时左右的刘海；顶栏的高度 Scaffold 自己会算进去
    // 2. What content must clear: the tab bar and side cutouts in landscape; Scaffold adds the top bar itself
    val tabBarHeight = LocalTabBarHeight.current
    val sideInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    val contentInsets = sideInsets.add(WindowInsets(bottom = tabBarHeight))

    // 3. 内容铺满整页、顶栏叠在上面；没有 bottomBar 的 Scaffold 会把 contentInsets 的底部也算进给内容的 padding
    // 3. Content fills the page under the top bar; a Scaffold without a bottomBar adds the bottom of contentInsets
    //    to the padding it hands to content
    Scaffold(
        modifier = modifier,
        topBar = {
            // 底色由模糊层画，顶栏自己透明。
            // The blur layer draws the background, so the bar itself is transparent.
            TopAppBar(
                title = header,
                modifier = Modifier.hazeBlur(input = HazeInput.Backdrop(hazeState), style = topBarBlurStyle),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
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
 * 栏（顶栏、tab 栏）的毛玻璃样式：页面底色打底，模糊的内容上半透明地叠一层 [tint]。
 * A bar's frosted style (top bar or tab bar): the page background underneath, with [tint] laid translucently over
 * the blurred content.
 *
 * 样式里读的是 [tint] 的当前值，颜色变化时 Haze 直接重画、不用重组；不能模糊时（Android 11 及以下）用接近实心的 [tint]。
 * The style reads [tint]'s current value, so Haze redraws on color changes without recomposing; without blur
 * (Android 11 and below) it uses a nearly solid [tint].
 */
@Composable
fun rememberBarBlurStyle(tint: State<Color>, blurRadius: Dp = 20.dp): HazeBlurStyle {
    val backgroundColor = MaterialTheme.colorScheme.background
    return remember(backgroundColor, tint, blurRadius) {
        HazeBlurStyle {
            backgroundColor(backgroundColor)
            colorEffects(listOf(HazeColorEffect.tint(tint.value.copy(alpha = BAR_TINT_ALPHA))))
            fallbackColorEffect(HazeColorEffect.tint(tint.value.copy(alpha = BAR_FALLBACK_ALPHA)))
            blurRadius(blurRadius)
        }
    }
}

/**
 * 顶栏的毛玻璃样式：内容在顶部时叠色和页面底色一样，滚到顶栏下面后换成导航栏那一层的灰。
 * The top bar's frosted style: the tint matches the page background while content is at its start, then turns
 * the navigation bar's gray once content scrolls under the bar.
 *
 * 「滚到下面」直接看 [scrollState] 能不能往回滚，不用 `TopAppBarDefaults.pinnedScrollBehavior`：
 * 它把下拉刷新拉动的距离也算成滚动，松手后顶栏一直停在滚动后的颜色。
 * "Scrolled under" reads whether [scrollState] can scroll backward instead of using
 * `TopAppBarDefaults.pinnedScrollBehavior`, which counts a pull-to-refresh drag as scrolling and leaves the bar in
 * its scrolled color after release.
 */
@Composable
private fun rememberTopBarBlurStyle(scrollState: ScrollableState): HazeBlurStyle {
    // 1. 内容有没有滚到顶栏下面
    // 1. Whether content has scrolled under the top bar
    val isScrolledUnder = scrollState.canScrollBackward

    // 2. 叠色，换色时渐变（同 Material TopAppBar 自己换色的动画）
    // 2. The tint, fading between colors (the same animation Material TopAppBar uses for its own color change)
    val colorScheme = MaterialTheme.colorScheme
    val targetTint = if (isScrolledUnder) colorScheme.surfaceContainer else colorScheme.background
    val tint = animateColorAsState(
        targetValue = targetTint,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "topBarTint",
    )

    // 3. 建样式
    // 3. Build the style
    return rememberBarBlurStyle(tint = tint)
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
