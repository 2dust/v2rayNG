package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.LocalAppColors

private val StatusBarHeight = 64.dp
private val BarHorizontalPadding = 16.dp
private val FabEndPadding = 24.dp
private val FabVerticalOffset = (-28).dp
private val FabIconSize = 24.dp

@Composable
fun MainBottomBar(
    statusText: String,
    isRunning: Boolean,
    onAction: (MainAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inactiveFab = LocalAppColors.current.fabInactive

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AppDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StatusBarHeight)
                    .clickable { onAction(MainAction.StatusBarClick) }
                    .padding(horizontal = BarHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        contentDescription = statusText
                    }
                )
            }
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }

        FloatingActionButton(
            onClick = { onAction(MainAction.ToggleService) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = FabEndPadding)
                .offset(y = FabVerticalOffset),
            containerColor = if (isRunning) MaterialTheme.colorScheme.secondary else inactiveFab,
        ) {
            Icon(
                painter = painterResource(
                    if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp
                ),
                contentDescription = stringResource(
                    if (isRunning) R.string.acc_stop else R.string.acc_start
                ),
                modifier = Modifier.size(FabIconSize),
                tint = LocalAppColors.current.fabContent
            )
        }
    }
}
