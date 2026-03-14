package com.myweld.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myweld.app.ui.theme.MyWeldColors
import com.myweld.app.ui.theme.ValueSmallTextStyle
import com.myweld.app.ui.theme.ValueTextStyle

/**
 * Premium voltage bar with gradient fill, glow effect, and charging status.
 *
 * Color transitions: Red → Orange → Yellow → Green based on charge level.
 * Shows "⚡ Charging XX%" when not fully charged, "✓ Fully Charged" at 100%.
 * Animated glow pulse when fully charged.
 */
@Composable
fun VoltageBar(
    voltageMv: Int,
    chargePercent: Int = 0,
    maxVoltageMv: Int = 5700,
    fullVoltageMv: Int = 5500,
    modifier: Modifier = Modifier,
) {
    val voltage = voltageMv / 1000f
    val maxVoltage = maxVoltageMv / 1000f
    val percent = chargePercent.toFloat().coerceIn(0f, 100f)
    val fillFraction = (percent / 100f).coerceIn(0f, 1f)

    val isFullyCharged = chargePercent >= 100
    val isCharging = chargePercent in 1..99

    // Glow animation when fully charged
    val infiniteTransition = rememberInfiniteTransition(label = "voltageGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isFullyCharged) 0.8f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    // Charging pulse animation
    val chargingAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isCharging) 0.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chargingAlpha",
    )

    // Determine bar color based on percentage
    val barColor by animateColorAsState(
        targetValue = when {
            percent < 25f -> MyWeldColors.VoltageRed
            percent < 50f -> MyWeldColors.VoltageOrange
            percent < 75f -> MyWeldColors.VoltageYellow
            else -> MyWeldColors.VoltageGreen
        },
        animationSpec = tween(500),
        label = "barColor",
    )

    val glowColor = if (isFullyCharged) MyWeldColors.GlowGreen else barColor

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "%.2f".format(voltage),
                        style = ValueTextStyle,
                        color = barColor,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "V",
                        style = ValueSmallTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "/ %.1f V".format(maxVoltage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "%d%%".format(chargePercent.coerceIn(0, 100)),
                    style = ValueSmallTextStyle.copy(fontWeight = FontWeight.Bold),
                    color = barColor,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Voltage bar with glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                ) {
                    val barHeight = size.height
                    val barWidth = size.width
                    val cornerRadius = CornerRadius(8.dp.toPx())

                    // Background track
                    drawRoundRect(
                        color = Color(0xFF2A2A3A),
                        size = Size(barWidth, barHeight),
                        cornerRadius = cornerRadius,
                    )

                    // Filled portion with gradient
                    if (fillFraction > 0.01f) {
                        val fillWidth = barWidth * fillFraction
                        val gradient = Brush.horizontalGradient(
                            colors = listOf(
                                barColor.copy(alpha = 0.7f),
                                barColor,
                            ),
                            startX = 0f,
                            endX = fillWidth,
                        )
                        drawRoundRect(
                            brush = gradient,
                            size = Size(fillWidth, barHeight),
                            cornerRadius = cornerRadius,
                        )

                        // Glow overlay on the fill
                        drawRoundRect(
                            color = glowColor.copy(alpha = glowAlpha * 0.3f),
                            size = Size(fillWidth, barHeight),
                            cornerRadius = cornerRadius,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Charging status label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    isFullyCharged -> {
                        Text(
                            text = "✓ Fully Charged",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MyWeldColors.VoltageGreen,
                        )
                    }
                    isCharging -> {
                        Text(
                            text = "⚡ Charging  %d%%".format(chargePercent),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MyWeldColors.StateCharging.copy(alpha = chargingAlpha),
                            ),
                        )
                    }
                    else -> {
                        Text(
                            text = "⏻ Not Charging",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}
