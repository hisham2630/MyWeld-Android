package com.myweld.app.data.ble

/**
 * App-wide events emitted by the BLE manager and shown as snackbar toasts.
 */
sealed class AppEvent {
    data class Connected(val deviceName: String) : AppEvent()
    data object Disconnected : AppEvent()
    data object Reconnecting : AppEvent()
    data class ReconnectFailed(val reason: String) : AppEvent()
    data class CommandSent(val label: String) : AppEvent()
    data class CommandFailed(val label: String) : AppEvent()
    /** Firmware rejected a command because not authenticated yet. */
    data object AuthRequired : AppEvent()
    /** Authentication succeeded. */
    data object AuthSuccess : AppEvent()
    /** Authentication failed (wrong PIN). */
    data object AuthFailed : AppEvent()
    /** Authentication locked out after too many wrong attempts. */
    data class AuthLocked(val remainingSec: Int) : AppEvent()
}
