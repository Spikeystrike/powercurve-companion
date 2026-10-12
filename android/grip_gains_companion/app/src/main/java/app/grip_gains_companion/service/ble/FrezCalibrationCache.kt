package app.grip_gains_companion.service.ble

import android.content.Context

/** Coefficients persist separately for each serial number; the unloaded tare is never cached. */
class FrezCalibrationCache(context: Context) {
    private val preferences = context.getSharedPreferences("frez_calibration", Context.MODE_PRIVATE)

    fun read(serial: String): Double? = runCatching {
        preferences.getString(serial, null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it != 0.0 }
    }.getOrNull()

    fun save(serial: String, coefficient: Double): Boolean {
        require(serial.isNotBlank() && coefficient.isFinite() && coefficient != 0.0)
        return preferences.edit().putString(serial, coefficient.toString()).commit()
    }
}
