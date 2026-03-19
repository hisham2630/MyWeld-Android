package com.myweld.app.data.ble

import com.myweld.app.data.model.WeldParams
import com.myweld.app.data.model.WeldPreset
import com.myweld.app.data.model.WeldState
import com.myweld.app.data.model.WelderStatus
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary BLE protocol encoder/decoder.
 *
 * Packet format:
 *   [SYNC=0xAA] [TYPE] [LEN] [PAYLOAD...] [CRC]
 *
 * CRC = XOR of TYPE + LEN + all PAYLOAD bytes.
 * All multi-byte values are little-endian (ESP32 native).
 */
object BleProtocol {

    const val SYNC_BYTE: Byte = 0xAA.toByte()
    const val HEADER_SIZE = 3 // SYNC + TYPE + LEN
    const val CRC_SIZE = 1

    // Message types
    const val TYPE_STATUS: Byte = 0x01
    const val TYPE_PARAMS_READ: Byte = 0x02
    const val TYPE_PARAMS_RESPONSE: Byte = 0x03
    const val TYPE_PARAMS_WRITE: Byte = 0x04
    const val TYPE_CMD: Byte = 0x05
    const val TYPE_ACK: Byte = 0x06
    const val TYPE_NAK: Byte = 0x07
    const val TYPE_VERSION: Byte = 0x08
    const val TYPE_VERSION_RESPONSE: Byte = 0x09
    const val TYPE_PRESET_LIST: Byte = 0x0A
    const val TYPE_PRESET_LIST_RESP: Byte = 0x0B

    // Command sub-types
    const val CMD_LOAD_PRESET: Byte = 0x01
    const val CMD_SAVE_PRESET: Byte = 0x02
    const val CMD_FACTORY_RESET: Byte = 0x03
    const val CMD_RESET_WELD_COUNTER: Byte = 0x04
    const val CMD_CALIBRATE_ADC: Byte = 0x05
    const val CMD_AUTH: Byte = 0x06
    const val CMD_CHANGE_PIN: Byte = 0x07
    const val CMD_REBOOT: Byte = 0x08

    // Error codes
    const val ERR_INVALID_RANGE = 0x01
    const val ERR_INVALID_PRESET = 0x02
    const val ERR_NVS_FAILURE = 0x03
    const val ERR_BUSY = 0x04
    const val ERR_UNKNOWN_CMD = 0x05
    const val ERR_AUTH_FAILED = 0x06
    const val ERR_AUTH_REQUIRED = 0x07
    const val ERR_AUTH_LOCKED = 0x08

    // OTA message types
    const val TYPE_OTA_BEGIN: Byte = 0x10
    const val TYPE_OTA_DATA: Byte = 0x11
    const val TYPE_OTA_END: Byte = 0x12
    const val TYPE_OTA_ACK: Byte = 0x13
    const val TYPE_OTA_ABORT: Byte = 0x14
    const val TYPE_OTA_RESULT: Byte = 0x15

    // OTA status codes (in OTA_ACK)
    const val OTA_STATUS_OK = 0x00
    const val OTA_STATUS_SEQ_ERR = 0x01
    const val OTA_STATUS_FLASH_ERR = 0x02
    const val OTA_STATUS_BUSY = 0x03
    const val OTA_STATUS_AUTH_REQ = 0x04
    const val OTA_STATUS_TOO_LARGE = 0x05
    const val OTA_STATUS_CRC_FAIL = 0x06
    const val OTA_STATUS_ABORT = 0x07
    const val OTA_STATUS_HW_MISMATCH = 0x08

    // OTA result codes (in OTA_RESULT)
    const val OTA_RESULT_SUCCESS = 0x00
    const val OTA_RESULT_CRC_FAIL = 0x01
    const val OTA_RESULT_FLASH_ERR = 0x02
    const val OTA_RESULT_ABORTED = 0x03

    // OTA chunk size: MTU(247) - ATT_HEADER(3) = 244 max BLE payload
    // Protocol overhead: SYNC(1) + TYPE(1) + LEN(1) + SEQ(2) + CRC(1) = 6
    // Max data per chunk: 244 - 6 = 238
    const val OTA_CHUNK_SIZE = 238
    const val OTA_ACK_WINDOW = 32  // ESP32 only ACKs every Nth chunk

