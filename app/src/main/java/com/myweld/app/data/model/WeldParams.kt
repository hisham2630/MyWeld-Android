package com.myweld.app.data.model

/**
 * Weld parameters that can be read/written via BLE.
 * Maps to PARAMS_WRITE payload (0x04).
 */
data class WeldParams(
    val p1Ms: Float = 5.0f,
    val tMs: Float = 0.0f,
    val p2Ms: Float = 0.0f,
    val p3Ms: Float = 0.0f,
    val p4Ms: Float = 0.0f,
    val sSeconds: Float = 0.5f,
    val autoMode: Boolean = false,
    val soundOn: Boolean = true,
    val brightness: Int = 80,
    val volume: Int = 80,
    val theme: Int = 0,
    val bleName: String = "MyWeld",
) {
    companion object {
        const val PULSE_MIN = 5.0f
        const val PULSE_MAX = 50.0f
        const val PULSE_STEP = 5.0f

        const val PAUSE_MIN = 20.0f
        const val PAUSE_MAX = 150.0f
        const val PAUSE_STEP = 5.0f

        const val S_MIN = 0.3f
        const val S_MAX = 2.0f
        const val S_STEP = 0.1f
        const val BLE_NAME_MAX_LEN = 15
    }

    /** P1 in firmware's 0.1ms integer units */
    val p1X10: Int get() = (p1Ms * 10).toInt()
    /** T in firmware's 0.1ms integer units */
    val tX10: Int get() = (tMs * 10).toInt()
    /** P2 in firmware's 0.1ms integer units */
    val p2X10: Int get() = (p2Ms * 10).toInt()
    /** P3 in firmware's 0.1ms integer units (0 = disabled) */
    val p3X10: Int get() = (p3Ms * 10).toInt()
    /** P4 in firmware's 0.1ms integer units (0 = disabled) */
    val p4X10: Int get() = (p4Ms * 10).toInt()
    /** S in firmware's 0.1s integer units */
    val sX10: Int get() = (sSeconds * 10).toInt()
}
