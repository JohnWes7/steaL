package io.github.johnwes7.steal.views.components.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.views.components.shared.BrandHeader
import io.github.johnwes7.steal.views.theme.ExtendedTheme
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 首页顶栏的品牌头部：绿色标语加「steaL」字标，最后的 L 是品牌蓝；排版和其他 tab 的 `PageHeaderView` 共用 [BrandHeader]。
 * The home page's brand header: the green tagline above the "steaL" wordmark, whose final L is brand blue; it shares
 * [BrandHeader]'s layout with the other tabs' `PageHeaderView`.
 */
@Composable
fun GamesHeaderView(modifier: Modifier = Modifier) {
    // 1. 字标：App 名字的最后一个字母换成品牌蓝
    // 1. The wordmark: the app name with its last letter in brand blue
    val appName = stringResource(R.string.app_name)
    val body = appName.dropLast(1)
    val lastLetter = appName.takeLast(1)
    val brandBlue = ExtendedTheme.colors.brandBlue
    val wordmark = buildAnnotatedString {
        append(body)
        withStyle(SpanStyle(color = brandBlue)) {
            append(lastLetter)
        }
    }

    // 2. 标语在上、字标在下
    // 2. Tagline above the wordmark
    BrandHeader(
        tagline = stringResource(R.string.games_header_tagline),
        title = wordmark,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun GamesHeaderViewPreview() {
    SteaLTheme(darkTheme = true) {
        GamesHeaderView()
    }
}