    /**
     * Calculate CRC: XOR of all bytes after SYNC and before CRC.
     */
    fun calculateCrc(type: Byte, len: Byte, payload: ByteArray): Byte {
        var crc = type.toInt() xor len.toInt()
        for (b in payload) {
            crc = crc xor b.toInt()
        }
        return crc.toByte()
    }

    /**
     * Build a complete packet: [SYNC][TYPE][LEN][PAYLOAD][CRC]
     */
    fun buildPacket(type: Byte, payload: ByteArray = byteArrayOf()): ByteArray {
        val len = payload.size.toByte()
        val crc = calculateCrc(type, len, payload)
        return byteArrayOf(SYNC_BYTE, type, len) + payload + byteArrayOf(crc)
    }

    /**
     * Validate a received packet's CRC.
     * @param data Full packet including SYNC, TYPE, LEN, PAYLOAD, CRC
     * @return true if valid
     */
    fun validatePacket(data: ByteArray): Boolean {
        if (data.size < HEADER_SIZE + CRC_SIZE) return false
        if (data[0] != SYNC_BYTE) return false

        val type = data[1]
        val len = data[2].toInt() and 0xFF
        if (data.size < HEADER_SIZE + len + CRC_SIZE) return false

        val payload = data.copyOfRange(HEADER_SIZE, HEADER_SIZE + len)
        val expectedCrc = calculateCrc(type, len.toByte(), payload)
        val actualCrc = data[HEADER_SIZE + len]

        return expectedCrc == actualCrc
    }

    /**
     * Check if received data is legacy text protocol (not binary).
     * Legacy text protocol starts with ASCII chars, not 0xAA.
     */
    fun isLegacyProtocol(data: ByteArray): Boolean {
        if (data.isEmpty()) return false
        val firstByte = data[0].toInt() and 0xFF
        return firstByte in 0x20..0x7E // printable ASCII range
    }

    // ========================================================================
    // Decoders
    // ========================================================================

    /**
     * Parse STATUS packet (0x01) payload → WelderStatus.
     * Supports 36-byte (legacy without P3/P4), 44-byte (with calibration),
     * and 46-byte (full with max_supercap_mv) packets.
     *
     * Offset map (46-byte ble_status_packet_t):
     *   [0–1]  supercap_mv    [2–3]  protection_mv  [4] state  [5] charge%
     *   [6]    auto_mode      [7]    active_preset
     *   [8–11] session_welds  [12–15] total_welds
     *   [16]   ble_connected  [17] sound_on  [18] theme  [19] error_code
     *   [20–21] p1_x10  [22–23] t_x10  [24–25] p2_x10
     *   [26–27] p3_x10  [28–29] p4_x10  [30–31] s_x10
     *   [32] fw_major  [33] fw_minor  [34] volume  [35] auth_lockout_sec
     *   [36–37] raw_supercap_mv  [38–39] raw_protection_mv
     *   [40–41] cal_factor_v_x1000  [42–43] cal_factor_p_x1000
     *   [44–45] max_supercap_mv
     */
    fun decodeStatus(payload: ByteArray): WelderStatus? {
        if (payload.size < 36) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

        // Parse extended calibration fields if present (44-byte packet)
        val rawSupercapMv = if (payload.size >= 44) buf.getShort(36).toInt() and 0xFFFF else 0
        val rawProtectionMv = if (payload.size >= 44) buf.getShort(38).toInt() and 0xFFFF else 0
        val calFactorV = if (payload.size >= 44) buf.getShort(40).toInt() and 0xFFFF else 1000
        val calFactorP = if (payload.size >= 44) buf.getShort(42).toInt() and 0xFFFF else 1000
        val maxSupercapMv = if (payload.size >= 46) buf.getShort(44).toInt() and 0xFFFF else 5700

        return WelderStatus(
            supercapVoltageMv = buf.getShort(0).toInt() and 0xFFFF,
            protectionVoltageMv = buf.getShort(2).toInt() and 0xFFFF,
            state = WeldState.fromCode(buf.get(4).toInt() and 0xFF),
            chargePercent = buf.get(5).toInt() and 0xFF,
            autoMode = buf.get(6).toInt() != 0,
            activePreset = buf.get(7).toInt() and 0xFF,
            sessionWelds = buf.getInt(8).toLong() and 0xFFFFFFFFL,
            totalWelds = buf.getInt(12).toLong() and 0xFFFFFFFFL,
            soundOn = buf.get(17).toInt() != 0,
            theme = buf.get(18).toInt() and 0xFF,
            errorCode = buf.get(19).toInt() and 0xFF,
            p1Ms = (buf.getShort(20).toInt() and 0xFFFF) / 10f,
            tMs = (buf.getShort(22).toInt() and 0xFFFF) / 10f,
            p2Ms = (buf.getShort(24).toInt() and 0xFFFF) / 10f,
            p3Ms = (buf.getShort(26).toInt() and 0xFFFF) / 10f,
            p4Ms = (buf.getShort(28).toInt() and 0xFFFF) / 10f,
            sSeconds = (buf.getShort(30).toInt() and 0xFFFF) / 10f,
            fwMajor = buf.get(32).toInt() and 0xFF,
            fwMinor = buf.get(33).toInt() and 0xFF,
            volume = buf.get(34).toInt() and 0xFF,
            authLockoutSec = buf.get(35).toInt() and 0xFF,
            rawSupercapMv = rawSupercapMv,
            rawProtectionMv = rawProtectionMv,
            calFactorVx1000 = calFactorV,
            calFactorPx1000 = calFactorP,
            maxSupercapMv = maxSupercapMv,
        )
    }

