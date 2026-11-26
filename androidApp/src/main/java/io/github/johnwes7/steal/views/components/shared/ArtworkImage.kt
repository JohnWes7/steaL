package io.github.johnwes7.steal.views.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 游戏图片：默认裁切铺满，加载中是占位底色，失败或没有地址时在占位上显示破图图标。
 * Game artwork, cropped to fill by default: the placeholder color while loading, and a broken-image icon on it when
 * loading fails or there is no URL.
 *
 * 图片是装饰，不朗读；标题由旁边的文字提供。尺寸由 [modifier] 决定。[contentScale] 不是裁切时，留白处露出
 * [placeholderColor]；要在图片后面垫别的东西就传透明。
 * The image is decorative and not read out; the nearby text names the game. [modifier] decides the size. When
 * [contentScale] doesn't crop, the gaps show [placeholderColor]; pass transparent to put something else behind the
 * image.
 */
@Composable
fun ArtworkImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    // 换了地址就重新算失败与否。
    // A new URL resets the failure flag.
    var hasFailed by remember(url) { mutableStateOf(false) }
    val context = LocalPlatformContext.current
    val request = 
        ImageRequest.Builder(context)
        .data(url)
        .crossfade(true)
        .build()

    Box(
        modifier = modifier.background(placeholderColor),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = contentScale,
            onSuccess = { _ -> hasFailed = false },
            onError = { _ -> hasFailed = true },
            modifier = Modifier.fillMaxSize(),
        )
        if (hasFailed) {
            Icon(
                painter = painterResource(R.drawable.broken_image_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Preview
@Composable
private fun ArtworkImagePreview() {
    SteaLTheme {
        ArtworkImage(url = null, modifier = Modifier.size(width = 160.dp, height = 240.dp))
    }
}
