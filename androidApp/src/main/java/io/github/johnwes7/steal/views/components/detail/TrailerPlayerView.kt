package io.github.johnwes7.steal.views.components.detail

import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.material3.PlayerDefaults
import androidx.media3.ui.compose.material3.indicator.ProgressSlider
import androidx.media3.ui.compose.state.rememberErrorState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.views.components.shared.ArtworkImage
import io.github.johnwes7.steal.views.theme.SteaLTheme
import kotlinx.coroutines.delay
import androidx.media3.ui.compose.material3.Player as MaterialPlayer

/**
 * 控件没人操作多久后自动隐藏（毫秒）。
 * How long the controls stay up without interaction before hiding, in milliseconds.
 */
private const val CONTROLS_AUTO_HIDE_MS = 3_000L

/**
 * 封面上播放键的大小，同 Media3 播放器中间的大播放键：点了换成播放器时，键的位置和大小不跳。
 * The size of the poster's play button, matching the large play button in the middle of the Media3 player, so the
 * button neither moves nor resizes when the player takes over.
 */
private val LARGE_BUTTON_SIZE = 63.dp

/**
 * 封面上播放键的图标大小，同 Media3 的大播放键。
 * The icon size of the poster's play button, matching Media3's large play button.
 */
private val LARGE_ICON_SIZE = 40.dp

/**
 * 还没播的预告片：铺满的封面，中间一个播放键。
 * A trailer that is not playing: the poster filling the page with a play button in the middle.
 */
@Composable
fun TrailerPosterView(
    posterUrl: String?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Steam 的封面本身是 16:9，铺满整页。
        // Steam's poster is 16:9 already and fills the page.
        ArtworkImage(
            url = posterUrl,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = onPlay,
            colors = trailerButtonColors(),
            modifier = Modifier.size(LARGE_BUTTON_SIZE),
        ) {
            Icon(
                painter = painterResource(R.drawable.play_arrow_fill1_24px),
                contentDescription = stringResource(R.string.game_detail_play_trailer),
                modifier = Modifier.size(LARGE_ICON_SIZE),
            )
        }
    }
}

/**
 * 在播的预告片：Media3 的 Material 3 播放器，中间只有播放 / 暂停，下面是时间和进度条，右上角关闭。
 * A playing trailer: Media3's Material 3 player with only play / pause in the middle, the times and progress slider
 * below, and a close button at the top right.
 *
 * 点画面显示或隐藏控件；在播且没在拖进度条时 3 秒后自动隐藏，暂停时一直显示。播放失败时控件一直显示，中间的播放键就是重试
 * （Media3 出错后按播放会重新 `prepare()`）。出画面前盖着 [posterUrl] 的封面，不闪黑块。[onClose] 回到封面。
 * Tapping the picture shows or hides the controls; they hide after 3 seconds while playing and not scrubbing, and stay
 * up while paused. After a playback failure the controls stay up and the middle play button retries (Media3
 * re-prepares on play after an error). The [posterUrl] poster covers the picture until the first frame, so no black
 * box flashes. [onClose] returns to the poster.
 */
@OptIn(UnstableApi::class, ExperimentalApi::class)
@Composable
fun TrailerPlayerView(
    player: Player,
    posterUrl: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 1. 播放器状态：在不在播、有没有出错
    // 1. Player state: whether it is playing and whether it failed
    val playPauseState = rememberPlayPauseButtonState(player)
    val errorState = rememberErrorState(player)
    val isPlaying = !playPauseState.showPlay
    val hasError = errorState.error != null

    // 2. 控件显示：点按切换，出错时一直显示
    // 2. Controls visibility: toggled by taps, always up after an error
    var showsControls by remember { mutableStateOf(true) }
    var isScrubbing by remember { mutableStateOf(false) }
    val controlsVisible = showsControls || hasError

    // 3. 自动隐藏：在播、没在拖进度条时等 3 秒；任何一个条件变了就重新计时
    // 3. Auto-hide: wait 3 seconds while playing and not scrubbing; any change restarts the wait
    LaunchedEffect(showsControls, isPlaying, isScrubbing) {
        val shouldAutoHide = showsControls && isPlaying && !isScrubbing
        if (shouldAutoHide) {
            delay(CONTROLS_AUTO_HIDE_MS)
            showsControls = false
        }
    }

    // 4. 播放器：中间去掉上一条 / 后退 / 快进 / 下一条（单条预告片用不上）
    // 4. The player: previous / seek back / seek forward / next removed from the middle (useless for a single trailer)
    val tapToggleModifier = Modifier.clickable(interactionSource = null, indication = null) {
        showsControls = !showsControls
    }
    MaterialPlayer(
        player = player,
        modifier = modifier.then(tapToggleModifier),
        shutter = {
            ArtworkImage(
                url = posterUrl,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        },
        showControls = controlsVisible,
        topControls = { slotPlayer, visible ->
            PlayerDefaults.TopControls(
                player = slotPlayer,
                visible = visible,
                modifier = Modifier.fillMaxWidth(),
            ) { _ ->
                TrailerCloseButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
        },
        centerControls = { slotPlayer, visible ->
            PlayerDefaults.CenterControls(
                player = slotPlayer,
                visible = visible,
                backSecondary = {},
                back = {},
                forward = {},
                forwardSecondary = {},
            )
        },
        bottomControls = { slotPlayer, visible ->
            PlayerDefaults.BottomControls(
                player = slotPlayer,
                visible = visible,
                progressSlider = { sliderPlayer ->
                    ProgressSlider(
                        player = sliderPlayer,
                        onValueChange = { _ -> isScrubbing = true },
                        onValueChangeFinished = { isScrubbing = false },
                    )
                },
            )
        },
    )
}

/**
 * 播放器右上角的关闭键。
 * The close button at the player's top right.
 */
@Composable
private fun TrailerCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, colors = trailerButtonColors(), modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.close_24px),
            contentDescription = stringResource(R.string.action_close),
        )
    }
}

/**
 * 叠在视频上的按钮颜色：底色同 Media3 播放器的按钮（`secondaryContainer` 80%），图标用默认的内容色。
 * Colors for buttons over the video: the same backdrop as the Media3 player's buttons (80% `secondaryContainer`)
 * with the default content color for the icon.
 */
@Composable
private fun trailerButtonColors(): IconButtonColors {
    val containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
    return IconButtonDefaults.iconButtonColors(containerColor = containerColor)
}

// MARK: - 预览 / Previews

@Preview
@Composable
private fun TrailerPosterViewPreview() {
    SteaLTheme(darkTheme = true) {
        TrailerPosterView(
            posterUrl = null,
            onPlay = {},
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
    }
}
