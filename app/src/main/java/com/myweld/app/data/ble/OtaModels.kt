package com.myweld.app.data.ble

/** Decoded OTA_ACK (0x13) from firmware. */
data class OtaAckResult(
    val status: Int,    // BLE_OTA_STATUS_* code
    val progress: Int,  // 0–100
    val seq: Int,       // Acknowledged sequence number
) {
    val isOk get() = status == BleProtocol.OTA_STATUS_OK
}

/** Decoded OTA_RESULT (0x15) from firmware. */
data class OtaResult(
    val result: Int,    // BLE_OTA_RESULT_* code
) {
    val isSuccess get() = result == BleProtocol.OTA_RESULT_SUCCESS

    fun errorMessage(): String = when (result) {
        BleProtocol.OTA_RESULT_SUCCESS -> "Success"
        BleProtocol.OTA_RESULT_CRC_FAIL -> "SHA-256 verification failed"
        BleProtocol.OTA_RESULT_FLASH_ERR -> "Flash write error"
        BleProtocol.OTA_RESULT_ABORTED -> "Update aborted"
        else -> "Unknown error (0x${result.toString(16)})"
    }
}

/** OTA transfer progress state. */
data class OtaProgress(
    val state: OtaState,
    val percent: Int = 0,
    val bytesTransferred: Long = 0,
    val totalBytes: Long = 0,
    val errorMessage: String? = null,
)

/** OTA state machine states. */
enum class OtaState {
    IDLE,
    PREPARING,      // Computing SHA-256
    SENDING_BEGIN,  // Sending OTA_BEGIN
    TRANSFERRING,   // Sending chunks
    FINALIZING,     // Sending OTA_END, waiting for result
    SUCCESS,        // OTA complete, device rebooting
    ERROR,          // OTA failed
    ABORTED,        // User cancelled
}
