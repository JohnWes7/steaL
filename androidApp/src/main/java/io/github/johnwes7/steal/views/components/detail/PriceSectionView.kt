package io.github.johnwes7.steal.views.components.detail

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.previews.previewPriceGame
import io.github.johnwes7.steal.shared.models.itad.ItadPrice
import io.github.johnwes7.steal.shared.models.itad.ItadPriceDeal
import io.github.johnwes7.steal.shared.models.itad.ItadPriceGame
import io.github.johnwes7.steal.shared.viewmodels.GameDetailViewModel.PricesLoadState
import io.github.johnwes7.steal.views.components.shared.DealCurrentPrice
import io.github.johnwes7.steal.views.components.shared.DealRegularPrice
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 当前报价看哪些商店：只看能在 Steam 激活的，或全部。
 * Which shops the current offers show: only those that activate on Steam, or all of them.
 */
enum class OfferFilter {
    Steam,
    All,
}

/**
 * 比价区块：当前报价（Steam / All 筛选，收起时只留最便宜的一条）和三档历史低价；比价晚于 Steam 详情到，所以自带加载中。
 * The price section: the current offers (Steam / All filter, only the cheapest one while collapsed) and the three
 * historical lows; prices arrive after the Steam details, so it carries its own loading state.
 *
 * 筛选和展开状态由页面持有，重试之后不会丢。`Hidden` 时什么都不显示。
 * The page owns the filter and expanded state so a retry keeps them. `Hidden` shows nothing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PriceSectionView(
    state: PricesLoadState,
    offerFilter: OfferFilter,
    onSelectOfferFilter: (OfferFilter) -> Unit,
    isShowingAllOffers: Boolean,
    onToggleOffers: () -> Unit,
    onRetry: () -> Unit,
    onOfferClick: (ItadPriceDeal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.game_detail_section_prices)
    when (state) {
        PricesLoadState.Hidden -> Unit
        PricesLoadState.Loading -> DetailSection(title = title, modifier = modifier) {
            LoadingIndicator(modifier = Modifier.size(40.dp))
        }
        // 查了没有：不给重试，重试也不会变。
        // Checked and missing: no retry, since retrying wouldn't change anything.
        PricesLoadState.NotFound -> DetailSection(title = title, modifier = modifier) {
            DetailUnavailablePrompt(message = stringResource(R.string.game_detail_prices_not_found), onRetry = null)
        }
        PricesLoadState.Failed -> DetailSection(title = title, modifier = modifier) {
            DetailUnavailablePrompt(message = stringResource(R.string.game_detail_prices_failed), onRetry = onRetry)
        }
        is PricesLoadState.Loaded -> Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            CurrentOffers(
                prices = state.prices,
                offerFilter = offerFilter,
                onSelectOfferFilter = onSelectOfferFilter,
                isShowingAllOffers = isShowingAllOffers,
                onToggleOffers = onToggleOffers,
                onOfferClick = onOfferClick,
            )
            PriceHistory(prices = state.prices)
        }
        // 永久免费：历史低价都是 0，不显示。
        // Free forever: every historical low is zero, so they're left out.
        is PricesLoadState.LoadedWithoutHistory -> CurrentOffers(
            prices = state.prices,
            offerFilter = offerFilter,
            onSelectOfferFilter = onSelectOfferFilter,
            isShowingAllOffers = isShowingAllOffers,
            onToggleOffers = onToggleOffers,
            onOfferClick = onOfferClick,
            modifier = modifier,
        )
    }
}

// MARK: - 当前报价 / Current offers

/**
 * 当前报价：筛选按钮、最便宜的一条，其余的展开时带动画出现。
 * The current offers: the filter buttons, the cheapest offer, and the rest animating in when expanded.
 */
