package io.github.johnwes7.steal.views.components.detail

import android.icu.text.NumberFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.previews.previewGameInfo
import io.github.johnwes7.steal.shared.models.itad.ItadGameInfoResponse
import io.github.johnwes7.steal.shared.models.itad.ItadReview
import io.github.johnwes7.steal.shared.viewmodels.GameDetailViewModel.InfoLoadState
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * ITAD 信息区块：各来源的评分、在线人数和 ITAD 排名；晚于 Steam 详情到，自带加载中。`Hidden` 时什么都不显示。
 * The ITAD info section: each source's review score, players online and the ITAD rank; it arrives after the Steam
 * details, so it carries its own loading state. `Hidden` shows nothing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItadInfoSectionView(state: InfoLoadState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (state == InfoLoadState.Hidden) {
        return
    }

    DetailSection(title = stringResource(R.string.game_detail_section_itad), modifier = modifier) {
        when (state) {
            InfoLoadState.Hidden -> Unit
            InfoLoadState.Loading -> LoadingIndicator(modifier = Modifier.size(40.dp))
            // 查了没有：不给重试，重试也不会变。
            // Checked and missing: no retry, since retrying wouldn't change anything.
            InfoLoadState.NotFound -> DetailUnavailablePrompt(
                message = stringResource(R.string.game_detail_itad_not_found),
                onRetry = null,
            )
            InfoLoadState.Failed -> DetailUnavailablePrompt(
                message = stringResource(R.string.game_detail_itad_failed),
                onRetry = onRetry,
            )
            is InfoLoadState.Loaded -> InfoContent(info = state.info)
        }
    }
}

/**
 * 拿到信息后的内容：评分列表和底下两个指标；评分来源或分数缺的跳过，人数和排名是 0 当没有。
 * The content once the info is in: the review list and the two metrics below; reviews without a source or score
 * are skipped, and a zero player count or rank counts as missing.
 */
@Composable
private fun InfoContent(info: ItadGameInfoResponse) {
    // 1. 整理要显示的数据
    // 1. Gather what to show
    val allReviews = info.reviews.orEmpty()
    val reviews = allReviews.filter { review -> !review.source.isNullOrEmpty() && review.score != null }
    val recentPlayers = info.players?.recent
    val shownPlayers = if (recentPlayers != null && recentPlayers > 0) recentPlayers else null
    val rank = info.stats?.rank
    val shownRank = if (rank != null && rank > 0) rank else null

    // 2. 评分列表 + 指标
    // 2. The reviews and the metrics
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (reviews.isNotEmpty()) {
            Column {
                for ((index, review) in reviews.withIndex()) {
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    ReviewRow(review = review, modifier = Modifier.padding(vertical = 14.dp))
                }
            }
        }
        if (shownPlayers != null || shownRank != null) {
            Metrics(players = shownPlayers, rank = shownRank)
        }
    }
}

/**
 * 一个评分来源：来源和分数，下面一条分数条，再下面评分人数。
 * One review source: the source and score, a score bar below, then the review count.
 */
@Composable
private fun ReviewRow(review: ItadReview, modifier: Modifier = Modifier) {
    val rawScore = review.score ?: 0
    val score = rawScore.coerceIn(0, 100)
    val source = review.source.orEmpty()
    val count = review.count
    val countText = if (count == null) {
        stringResource(R.string.game_detail_review_count_unavailable)
    } else {
        val formattedCount = formattedInteger(value = count)
        pluralStringResource(R.plurals.game_detail_review_count, count, formattedCount)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.thumb_up_fill1_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = source,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.game_detail_review_score_max),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ScoreBar(score = score)
        Text(text = countText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 分数条：自己画的两段，分数用主色、剩下的用中性底色，中间留缝。不用进度条：M3 的进度条是给进行中的过程用的。
 * The score bar: two drawn segments, the score in primary and the rest in a neutral track with a gap between. Not
 * a progress indicator: M3 reserves those for processes.
 */
@Composable
private fun ScoreBar(score: Int) {
    val remaining = 100 - score
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
    ) {
        if (score > 0) {
            Box(
                modifier = Modifier
                    .weight(score.toFloat())
                    .fillMaxHeight()
                    .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape),
            )
        }
        if (score > 0 && remaining > 0) {
            Spacer(modifier = Modifier.width(4.dp))
        }
        if (remaining > 0) {
            Box(
                modifier = Modifier
                    .weight(remaining.toFloat())
                    .fillMaxHeight()
                    .background(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape),
            )
        }
    }
}

/**
 * 在线人数和 ITAD 排名并排，两个都有时中间一条竖线。
 * Players online and the ITAD rank side by side, with a vertical divider when both are there.
 */
@Composable
private fun Metrics(players: Int?, rank: Int?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (players != null) {
            Metric(
                iconRes = R.drawable.group_fill1_24px,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                value = formattedInteger(value = players),
                label = stringResource(R.string.game_detail_players_online),
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
        if (players != null && rank != null) {
            VerticalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        if (rank != null) {
            Metric(
                iconRes = R.drawable.military_tech_fill1_24px,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                value = stringResource(R.string.game_detail_rank_value, rank),
                label = stringResource(R.string.game_detail_itad_rank),
                valueColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Metric(
    @DrawableRes iconRes: Int,
    containerColor: Color,
    iconColor: Color,
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DetailIconTile(iconRes = iconRes, containerColor = containerColor, iconColor = iconColor)
        Column {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 按当前语言加千位分隔符的整数。
 * An integer with the current locale's grouping separators.
 */
@Composable
private fun formattedInteger(value: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    return formatter.format(value)
}

// MARK: - 预览 / Previews

@Preview(heightDp = 760)
@Composable
private fun ItadInfoSectionLoadedPreview() {
    SteaLTheme(darkTheme = true) {
        Column(modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)) {
            ItadInfoSectionView(state = InfoLoadState.Loaded(info = previewGameInfo), onRetry = {})
        }
    }
}

@Preview
@Composable
private fun ItadInfoSectionOtherStatesPreview() {
    val states = listOf(InfoLoadState.Loading, InfoLoadState.NotFound, InfoLoadState.Failed)
    SteaLTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            for (state in states) {
                ItadInfoSectionView(state = state, onRetry = {})
            }
        }
    }
}
