package com.myweld.app.data.model

/**
 * Weld machine states — maps 1:1 to firmware map_weld_state() output.
 *
 * Protocol codes (ESP32 → Android):
 *   0 = IDLE       (ready, waiting)
 *   1 = READY      (armed / contact detected)
 *   2 = CHARGING   (pre-fire settling)
 *   3 = FIRING     (P1, PAUSE, or P2)
 *   4 = COOLDOWN   (re-enabling charger)
 *   5 = BLOCKED    (low voltage or protection fault)
 *   6 = ERROR      (hardware fault)
 */
enum class WeldState(val code: Int, val label: String) {
    IDLE(0, "IDLE"),
    READY(1, "READY"),
    CHARGING(2, "CHARGING"),
    FIRING(3, "FIRING"),
    COOLDOWN(4, "COOLDOWN"),
    BLOCKED(5, "BLOCKED"),
    ERROR(6, "ERROR");

    companion object {
        fun fromCode(code: Int): WeldState =
            entries.firstOrNull { it.code == code } ?: ERROR
    }
}
