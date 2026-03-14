package com.myweld.app.viewmodel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myweld.app.data.ble.BleConnectionState
import com.myweld.app.data.ble.MyWeldBleManager
import com.myweld.app.data.model.SavedDevice
import com.myweld.app.data.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiscoveredDevice(
    val device: BluetoothDevice,
    val name: String,
    val rssi: Int,
    val macAddress: String,
)

@SuppressLint("MissingPermission")
class ScanViewModel(
    private val bleManager: MyWeldBleManager,
    private val deviceRepository: DeviceRepository,
) : ViewModel() {

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState

    val savedDevices = deviceRepository.savedDevices.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    fun onScanResult(result: ScanResult) {
        val device    = result.device
        val scanName  = device.name  // Android OS cache — may be stale after a rename
        val mac       = device.address

        // Prefer the user-set name stored in DeviceRepository over the BLE scan cache.
        // Android caches advertising names at the OS level; after a rename the old name
        // can persist in scan results for several minutes. Our saved record is always fresh.
        val savedName = savedDevices.value.firstOrNull { it.macAddress == mac }?.name
        val displayName = savedName ?: scanName ?: return  // skip if completely unnamed

        val discovered = DiscoveredDevice(
            device     = device,
            name       = displayName,
            rssi       = result.rssi,
            macAddress = mac,
        )

        val current = _discoveredDevices.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.macAddress == mac }
        if (existingIndex >= 0) {
            current[existingIndex] = discovered
        } else {
            current.add(discovered)
        }
        _discoveredDevices.value = current
    }

    fun setScanningState(scanning: Boolean) {
        _isScanning.value = scanning
    }

    fun clearDiscoveredDevices() {
        _discoveredDevices.value = emptyList()
    }

    fun connectToDevice(device: BluetoothDevice) {
        bleManager.connectToDevice(device)
        viewModelScope.launch {
            val name = device.name ?: device.address
            deviceRepository.saveDevice(SavedDevice(device.address, name))
        }
    }

    fun forgetDevice(macAddress: String) {
        viewModelScope.launch {
            deviceRepository.forgetDevice(macAddress)
        }
    }
}
