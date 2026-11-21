package io.github.johnwes7.steal.views.components.shared

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.shared.models.app.LoadError
import io.github.johnwes7.steal.views.theme.SteaLTheme

// MARK: - 通用说明块 / General message block

/**
 * 一块内容显示不出来时的说明：图标、标题、原因，可选一个操作按钮。
 * Explains why a block of content can't be shown: an icon, a title, the reason, and an optional action button.
 *
 * [actionLabel] 和 [onAction] 都给了才显示按钮。
 * The button only appears when both [actionLabel] and [onAction] are given.
 */
@Composable
fun ContentUnavailableView(
    @DrawableRes iconRes: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 图标放在浅色圆底上，比单独一个线框图标更像一块完整的提示。
        // The icon sits on a tinted circle so the message reads as one unit rather than a lone outline icon.
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

// MARK: - 加载失败 / Load failures

/**
 * 加载失败的提示：按 [error] 显示对应的图标、标题和原因，[onRetry] 不为 `null` 时带 Retry 按钮。
 * A load failure: shows the icon, title and reason for [error], with a Retry button when [onRetry] is not `null`.
 */
@Composable
fun LoadErrorView(
    error: LoadError,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ContentUnavailableView(
        iconRes = loadErrorIconRes(error = error),
        title = stringResource(loadErrorTitleRes(error = error)),
        message = stringResource(loadErrorMessageRes(error = error)),
        actionLabel = stringResource(R.string.action_retry),
        onAction = onRetry,
        modifier = modifier,
    )
}

@DrawableRes
private fun loadErrorIconRes(error: LoadError): Int {
    return when (error) {
        LoadError.Offline -> R.drawable.wifi_off_24px
        is LoadError.Server -> R.drawable.cloud_off_24px
        LoadError.Decoding -> R.drawable.help_24px
        LoadError.Unknown -> R.drawable.warning_24px
    }
}

@StringRes
private fun loadErrorTitleRes(error: LoadError): Int {
    return when (error) {
        LoadError.Offline -> R.string.load_error_offline_title
        is LoadError.Server -> R.string.load_error_server_title
        LoadError.Decoding -> R.string.load_error_decoding_title
        LoadError.Unknown -> R.string.load_error_unknown_title
    }
}

@StringRes
private fun loadErrorMessageRes(error: LoadError): Int {
    return when (error) {
        LoadError.Offline -> R.string.load_error_offline_message
        is LoadError.Server -> R.string.load_error_server_message
        LoadError.Decoding -> R.string.load_error_decoding_message
        LoadError.Unknown -> R.string.load_error_unknown_message
    }
}

// MARK: - 预览 / Previews

@Preview
@Composable
private fun ContentUnavailableViewPreview() {
    SteaLTheme {
        Column {
            ContentUnavailableView(
                iconRes = R.drawable.money_off_24px,
                title = "No deals found",
                message = "There are no discounted games to show right now. Try again later.",
                actionLabel = "Refresh",
                onAction = {},
            )
            LoadErrorView(error = LoadError.Offline, onRetry = {})
            LoadErrorView(error = LoadError.Server(statusCode = 503), onRetry = null)
        }
    }
}
