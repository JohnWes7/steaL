package io.github.johnwes7.steal.views.components.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.shared.models.app.WishlistFilter
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 愿望单顶部的筛选：撑满一行的连接式按钮组，四个筛选项选一个，点了调 [onSelect]。
 * The filter at the top of the wishlist: a full-width connected button group where one of four filters is picked,
 * and a tap calls [onSelect].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WishlistFilterBar(
    selected: WishlistFilter,
    onSelect: (WishlistFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = WishlistFilter.entries
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        for ((index, filter) in filters.withIndex()) {
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                filters.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = filter == selected,
                onCheckedChange = { _ -> onSelect(filter) },
                shapes = shapes,
                // 四个按钮分一行，默认的左右留白会把「Upcoming」挤成省略号。
                // Four buttons share one row; the default side padding would squeeze "Upcoming" into an ellipsis.
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
            ) {
                Text(text = wishlistFilterLabel(filter = filter), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * 筛选项在界面上的文字。
 * A filter's label in the UI.
 */
@Composable
fun wishlistFilterLabel(filter: WishlistFilter): String {
    val labelRes = when (filter) {
        WishlistFilter.All -> R.string.wishlist_filter_all
        WishlistFilter.OnSale -> R.string.wishlist_filter_on_sale
        WishlistFilter.AllTimeLow -> R.string.wishlist_filter_all_time_low
        WishlistFilter.Upcoming -> R.string.wishlist_filter_upcoming
    }
    return stringResource(labelRes)
}

@Preview
@Composable
private fun WishlistFilterBarPreview() {
    SteaLTheme(darkTheme = true) {
        WishlistFilterBar(
            selected = WishlistFilter.OnSale,
            onSelect = { _ -> },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        )
    }
}
