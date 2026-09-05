package com.v2ray.ang.ui.routing

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.RoutingRuleRow
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.StringOptions
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val KEY_DOMAIN_STRATEGY = "domain_strategy"
private const val KEY_RULE_TITLE = "rule_title"
private const val CONTENT_TYPE_RULE = "rule"

private val RowHorizontalPad = 16.dp
private val RowInnerGap = 4.dp
private val RowActionGap = 8.dp
private val LockIconSize = 16.dp

@Stable
class RoutingRowCallbacks(private val onAction: (RoutingAction) -> Unit) {
    fun edit(ruleId: String) = onAction(RoutingAction.EditRule(ruleId))
    fun toggle(ruleId: String, enabled: Boolean) = onAction(RoutingAction.ToggleRule(ruleId, enabled))
}

@Composable
fun RoutingRuleList(
    rules: List<RoutingRuleRow>,
    domainStrategies: StringOptions,
    selectedStrategy: String,
    onAction: (RoutingAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromId = from.key as? String ?: return@rememberReorderableLazyListState
        val toId = to.key as? String ?: return@rememberReorderableLazyListState
        onAction(RoutingAction.MoveRule(fromId, toId))
    }

    val callbacks = remember(onAction) { RoutingRowCallbacks(onAction) }

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
        checkedTrackColor = MaterialTheme.colorScheme.secondary,
    )

    LazyColumn(
        state = lazyListState,
        modifier = modifier
            .fillMaxSize()
            .verticalScrollbar(lazyListState),
        contentPadding = NavigationBarsBottomPadding(),
    ) {
        item(key = KEY_DOMAIN_STRATEGY) {
            SettingsListItem(
                title = stringResource(R.string.routing_settings_domain_strategy),
                entries = domainStrategies,
                values = domainStrategies,
                selectedValue = selectedStrategy,
                onSelected = { value -> onAction(RoutingAction.SelectDomainStrategy(value)) },
            )
        }
        item(key = KEY_RULE_TITLE) {
            Text(
                text = stringResource(R.string.routing_settings_rule_title),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(RowHorizontalPad),
            )
        }
        items(
            items = rules,
            key = { it.id },
            contentType = { CONTENT_TYPE_RULE },
        ) { rule ->
            ReorderableItem(reorderableState, key = rule.id) { isDragging ->
                ReorderableListItem(scope = this, isDragging = isDragging) {
                    RoutingRuleRowItem(
                        rule = rule,
                        callbacks = callbacks,
                        switchColors = switchColors,
                    )
                }
                ItemDivider()
            }
        }
    }
}

@Composable
private fun RoutingRuleRowItem(
    rule: RoutingRuleRow,
    callbacks: RoutingRowCallbacks,
    switchColors: SwitchColors,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RowHorizontalPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.remarks,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (rule.locked) {
                    Spacer(modifier = Modifier.width(RowInnerGap))
                    Icon(
                        painter = painterResource(R.drawable.ic_lock_24dp),
                        contentDescription = stringResource(R.string.acc_locked),
                        modifier = Modifier.size(LockIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (rule.detail.isNotEmpty()) {
                Spacer(modifier = Modifier.height(RowInnerGap))
                Text(
                    text = rule.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (rule.outboundTag.isNotEmpty()) {
                Spacer(modifier = Modifier.height(RowInnerGap))
                Text(
                    text = rule.outboundTag,
                    style = MaterialTheme.typography.labelMedium,
                    color = colorConfigType,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(start = RowActionGap),
        ) {
            IconButton(onClick = { callbacks.edit(rule.id) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit_24dp),
                    contentDescription = stringResource(R.string.acc_edit),
                )
            }
            Spacer(modifier = Modifier.height(RowInnerGap))
            Switch(
                checked = rule.enabled,
                onCheckedChange = { enabled -> callbacks.toggle(rule.id, enabled) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .scale(0.8f),
                colors = switchColors,
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewRoutingRuleList() {
    AppTheme {
        RoutingRuleList(
            rules = listOf(
                RoutingRuleRow(
                    id = "1",
                    remarks = "Google services",
                    detail = "geosite:google",
                    outboundTag = "proxy",
                    locked = true,
                    enabled = true,
                ),
                RoutingRuleRow(
                    id = "2",
                    remarks = "Bypass LAN",
                    detail = "geoip:private",
                    outboundTag = "direct",
                    locked = false,
                    enabled = false,
                ),
            ),
            domainStrategies = StringOptions(listOf("AsIs", "IPIfNonMatch", "IPOnDemand")),
            selectedStrategy = "AsIs",
            onAction = {},
        )
    }
}
