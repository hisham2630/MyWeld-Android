package com.myweld.app.data.model

/**
 * A weld preset profile — matches firmware's weld_preset_t.
 *
 * Presets contain ONLY pulse parameters (P1, T, P2, P3, P4).
 * S delay and auto_mode are device-level user preferences — NOT per-preset.
 */
data class WeldPreset(
    val index: Int,
    val name: String,
    val p1Ms: Float,
    val tMs: Float,
    val p2Ms: Float,
    val p3Ms: Float = 0.0f,
    val p4Ms: Float = 0.0f,
) {
    companion object {
        const val MAX_PRESETS = 20   // 7 factory (read-only) + 13 user-custom
        const val NAME_MAX_LEN = 19
        const val PRESETS_PER_PAGE = 5   // Matches firmware BLE_PRESETS_PER_PAGE
        const val TOTAL_PAGES = MAX_PRESETS / PRESETS_PER_PAGE  // 4

        /**
         * Sentinel value sent by firmware when no preset is loaded.
         * Maps to PRESET_USER_DEFINED (0xFF) in config.h.
         */
        const val PRESET_USER_DEFINED = 0xFF
    }
}
