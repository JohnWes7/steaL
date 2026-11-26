package io.github.johnwes7.steal.views.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.johnwes7.steal.R
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * 页面底部的 ID：Steam appID 一直有；[itadID] 是 `null`（还没拿到、没收录、试玩版）时不显示那一行。
 * The IDs at the bottom of the page: the Steam appID is always there; the ITAD line is left out while [itadID] is
 * `null` (not in yet, not listed, or a demo).
 */
@Composable
fun DetailFooterView(appID: Int, itadID: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FooterRow(label = stringResource(R.string.game_detail_steam_app_id), value = appID.toString())
        if (itadID != null) {
            FooterRow(label = stringResource(R.string.game_detail_itad_id), value = itadID)
        }
    }
}

@Composable
private fun FooterRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview
@Composable
private fun DetailFooterPreview() {
    SteaLTheme(darkTheme = true) {
        Column(modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)) {
            DetailFooterView(appID = 3357650, itadID = "01973dd3-e247-712c-aaee-acff181a3175")
        }
    }
}
