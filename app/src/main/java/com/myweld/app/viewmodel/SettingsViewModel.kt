package com.myweld.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myweld.app.BuildConfig
import com.myweld.app.data.ble.MyWeldBleManager
import com.myweld.app.data.model.WeldParams
import com.myweld.app.data.repository.DeviceRepository
import com.myweld.app.data.repository.WelderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val welderRepository: WelderRepository,
    private val deviceRepository: DeviceRepository,
    private val bleManager: MyWeldBleManager,
) : ViewModel() {

    init {
        // Sync the persisted preference into the BLE manager at startup
        viewModelScope.launch {
            deviceRepository.autoReconnect.collect { enabled ->
                bleManager.autoReconnectEnabled = enabled
            }
        }
    }

    val status = welderRepository.status
    val connectionState = welderRepository.connectionState

    val isDarkMode = deviceRepository.isDarkMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true,
    )

    val autoReconnect = deviceRepository.autoReconnect.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true,
    )

    val savedDevices = deviceRepository.savedDevices.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    fun setDarkMode(dark: Boolean) {
        viewModelScope.launch { deviceRepository.setDarkMode(dark) }
    }

    fun setAutoReconnect(enabled: Boolean) {
        bleManager.autoReconnectEnabled = enabled
        viewModelScope.launch { deviceRepository.setAutoReconnect(enabled) }
    }

    fun forgetDevice(macAddress: String) {
        viewModelScope.launch { deviceRepository.forgetDevice(macAddress) }
    }

    fun factoryReset() {
        welderRepository.factoryReset()
    }

    fun rebootDevice() {
        welderRepository.rebootDevice()
    }

    fun resetWeldCounter(target: Int) {
        welderRepository.resetWeldCounter(target)
    }

    // ── Device controls (send full params with specific overrides) ────────────

    /** Helper: build WeldParams from current status with optional overrides. */
    private fun sendDeviceParams(
        brightness: Int? = null,
        volume: Int? = null,
        soundOn: Boolean? = null,
        bleName: String? = null,
    ) {
        val s = welderRepository.status.value
        welderRepository.writeParams(
            WeldParams(
                p1Ms = s.p1Ms,
                tMs = s.tMs,
                p2Ms = s.p2Ms,
                p3Ms = s.p3Ms,
                p4Ms = s.p4Ms,
                sSeconds = s.sSeconds,
                autoMode = s.autoMode,
                soundOn = soundOn ?: s.soundOn,
                brightness = brightness ?: s.brightness,
                volume = volume ?: s.volume,
                theme = s.theme,
                bleName = bleName ?: s.bleName.ifBlank { "MyWeld" },
            )
        )
    }

    /** Set display brightness (10–100%) on the device. */
    fun setBrightness(value: Int) {
        sendDeviceParams(brightness = value.coerceIn(10, 100))
    }

    /** Set master sound volume (0–100%) on the device. */
    fun setVolume(value: Int) {
        sendDeviceParams(volume = value.coerceIn(0, 100))
    }

    /** Toggle sound on/off on the device. */
    fun toggleSound(enabled: Boolean) {
        sendDeviceParams(soundOn = enabled)
    }

    /**
     * Rename the BLE device. Sends the full current params with just the name changed.
     * The firmware applies it immediately and saves to NVS.
     * Max 15 chars (firmware ble_name field is 16 bytes with null terminator).
     */
    fun renameMachine(newName: String) {
        val name = newName.trim().take(WeldParams.BLE_NAME_MAX_LEN).ifEmpty { return }
        sendDeviceParams(bleName = name)
    }

    /** Send a new 4-digit PIN to the firmware. Must already be authenticated. */
    fun changePin(newPin: String) {
        val pin = newPin.trim().filter { it.isDigit() }.take(4)
        if (pin.length == 4) welderRepository.changePin(pin)
    }

    // ── ADC Calibration ──────────────────────────────────────────────────────

    /**
     * Calibrate an ADC channel using the user's multimeter reading.
     * Channels 0/1 are debug-only (factory ADC calibration).
     * Channel 2 is user-facing (max supercap voltage setting).
     * @param channel 0 = supercap ADC, 1 = protection ADC, 2 = max supercap voltage
     * @param referenceVolts voltage in V (multimeter reading or target max voltage)
     */
    fun calibrateAdc(channel: Int, referenceVolts: Float) {
        // Channels 0/1 (ADC cal) are debug-only; channel 2 (max voltage) is user-facing
        if (channel < 2 && !BuildConfig.DEBUG) return
        val referenceMv = (referenceVolts * 1000).toInt().coerceIn(100, 20000)
        welderRepository.calibrateAdc(channel, referenceMv)
    }

    /**
     * Set max supercap voltage. Convenience wrapper for channel 2 calibration.
     * Range: 4.0–12.0V (matches firmware SUPERCAP_V_MIN/MAX).
     */
    fun setMaxSupercapVoltage(volts: Float) {
        val clamped = volts.coerceIn(4.0f, 12.0f)
        calibrateAdc(2, clamped)
    }

    fun disconnect() {
        welderRepository.disconnect()
    }
}
