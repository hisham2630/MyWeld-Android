package com.myweld.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myweld.app.data.ble.BleConnectionState
import com.myweld.app.ui.theme.MyWeldColors

/**
 * BLE connection status badge.
 */
@Composable
fun ConnectionBadge(
    state: BleConnectionState,
    deviceName: String? = null,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = when (state) {
            is BleConnectionState.Connected -> MyWeldColors.StateReady
            is BleConnectionState.Connecting, is BleConnectionState.Reconnecting,
            is BleConnectionState.Authenticating -> MyWeldColors.StateCharging
            is BleConnectionState.Error -> MyWeldColors.StateError
            else -> MyWeldColors.StateIdle
        },
        animationSpec = tween(300),
        label = "connectionColor",
    )

    val icon = when (state) {
        is BleConnectionState.Connected -> Icons.Filled.Bluetooth
        is BleConnectionState.Connecting, is BleConnectionState.Reconnecting,
        is BleConnectionState.Authenticating,
        is BleConnectionState.Scanning -> Icons.Filled.BluetoothSearching
        else -> Icons.Filled.BluetoothDisabled
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = state.displayName,
                modifier = Modifier.size(16.dp),
                tint = color,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (state is BleConnectionState.Connected && deviceName != null) {
                    deviceName
                } else {
                    state.displayName
                },
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}
