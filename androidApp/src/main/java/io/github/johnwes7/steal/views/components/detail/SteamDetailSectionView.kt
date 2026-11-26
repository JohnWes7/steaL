package io.github.johnwes7.steal.views.components.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.previews.previewAppDetails
import io.github.johnwes7.steal.shared.models.steam.SteamAppDetails
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 收起时简介显示的行数。
 * How many lines of the description show while collapsed.
 */
private const val COLLAPSED_DESCRIPTION_LINES = 3

/**
 * Steam 简介区块：收起时简介三行；展开后是全文、开发商、发行商、分类和平台。什么都没有时整块不显示。
 * The Steam description section: three lines of description when collapsed; the full text, developers, publishers,
 * categories and platforms when expanded. Nothing shows when there is nothing to show.
 *
 * 展开状态由页面持有（[isExpanded] / [onToggleExpanded]），区块重新组合也不会丢。
 * The page owns the expanded state ([isExpanded] / [onToggleExpanded]), so it survives the section recomposing.
 */
@Composable
fun SteamDetailSectionView(
    details: SteamAppDetails,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 1. 整理要显示的内容：空字符串当没有
    // 1. Gather what to show: empty strings count as missing
    val description = details.shortDescription
    val allDevelopers = details.developers.orEmpty()
    val developers = allDevelopers.filter { name -> name.isNotEmpty() }
    val allPublishers = details.publishers.orEmpty()
    val publishers = allPublishers.filter { name -> name.isNotEmpty() }
    val categoryNames = details.categories.orEmpty().mapNotNull { category -> category.description }
    val nonEmptyCategoryNames = categoryNames.filter { name -> name.isNotEmpty() }
    val platforms = platformText(details = details)

    // 2. 什么都没有就不显示
    // 2. Show nothing when there is nothing
    val hasCredits = developers.isNotEmpty() || publishers.isNotEmpty()
    val hasMetadata = nonEmptyCategoryNames.isNotEmpty() || platforms.isNotEmpty()
    if (description == null && !hasCredits && !hasMetadata) {
        return
    }

    // 3. 简介 + 展开内容 + 展开按钮
    // 3. The description, the expanded details and the toggle
    DetailSection(title = stringResource(R.string.game_detail_section_about), modifier = modifier) {
        if (description != null) {
            DescriptionText(description = description, isExpanded = isExpanded)
        }
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (developers.isNotEmpty()) {
                    InfoRow(
                        label = stringResource(R.string.game_detail_developer),
                        value = developers.joinToString(separator = ", "),
                    )
                }
                if (publishers.isNotEmpty()) {
                    InfoRow(
                        label = stringResource(R.string.game_detail_publisher),
                        value = publishers.joinToString(separator = ", "),
                    )
                }
                if (nonEmptyCategoryNames.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.game_detail_categories),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CategoryTags(names = nonEmptyCategoryNames)
                }
                if (platforms.isNotEmpty()) {
                    InfoRow(label = stringResource(R.string.game_detail_platforms), value = platforms)
                }
            }
        }
        DetailMoreButton(isExpanded = isExpanded, onClick = onToggleExpanded, modifier = Modifier.align(Alignment.End))
    }
}

/**
 * 简介正文：解码 Steam 带的 HTML 实体（`&quot;` 等）；收起时三行，展开收起时高度带动画。
 * The description body: decodes Steam's HTML entities (`&quot;` and so on); three lines when collapsed, with the
 * height animating between the two.
 */
@Composable
private fun DescriptionText(description: String, isExpanded: Boolean) {
    val decoded = AnnotatedString.fromHtml(htmlString = description)
    val maxLines = if (isExpanded) Int.MAX_VALUE else COLLAPSED_DESCRIPTION_LINES
    Text(
        text = decoded,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.animateContentSize(),
    )
}

/**
 * 一行「标签 + 值」，标签列固定宽度，值对齐成一列。
 * One "label + value" row; the label column has a fixed width so the values line up.
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * 分类标签，自动换行、全部显示。
 * The category tags, wrapping onto as many lines as they need.
 */
@Composable
private fun CategoryTags(names: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (name in names) {
            DetailTag(text = name)
        }
    }
}

/**
 * 支持的平台，拼成「Windows · macOS」；一个都没有时是空字符串。
 * The supported platforms joined as "Windows · macOS"; an empty string when there are none.
 */
@Composable
private fun platformText(details: SteamAppDetails): String {
    val platforms = details.platforms
    if (platforms == null) {
        return ""
    }
    val names = mutableListOf<String>()
    if (platforms.windows == true) {
        names.add(stringResource(R.string.game_detail_platform_windows))
    }
    if (platforms.mac == true) {
        names.add(stringResource(R.string.game_detail_platform_macos))
    }
    if (platforms.linux == true) {
        names.add(stringResource(R.string.game_detail_platform_linux))
    }
    return names.joinToString(separator = " · ")
}

// MARK: - 预览 / Previews

@Preview
@Composable
private fun SteamDetailSectionCollapsedPreview() {
    SteaLTheme(darkTheme = true) {
        Column(modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)) {
            SteamDetailSectionView(details = previewAppDetails, isExpanded = false, onToggleExpanded = {})
        }
    }
}

@Preview
@Composable
private fun SteamDetailSectionExpandedPreview() {
    SteaLTheme(darkTheme = true) {
        Column(modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)) {
            SteamDetailSectionView(details = previewAppDetails, isExpanded = true, onToggleExpanded = {})
        }
    }
}
