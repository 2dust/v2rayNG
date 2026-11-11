package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.RxGlassButton
import com.v2ray.ang.ui.compose.RxGlassPanel

@Composable
fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    isTesting: Boolean,
    pingText: String?,
    canConnect: Boolean,
    needsAppSelection: Boolean,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pingCheckDescription = stringResource(R.string.connection_test_pending)
    RxGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Tapping the status line re-checks the ping while connected; there is no
            // separate lightning button, so the user cannot hit it by accident.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = isRunning && !isTesting) {
                        onAction(MainAction.TestCurrentServer)
                    }
                    .semantics {
                        if (isRunning) contentDescription = pingCheckDescription
                    }
                    .padding(vertical = 2.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) colors.tertiary else colors.outline)
                )
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.primary,
                    )
                } else if (pingText != null) {
                    Text(
                        text = pingText,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.tertiary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.tertiary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RxGlassButton(
                    onClick = { onAction(MainAction.ToggleService) },
                    enabled = canConnect,
                    accent = true,
                    modifier = Modifier.weight(1f).height(60.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_power_24dp
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(
                                if (isRunning) R.string.rx_disconnect else R.string.rx_connect
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (isRunning) {
                            Text(
                                text = pingText
                                    ?: stringResource(R.string.rx_ping_tap_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalContentColor.current.copy(alpha = 0.78f),
                            )
                        }
                    }
                }
                RxGlassButton(
                    onClick = { onNavigate(MainDestination.PerAppProxy) },
                    modifier = Modifier.padding(start = 10.dp).size(60.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_per_apps_24dp),
                        contentDescription = stringResource(R.string.rx_app_routing),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            if (!isRunning && !canConnect) {
                Text(
                    text = stringResource(
                        if (needsAppSelection) R.string.rx_select_apps_hint else R.string.rx_connect_hint
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}
