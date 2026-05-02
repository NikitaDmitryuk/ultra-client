package io.nikdmitryuk.ultraclient.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.nikdmitryuk.ultraclient.domain.model.VpnState
import io.nikdmitryuk.ultraclient.presentation.theme.UltraConnected
import io.nikdmitryuk.ultraclient.presentation.theme.UltraConnecting
import io.nikdmitryuk.ultraclient.presentation.theme.UltraError

@Composable
fun PowerButton(
    state: VpnState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetColor =
        when (state) {
            is VpnState.Connected -> UltraConnected
            is VpnState.Connecting -> UltraConnecting
            is VpnState.Error -> UltraError
            VpnState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val color by animateColorAsState(targetColor, tween(400), label = "btn_color")
    val isLoading = state is VpnState.Connecting

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(120.dp)) {
        if (isLoading) {
            CircularProgressIndicator(
                color = color,
                strokeWidth = 3.dp,
                modifier = Modifier.size(120.dp),
            )
        }
        IconButton(
            onClick = { if (!isLoading) onClick() },
            modifier =
                Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .border(2.dp, color, CircleShape),
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Power,
                contentDescription = "Toggle VPN",
                tint = color,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}
