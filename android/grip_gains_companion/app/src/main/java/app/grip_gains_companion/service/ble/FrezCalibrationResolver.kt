package app.grip_gains_companion.service.ble

import java.io.IOException

/** Refresh online; only transport failures may fall back to a device-specific saved factor. */
class FrezCalibrationResolver(
    private val read: (String) -> Double?,
    private val save: (String, Double) -> Boolean,
    private val fetch: (String) -> Double
) {
    data class Calibration(val coefficient: Double, val offline: Boolean, val saved: Boolean)

    fun load(serial: String): Calibration {
        val coefficient = try {
            fetch(serial)
        } catch (error: IOException) {
            val cached = read(serial)?.takeIf { it.isFinite() && it != 0.0 }
                ?: throw IOException("No saved FREZ calibration for this device. Connect to the internet once, then reconnect.", error)
            return Calibration(cached, offline = true, saved = true)
        }
        require(coefficient.isFinite() && coefficient != 0.0) { "Invalid FREZ calibration factor. Reconnect to retry." }
        return Calibration(coefficient, offline = false, saved = save(serial, coefficient))
    }
}
