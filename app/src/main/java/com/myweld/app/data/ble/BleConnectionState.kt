package com.myweld.app.data.ble

/**
 * BLE connection state sealed class for type-safe state management.
 */
sealed class BleConnectionState {
    data object Disconnected : BleConnectionState()
    data object Scanning : BleConnectionState()
    data object Connecting : BleConnectionState()
    /** GATT connected, waiting for PIN authentication from the user. */
    data object Authenticating : BleConnectionState()
    /** Fully connected and authenticated. */
    data object Connected : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
    data object Reconnecting : BleConnectionState()

    val isConnected: Boolean get() = this is Connected
    val isScanning: Boolean get() = this is Scanning
    val displayName: String
        get() = when (this) {
            is Disconnected -> "Disconnected"
            is Scanning -> "Scanning"
            is Connecting -> "Connecting…"
            is Authenticating -> "Authenticating…"
            is Connected -> "Connected"
            is Error -> "Error: $message"
            is Reconnecting -> "Reconnecting…"
        }
}
