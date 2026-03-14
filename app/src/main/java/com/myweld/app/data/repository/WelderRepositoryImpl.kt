package com.myweld.app.data.repository

import com.myweld.app.data.ble.AppEvent
import com.myweld.app.data.ble.BleConnectionState
import com.myweld.app.data.ble.MyWeldBleManager
import com.myweld.app.data.model.WeldParams
import com.myweld.app.data.model.WelderStatus
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository implementation that delegates to BLE manager.
 */
class WelderRepositoryImpl(
    private val bleManager: MyWeldBleManager,
) : WelderRepository {

    override val status: StateFlow<WelderStatus> = bleManager.status
    override val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState
    override val isLegacyFirmware: StateFlow<Boolean> = bleManager.isLegacyFirmware
    override val presets = bleManager.presets
    override val events: SharedFlow<AppEvent> = bleManager.events

    override fun writeParams(params: WeldParams) = bleManager.writeParams(params)
    override fun loadPreset(index: Int) = bleManager.loadPreset(index)
    override fun savePreset(index: Int, name: String, p1Ms: Float, tMs: Float, p2Ms: Float, p3Ms: Float, p4Ms: Float) =
        bleManager.savePreset(index, name, p1Ms, tMs, p2Ms, p3Ms, p4Ms)
    override fun authenticate(pin: String) = bleManager.authenticate(pin)
    override fun changePin(newPin: String) = bleManager.changePin(newPin)
    override fun factoryReset() = bleManager.factoryReset()
    override fun rebootDevice() = bleManager.rebootDevice()
    override fun resetWeldCounter(target: Int) = bleManager.resetWeldCounter(target)
    override fun calibrateAdc(channel: Int, referenceMv: Int) = bleManager.calibrateAdc(channel, referenceMv)
    override fun requestVersion() = bleManager.requestVersion()
    override fun requestParams() = bleManager.requestParams()
    override fun requestPresetList() = bleManager.requestPresetList()
    override fun disconnect() = bleManager.disconnectDevice()
}
