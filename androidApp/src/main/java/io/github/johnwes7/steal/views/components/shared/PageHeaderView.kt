package io.github.johnwes7.steal.views.components.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.views.theme.BrandFontFamily
import io.github.johnwes7.steal.views.theme.ExtendedTheme
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * tab 根页面顶栏的头部：固定的「STEAL」小字在上，这一页的大标题 [title] 在下，排版同首页的品牌头部。
 * The header in a tab root screen's top bar: the fixed "STEAL" tagline above this screen's large [title], laid out
 * like the home page's brand header.
 *
 * 只给 tab 的根页面用，传给 [PageScaffold] 的 header；推进来的页面用普通标题加返回箭头。
 * Only for tab root screens, passed as [PageScaffold]'s header; pushed screens use a plain title with a back arrow.
 */
@Composable
fun PageHeaderView(title: String, modifier: Modifier = Modifier) {
    BrandHeader(
        tagline = stringResource(R.string.page_header_tagline),
        title = AnnotatedString(title),
        modifier = modifier,
    )
}

/**
 * 品牌头部的排版：绿色小字 [tagline] 在上、大字 [title] 在下；首页和各 tab 根页面共用，大小一致。
 * The brand header layout: the green [tagline] above the large [title]; shared by the home page and the tab root
 * screens so they match in size.
 *
 * 字体和字号都固定：字体是 [BrandFontFamily]，不跟手机的系统字体变；字号不随系统字体放大，常驻顶栏放大会挤掉内容。
 * Both the font and the sizes are fixed: the font is [BrandFontFamily], unaffected by the phone's system font, and the
 * sizes don't follow the system font size, since an enlarged persistent bar crowds out the content.
 */
@Composable
fun BrandHeader(
    tagline: String,
    title: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    // 1. 固定字体、字号和行高：字号按 dp 换算成 sp，抵消系统字体缩放
    // 1. Fixed font, sizes and line heights: sizes converted from dp to sp, cancelling the system font scale
    val density = LocalDensity.current
    // 行高要写明：不写会继承顶栏标题的 28sp 行高，40 的大字超出行框，贴着顶栏底边。
    // Line heights must be explicit: otherwise they inherit the top bar title's 28sp, and the 40 title overflows its
    // line box and sits on the bar's bottom edge.
    val taglineStyle = TextStyle(
        fontFamily = BrandFontFamily,
        fontSize = with(density) { 13.dp.toSp() },
        lineHeight = with(density) { 16.dp.toSp() },
        fontWeight = FontWeight.Bold,
    )
    val titleStyle = TextStyle(
        fontFamily = BrandFontFamily,
        fontSize = with(density) { 40.dp.toSp() },
        lineHeight = with(density) { 46.dp.toSp() },
        fontWeight = FontWeight.Bold,
    )

    // 2. 标语在上、标题在下，下面留出和内容的间距
    // 2. Tagline above the title, with room below before the content
    Column(modifier = modifier.padding(top = 2.dp, bottom = 12.dp)) {
        Text(
            text = tagline,
            style = taglineStyle,
            color = ExtendedTheme.colors.brandGreen,
        )
        Text(
            text = title,
            style = titleStyle,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Preview
@Composable
private fun PageHeaderViewPreview() {
    SteaLTheme(darkTheme = true) {
        PageHeaderView(title = "Settings")
    }
}
