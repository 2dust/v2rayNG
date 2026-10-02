package com.v2ray.ang.ui.subscription

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.Utils

@Composable
internal fun SubscriptionRow(
    subscription: SubscriptionItem,
    subscriptionName: String,
    actions: List<CustomAccessibilityAction>,
    onUpdate: (Boolean) -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val lastUpdated = Utils.formatTimestamp(subscription.lastUpdated)
    val lastUpdatedAccessibility = if (lastUpdated.isNotEmpty()) {
        stringResource(
            R.string.acc_last_updated,
            DateUtils.formatDateTime(
                context,
                subscription.lastUpdated,
                DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_SHOW_TIME or
                    DateUtils.FORMAT_SHOW_YEAR
            )
        )
    } else {
        ""
    }
    val subscriptionUpdateState = stringResource(
        if (subscription.enabled) R.string.acc_subscription_update_on
        else R.string.acc_subscription_update_off,
    )
    val updateActionLabel = stringResource(
        if (subscription.enabled) R.string.acc_disable_subscription_update
        else R.string.acc_enable_subscription_update
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = subscriptionName
                stateDescription = subscriptionUpdateState
                customActions = actions
                onClick(label = updateActionLabel, action = null)
            }
            .toggleable(
                value = subscription.enabled,
                role = Role.Switch,
                onValueChange = onUpdate,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subscription.remarks,
                modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subscription.url.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subscription.url,
                    modifier = Modifier.semantics { hideFromAccessibility() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (lastUpdated.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = lastUpdated,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = lastUpdatedAccessibility
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Row {
                if (subscription.url.isNotEmpty()) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.clearAndSetSemantics {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share_24dp),
                            contentDescription = null
                        )
                    }
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.clearAndSetSemantics {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit_24dp),
                        contentDescription = null
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.clearAndSetSemantics {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = null
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Switch(
                checked = subscription.enabled,
                onCheckedChange = null,
                modifier = Modifier.scale(0.7f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    checkedTrackColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}
