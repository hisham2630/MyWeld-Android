package com.myweld.app.data.model

/**
 * Persisted BLE device info for multi-device support.
 */
data class SavedDevice(
    val macAddress: String,
    val name: String,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
)
