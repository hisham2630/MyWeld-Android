package com.myweld.app.data.ble

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.security.MessageDigest

/**
 * OTA firmware transfer engine.
 *
 * Reads firmware binary from an InputStream, computes SHA-256,
 * then sends it to the ESP32 in 240-byte chunks over BLE with
 * per-chunk ACK flow control.
 *
 * Usage:
 *   1. Call [startOta] with the firmware InputStream and total size
 *   2. Observe [progress] for UI updates
 *   3. Call [abort] to cancel mid-transfer
 */
class OtaService(private val bleManager: MyWeldBleManager) {

    companion object {
        private const val TAG = "OtaService"
        private const val CHUNK_SIZE = BleProtocol.OTA_CHUNK_SIZE
        private const val ACK_WINDOW = BleProtocol.OTA_ACK_WINDOW
        private const val ACK_TIMEOUT_MS = 10_000L
        private const val MAX_RETRIES = 3
    }

    private val _progress = MutableStateFlow(OtaProgress(state = OtaState.IDLE))
    val progress: StateFlow<OtaProgress> = _progress.asStateFlow()

    private var otaJob: Job? = null
    @Volatile private var abortRequested = false

    /**
     * Start OTA firmware transfer.
     *
     * @param inputStream  Firmware binary data (will be fully read into memory)
     * @param totalSize    Expected size in bytes
     * @param fwMajor      New firmware version major (for OTA_BEGIN header)
     * @param fwMinor      New firmware version minor
     * @param fwPatch      New firmware version patch
     */
    fun startOta(
        scope: CoroutineScope,
        inputStream: InputStream,
        totalSize: Int,
        fwMajor: Int = 0,
        fwMinor: Int = 0,
        fwPatch: Int = 0,
    ) {
        if (otaJob?.isActive == true) {
            Log.w(TAG, "OTA already in progress")
            return
        }
        abortRequested = false

        otaJob = scope.launch(Dispatchers.IO) {
            try {
                performOta(inputStream, totalSize, fwMajor, fwMinor, fwPatch)
            } catch (e: CancellationException) {
                updateProgress(OtaState.ABORTED, errorMessage = "Cancelled")
                sendAbort()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "OTA failed", e)
                updateProgress(OtaState.ERROR, errorMessage = e.message ?: "Unknown error")
                sendAbort()
            }
        }
    }

    /** Cancel the ongoing OTA transfer. */
    fun abort() {
        abortRequested = true
        otaJob?.cancel()
        otaJob = null
    }

    /** Reset state to IDLE. */
    fun reset() {
        abort()
        _progress.value = OtaProgress(state = OtaState.IDLE)
    }

    // ========================================================================
    // Internal: OTA transfer flow
    // ========================================================================

    private suspend fun performOta(
        inputStream: InputStream,
        totalSize: Int,
        fwMajor: Int,
        fwMinor: Int,
        fwPatch: Int,
    ) {
        // ── Step 1: Read firmware binary ──────────────────────────────────────
        updateProgress(OtaState.PREPARING)
        Log.i(TAG, "Reading firmware binary ($totalSize bytes)...")

        val firmware = inputStream.use { it.readBytes() }
        if (firmware.size != totalSize) {
            throw OtaException("Size mismatch: expected $totalSize, got ${firmware.size}")
        }

        // ── Step 2: Compute SHA-256 ──────────────────────────────────────────
        Log.i(TAG, "Computing SHA-256...")
        val sha256 = MessageDigest.getInstance("SHA-256").digest(firmware)
        Log.i(TAG, "SHA-256: ${sha256.joinToString("") { "%02x".format(it) }}")

        // ── Step 3: Send OTA_BEGIN ─────────────────────────────────────────
        updateProgress(OtaState.SENDING_BEGIN)
        Log.i(TAG, "Sending OTA_BEGIN (size=$totalSize, version=$fwMajor.$fwMinor.$fwPatch)")

        val beginPacket = BleProtocol.encodeOtaBegin(totalSize, sha256, fwMajor, fwMinor, fwPatch)
        val beginAck = sendAndWaitForAck(beginPacket)

        if (beginAck == null || !beginAck.isOk) {
            val err = beginAck?.let { otaStatusMessage(it.status) } ?: "No response from device"
            throw OtaException("OTA_BEGIN rejected: $err")
        }

        Log.i(TAG, "OTA_BEGIN accepted — starting chunk transfer")

        // Request high-priority BLE connection (7.5ms interval instead of ~50ms)
        bleManager.requestHighPriority()
        delay(100) // Give connection interval time to update

        // ── Step 4: Send firmware chunks (windowed ACK for speed) ──────────────
        // Strategy: enqueue all writes as write-with-response (reliable delivery),
        // but only wait for the ESP32's application-level ACK every N chunks.
        // The NordicSemi BLE library handles sequential write-with-response internally.
        val totalChunks = (totalSize + CHUNK_SIZE - 1) / CHUNK_SIZE
        var seq = 0
        var offset = 0
        val transferStart = System.currentTimeMillis()

        Log.i(TAG, "Sending $totalChunks chunks (ACK every $ACK_WINDOW)...")

        while (offset < totalSize) {
            if (abortRequested) throw CancellationException("User cancelled OTA")

            // Determine how many chunks in this window
            val chunksRemaining = totalChunks - seq
            val windowSize = minOf(chunksRemaining, ACK_WINDOW)
            val lastSeqInWindow = seq + windowSize - 1
            val windowStart = System.currentTimeMillis()

            // Set up deferred for the ACK that comes after the last chunk in window
            val ackDeferred = bleManager.expectOtaAck()

            // Enqueue all chunks in this window (write-no-response, with suspend flow control)
            for (i in 0 until windowSize) {
                if (abortRequested) throw CancellationException("User cancelled OTA")

                val remaining = totalSize - offset
                val chunkSize = minOf(remaining, CHUNK_SIZE)
                val chunkData = firmware.copyOfRange(offset, offset + chunkSize)
                val dataPacket = BleProtocol.encodeOtaData(seq, chunkData)

                // Write-no-response with suspend: waits for BLE buffer space, no round-trip
                bleManager.sendOtaDataFast(dataPacket)

                offset += chunkSize
                seq++

                // Update progress
                val percent = (offset.toLong() * 100 / totalSize).toInt()
                updateProgress(
                    OtaState.TRANSFERRING,
                    percent = percent,
                    bytesTransferred = offset.toLong(),
                    totalBytes = totalSize.toLong(),
                )
            }

            // Wait for the windowed ACK from ESP32 (only once per window)
            val ack = withTimeoutOrNull(ACK_TIMEOUT_MS) { ackDeferred.await() }
            val windowMs = System.currentTimeMillis() - windowStart

            if (ack == null) {
                throw OtaException("ACK timeout after window ending at seq=$lastSeqInWindow")
            }
            if (!ack.isOk) {
                throw OtaException("Chunk failed at seq=${ack.seq}: ${otaStatusMessage(ack.status)}")
            }

            val bytesInWindow = windowSize * CHUNK_SIZE
            val kbps = if (windowMs > 0) bytesInWindow * 1000.0 / windowMs / 1024 else 0.0
            Log.d(TAG, "Window seq=${lastSeqInWindow - windowSize + 1}..$lastSeqInWindow: " +
                    "${windowMs}ms (${String.format("%.1f", kbps)} KB/s)")
        }

        val totalMs = System.currentTimeMillis() - transferStart
        val avgKbps = if (totalMs > 0) totalSize * 1000.0 / totalMs / 1024 else 0.0
        Log.i(TAG, "All $totalChunks chunks sent ($totalSize bytes) in ${totalMs}ms " +
                "(${String.format("%.1f", avgKbps)} KB/s avg)")

        // Restore balanced connection priority
        bleManager.requestBalancedPriority()

        // ── Step 5: Send OTA_END ────────────────────────────────────────────
        updateProgress(OtaState.FINALIZING, percent = 100, bytesTransferred = totalSize.toLong(), totalBytes = totalSize.toLong())
        Log.i(TAG, "Sending OTA_END...")

        val endPacket = BleProtocol.encodeOtaEnd()
        val result = sendAndWaitForResult(endPacket)

        if (result != null && result.isSuccess) {
            Log.i(TAG, "OTA SUCCESS! Device is rebooting.")
            updateProgress(OtaState.SUCCESS, percent = 100, bytesTransferred = totalSize.toLong(), totalBytes = totalSize.toLong())
        } else {
            val err = result?.errorMessage() ?: "No result from device"
            throw OtaException("OTA finalization failed: $err")
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private suspend fun sendAndWaitForAck(packet: ByteArray): OtaAckResult? {
        val ackDeferred = bleManager.expectOtaAck()
        bleManager.sendOtaPacket(packet)
        return withTimeoutOrNull(ACK_TIMEOUT_MS) {
            ackDeferred.await()
        }
    }

    private suspend fun sendAndWaitForResult(packet: ByteArray): OtaResult? {
        val resultDeferred = bleManager.expectOtaResult()
        bleManager.sendOtaPacket(packet)
        return withTimeoutOrNull(10_000L) { // Longer timeout for validation + reboot
            resultDeferred.await()
        }
    }

    private fun sendAbort() {
        try {
            val abortPacket = BleProtocol.encodeOtaAbort()
            bleManager.sendOtaPacket(abortPacket)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send OTA abort", e)
        }
    }

    private fun updateProgress(
        state: OtaState,
        percent: Int = _progress.value.percent,
        bytesTransferred: Long = _progress.value.bytesTransferred,
        totalBytes: Long = _progress.value.totalBytes,
        errorMessage: String? = null,
    ) {
        _progress.value = OtaProgress(
            state = state,
            percent = percent,
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            errorMessage = errorMessage,
        )
    }

    private fun otaStatusMessage(status: Int): String = when (status) {
        BleProtocol.OTA_STATUS_OK -> "OK"
        BleProtocol.OTA_STATUS_SEQ_ERR -> "Sequence error"
        BleProtocol.OTA_STATUS_FLASH_ERR -> "Flash write error"
        BleProtocol.OTA_STATUS_BUSY -> "Device busy"
        BleProtocol.OTA_STATUS_AUTH_REQ -> "Authentication required"
        BleProtocol.OTA_STATUS_TOO_LARGE -> "Firmware too large"
        BleProtocol.OTA_STATUS_CRC_FAIL -> "Checksum error"
        BleProtocol.OTA_STATUS_ABORT -> "Aborted"
        else -> "Unknown (0x${status.toString(16)})"
    }
}

class OtaException(message: String) : Exception(message)
