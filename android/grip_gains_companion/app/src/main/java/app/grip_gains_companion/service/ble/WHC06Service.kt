package app.grip_gains_companion.service.ble

import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.grip_gains_companion.util.AppLogger

class WHC06Service {
    companion object {
        private const val TAG = "WHC06Service"
        private const val DISCONNECT_TIMEOUT_MS = 15000L
    }
    var onForceSample: ((Double, Long) -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null
    var assumeHardwareIsLbs = false
    private val handler = Handler(Looper.getMainLooper())
    private var disconnectTimer: Runnable? = null
    private var receivedSample = false
    private var loggedRejected = false

    fun start() {
        receivedSample = false
        loggedRejected = false
        AppLogger.i(TAG, "Waiting for WH-C06 weight advertisements")
        resetDisconnectTimer()
    }
    fun stop() { cancelDisconnectTimer() }

    // BluetoothManager calls this only for the selected device address.
    fun processAdvertisement(result: ScanResult): Boolean {
        val record = result.scanRecord ?: return false
        val blocks = record.manufacturerSpecificData
        val preferred = record.getManufacturerSpecificData(0x0100)
        var payload = preferred?.takeIf { it.size >= 12 }
        if (payload == null) {
            for (i in 0 until blocks.size()) {
                val candidate = blocks.valueAt(i)
                if (candidate.size >= 12) { payload = candidate; break }
            }
        }
        val weight = Whc06Decoder.decode(payload, assumeHardwareIsLbs)
        if (weight == null) {
            if (!loggedRejected) {
                val sizes = (0 until blocks.size()).joinToString { "${blocks.keyAt(it).toString(16)}:${blocks.valueAt(it).size}" }
                AppLogger.w(TAG, "Advertisement has no weight payload (manufacturer id:length = $sizes)")
                loggedRejected = true
            }
            return false
        }
        if (!receivedSample) {
            AppLogger.i(TAG, "Receiving weight samples; unit code=${payload?.getOrNull(14)?.toInt()?.and(15) ?: 0}, fallback=${if (assumeHardwareIsLbs) "lb" else "kg"}")
            receivedSample = true
        }
        resetDisconnectTimer()
        onForceSample?.invoke(weight, SystemClock.elapsedRealtimeNanos() / 1000)
        return true
    }
    private fun resetDisconnectTimer() {
        cancelDisconnectTimer()
        disconnectTimer = Runnable {
            AppLogger.w(TAG, "No valid WH-C06 readings for 15 seconds; waiting for the scale")
            receivedSample = false
            onDisconnect?.invoke()
        }
        handler.postDelayed(disconnectTimer!!, DISCONNECT_TIMEOUT_MS)
    }
    private fun cancelDisconnectTimer() {
        disconnectTimer?.let(handler::removeCallbacks)
        disconnectTimer = null
    }
}