    /**
     * Parse ACK packet (0x06) payload.
     * @return Pair(originalType, success=true)
     */
    fun decodeAck(payload: ByteArray): Pair<Byte, Boolean> {
        if (payload.size < 2) return Pair(0, false)
        return Pair(payload[0], payload[1].toInt() == 0)
    }

    /**
     * Parse NAK packet (0x07) payload.
     * @return Pair(originalType, errorCode)
     */
    fun decodeNak(payload: ByteArray): Pair<Byte, Int> {
        if (payload.size < 3) return Pair(0, ERR_UNKNOWN_CMD)
        return Pair(payload[0], payload[1].toInt() and 0xFF)
    }

    /**
     * Parse PARAMS_RESPONSE payload (0x03) — ble_params_packet_t (33 bytes).
     * Returns the BLE device name, or null if payload is too short.
     *
     * Payload layout (matches ble_protocol.h ble_params_packet_t):
     *   [0–1]  p1_x10  [2–3] t_x10  [4–5] p2_x10  [6–7] p3_x10  [8–9] p4_x10
     *   [10–11] s_x10  [12] auto_mode  [13] sound_on  [14] brightness
     *   [15] volume  [16] theme  [17–32] ble_name (16 bytes, null-terminated)
     */
    fun decodeParamsResponse(payload: ByteArray): String? {
        if (payload.size < 33) return null
        val nameBytes = payload.copyOfRange(17, 33)
        val nullIdx = nameBytes.indexOf(0)
        return if (nullIdx >= 0) {
            String(nameBytes, 0, nullIdx, Charsets.UTF_8).trim()
        } else {
            String(nameBytes, Charsets.UTF_8).trim()
        }.ifEmpty { null }
    }

    // ========================================================================
    // Encoders
    // ========================================================================

