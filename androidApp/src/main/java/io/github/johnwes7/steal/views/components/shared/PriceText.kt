package io.github.johnwes7.steal.views.components.shared

import android.icu.text.NumberFormat
import android.icu.util.Currency
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.shared.models.itad.ItadPrice
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 把 [price] 按系统语言格式化成带货币的文字，金额为 0 时是「Free」。
 * Formats [price] with its currency for the system locale; "Free" when the amount is zero.
 *
 * 小数位跟着货币走（日元、韩元没有小数），符号跟着语言走（英文澳区是 `A$`，澳洲英语是 `$`）。
 * Fraction digits follow the currency (none for yen or won) and the symbol follows the locale (`A$` in US English,
 * `$` in Australian English).
 */
@Composable
fun formattedPrice(price: ItadPrice): String {
    if (price.amountInt == 0) {
        return stringResource(R.string.price_free)
    }

    // 1. 取当前语言：语言设置变了会跟着重组
    // 1. Read the current locale: a language change recomposes
    val locale = LocalConfiguration.current.locales[0]

    // 2. 每种语言 + 货币建一次格式器：setCurrency 会改它自己，不能跨货币共用
    // 2. Build one formatter per locale and currency: setCurrency mutates it, so it can't be shared across currencies
    val formatter = remember(locale, price.currency) {
        val numberFormat = NumberFormat.getCurrencyInstance(locale)
        numberFormat.currency = Currency.getInstance(price.currency)
        numberFormat
    }

    // 3. 格式化
    // 3. Format
    return formatter.format(price.amount)
}

/**
 * 当前价：默认品牌绿、加粗、等宽数字，免费时是「Free」。
 * The current price: brand green by default, bold, with tabular figures; "Free" when there is nothing to pay.
 *
 * 放在容器色上时，[color] 传配对的文字色（如 `onPrimaryContainer`）。
 * On a container color, pass the matching content color (such as `onPrimaryContainer`) as [color].
 */
@Composable
fun DealCurrentPrice(
    price: ItadPrice,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = formattedPrice(price = price),
        style = style.copy(fontFeatureSettings = TABULAR_FIGURES),
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * 划线原价：只在 [regular] 确实高于 [price] 时显示，朗读成「Was A$90.00」。
 * The struck-through regular price: only shown when [regular] is actually above [price], read out as "Was A$90.00".
 */
@Composable
fun DealRegularPrice(
    regular: ItadPrice,
    price: ItadPrice,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (regular.amountInt <= price.amountInt) {
        return
    }

    val text = formattedPrice(price = regular)
    val description = stringResource(R.string.deal_regular_price_description, text)
    Text(
        text = text,
        style = style.copy(fontFeatureSettings = TABULAR_FIGURES),
        color = color,
        textDecoration = TextDecoration.LineThrough,
        maxLines = 1,
        modifier = modifier.semantics { contentDescription = description },
    )
}

/**
 * 等宽数字（OpenType `tnum`）：价格的每一位宽度相同，上下几行能对齐。
 * Tabular figures (OpenType `tnum`): every digit has the same width, so prices line up across rows.
 */
private const val TABULAR_FIGURES = "tnum"

@Preview
@Composable
private fun PriceTextPreview() {
    SteaLTheme {
        val price = ItadPrice(amount = 40.43, amountInt = 4043, currency = "AUD")
        val regular = ItadPrice(amount = 90.0, amountInt = 9000, currency = "AUD")
        Column {
            DealCurrentPrice(price = price, style = MaterialTheme.typography.headlineSmall)
            DealRegularPrice(regular = regular, price = price, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
