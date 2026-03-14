package com.myweld.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.myweld.app.data.model.WeldState
import com.myweld.app.ui.theme.MyWeldColors

/**
 * Status indicator with animated pulsing dot and state label.
 */
@Composable
fun StatusIndicator(
    state: WeldState,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = when (state) {
            WeldState.IDLE -> MyWeldColors.StateIdle
            WeldState.READY -> MyWeldColors.StateReady
            WeldState.CHARGING -> MyWeldColors.StateCharging
            WeldState.FIRING -> MyWeldColors.StateFiring
            WeldState.COOLDOWN -> MyWeldColors.StateCharging  // Calm blue — settling
            WeldState.BLOCKED -> MyWeldColors.StateError      // Red — blocked
            WeldState.ERROR -> MyWeldColors.StateError
        },
        animationSpec = tween(300),
        label = "statusColor",
    )

    // Pulse animation for active or warning states
    val shouldPulse = state == WeldState.CHARGING || state == WeldState.READY ||
        state == WeldState.BLOCKED
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (shouldPulse) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        // Indicator dot with glow
        Box(
            modifier = Modifier
                .size(14.dp)
                .alpha(pulseAlpha)
                .shadow(
                    elevation = if (shouldPulse) 4.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = color,
                    spotColor = color,
                )
                .background(color = color, shape = CircleShape),
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = state.label,
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
    }
}
