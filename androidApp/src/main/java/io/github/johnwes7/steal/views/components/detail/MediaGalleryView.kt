package io.github.johnwes7.steal.views.components.detail

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.johnwes7.steal.previews.PREVIEW_HEADER_IMAGE_URL
import io.github.johnwes7.steal.previews.previewManyScreenshotUrls
import io.github.johnwes7.steal.previews.previewScreenshotUrls
import io.github.johnwes7.steal.views.components.shared.ArtworkImage
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 画廊里的一页。
 * One page of the gallery.
 */
private sealed interface GalleryPage {

    /**
     * 一张图：主图或截图。
     * An image: the header or a screenshot.
     */
    data class Image(val url: String) : GalleryPage
}

/**
 * 页点一次最多显示几个：页数更多时页点跟着当前页滑动。
 * The most page dots shown at once: with more pages the dots slide along with the current page.
 */
private const val MAX_VISIBLE_DOTS = 9

/**
 * 详情页顶部的媒体画廊：16:9、左右翻页，下面是页点；一张图都没有时是占位。
 * The media gallery at the top of the detail screen: 16:9 and swiped sideways, with page dots below; a placeholder
 * when there are no images at all.
 *
 * 页的顺序是主图 [headerImageUrl] 在前、截图 [screenshotUrls] 在后。宽度由 [modifier] 决定，详情页让它贴到屏幕两边。
 * The header [headerImageUrl] comes first, then the [screenshotUrls]. [modifier] decides the width; the detail
 * screen runs it edge to edge.
 */
@Composable
fun MediaGalleryView(
    headerImageUrl: String?,
    screenshotUrls: List<String>,
    modifier: Modifier = Modifier,
) {
    val pages = galleryPages(headerImageUrl = headerImageUrl, screenshotUrls = screenshotUrls)
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(modifier = modifier.fillMaxWidth()) {
        if (pages.isEmpty()) {
            // 没有地址时 ArtworkImage 显示占位底色和破图图标。
            // With no URL, ArtworkImage shows the placeholder color and a broken-image icon.
            ArtworkImage(
                url = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) { pageIndex ->
                val page = pages[pageIndex]
                when (page) {
                    is GalleryPage.Image -> GalleryImagePage(url = page.url)
                }
            }
        }

        if (pages.size > 1) {
            GalleryPageDots(
                currentPage = pagerState.currentPage,
                pageCount = pages.size,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp),
            )
        }
    }
}

/**
 * 画廊的页：主图在前、截图在后。
 * The gallery's pages: the header first, then the screenshots.
 */
private fun galleryPages(headerImageUrl: String?, screenshotUrls: List<String>): List<GalleryPage> {
    val headerUrls = listOfNotNull(headerImageUrl)
    val imageUrls = headerUrls + screenshotUrls
    return imageUrls.map { url -> GalleryPage.Image(url = url) }
}

/**
 * 一页图：完整显示，留白处垫同一张图的模糊放大版。
 * An image page shown whole, with a blurred, enlarged copy of the same image filling the gaps.
 *
 * `Modifier.blur` 只在 Android 12 起生效，更早的版本不垫模糊、留白是占位底色（不模糊的放大图压暗了也很乱）。
 * `Modifier.blur` only works from Android 12; earlier versions skip the backdrop and the gaps show the placeholder
 * color (an unblurred enlarged copy looks messy even when darkened).
 */
@Composable
private fun GalleryImagePage(url: String) {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (canBlur) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 24.dp),
            )
            // 压暗一半，模糊的底不和图片本身抢。
            // Darken it by half so the blurred backdrop doesn't compete with the image.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
            )
        }
        ArtworkImage(
            url = url,
            contentScale = ContentScale.Fit,
            placeholderColor = Color.Transparent,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * 页点：当前页实心；页数超过 [MAX_VISIBLE_DOTS] 时只显示当前页附近的一段，两头还有更多时那一端的点缩小。
 * Page dots with the current one solid; past [MAX_VISIBLE_DOTS] pages only a run around the current page shows, and
 * the end dot shrinks on a side that has more.
 */
@Composable
private fun GalleryPageDots(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    // 1. 显示哪一段：当前页尽量在中间，到两头时贴边
    // 1. Pick the visible run: the current page centered where possible, pinned at either end
    val visibleCount = minOf(pageCount, MAX_VISIBLE_DOTS)
    val centeredFirst = currentPage - visibleCount / 2
    val lastPossibleFirst = pageCount - visibleCount
    val firstVisible = centeredFirst.coerceIn(0, lastPossibleFirst)
    val lastVisible = firstVisible + visibleCount - 1

    // 2. 画点：这一段外面还有页的那一端用小点
    // 2. Draw the dots: a small dot at an end that has pages beyond it
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (page in firstVisible..lastVisible) {
            val hasMoreBefore = page == firstVisible && firstVisible > 0
            val hasMoreAfter = page == lastVisible && lastVisible < pageCount - 1
            val dotSize = if (hasMoreBefore || hasMoreAfter) 5.dp else 8.dp
            val dotColor = if (page == currentPage) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            }
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}

// MARK: - 预览 / Previews

@Preview
@Composable
private fun MediaGalleryViewPreview() {
    SteaLTheme(darkTheme = true) {
        MediaGalleryView(headerImageUrl = PREVIEW_HEADER_IMAGE_URL, screenshotUrls = previewScreenshotUrls)
    }
}

@Preview
@Composable
private fun MediaGalleryViewManyPagesPreview() {
    SteaLTheme(darkTheme = true) {
        MediaGalleryView(headerImageUrl = PREVIEW_HEADER_IMAGE_URL, screenshotUrls = previewManyScreenshotUrls)
    }
}

@Preview
@Composable
private fun MediaGalleryViewEmptyPreview() {
    SteaLTheme(darkTheme = true) {
        MediaGalleryView(headerImageUrl = null, screenshotUrls = emptyList())
    }
}

@Preview
@Composable
private fun GalleryPageDotsPreview() {
    SteaLTheme(darkTheme = true) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GalleryPageDots(currentPage = 0, pageCount = 25)
            GalleryPageDots(currentPage = 12, pageCount = 25)
            GalleryPageDots(currentPage = 24, pageCount = 25)
        }
    }
}
