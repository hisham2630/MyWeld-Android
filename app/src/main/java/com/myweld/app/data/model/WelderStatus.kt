package com.myweld.app.data.model

/**
 * Live status snapshot from the welder.
 * Parsed from BLE STATUS packets (0x01).
 */
data class WelderStatus(
    val supercapVoltageMv: Int = 0,
    val protectionVoltageMv: Int = 0,
    val state: WeldState = WeldState.IDLE,
    val chargePercent: Int = 0,
    val autoMode: Boolean = false,
    val activePreset: Int = 0,
    val sessionWelds: Long = 0,
    val totalWelds: Long = 0,
    val soundOn: Boolean = true,
    val brightness: Int = 80,
    val volume: Int = 80,
    val theme: Int = 0,
    val errorCode: Int = 0,
    val bleName: String = "MyWeld",
    val p1Ms: Float = 0f,
    val tMs: Float = 0f,
    val p2Ms: Float = 0f,
    val p3Ms: Float = 0f,
    val p4Ms: Float = 0f,
    val sSeconds: Float = 0f,
    val fwMajor: Int = 0,
    val fwMinor: Int = 0,
    val authLockoutSec: Int = 0,
    // Debug / calibration data (from extended status packet)
    val rawSupercapMv: Int = 0,
    val rawProtectionMv: Int = 0,
    val calFactorVx1000: Int = 1000,
    val calFactorPx1000: Int = 1000,
    val maxSupercapMv: Int = 5700,  // Configured max supercap voltage (mV), default 5.7V
) {
    /** Supercap voltage in Volts */
    val supercapVoltage: Float get() = supercapVoltageMv / 1000f

    /** Protection rail voltage in Volts */
    val protectionVoltage: Float get() = protectionVoltageMv / 1000f

    /** Firmware version string */
    val firmwareVersion: String get() = "$fwMajor.$fwMinor"

    /** Whether welder is in a safe state (not firing) */
    val isSafe: Boolean get() = state != WeldState.FIRING

    /** Whether voltage is critically low */
    val isLowVoltage: Boolean get() = supercapVoltage < 4.0f

    // Debug / calibration computed properties
    /** Raw (uncalibrated) supercap voltage in Volts */
    val rawSupercapVoltage: Float get() = rawSupercapMv / 1000f

    /** Raw (uncalibrated) protection voltage in Volts */
    val rawProtectionVoltage: Float get() = rawProtectionMv / 1000f

    /** Supercap ADC calibration factor */
    val calFactorVoltage: Float get() = calFactorVx1000 / 1000f

    /** Protection ADC calibration factor */
    val calFactorProtection: Float get() = calFactorPx1000 / 1000f

    /** Whether calibration factors are non-default (device has been calibrated) */
    val isCalibrated: Boolean get() = calFactorVx1000 != 1000 || calFactorPx1000 != 1000

    /** Configured max supercap voltage in Volts */
    val maxSupercapVoltage: Float get() = maxSupercapMv / 1000f

    /** Formatted max supercap voltage string (e.g. "5.70 V") */
    val maxSupercapVoltageFormatted: String get() = "${"%,.2f".format(maxSupercapVoltage)} V"
}
