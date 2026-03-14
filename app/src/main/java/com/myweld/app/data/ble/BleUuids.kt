package com.myweld.app.data.ble

import java.util.UUID

/**
 * BLE Service and Characteristic UUIDs — must match firmware.
 *
 * Base UUID pattern: 12XX0000-0000-1000-8000-00805F9B34FB
 *   where XX = 34 (service), 35 (params), 36 (status/TX), 37 (cmd/RX)
 */
object BleUuids {
    /** MyWeld GATT Service UUID */
    val SERVICE: UUID = UUID.fromString("00001234-0000-1000-8000-00805F9B34FB")

    /** Params characteristic — READ: one-shot params read */
    val CHAR_PARAMS: UUID = UUID.fromString("00001235-0000-1000-8000-00805F9B34FB")

    /** Status/TX characteristic — READ, NOTIFY: periodic status from ESP32 */
    val CHAR_STATUS: UUID = UUID.fromString("00001236-0000-1000-8000-00805F9B34FB")

    /** Command/RX characteristic — WRITE: send commands to ESP32 */
    val CHAR_CMD: UUID = UUID.fromString("00001237-0000-1000-8000-00805F9B34FB")

    /** OTA characteristic — WRITE: send firmware chunks, NOTIFY: receive OTA status */
    val CHAR_OTA: UUID = UUID.fromString("00001238-0000-1000-8000-00805F9B34FB")

    /** Client Characteristic Configuration Descriptor (for enabling notifications) */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}
