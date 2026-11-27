package io.github.johnwes7.steal.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.johnwes7.steal.shared.models.steam.SteamMovie

/**
 * 预告片退到后台时停下的位置，回到前台重建播放器时从这里接着。
 * Where a trailer stopped when the app went to the background, so the rebuilt player picks up from there.
 */
private class ResumePoint {

    /**
     * 停下时的播放位置（毫秒）；`null` 表示还没停过，从头播。
     * The playback position in milliseconds when it stopped; `null` means it has not stopped yet and plays from the start.
     */
    var positionMs: Long? = null
}

/**
 * 画廊预告片的播放器：[movie] 有 HLS 地址时建一个 ExoPlayer 并起播，没有（包括 `null`，即没在播）时不建、返回 `null`。
 * The player for a gallery trailer: builds an ExoPlayer and starts it when [movie] has an HLS URL, and builds nothing
 * and returns `null` otherwise (including `null`, meaning nothing is playing).
 *
 * App 退到后台（`onStop`）就释放，回到前台重建、停在原来的位置并暂停；[movie] 换了或离开组合也释放。
 * Released when the app goes to the background (`onStop`) and rebuilt on return, paused at the same position; also
 * released when [movie] changes or this leaves the composition.
 */
@Composable
fun rememberTrailerPlayer(movie: SteamMovie?): Player? {
    val context = LocalContext.current
    // 按视频分开存：换一条视频时旧播放器释放、写回的是旧的那份，不会把新视频的播放器清掉。
    // Kept per movie: when the movie changes, the old player's release writes to the old copy and never clears the new player.
    val playerState = remember(movie) { mutableStateOf<Player?>(null) }
    val resumePoint = remember(movie) { ResumePoint() }

    LifecycleStartEffect(movie) {
        val url = movie?.hlsH264
        if (url == null) {
            return@LifecycleStartEffect onStopOrDispose {}
        }

        val exoPlayer = createTrailerPlayer(context = context, url = url, resumePoint = resumePoint)
        playerState.value = exoPlayer
        onStopOrDispose {
            resumePoint.positionMs = exoPlayer.currentPosition
            exoPlayer.release()
            playerState.value = null
        }
    }

    return playerState.value
}

/**
 * 建一个播 [url] 的 ExoPlayer 并 `prepare()`：[resumePoint] 没有位置时从头自动播，有位置时停在那里、暂停。
 * Builds an ExoPlayer for [url] and prepares it: plays from the start when [resumePoint] has no position, otherwise
 * waits paused at that position.
 */
private fun createTrailerPlayer(context: Context, url: String, resumePoint: ResumePoint): ExoPlayer {
    // 1. 播放器：音频焦点交给 ExoPlayer（来电、别的 App 放声音时让出），拔耳机暂停
    // 1. The player: ExoPlayer handles audio focus (yielding to calls and other apps' audio) and pauses on unplugging headphones
    val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()
    val handlesAudioFocus = true
    val player = ExoPlayer.Builder(context)
        .setAudioAttributes(audioAttributes, handlesAudioFocus)
        .setHandleAudioBecomingNoisy(true)
        .build()

    // 2. 视频：写明是 HLS，不靠地址后缀推断（Steam 的地址后面带 `?t=`）
    // 2. The video: declared as HLS rather than inferred from the URL's ending (Steam's URLs carry `?t=`)
    val mediaItem = MediaItem.Builder()
        .setUri(url)
        .setMimeType(MimeTypes.APPLICATION_M3U8)
        .build()

    // 3. 起播：第一次从头自动播；回到前台重建时停在原位置
    // 3. Start: the first time plays from the start; a rebuild on return waits at the old position
    val resumePosition = resumePoint.positionMs
    if (resumePosition == null) {
        player.setMediaItem(mediaItem)
        player.playWhenReady = true
    } else {
        player.setMediaItem(mediaItem, resumePosition)
        player.playWhenReady = false
    }
    player.prepare()
    return player
}
