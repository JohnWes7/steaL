package io.github.johnwes7.steal.views.components.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 首页顶栏的品牌头部：绿色标语加「steaL」字标，最后的 L 是品牌蓝。
 * The home page's brand header: the green tagline above the "steaL" wordmark, whose final L is brand blue.
 *
 * 字号固定、不随系统字体放大：常驻顶栏放大会挤掉内容。
 * The sizes are fixed rather than following the system font size, since an enlarged persistent bar crowds out the
 * content.
 */
@Composable
fun GamesHeaderView(modifier: Modifier = Modifier) {
    // 1. 固定字号和行高：按 dp 换算成 sp，抵消系统字体缩放
    // 1. Fixed sizes and line heights: dp converted to sp, cancelling the system font scale
    val density = LocalDensity.current
    // 行高要写明：不写会继承顶栏标题的 28sp 行高，40 的字标超出行框，贴着顶栏底边。
    // Line heights must be explicit: otherwise they inherit the top bar title's 28sp, and the 40 wordmark overflows
    // its line box and sits on the bar's bottom edge.
    val taglineStyle = TextStyle(
        fontSize = with(density) { 13.dp.toSp() },
        lineHeight = with(density) { 16.dp.toSp() },
        fontWeight = FontWeight.Bold,
    )
    val wordmarkStyle = TextStyle(
        fontSize = with(density) { 40.dp.toSp() },
        lineHeight = with(density) { 46.dp.toSp() },
        fontWeight = FontWeight.Bold,
    )

    // 2. 字标：App 名字的最后一个字母换成品牌蓝
    // 2. The wordmark: the app name with its last letter in brand blue
    val appName = stringResource(R.string.app_name)
    val body = appName.dropLast(1)
    val lastLetter = appName.takeLast(1)
    val brandBlue = MaterialTheme.colorScheme.secondary
    val wordmark = buildAnnotatedString {
        append(body)
        withStyle(SpanStyle(color = brandBlue)) {
            append(lastLetter)
        }
    }

    // 3. 标语在上、字标在下，下面留出和内容的间距
    // 3. Tagline above the wordmark, with room below before the content
    Column(modifier = modifier.padding(top = 2.dp, bottom = 12.dp)) {
        Text(
            text = stringResource(R.string.games_header_tagline),
            style = taglineStyle,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = wordmark,
            style = wordmarkStyle,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Preview
@Composable
private fun GamesHeaderViewPreview() {
    SteaLTheme(darkTheme = true) {
        GamesHeaderView()
    }
}
