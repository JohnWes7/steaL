package io.github.johnwes7.steal.views.components.home

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
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
    // 1. 固定字号：按 dp 换算成 sp，抵消系统字体缩放
    // 1. Fixed sizes: dp converted to sp, cancelling the system font scale
    val density = LocalDensity.current
    val taglineSize = with(density) { 13.dp.toSp() }
    val wordmarkSize = with(density) { 40.dp.toSp() }

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

    // 3. 标语在上、字标在下
    // 3. Tagline above the wordmark
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.games_header_tagline),
            fontSize = taglineSize,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = wordmark,
            fontSize = wordmarkSize,
            fontWeight = FontWeight.Bold,
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
