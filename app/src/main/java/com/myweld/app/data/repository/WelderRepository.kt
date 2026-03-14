package com.myweld.app.data.repository

import com.myweld.app.data.ble.AppEvent
import com.myweld.app.data.ble.BleConnectionState
import com.myweld.app.data.model.WeldParams
import com.myweld.app.data.model.WeldPreset
import com.myweld.app.data.model.WelderStatus
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for welder data access.
 * Abstracts BLE communication from ViewModels.
 */
interface WelderRepository {
    val status: StateFlow<WelderStatus>
    val connectionState: StateFlow<BleConnectionState>
    val isLegacyFirmware: StateFlow<Boolean>
    /** Live preset list from firmware (20 slots with full parameters). */
    val presets: StateFlow<List<WeldPreset>>

    /** App-wide events (toasts) — connect, disconnect, command results. */
    val events: SharedFlow<AppEvent>

    fun writeParams(params: WeldParams)
    fun loadPreset(index: Int)
    fun savePreset(index: Int, name: String, p1Ms: Float, tMs: Float, p2Ms: Float, p3Ms: Float, p4Ms: Float)
    fun authenticate(pin: String)
    fun changePin(newPin: String)
    fun factoryReset()
    fun rebootDevice()
    fun resetWeldCounter(target: Int)
    fun calibrateAdc(channel: Int, referenceMv: Int)
    fun requestVersion()
    fun requestParams()
    fun requestPresetList()
    fun disconnect()
}
