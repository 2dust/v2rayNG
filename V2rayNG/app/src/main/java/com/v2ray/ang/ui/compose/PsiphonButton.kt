package com.v2ray.ang.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

/**
 * "Connect to Psiphon" toggle, styled like the dark secondary pill of the design: a charcoal-violet pill with a
 * status dot that is violet while idle and green while a Psiphon profile runs. A tap only reports [onToggle];
 * what it does is up to the main screen's action handler.
 */
@Composable
fun PsiphonButton(
    connected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    val base = scheme.surfaceContainerHigh
    val accent by animateColorAsState(if (connected) XfGreen else scheme.secondary, label = "psiphonAccent")
    val outline by animateColorAsState(
        if (connected) XfGreen.copy(alpha = 0.55f) else Color.Transparent,
        label = "psiphonOutline",
    )
    val label = stringResource(if (connected) R.string.mx_psiphon_connected else R.string.mx_psiphon_connect)
    val stateText = stringResource(if (connected) R.string.mx_psiphon_state_on else R.string.mx_psiphon_state_off)
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
            .pressScale(source)
            .clip(GlassPillShape)
            .background(base)
            .border(BorderStroke(1.dp, outline), GlassPillShape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = stateText }
            .heightIn(min = 50.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * "Auto connect": runs real tests on the shown profiles and connects to the fastest one that works. Styled like the
 * white primary pill of the design (violet-filled in the day theme); a small spinner shows while it tests.
 */
@Composable
fun AutoConnectButton(
    testing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The same sliding pill as every other bar button of the app; it shows the spinner and the arrow while it tests.
    SlidingPillButton(
        text = stringResource(R.string.mx_auto_connect),
        onClick = onClick,
        modifier = modifier,
        enabled = !testing,
        busy = testing,
    )
}
