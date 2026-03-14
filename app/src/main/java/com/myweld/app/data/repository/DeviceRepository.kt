package com.myweld.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myweld.app.data.model.SavedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "myweld_prefs")

/**
 * Persists app preferences and saved BLE devices using DataStore.
 */
class DeviceRepository(private val context: Context) {

    companion object {
        private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        private val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        private val KEY_SAVED_DEVICES = stringSetPreferencesKey("saved_devices")
        private val KEY_LAST_DEVICE_MAC = stringPreferencesKey("last_device_mac")
        // Stores entries like "AA:BB:CC:DD:EE:FF=1234"
        private val KEY_DEVICE_PINS = stringSetPreferencesKey("device_pins")

    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DARK_MODE] ?: true // default dark
    }

    val autoReconnect: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_RECONNECT] ?: true
    }

    val savedDevices: Flow<List<SavedDevice>> = context.dataStore.data.map { prefs ->
        val deviceSet = prefs[KEY_SAVED_DEVICES] ?: emptySet()
        deviceSet.mapNotNull { json ->
            try {
                val obj = JSONObject(json)
                SavedDevice(
                    macAddress = obj.getString("mac"),
                    name = obj.getString("name"),
                    lastSeenTimestamp = obj.getLong("lastSeen"),
                )
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.lastSeenTimestamp }
    }

    val lastDeviceMac: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_DEVICE_MAC]
    }

    suspend fun setDarkMode(dark: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_DARK_MODE] = dark }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_RECONNECT] = enabled }
    }

    suspend fun saveDevice(device: SavedDevice) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_SAVED_DEVICES]?.toMutableSet() ?: mutableSetOf()
            // Remove any previous entry for this MAC
            existing.removeAll { json ->
                try {
                    JSONObject(json).getString("mac") == device.macAddress
                } catch (_: Exception) {
                    false
                }
            }
            // Add updated entry
            val json = JSONObject().apply {
                put("mac", device.macAddress)
                put("name", device.name)
                put("lastSeen", device.lastSeenTimestamp)
            }.toString()
            existing.add(json)
            prefs[KEY_SAVED_DEVICES] = existing
            prefs[KEY_LAST_DEVICE_MAC] = device.macAddress
        }
    }

    suspend fun forgetDevice(macAddress: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_SAVED_DEVICES]?.toMutableSet() ?: mutableSetOf()
            existing.removeAll { json ->
                try {
                    JSONObject(json).getString("mac") == macAddress
                } catch (_: Exception) {
                    false
                }
            }
            prefs[KEY_SAVED_DEVICES] = existing
        }
        // Also clear stored PIN for this device
        clearPinForDevice(macAddress)
    }

    // ── PIN per device ────────────────────────────────────────────────────────

    /** Returns the stored PIN for [mac], or null if never authenticated. */
    suspend fun getPinForDevice(mac: String): String? {
        return context.dataStore.data
            .map { it[KEY_DEVICE_PINS] }
            .map { set -> set?.firstOrNull { it.startsWith("$mac=") }?.removePrefix("$mac=") }
            .first()
    }

    /** Store the PIN for [mac] so future connections are auto-authenticated. */
    suspend fun storePinForDevice(mac: String, pin: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_DEVICE_PINS]?.toMutableSet() ?: mutableSetOf()
            existing.removeAll { it.startsWith("$mac=") }
            existing.add("$mac=$pin")
            prefs[KEY_DEVICE_PINS] = existing
        }
    }

    /** Clear stored PIN for [mac] (e.g. after wrong PIN). Forces re-prompt. */
    suspend fun clearPinForDevice(mac: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_DEVICE_PINS]?.toMutableSet() ?: mutableSetOf()
            existing.removeAll { it.startsWith("$mac=") }
            prefs[KEY_DEVICE_PINS] = existing
        }
    }

}
