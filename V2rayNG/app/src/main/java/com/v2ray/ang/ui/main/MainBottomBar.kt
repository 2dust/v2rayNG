package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.RxGlassButton
import com.v2ray.ang.ui.compose.RxGlassPanel

@Composable
fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    canConnect: Boolean,
    needsAppSelection: Boolean,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    RxGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                IconButton(
                    onClick = { onAction(MainAction.TestCurrentServer) },
                    enabled = isRunning,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_flash_on_24dp),
                        contentDescription = stringResource(R.string.connection_test_pending),
                        modifier = Modifier.size(20.dp),
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
                    Text(
                        text = stringResource(
                            if (isRunning) R.string.rx_disconnect else R.string.rx_connect
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
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
