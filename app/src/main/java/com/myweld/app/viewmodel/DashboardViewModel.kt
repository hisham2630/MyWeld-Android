package com.myweld.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myweld.app.data.model.WeldParams
import com.myweld.app.data.model.WeldPreset
import com.myweld.app.data.model.WelderStatus
import com.myweld.app.data.repository.WelderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** How the dashboard controls behave. */
enum class DashboardControlMode {
    /** Preset selected from the list. Factory presets lock params. */
    PRESET,
    /** User-defined — all param cards always unlocked, free adjustment. */
    USER_DEFINED,
}

/** Minimum preset index considered "custom" (editable). */
private const val FIRST_CUSTOM_INDEX = 7

class DashboardViewModel(
    private val repository: WelderRepository,
) : ViewModel() {

    val status: StateFlow<WelderStatus> = repository.status
    val connectionState = repository.connectionState
    val isLegacyFirmware = repository.isLegacyFirmware
    val presets: StateFlow<List<com.myweld.app.data.model.WeldPreset>> = repository.presets

    // ── Control mode ──────────────────────────────────────────────────────────

    private val _controlMode = MutableStateFlow(DashboardControlMode.PRESET)
    val controlMode: StateFlow<DashboardControlMode> = _controlMode.asStateFlow()

    fun setControlMode(mode: DashboardControlMode) {
        _controlMode.value = mode
        if (mode == DashboardControlMode.USER_DEFINED) {
            // Send BLE command so ESP32 switches to User Defined mode
            repository.loadPreset(WeldPreset.PRESET_USER_DEFINED)
        } else {
            // Switching to PRESET tab: auto-load preset 0 (first preset)
            // so the ESP32 immediately applies it instead of staying on user-defined
            val currentPreset = status.value.activePreset
            if (currentPreset == WeldPreset.PRESET_USER_DEFINED) {
                repository.loadPreset(0)
            }
        }
    }

    /** Load a specific preset by index (called from the preset picker dialog). */
    fun selectPreset(index: Int) {
        repository.loadPreset(index)
    }

    // ── Active preset name ────────────────────────────────────────────────────

    /** Human-readable name of the currently active preset. */
    val activePresetName: StateFlow<String> = combine(
        repository.status,
        repository.presets,
    ) { s, presetList ->
        if (s.activePreset == WeldPreset.PRESET_USER_DEFINED) {
            "User Defined"
        } else {
            presetList.getOrNull(s.activePreset)?.name?.ifEmpty { null }
                ?: "Preset ${s.activePreset + 1}"
        }
    }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            "Preset 1",
        )

    init {
        // Auto-sync controlMode from firmware's activePreset status.
        // When the ESP32 reports activePreset = 0xFF, switch UI to USER_DEFINED.
        // When it reports a valid preset index, switch UI to PRESET.
        repository.status
            .map { it.activePreset }
            .distinctUntilChanged()
            .onEach { activePreset ->
                val newMode = if (activePreset == WeldPreset.PRESET_USER_DEFINED) {
                    DashboardControlMode.USER_DEFINED
                } else {
                    DashboardControlMode.PRESET
                }
                if (_controlMode.value != newMode) {
                    _controlMode.value = newMode
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * True when params should be locked:
     * - Control mode is PRESET, AND
     * - Active preset is a factory preset (not custom)
     */
    val paramsLocked: StateFlow<Boolean> = combine(
        repository.status,
        _controlMode,
    ) { s, mode ->
        mode == DashboardControlMode.PRESET && s.activePreset < FIRST_CUSTOM_INDEX
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // ── Voltage history ───────────────────────────────────────────────────────

    private val _voltageHistory = MutableStateFlow<List<Float>>(emptyList())
    val voltageHistory: StateFlow<List<Float>> = _voltageHistory.asStateFlow()

    private val maxHistorySize = 60

    fun recordVoltage(voltage: Float) {
        val current = _voltageHistory.value.toMutableList()
        current.add(voltage)
        if (current.size > maxHistorySize) current.removeAt(0)
        _voltageHistory.value = current
    }

    // ── Param adjustments ─────────────────────────────────────────────────────

    fun adjustP1(delta: Float) {
        if (paramsLocked.value) return
        val current = status.value
        val newVal = (current.p1Ms + delta).coerceIn(WeldParams.PULSE_MIN, WeldParams.PULSE_MAX)
        sendParamUpdate(current.copy(p1Ms = newVal))
    }

    fun adjustT(delta: Float) {
        if (paramsLocked.value) return
        val current = status.value
        // T=0 means single-pulse (disabled). Going up from 0 snaps to PAUSE_MIN.
        val raw = current.tMs + delta
        val newVal = if (raw <= 0f) 0f else raw.coerceIn(WeldParams.PAUSE_MIN, WeldParams.PAUSE_MAX)
        sendParamUpdate(current.copy(tMs = newVal))
    }

    fun adjustP2(delta: Float) {
        if (paramsLocked.value) return
        val current = status.value
        // P2=0 means disabled. Going up from 0 snaps to PULSE_MIN.
        val raw = current.p2Ms + delta
        val newVal = if (raw <= 0f) 0f else raw.coerceIn(WeldParams.PULSE_MIN, WeldParams.PULSE_MAX)
        sendParamUpdate(current.copy(p2Ms = newVal))
    }

    fun adjustP3(delta: Float) {
        if (paramsLocked.value) return
        val current = status.value
        val raw = current.p3Ms + delta
        val newVal = if (raw <= 0f) 0f else raw.coerceIn(WeldParams.PULSE_MIN, WeldParams.PULSE_MAX)
        sendParamUpdate(current.copy(p3Ms = newVal))
    }

    fun adjustP4(delta: Float) {
        if (paramsLocked.value) return
        val current = status.value
        val raw = current.p4Ms + delta
        val newVal = if (raw <= 0f) 0f else raw.coerceIn(WeldParams.PULSE_MIN, WeldParams.PULSE_MAX)
        sendParamUpdate(current.copy(p4Ms = newVal))
    }

    fun adjustS(delta: Float) {
        if (paramsLocked.value) return
        val current = status.value
        val newVal = (current.sSeconds + delta).coerceIn(WeldParams.S_MIN, WeldParams.S_MAX)
        sendParamUpdate(current.copy(sSeconds = newVal))
    }

    fun toggleMode() {
        val current = status.value
        sendParamUpdate(current.copy(autoMode = !current.autoMode))
    }

    private fun sendParamUpdate(status: WelderStatus) {
        repository.writeParams(
            WeldParams(
                p1Ms = status.p1Ms,
                tMs = status.tMs,
                p2Ms = status.p2Ms,
                p3Ms = status.p3Ms,
                p4Ms = status.p4Ms,
                sSeconds = status.sSeconds,
                autoMode = status.autoMode,
                soundOn = status.soundOn,
                brightness = status.brightness,
                volume = status.volume,
                theme = status.theme,
                bleName = status.bleName.ifBlank { "MyWeld" },
            )
        )
    }
}