@Composable
private fun CurrentOffers(
    prices: ItadPriceGame,
    offerFilter: OfferFilter,
    onSelectOfferFilter: (OfferFilter) -> Unit,
    isShowingAllOffers: Boolean,
    onToggleOffers: () -> Unit,
    onOfferClick: (ItadPriceDeal) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 1. 按筛选取报价，从便宜到贵
    // 1. Pick the offers for the filter, cheapest first
    val selectedDeals = if (offerFilter == OfferFilter.Steam) prices.steamDeals else prices.deals
    val bestDeal = if (offerFilter == OfferFilter.Steam) prices.bestSteamDeal else prices.bestDeal
    val sortedDeals = selectedDeals.sortedBy { deal -> deal.price.amountInt }
    val cheapestDeal = sortedDeals.firstOrNull()
    val otherDeals = sortedDeals.drop(1)

    // 2. 筛选按钮 + 报价
    // 2. The filter buttons and the offers
    DetailSection(title = stringResource(R.string.game_detail_section_current_offers), modifier = modifier) {
        OfferFilterButtons(
            selected = offerFilter,
            steamCount = prices.steamDeals.size,
            allCount = prices.deals.size,
            onSelect = onSelectOfferFilter,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (cheapestDeal == null) {
            Text(
                text = stringResource(R.string.game_detail_offers_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            OfferRow(deal = cheapestDeal, isBest = cheapestDeal == bestDeal, onClick = { onOfferClick(cheapestDeal) })
        }

        // 最便宜的一条一直在；其余的放进 AnimatedVisibility，展开 / 收起才有动画（直接换列表会一下跳出来）。
        // The cheapest offer always shows; the rest sit in AnimatedVisibility so expanding animates (swapping the list
        // would just jump).
        AnimatedVisibility(visible = isShowingAllOffers) {
            Column {
                for (deal in otherDeals) {
                    OfferRow(deal = deal, isBest = deal == bestDeal, onClick = { onOfferClick(deal) })
                }
            }
        }

        if (otherDeals.isNotEmpty()) {
            DetailMoreButton(
                isExpanded = isShowingAllOffers,
                onClick = onToggleOffers,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

/**
 * Steam / All 筛选：M3 Expressive 用连接式按钮组代替分段按钮，每个按钮带报价数。
 * The Steam / All filter: M3 Expressive replaces segmented buttons with a connected button group; each button shows
 * its offer count.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OfferFilterButtons(
    selected: OfferFilter,
    steamCount: Int,
    allCount: Int,
    onSelect: (OfferFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ToggleButton(
            checked = selected == OfferFilter.Steam,
            onCheckedChange = { _ -> onSelect(OfferFilter.Steam) },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            modifier = Modifier
                .weight(1f)
                .semantics { role = Role.RadioButton },
        ) {
            Text(text = stringResource(R.string.game_detail_offers_steam, steamCount), maxLines = 1)
        }
        ToggleButton(
            checked = selected == OfferFilter.All,
            onCheckedChange = { _ -> onSelect(OfferFilter.All) },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            modifier = Modifier
                .weight(1f)
                .semantics { role = Role.RadioButton },
        ) {
            Text(text = stringResource(R.string.game_detail_offers_all, allCount), maxLines = 1)
        }
    }
}

/**
 * 一行报价：最低价是实心主色块；其他行左边一条竖线，打折是主色、原价是淡色，下面一条分隔线。
 * One offer row: the cheapest is a solid primary slab; other rows get a leading bar (primary on sale, muted at full
 * price) and a divider below.
 */
@Composable
private fun OfferRow(deal: ItadPriceDeal, isBest: Boolean, onClick: () -> Unit) {
    if (isBest) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            OfferRowContent(deal = deal, isBest = true, modifier = Modifier.padding(16.dp))
        }
        return
    }

    val isOnSale = deal.cut > 0
    val barColor = if (isOnSale) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(onClick = onClick, color = Color.Transparent) {
        Column {
            Row(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(color = barColor, shape = CircleShape),
                )
                OfferRowContent(deal = deal, isBest = false, modifier = Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * 一行报价的内容：左边商店、折扣、DRM，右边现价、划线原价和「离开 App」图标。
 * One offer's content: shop, discount and DRM on the left; the price, struck-through regular price and the "leaves
 * the app" icon on the right.
 */
@Composable
private fun OfferRowContent(deal: ItadPriceDeal, isBest: Boolean, modifier: Modifier = Modifier) {
    // 1. 文字颜色：实心块上跟着块的 onPrimary 走，普通行折扣用 secondary、其余用次要文字色
    // 1. Text colors: on the slab they follow its onPrimary; on plain rows the discount is secondary, the rest muted
    val mutedColor = if (isBest) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val discountColor = when {
        isBest -> MaterialTheme.colorScheme.onPrimary
        deal.cut > 0 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val priceColor = if (isBest) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    // 2. 文字内容
    // 2. The text
    val discountText = if (deal.cut > 0) {
        stringResource(R.string.game_detail_offer_save, deal.cut)
    } else {
        stringResource(R.string.game_detail_offer_current_price)
    }
    val drmNames = deal.drm.map { platform -> platform.name }
    val drmText = if (drmNames.isEmpty()) {
        stringResource(R.string.game_detail_offer_drm_unavailable)
    } else {
        drmNames.joinToString(separator = ", ")
    }

    // 3. 左右两栏
    // 3. The two columns
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OfferShopName(name = deal.shop.name, isBest = isBest)
            Text(text = discountText, style = MaterialTheme.typography.bodyMedium, color = discountColor)
            Text(
                text = drmText,
                style = MaterialTheme.typography.labelMedium,
                color = mutedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            DealCurrentPrice(price = deal.price, style = MaterialTheme.typography.titleLarge, color = priceColor)
            DealRegularPrice(
                regular = deal.regular,
                price = deal.price,
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor,
            )
        }
        Icon(
            painter = painterResource(R.drawable.open_in_new_24px),
            contentDescription = null,
            tint = mutedColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 商店名；最低价那一行后面加「Best」徽标（实心主色块上反过来用 onPrimary 底）。
 * The shop name; the cheapest row adds a "Best" badge (flipped to an onPrimary fill on the primary slab).
 */
@Composable
private fun OfferShopName(name: String, isBest: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (isBest) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.onPrimary) {
                Text(
                    text = stringResource(R.string.game_detail_offer_best),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// MARK: - 历史低价 / Price history

/**
 * 三档历史低价（全部 / 一年 / 三个月），有几档显示几档，价格一样也不合并；一档都没有时一行说明。
 * The three historical lows (all time / one year / three months), each one that exists, even when the prices match;
 * a one-line note when there are none.
 */
@Composable
private fun PriceHistory(prices: ItadPriceGame) {
    // 1. 有值的几档，颜色依次是 primary / secondary / 次要文字色
    // 1. The lows that exist, colored primary / secondary / muted in that order
    val history = prices.historyLow
    val lows = listOf(
        HistoryLow(labelRes = R.string.game_detail_history_all_time, price = history.all, accent = MaterialTheme.colorScheme.primary),
        HistoryLow(labelRes = R.string.game_detail_history_one_year, price = history.y1, accent = MaterialTheme.colorScheme.secondary),
        HistoryLow(
            labelRes = R.string.game_detail_history_three_months,
            price = history.m3,
            accent = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
    val existingLows = lows.filter { low -> low.price != null }

    // 2. 每档一行
    // 2. One row per low
    DetailSection(title = stringResource(R.string.game_detail_section_price_history)) {
        if (existingLows.isEmpty()) {
            Text(
                text = stringResource(R.string.game_detail_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (low in existingLows) {
            val price = low.price
            if (price == null) {
                continue
            }
            HistoryRow(label = stringResource(low.labelRes), price = price, accent = low.accent)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * 一档历史低价：文字、价格（可能没有）和强调色。
 * One historical low: its label, its price (possibly missing) and its accent color.
 */
private class HistoryLow(val labelRes: Int, val price: ItadPrice?, val accent: Color)

@Composable
private fun HistoryRow(label: String, price: ItadPrice, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(color = accent, shape = CircleShape),
        )
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        DealCurrentPrice(price = price, style = MaterialTheme.typography.titleLarge, color = accent)
    }
}

// MARK: - 预览 / Previews

@Preview(heightDp = 900)
@Composable
private fun PriceSectionLoadedPreview() {
    SteaLTheme(darkTheme = true) {
        Column(modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)) {
            PriceSectionView(
                state = PricesLoadState.Loaded(prices = previewPriceGame),
                offerFilter = OfferFilter.All,
                onSelectOfferFilter = {},
                isShowingAllOffers = true,
                onToggleOffers = {},
                onRetry = {},
                onOfferClick = {},
            )
        }
    }
}

@Preview(heightDp = 900)
@Composable
private fun PriceSectionOtherStatesPreview() {
    val states = listOf(
        PricesLoadState.Loading,
        PricesLoadState.LoadedWithoutHistory(prices = previewPriceGame),
        PricesLoadState.NotFound,
        PricesLoadState.Failed,
    )
    SteaLTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            for (state in states) {
                PriceSectionView(
                    state = state,
                    offerFilter = OfferFilter.Steam,
                    onSelectOfferFilter = {},
                    isShowingAllOffers = false,
                    onToggleOffers = {},
                    onRetry = {},
                    onOfferClick = {},
                )
            }
        }
    }
}
