package io.nikdmitryuk.ultraclient.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.nikdmitryuk.ultraclient.domain.model.VpnState
import io.nikdmitryuk.ultraclient.presentation.theme.UltraConnected
import io.nikdmitryuk.ultraclient.presentation.theme.UltraConnecting
import io.nikdmitryuk.ultraclient.presentation.theme.UltraError

@Composable
fun VpnStatusIndicator(state: VpnState, modifier: Modifier = Modifier) {
    val (color, label) = when (state) {
        is VpnState.Connected -> UltraConnected to "Connected"
        is VpnState.Connecting -> UltraConnecting to "Connecting..."
        is VpnState.Error -> UltraError to "Error"
        VpnState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant to "Disconnected"
    }

    val pulse by if (state is VpnState.Connecting) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "alpha"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1f) }.let {
            object : androidx.compose.runtime.State<Float> {
                override val value: Float = 1f
            }
        }.let {
            androidx.compose.runtime.remember { it }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
                .alpha(pulse)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}
