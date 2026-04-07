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

/**
 * Decoded VERSION_RESPONSE (0x09) from firmware.
 * Extended in protocol V5+ to include hardware variant fields.
 */
data class VersionInfo(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val boardVariant: Int = 0,   // 0=unknown, 1=JC3248W535, 2=DevKit
    val displayType: Int = 0,    // 0=unknown, 1=QSPI, 2=Nextion, 3=LCD2004
    val audioType: Int = 0,      // 0=unknown, 1=I2S, 2=Buzzer
    val hwCompatId: Long = 0L,   // Hardware compatibility ID (0 = pre-V5 firmware)
) {
    val versionString get() = "$major.$minor.$patch"

    /** Human-readable variant slug matching firmware HW_VARIANT_SLUG */
    val variantSlug: String get() = when {
        boardVariant == 1 -> "jc3248w535"
        boardVariant == 2 && displayType == 2 -> "devkit-nextion"
        boardVariant == 2 && displayType == 3 -> "devkit-lcd2004"
        boardVariant == 3 && displayType == 2 -> "goouuu-nextion"
        boardVariant == 3 && displayType == 3 -> "goouuu-lcd2004"
        else -> "unknown"
    }
}
