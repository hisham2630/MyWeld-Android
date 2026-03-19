package com.myweld.app.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.myweld.app.data.model.WeldParams
import com.myweld.app.data.model.WeldPreset
import com.myweld.app.data.model.WelderStatus
import com.myweld.app.data.repository.DeviceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data

/**
 * Core BLE manager. Wraps NordicSemi BleManager and handles connection,
 * auto-reconnect, pin authentication, and BLE packet I/O.
 */
@SuppressLint("MissingPermission")
class MyWeldBleManager(
    context: Context,
    private val deviceRepository: DeviceRepository,
) : BleManager(context) {

    companion object {
        private const val TAG = "MyWeldBLE"
        private const val MTU_SIZE = 247
        private const val RECONNECT_DELAY_INITIAL_MS = 1_500L
        private const val RECONNECT_DELAY_MAX_MS = 30_000L
    }

    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var cmdCharacteristic: BluetoothGattCharacteristic? = null
    private var paramsCharacteristic: BluetoothGattCharacteristic? = null
    private var otaCharacteristic: BluetoothGattCharacteristic? = null

    // Receive buffer for assembling fragmented packets
    private var receiveBuffer = byteArrayOf()

    // Reconnect state
    private var lastDevice: BluetoothDevice? = null
    var autoReconnectEnabled = true
    private var userDisconnected = false
    private var reconnectJob: Job? = null

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Guard window: after writeParams / loadPreset, ignore autoMode and activePreset
    // from incoming STATUS packets for a short time so the optimistic update isn't
    // overwritten by a stale periodic STATUS that arrives before the firmware
    // processes the BLE command.
    @Volatile private var paramsWriteTimestamp = 0L
    private val PARAMS_GUARD_MS = 2000L  // 2-second guard window

    // ── Public state flows ────────────────────────────────────────────────────

    private val _status = MutableStateFlow(WelderStatus())
    val status: StateFlow<WelderStatus> = _status.asStateFlow()

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _ackFlow = MutableSharedFlow<Pair<Byte, Boolean>>(extraBufferCapacity = 16)
    val ackFlow: SharedFlow<Pair<Byte, Boolean>> = _ackFlow.asSharedFlow()

    private val _nakFlow = MutableSharedFlow<Pair<Byte, Int>>(extraBufferCapacity = 16)
    val nakFlow: SharedFlow<Pair<Byte, Int>> = _nakFlow.asSharedFlow()

    private val _isLegacyFirmware = MutableStateFlow(false)
    val isLegacyFirmware: StateFlow<Boolean> = _isLegacyFirmware.asStateFlow()

    /** App-wide events consumed by the UI layer to show toast/snackbar feedback. */
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    /** Live presets sourced from firmware — empty until first PRESET_LIST_RESP. */
    private val _presets = MutableStateFlow<List<WeldPreset>>(emptyList())
    val presets: StateFlow<List<WeldPreset>> = _presets.asStateFlow()

    /** Decoded VERSION_RESPONSE from firmware — includes hw_compat_id for variant checking. */
    private val _versionInfo = MutableStateFlow<VersionInfo?>(null)
    val versionInfo: StateFlow<VersionInfo?> = _versionInfo.asStateFlow()

    /** Page-assembly buffers for the paginated PRESET_LIST_RESP protocol. */
    private val _pendingPresets = Array<WeldPreset?>(WeldPreset.MAX_PRESETS) { null }
    private var _pendingPagesReceived = 0
    private val _totalPages = WeldPreset.TOTAL_PAGES  // 4 pages of 5

    // OTA deferred responses — set by OtaService before sending a packet
    @Volatile private var _pendingOtaAck: CompletableDeferred<OtaAckResult>? = null
    @Volatile private var _pendingOtaResult: CompletableDeferred<OtaResult>? = null

    // ── BleManager overrides ──────────────────────────────────────────────────

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val service = gatt.getService(BleUuids.SERVICE)
        if (service == null) {
            Log.e(TAG, "MyWeld service not found")
            return false
        }

        statusCharacteristic = service.getCharacteristic(BleUuids.CHAR_STATUS)
        cmdCharacteristic = service.getCharacteristic(BleUuids.CHAR_CMD)
        paramsCharacteristic = service.getCharacteristic(BleUuids.CHAR_PARAMS)
        otaCharacteristic = service.getCharacteristic(BleUuids.CHAR_OTA)

        val hasStatus = statusCharacteristic != null
        val hasCmd = cmdCharacteristic != null
        val hasOta = otaCharacteristic != null

        Log.i(TAG, "Service found — Status: $hasStatus, Cmd: $hasCmd, Params: ${paramsCharacteristic != null}, OTA: $hasOta")
        return hasStatus && hasCmd
    }

    override fun initialize() {
        requestMtu(MTU_SIZE).enqueue()

        setNotificationCallback(statusCharacteristic).with { _, data ->
            handleReceivedData(data)
        }
        enableNotifications(statusCharacteristic).enqueue()

        // Subscribe to OTA characteristic notifications (if available)
        otaCharacteristic?.let { ota ->
            setNotificationCallback(ota).with { _, data ->
                val bytes = data.value ?: return@with
                // OTA responses come on the OTA characteristic — process them
                var buffer = bytes
                while (true) {
                    val result = BleProtocol.extractPacket(buffer) ?: break
                    val (packet, remaining) = result
                    buffer = remaining
                    handleOtaPacket(packet)
                }
            }
            enableNotifications(ota).enqueue()
            Log.i(TAG, "OTA notifications enabled")
        }

        // Successful connection — cancel any pending reconnect loop
        userDisconnected = false
        reconnectJob?.cancel()
        reconnectJob = null

        val deviceMac = lastDevice?.address ?: ""
        val deviceName = lastDevice?.name ?: deviceMac.ifEmpty { "device" }

        // Start in Authenticating — only promote to Connected after PIN is accepted.
        _connectionState.value = BleConnectionState.Authenticating
        _events.tryEmit(AppEvent.Connected(deviceName))

        // Launch auth flow — check for stored PIN, auto-send or prompt
        managerScope.launch {
            val storedPin = if (deviceMac.isNotEmpty()) {
                deviceRepository.getPinForDevice(deviceMac)
            } else null

            if (storedPin != null) {
                // Auto-authenticate with stored PIN.
                // Subscribe to ACK/NAK BEFORE sending — UNDISPATCHED runs each coroutine
                // synchronously until its first suspension (the collect call), guaranteeing
                // the collectors are active before the packet leaves the queue.
                Log.i(TAG, "Auto-authenticating with stored PIN for $deviceMac")
                val autoDeferred = CompletableDeferred<Boolean>()
                val autoAckJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    _ackFlow.collect { (type, _) ->
                        if (type == BleProtocol.TYPE_CMD) autoDeferred.complete(true)
                    }
                }
                val autoNakJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    _nakFlow.collect { (type, err) ->
                        if (type == BleProtocol.TYPE_CMD &&
                            (err == BleProtocol.ERR_AUTH_FAILED || err == BleProtocol.ERR_AUTH_REQUIRED ||
                             err == BleProtocol.ERR_AUTH_LOCKED)) {
                            autoDeferred.complete(false)
                        }
                    }
                }

                sendPacket(BleProtocol.encodeCmdAuth(storedPin), label = null)

                val ack = try {
                    withTimeoutOrNull(4000L) { autoDeferred.await() }
                } finally {
                    autoAckJob.cancel()
                    autoNakJob.cancel()
                }

                when (ack) {
                    true -> {
                        Log.i(TAG, "Auto-auth succeeded")
                        _connectionState.value = BleConnectionState.Connected
                        _events.tryEmit(AppEvent.AuthSuccess)
                        requestParams()
                        requestPresetList()
                        requestVersion()
                    }
                    else -> {
                        // Stored PIN is wrong — clear it and ask user
                        Log.w(TAG, "Auto-auth failed (wrong stored PIN), clearing and asking user")
                        deviceRepository.clearPinForDevice(deviceMac)
                        _events.tryEmit(AppEvent.AuthRequired)
                    }
                }
            } else {
                // No stored PIN — ask user
                Log.i(TAG, "No stored PIN for $deviceMac, requesting from user")
                _events.tryEmit(AppEvent.AuthRequired)
            }
        }
        Log.i(TAG, "Initialized — notifications enabled")
    }

    override fun onServicesInvalidated() {
        statusCharacteristic = null
        cmdCharacteristic = null
        paramsCharacteristic = null
        otaCharacteristic = null
        receiveBuffer = byteArrayOf()
        _pendingOtaAck = null
        _pendingOtaResult = null

        if (!userDisconnected && autoReconnectEnabled && lastDevice != null) {
            Log.w(TAG, "Services invalidated — scheduling auto-reconnect")
            _connectionState.value = BleConnectionState.Reconnecting
            _events.tryEmit(AppEvent.Reconnecting)
            scheduleReconnect()
        } else {
            _connectionState.value = BleConnectionState.Disconnected
            if (!userDisconnected) {
                _events.tryEmit(AppEvent.Disconnected)
            }
            Log.w(TAG, "Services invalidated — disconnected (user-initiated=$userDisconnected)")
        }
    }

    // ── Auto-reconnect ────────────────────────────────────────────────────────

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = managerScope.launch {
            var delayMs = RECONNECT_DELAY_INITIAL_MS
            var attempt = 1

            while (autoReconnectEnabled && !userDisconnected) {
                val device = lastDevice ?: break
                Log.i(TAG, "Auto-reconnect attempt #$attempt to ${device.address} (waiting ${delayMs}ms first)")
                delay(delayMs)

                if (userDisconnected || !autoReconnectEnabled) break

                try {
                    connect(device)
                        .retry(1, 500)
                        .useAutoConnect(false)
                        .await()

                    Log.i(TAG, "Auto-reconnect succeeded on attempt #$attempt")
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Auto-reconnect attempt #$attempt failed: ${e.message}")
                }

                 if (_connectionState.value !is BleConnectionState.Connected) {
                    _connectionState.value = BleConnectionState.Reconnecting
                    delayMs = (delayMs * 2).coerceAtMost(RECONNECT_DELAY_MAX_MS)
                    attempt++
                } else {
                    break
                }
            }

            // If we exhausted attempts and still not connected, go to disconnected
            if (!userDisconnected &&
                _connectionState.value !is BleConnectionState.Connected
            ) {
                _connectionState.value = BleConnectionState.Disconnected
                _events.tryEmit(AppEvent.ReconnectFailed("Could not reach device"))
            }
        }
    }

    // ── Data reception ────────────────────────────────────────────────────────

    private fun handleReceivedData(data: Data) {
        val bytes = data.value ?: return

        if (receiveBuffer.isEmpty() && BleProtocol.isLegacyProtocol(bytes)) {
            Log.w(TAG, "Legacy text protocol detected — firmware update required")
            _isLegacyFirmware.value = true
            return
        }

        receiveBuffer += bytes
        processBuffer()
    }

    private fun processBuffer() {
        while (true) {
            val result = BleProtocol.extractPacket(receiveBuffer) ?: break
            val (packet, remaining) = result
            receiveBuffer = remaining
            handlePacket(packet)
        }
    }

    private fun handlePacket(packet: ParsedPacket) {
        when (packet.type) {
            BleProtocol.TYPE_STATUS -> {
                val status = BleProtocol.decodeStatus(packet.payload)
                if (status != null) {
                    // STATUS packets arrive every ~500ms. bleName/brightness/volume are
                    // authoritative only from PARAMS_RESPONSE — preserve local values here
                    // to prevent race conditions with pending PARAMS_WRITE optimistic updates.
                    //
                    // autoMode + activePreset use a time-based guard: after writeParams or
                    // loadPreset the app optimistically updates these fields and we ignore
                    // the (stale) value from the next few STATUS packets until the firmware
                    // has had time to process the BLE write and reflect it.
                    val inGuardWindow = (System.currentTimeMillis() - paramsWriteTimestamp) < PARAMS_GUARD_MS
                    val local = _status.value
                    _status.value = status.copy(
                        bleName = local.bleName,
                        brightness = local.brightness,
                        volume = local.volume,
                        autoMode = if (inGuardWindow) local.autoMode else status.autoMode,
                        activePreset = if (inGuardWindow) local.activePreset else status.activePreset,
                    )
                } else {
                    Log.w(TAG, "Failed to decode STATUS packet (${packet.payload.size} bytes)")
                }
            }

            BleProtocol.TYPE_PARAMS_RESPONSE -> {
                val name = BleProtocol.decodeParamsResponse(packet.payload)
                if (name != null) {
                    // Parse brightness (byte 14) and volume (byte 15) from PARAMS_RESPONSE
                    // Layout: [0-1]p1 [2-3]t [4-5]p2 [6-7]p3 [8-9]p4 [10-11]s [12]auto [13]sound [14]brightness [15]volume
                    val brightness = if (packet.payload.size > 14) packet.payload[14].toInt() and 0xFF else _status.value.brightness
                    val volume = if (packet.payload.size > 15) packet.payload[15].toInt() and 0xFF else _status.value.volume
                    _status.value = _status.value.copy(
                        bleName = name,
                        brightness = brightness,
                        volume = volume,
                    )
                    Log.d(TAG, "PARAMS_RESPONSE: name='$name', brightness=$brightness, volume=$volume")
                }
            }

            BleProtocol.TYPE_ACK -> {
                val ack = BleProtocol.decodeAck(packet.payload)
                _ackFlow.tryEmit(ack)
                Log.d(TAG, "ACK received for type 0x${ack.first.toString(16)}")
            }

            BleProtocol.TYPE_NAK -> {
                val nak = BleProtocol.decodeNak(packet.payload)
                _nakFlow.tryEmit(nak)
                Log.w(TAG, "NAK received for type 0x${nak.first.toString(16)}, error=${nak.second}")
            }

            BleProtocol.TYPE_VERSION_RESPONSE -> {
                val ver = BleProtocol.decodeVersionResponse(packet.payload)
                if (ver != null) {
                    _versionInfo.value = ver
                    Log.i(TAG, "VERSION: ${ver.versionString}, variant=${ver.variantSlug}, hwCompatId=0x${ver.hwCompatId.toString(16)}")
                } else {
                    Log.w(TAG, "Failed to decode VERSION_RESPONSE (${packet.payload.size} bytes)")
                }
            }

            BleProtocol.TYPE_PRESET_LIST_RESP -> {
                val page = BleProtocol.decodePresetListResp(packet.payload)
                if (page != null) {
                    val (pageIndex, presetList) = page
                    presetList.forEach { preset ->
                        if (preset.index < _pendingPresets.size) {
                            _pendingPresets[preset.index] = preset
                        }
                    }
                    _pendingPagesReceived++
                    if (_pendingPagesReceived >= _totalPages) {
                        // All pages received — publish the full list
                        _presets.value = _pendingPresets.mapIndexed { i, p ->
                            p ?: WeldPreset(index = i, name = "", p1Ms = 0f, tMs = 0f, p2Ms = 0f, p3Ms = 0f, p4Ms = 0f)
                        }
                        _pendingPagesReceived = 0  // reset for next request
                        Log.d(TAG, "PRESET_LIST_RESP complete: ${_presets.value.map { it.name }}")
                    } else {
                        Log.d(TAG, "PRESET_LIST_RESP page $pageIndex received, waiting for more")
                    }
                } else {
                    Log.w(TAG, "Failed to decode PRESET_LIST_RESP page (${packet.payload.size} bytes)")
                }
            }

            else -> Log.d(TAG, "Unknown packet type: 0x${packet.type.toString(16)}")
        }
    }

    /** Handle packets received on the OTA characteristic. */
    private fun handleOtaPacket(packet: ParsedPacket) {
        when (packet.type) {
            BleProtocol.TYPE_OTA_ACK -> {
                val ack = BleProtocol.decodeOtaAck(packet.payload)
                if (ack != null) {
                    _pendingOtaAck?.complete(ack)
                    _pendingOtaAck = null
                }
            }
            BleProtocol.TYPE_OTA_RESULT -> {
                val result = BleProtocol.decodeOtaResult(packet.payload)
                if (result != null) {
                    _pendingOtaResult?.complete(result)
                    _pendingOtaResult = null
                }
            }
            else -> Log.d(TAG, "Unknown OTA packet type: 0x${packet.type.toString(16)}")
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Snapshot of status before the most recent optimistic writeParams update.
     *  Used to revert on GATT failure or firmware NAK. */
    @Volatile private var preWriteSnapshot: WelderStatus? = null

    /** Active ACK/NAK listener job — cancelled after guard window or on next write. */
    private var writeResultListenerJob: Job? = null

    fun writeParams(params: WeldParams) {
        val chr = paramsCharacteristic
        if (chr == null) {
            Log.e(TAG, "PARAMS characteristic not available")
            _events.tryEmit(AppEvent.CommandFailed("Parameters saved"))
            return
        }

        // Cancel any previous ACK/NAK listener
        writeResultListenerJob?.cancel()

        // Snapshot current status BEFORE the optimistic update so we can revert
        val snapshot = _status.value
        preWriteSnapshot = snapshot

        // Optimistic local update — reflect the change in the UI immediately
        // without waiting for the next periodic STATUS notification from firmware.
        paramsWriteTimestamp = System.currentTimeMillis()
        _status.value = _status.value.copy(
            p1Ms       = params.p1Ms,
            tMs        = params.tMs,
            p2Ms       = params.p2Ms,
            p3Ms       = params.p3Ms,
            p4Ms       = params.p4Ms,
            sSeconds   = params.sSeconds,
            autoMode   = params.autoMode,
            soundOn    = params.soundOn,
            brightness = params.brightness,
            volume     = params.volume,
            theme      = params.theme,
            bleName    = params.bleName.ifBlank { _status.value.bleName },
        )

        // Listen for firmware ACK or NAK on PARAMS_WRITE.
        // ACK = firmware accepted → clear snapshot, all good.
        // NAK = firmware rejected → revert optimistic update immediately.
        // Timeout = guard window expires without response → let STATUS take over.
        writeResultListenerJob = managerScope.launch {
            val parentJob = coroutineContext[kotlinx.coroutines.Job]!!
            // Listen for ACK (success)
            val ackJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                _ackFlow.collect { (type, _) ->
                    if (type == BleProtocol.TYPE_PARAMS_WRITE) {
                        Log.d(TAG, "PARAMS_WRITE ACK — firmware accepted, keeping optimistic update")
                        preWriteSnapshot = null  // Confirmed — no revert needed
                        _events.tryEmit(AppEvent.CommandSent("Parameters saved"))
                        parentJob.cancel()       // Done listening
                    }
                }
            }
            // Listen for NAK (rejection)
            val nakJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                _nakFlow.collect { (type, errCode) ->
                    if (type == BleProtocol.TYPE_PARAMS_WRITE) {
                        Log.w(TAG, "PARAMS_WRITE NAK (error=$errCode) — reverting optimistic update")
                        preWriteSnapshot?.let { _status.value = it }
                        preWriteSnapshot = null
                        paramsWriteTimestamp = 0L  // Clear guard so STATUS packets take effect
                        val reason = when (errCode) {
                            BleProtocol.ERR_AUTH_REQUIRED -> "Not authenticated"
                            BleProtocol.ERR_BUSY -> "Device busy"
                            BleProtocol.ERR_INVALID_RANGE -> "Invalid parameter"
                            else -> "Error $errCode"
                        }
                        _events.tryEmit(AppEvent.CommandFailed(reason))
                        parentJob.cancel()       // Done listening
                    }
                }
            }
            // Timeout: if neither ACK nor NAK arrives within the guard window,
            // clean up and let the next STATUS packet be authoritative.
            delay(PARAMS_GUARD_MS)
            ackJob.cancel()
            nakJob.cancel()
            preWriteSnapshot = null
            Log.d(TAG, "PARAMS_WRITE listener timeout — guard expired")
        }

        writeCharacteristic(chr, BleProtocol.encodeParamsWrite(params), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            .done {
                // GATT delivery confirmed — but this does NOT mean the firmware accepted it.
                // The firmware sends ACK/NAK as separate BLE notifications, which are handled
                // by the writeResultListenerJob above. Do NOT emit success here — wait for ACK.
                // Re-read params from firmware to confirm the stored name
                requestParams()
                // If the name changed, update the saved device record so the device list stays in sync
                val newName = params.bleName.ifBlank { _status.value.bleName }
                val mac = lastDevice?.address ?: ""
                if (mac.isNotEmpty() && newName.isNotBlank()) {
                    managerScope.launch {
                        deviceRepository.saveDevice(
                            com.myweld.app.data.model.SavedDevice(
                                macAddress = mac,
                                name = newName,
                            )
                        )
                    }
                }
            }
            .fail { _, status ->
                Log.w(TAG, "PARAMS_WRITE GATT failure (status=$status) — reverting optimistic update")
                preWriteSnapshot?.let { _status.value = it }
                preWriteSnapshot = null
                paramsWriteTimestamp = 0L  // Clear guard so STATUS packets take effect
                writeResultListenerJob?.cancel()
                _events.tryEmit(AppEvent.CommandFailed("Write failed"))
            }
            .enqueue()
    }

    fun loadPreset(index: Int) {
        val label = if (index == WeldPreset.PRESET_USER_DEFINED) {
            "User Defined mode"
        } else {
            "Preset ${index + 1} loaded"
        }
        // Guard activePreset from being overwritten by stale STATUS packets
        paramsWriteTimestamp = System.currentTimeMillis()
        _status.value = _status.value.copy(activePreset = index)
        sendPacket(BleProtocol.encodeCmdLoadPreset(index), label = label)
    }

    fun savePreset(index: Int, name: String, p1Ms: Float, tMs: Float, p2Ms: Float, p3Ms: Float, p4Ms: Float) {
        sendPacket(
            BleProtocol.encodeCmdSavePreset(index, name, p1Ms, tMs, p2Ms, p3Ms, p4Ms),
            label = "\"$name\" saved",
        )
    }

    /**
     * Send PIN to firmware for authentication. On ACK → store PIN + emit AuthSuccess.
     * On NAK → emit AuthFailed. Called from the PIN dialog.
     *
     * KEY DESIGN: collectors are started with CoroutineStart.UNDISPATCHED so they run
     * synchronously on the current thread until their first suspension point (collect).
     * This guarantees they are fully subscribed BEFORE sendPacket enqueues the write,
     * eliminating the race where a fast firmware reply arrives before Android listens.
     * CompletableDeferred resolves instantly on first response — no full timeout wait.
     */
    fun authenticate(pin: String) {
        val mac = lastDevice?.address ?: ""
        if (cmdCharacteristic == null) {
            _events.tryEmit(AppEvent.CommandFailed("Auth failed"))
            return
        }

        managerScope.launch {
            val response = CompletableDeferred<Boolean?>()

            // UNDISPATCHED: runs until first suspension (collect), guaranteeing subscription
            // happens synchronously before sendPacket is called on the next lines.
            val ackJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                _ackFlow.collect { (t, _) ->
                    if (t == BleProtocol.TYPE_CMD) response.complete(true)
                }
            }
            val nakJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                _nakFlow.collect { (t, e) ->
                    if (t == BleProtocol.TYPE_CMD &&
                        (e == BleProtocol.ERR_AUTH_FAILED || e == BleProtocol.ERR_AUTH_REQUIRED ||
                         e == BleProtocol.ERR_AUTH_LOCKED)) {
                        response.complete(if (e == BleProtocol.ERR_AUTH_LOCKED) null else false)
                    }
                }
            }

            // Collectors are now subscribed — safe to send
            sendPacket(BleProtocol.encodeCmdAuth(pin), label = null)

            val authResult = try {
                withTimeoutOrNull(4000L) { response.await() }
            } finally {
                ackJob.cancel()
                nakJob.cancel()
            }

            when (authResult) {
                true -> {
                    Log.i(TAG, "PIN auth succeeded")
                    if (mac.isNotEmpty()) deviceRepository.storePinForDevice(mac, pin)
                    _connectionState.value = BleConnectionState.Connected
                    _events.tryEmit(AppEvent.AuthSuccess)
                    requestParams()
                    requestPresetList()
                    requestVersion()
                }
                false -> {
                    Log.w(TAG, "PIN auth failed (wrong PIN)")
                    _events.tryEmit(AppEvent.AuthFailed)
                }
                null -> {
                    // Locked out — pull remaining seconds from the latest status packet
                    val remaining = _status.value.authLockoutSec
                    Log.w(TAG, "PIN auth locked out ($remaining seconds remaining)")
                    _events.tryEmit(AppEvent.AuthLocked(remaining))
                }
            }
        }
    }

    fun changePin(newPin: String) {
        sendPacket(BleProtocol.encodeCmdChangePin(newPin), label = "PIN updated")
    }

    fun factoryReset() {
        sendPacket(BleProtocol.encodeCmdFactoryReset(), label = "Factory reset sent")
    }

    fun rebootDevice() {
        sendPacket(BleProtocol.encodeCmdReboot(), label = "Reboot command sent")
    }

    fun resetWeldCounter(target: Int) {
        sendPacket(BleProtocol.encodeCmdResetWeldCounter(target), label = "Weld counter reset")
    }

    fun calibrateAdc(channel: Int, referenceMv: Int) {
        sendPacket(BleProtocol.encodeCmdCalibrateAdc(channel, referenceMv), label = "ADC calibrated")
    }

    fun requestVersion() {
        sendPacket(BleProtocol.encodeVersionRequest(), label = null)
    }

    /** Request firmware to send back all 10 preset names via notify. */
    fun requestPresetList() {
        sendPacket(BleProtocol.encodePresetListRequest(), label = null)
    }

    fun requestParams() {
        val chr = paramsCharacteristic ?: run {
            Log.w(TAG, "PARAMS characteristic not available for read")
            return
        }
        readCharacteristic(chr)
            .with { _, data ->
                val bytes = data.value ?: return@with
                // The firmware returns a full PARAMS_RESPONSE binary packet on reads
                val result = BleProtocol.extractPacket(bytes)
                if (result != null) {
                    handlePacket(result.first)
                } else {
                    Log.w(TAG, "requestParams: unexpected response (${bytes.size} bytes)")
                }
            }
            .fail { _, status -> Log.w(TAG, "requestParams read failed: $status") }
            .enqueue()
    }

    /**
     * Sends a packet. If [label] is not null it emits a toast event on success or failure.
     */
    private fun sendPacket(packet: ByteArray, label: String?) {
        val cmd = cmdCharacteristic
        if (cmd == null) {
            Log.e(TAG, "CMD characteristic not available")
            if (label != null) _events.tryEmit(AppEvent.CommandFailed(label))
            return
        }
        writeCharacteristic(cmd, packet, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            .done { if (label != null) _events.tryEmit(AppEvent.CommandSent(label)) }
            .fail { _, _ -> if (label != null) _events.tryEmit(AppEvent.CommandFailed(label)) }
            .enqueue()
    }

    // ── OTA support ───────────────────────────────────────────────────────────

    /** Request high-priority BLE connection (7.5ms interval) for OTA transfers. */
    fun requestHighPriority() {
        requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH).enqueue()
        Log.i(TAG, "Requested CONNECTION_PRIORITY_HIGH")
    }

    /** Restore balanced connection priority after OTA. */
    fun requestBalancedPriority() {
        requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED).enqueue()
        Log.i(TAG, "Restored CONNECTION_PRIORITY_BALANCED")
    }

    /** Send a packet to the OTA characteristic (write-with-response for reliability). */
    fun sendOtaPacket(packet: ByteArray) {
        val ota = otaCharacteristic
        if (ota == null) {
            Log.e(TAG, "OTA characteristic not available")
            return
        }
        writeCharacteristic(ota, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            .fail { _, status -> Log.e(TAG, "OTA write failed: status=$status") }
            .enqueue()
    }

    /** Send OTA data chunk using write-without-response with flow control.
     *  Suspends until the data enters the BLE controller buffer. */
    suspend fun sendOtaDataFast(packet: ByteArray) {
        val ota = otaCharacteristic
        if (ota == null) {
            Log.e(TAG, "OTA characteristic not available")
            return
        }
        writeCharacteristic(ota, packet, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            .await()
    }

    /** Prepare to receive the next OTA_ACK. Call before sendOtaPacket for flow control. */
    fun expectOtaAck(): CompletableDeferred<OtaAckResult> {
        val d = CompletableDeferred<OtaAckResult>()
        _pendingOtaAck = d
        return d
    }

    /** Prepare to receive the next OTA_RESULT. Call before sendOtaPacket for flow control. */
    fun expectOtaResult(): CompletableDeferred<OtaResult> {
        val d = CompletableDeferred<OtaResult>()
        _pendingOtaResult = d
        return d
    }

    /** Check if OTA characteristic is available (firmware supports OTA). */
    val isOtaSupported: Boolean get() = otaCharacteristic != null

    // ── Connection management ─────────────────────────────────────────────────

    fun connectToDevice(device: BluetoothDevice) {
        lastDevice = device
        userDisconnected = false
        reconnectJob?.cancel()
        reconnectJob = null

        _connectionState.value = BleConnectionState.Connecting
        receiveBuffer = byteArrayOf()

        connect(device)
            .retry(3, 300)
            .useAutoConnect(false)
            .done { Log.i(TAG, "Connected to ${device.name ?: device.address}") }
            .fail { _, status ->
                _connectionState.value = BleConnectionState.Error("Connection failed (status=$status)")
                _events.tryEmit(AppEvent.CommandFailed("Connection failed"))
                Log.e(TAG, "Connection failed with status $status")
                if (autoReconnectEnabled && !userDisconnected) scheduleReconnect()
            }
            .enqueue()
    }

    fun disconnectDevice() {
        userDisconnected = true
        reconnectJob?.cancel()
        reconnectJob = null
        disconnect().enqueue()
        _connectionState.value = BleConnectionState.Disconnected
    }
}