    /**
     * Encode PARAMS_WRITE packet (0x04) from WeldParams.
     *
     * Payload layout (matches ble_protocol.h ble_params_packet_t = 33 bytes):
     *   [0–1]  p1_x10  [2–3] t_x10  [4–5] p2_x10  [6–7] p3_x10  [8–9] p4_x10
     *   [10–11] s_x10  [12] auto_mode  [13] sound_on  [14] brightness
     *   [15] volume  [16] theme  [17–32] ble_name (16 bytes)
     */
    fun encodeParamsWrite(params: WeldParams): ByteArray {
        val payload = ByteBuffer.allocate(33).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(params.p1X10.toShort())           // 0–1
            putShort(params.tX10.toShort())             // 2–3
            putShort(params.p2X10.toShort())            // 4–5
            putShort(params.p3X10.toShort())            // 6–7
            putShort(params.p4X10.toShort())            // 8–9
            putShort(params.sX10.toShort())             // 10–11
            put(if (params.autoMode) 1 else 0)          // 12
            put(if (params.soundOn) 1 else 0)           // 13
            put(params.brightness.toByte())             // 14
            put(params.volume.toByte())                 // 15
            put(params.theme.toByte())                  // 16

            // BLE name: 16 bytes, null-terminated, padded
            val nameBytes = params.bleName.toByteArray(Charsets.UTF_8)
            val namePadded = ByteArray(16)
            nameBytes.copyInto(namePadded, 0, 0, minOf(nameBytes.size, 15))
            put(namePadded)                             // 17–32
        }
        return buildPacket(TYPE_PARAMS_WRITE, payload.array())
    }

    /**
     * Encode CMD packet — Save Preset (pulse parameters only).
     *
     * Firmware payload layout:
     *   [CMD_SAVE_PRESET][index][name[20]][p1_x10 LE2][t_x10 LE2][p2_x10 LE2]
     *   [p3_x10 LE2][p4_x10 LE2]
     *   = 1 + 1 + 20 + 2 + 2 + 2 + 2 + 2 = 32 bytes
     *
     * NOTE: S delay and auto_mode are device-level preferences — NOT per-preset.
     */
    fun encodeCmdSavePreset(
        index: Int,
        name: String,
        p1Ms: Float,
        tMs: Float,
        p2Ms: Float,
        p3Ms: Float,
        p4Ms: Float,
    ): ByteArray {
        val namePadded = ByteArray(20)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        nameBytes.copyInto(namePadded, 0, 0, minOf(nameBytes.size, 19))

        val payload = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(CMD_SAVE_PRESET)                      // [0] sub-command
            put(index.toByte())                       // [1] preset index
            put(namePadded)                           // [2–21] name (20 bytes)
            putShort((p1Ms * 10).toInt().toShort())   // [22–23] p1_x10
            putShort((tMs * 10).toInt().toShort())    // [24–25] t_x10
            putShort((p2Ms * 10).toInt().toShort())   // [26–27] p2_x10
            putShort((p3Ms * 10).toInt().toShort())   // [28–29] p3_x10
            putShort((p4Ms * 10).toInt().toShort())   // [30–31] p4_x10
        }
        return buildPacket(TYPE_CMD, payload.array())
    }

    /**
     * Encode CMD packet — Load Preset.
     */
    fun encodeCmdLoadPreset(presetIndex: Int): ByteArray {
        val payload = byteArrayOf(CMD_LOAD_PRESET, presetIndex.toByte())
        return buildPacket(TYPE_CMD, payload)
    }

    /**
     * Encode CMD packet — Factory Reset.
     */
    fun encodeCmdFactoryReset(): ByteArray {
        return buildPacket(TYPE_CMD, byteArrayOf(CMD_FACTORY_RESET))
    }

    /**
     * Encode CMD packet — Reboot Device.
     */
    fun encodeCmdReboot(): ByteArray {
        return buildPacket(TYPE_CMD, byteArrayOf(CMD_REBOOT))
    }

    /**
     * Encode CMD packet — Reset Weld Counter.
     * @param target 0=session, 1=total, 2=both
     */
    fun encodeCmdResetWeldCounter(target: Int): ByteArray {
        val payload = byteArrayOf(CMD_RESET_WELD_COUNTER, target.toByte())
        return buildPacket(TYPE_CMD, payload)
    }

    /**
     * Encode CMD packet — Calibrate ADC.
     * Sends the user's multimeter reference voltage to the ESP32 which computes
     * a correction factor and stores it in NVS.
     * @param channel 0 = supercap voltage, 1 = protection rail
     * @param referenceMv reference voltage in millivolts (e.g. 4730 for 4.73V)
     */
    fun encodeCmdCalibrateAdc(channel: Int, referenceMv: Int): ByteArray {
        val payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(CMD_CALIBRATE_ADC)
            put(channel.toByte())
            putShort(referenceMv.toShort())
        }
        return buildPacket(TYPE_CMD, payload.array())
    }

    /**
     * Encode PIN authentication command.
     * Payload: [CMD_AUTH, pin[4 ASCII digits], 0x00] = 6 bytes total.
     */
    fun encodeCmdAuth(pin: String): ByteArray {
        val pinBytes = ByteArray(5) // 4 digits + null terminator
        pin.take(4).forEachIndexed { i, c -> pinBytes[i] = c.code.toByte() }
        val payload = ByteArray(6)
        payload[0] = CMD_AUTH
        pinBytes.copyInto(payload, 1)
        return buildPacket(TYPE_CMD, payload)
    }

    /**
     * Encode change-PIN command (must be authenticated first).
     */
    fun encodeCmdChangePin(newPin: String): ByteArray {
        val pinBytes = ByteArray(5)
        newPin.take(4).forEachIndexed { i, c -> pinBytes[i] = c.code.toByte() }
        val payload = ByteArray(6)
        payload[0] = CMD_CHANGE_PIN
        pinBytes.copyInto(payload, 1)
        return buildPacket(TYPE_CMD, payload)
    }

    /**
     * Encode PRESET_LIST request (0x0A) — no payload.
     * Firmware responds via notify with BLE_MSG_PRESET_LIST_RESP (0x0B).
     */
    fun encodePresetListRequest(): ByteArray = buildPacket(TYPE_PRESET_LIST)

    /**
     * Decode paginated PRESET_LIST_RESP (0x0B) payload — 151 bytes per page.
     *
     * Firmware sends full preset data (not just names):
     *   [page_index(1)]
     *   Per preset (5 per page):
     *     [name: 20 bytes] [p1_x10: 2] [t_x10: 2] [p2_x10: 2] [p3_x10: 2] [p4_x10: 2]
     *   = 30 bytes per preset, 5 per page → 151 bytes total.
     *
     * @return Pair(pageIndex 0–3, list of 5 WeldPreset) or null on bad input.
     */
    fun decodePresetListResp(payload: ByteArray): Pair<Int, List<WeldPreset>>? {
        val nameLen = 20
        val entrySize = 30  // 20 (name) + 5 × 2 (params)
        val slotsPerPage = 5
        val minSize = 1 + entrySize * slotsPerPage  // 151 bytes
        if (payload.size < minSize) return null

        val pageIndex = payload[0].toInt() and 0xFF
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(1) // skip page_index

        val presets = (0 until slotsPerPage).map { i ->
            val baseIndex = pageIndex * slotsPerPage + i
            val nameBytes = ByteArray(nameLen)
            buf.get(nameBytes)
            val nullIdx = nameBytes.indexOf(0)
            val name = if (nullIdx >= 0) String(nameBytes, 0, nullIdx, Charsets.UTF_8)
                       else String(nameBytes, Charsets.UTF_8)

            val p1 = (buf.short.toInt() and 0xFFFF) / 10.0f
            val t  = (buf.short.toInt() and 0xFFFF) / 10.0f
            val p2 = (buf.short.toInt() and 0xFFFF) / 10.0f
            val p3 = (buf.short.toInt() and 0xFFFF) / 10.0f
            val p4 = (buf.short.toInt() and 0xFFFF) / 10.0f

            WeldPreset(
                index = baseIndex,
                name  = name.trim(),
                p1Ms  = p1,
                tMs   = t,
                p2Ms  = p2,
                p3Ms  = p3,
                p4Ms  = p4,
            )
        }
        return Pair(pageIndex, presets)
    }

    /**
     * Encode VERSION request packet (0x08) — no payload.
     */
    fun encodeVersionRequest(): ByteArray {
        return buildPacket(TYPE_VERSION)
    }

    /**
     * Encode PARAMS_READ request (0x02) — no payload.
     */
    fun encodeParamsRead(): ByteArray {
        return buildPacket(TYPE_PARAMS_READ)
    }

    // ========================================================================
    // OTA Encoders / Decoders
    // ========================================================================

    /**
     * Encode OTA_BEGIN packet (0x10).
     * Payload: [total_size_u32_LE][sha256_32B][fw_major][fw_minor][fw_patch][hw_compat_id_u32_LE]
     *        = 43 bytes (protocol V5+).
     *
     * @param hwCompatId Target hardware compatibility ID. Use 0 to skip check
     *                   (backwards-compatible with pre-V5 firmware).
     */
    fun encodeOtaBegin(
        totalSize: Int,
        sha256: ByteArray,
        fwMajor: Int,
        fwMinor: Int,
        fwPatch: Int,
        hwCompatId: Long = 0L,
    ): ByteArray {
        require(sha256.size == 32) { "SHA-256 must be 32 bytes" }
        val payload = ByteBuffer.allocate(43).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(totalSize)              // 0–3
            put(sha256)                    // 4–35
            put(fwMajor.toByte())          // 36
            put(fwMinor.toByte())          // 37
            put(fwPatch.toByte())          // 38
            putInt(hwCompatId.toInt())     // 39–42
        }
        return buildPacket(TYPE_OTA_BEGIN, payload.array())
    }

    /**
     * Encode OTA_DATA packet (0x11).
     * Payload: [seq_u16_LE][data_up_to_240B].
     */
    fun encodeOtaData(seq: Int, chunkData: ByteArray): ByteArray {
        val payload = ByteBuffer.allocate(2 + chunkData.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(seq.toShort())
            put(chunkData)
        }
        return buildPacket(TYPE_OTA_DATA, payload.array())
    }

    /**
     * Encode OTA_END packet (0x12) — no payload.
     */
    fun encodeOtaEnd(): ByteArray = buildPacket(TYPE_OTA_END)

    /**
     * Encode OTA_ABORT packet (0x14).
     */
    fun encodeOtaAbort(reason: Int = OTA_STATUS_ABORT): ByteArray {
        return buildPacket(TYPE_OTA_ABORT, byteArrayOf(reason.toByte()))
    }

    /**
     * Decode OTA_ACK payload (0x13) — 4 bytes.
     * @return OtaAckResult or null if payload too short.
     */
    fun decodeOtaAck(payload: ByteArray): OtaAckResult? {
        if (payload.size < 4) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        return OtaAckResult(
            status = buf.get(0).toInt() and 0xFF,
            progress = buf.get(1).toInt() and 0xFF,
            seq = buf.getShort(2).toInt() and 0xFFFF,
        )
    }

    /**
     * Decode OTA_RESULT payload (0x15) — 1 byte.
     * @return OtaResult or null if payload empty.
     */
    fun decodeOtaResult(payload: ByteArray): OtaResult? {
        if (payload.isEmpty()) return null
        return OtaResult(result = payload[0].toInt() and 0xFF)
    }

    /**
     * Decode VERSION_RESPONSE payload (0x09) — 19 bytes (protocol V5+).
     *
     * Layout (ble_version_packet_t):
     *   [0] major  [1] minor  [2] patch  [3] reserved
     *   [4–11] build_date  [12] board_variant  [13] display_type
     *   [14] audio_type  [15–18] hw_compat_id (u32 LE)
     *
     * Falls back gracefully for pre-V5 firmware that sends shorter packets.
     */
    fun decodeVersionResponse(payload: ByteArray): VersionInfo? {
        if (payload.size < 4) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        return VersionInfo(
            major = buf.get(0).toInt() and 0xFF,
            minor = buf.get(1).toInt() and 0xFF,
            patch = buf.get(2).toInt() and 0xFF,
            boardVariant = if (payload.size >= 13) buf.get(12).toInt() and 0xFF else 0,
            displayType  = if (payload.size >= 14) buf.get(13).toInt() and 0xFF else 0,
            audioType    = if (payload.size >= 15) buf.get(14).toInt() and 0xFF else 0,
            hwCompatId   = if (payload.size >= 19) buf.getInt(15).toLong() and 0xFFFFFFFFL else 0L,
        )
    }

    // ========================================================================
    // Packet extraction from stream
    // ========================================================================

    /**
     * Try to extract one complete packet from a byte buffer.
     * @return Pair(packet, remainingBytes) or null if incomplete.
     */
    fun extractPacket(buffer: ByteArray): Pair<ParsedPacket, ByteArray>? {
        // Find SYNC byte
        val syncIndex = buffer.indexOf(SYNC_BYTE)
        if (syncIndex < 0) return null

        val data = if (syncIndex > 0) buffer.copyOfRange(syncIndex, buffer.size) else buffer
        if (data.size < HEADER_SIZE + CRC_SIZE) return null

        val type = data[1]
        val len = data[2].toInt() and 0xFF
        val totalSize = HEADER_SIZE + len + CRC_SIZE

        if (data.size < totalSize) return null

        val packet = data.copyOfRange(0, totalSize)
        val remaining = data.copyOfRange(totalSize, data.size)

        if (!validatePacket(packet)) {
            // Skip this SYNC and try next one
            val nextBuffer = data.copyOfRange(1, data.size)
            return extractPacket(nextBuffer)
        }

        val payload = packet.copyOfRange(HEADER_SIZE, HEADER_SIZE + len)
        return Pair(ParsedPacket(type, payload), remaining)
    }
}

/**
 * A parsed binary packet with type and payload.
 */
data class ParsedPacket(
    val type: Byte,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParsedPacket) return false
        return type == other.type && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        return 31 * type.hashCode() + payload.contentHashCode()
    }
}
