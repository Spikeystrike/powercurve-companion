package app.grip_gains_companion.service.ble

import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import android.util.Log

class WHC06Service {

    companion object {
        private const val TAG = "WHC06Service"
        private const val DISCONNECT_TIMEOUT_MS = 15000L // Bumped to 15s to tolerate crowded gym interference
    }

    var onForceSample: ((Double, Long) -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null
    var assumeHardwareIsLbs: Boolean = false

    private var baseTimestamp: Long = 0
    private var sampleCounter: Long = 0
    private var disconnectTimer: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    fun start() {
        Log.i(TAG, "Starting WHC06 service...")
        baseTimestamp = System.currentTimeMillis() * 1000
        sampleCounter = 0
        resetDisconnectTimer()
    }

    fun stop() {
        Log.i(TAG, "Stopping WHC06 service...")
        cancelDisconnectTimer()
    }

    fun processAdvertisement(scanResult: ScanResult) {
        val data = scanResult.scanRecord?.getManufacturerSpecificData(0x0100) ?: return
        val weight = Whc06Decoder.decode(data) ?: return
        resetDisconnectTimer()
        // Device advertisement intervals vary; use monotonic arrival time in microseconds.
        onForceSample?.invoke(weight, android.os.SystemClock.elapsedRealtimeNanos() / 1000)
    }

    private fun resetDisconnectTimer() {
        cancelDisconnectTimer()
        disconnectTimer = Runnable {
            Log.i(TAG, "WHC06 disconnect timeout - no advertisements received")
            onDisconnect?.invoke()
        }
        handler.postDelayed(disconnectTimer!!, DISCONNECT_TIMEOUT_MS)
    }

    private fun cancelDisconnectTimer() {
        disconnectTimer?.let { handler.removeCallbacks(it) }
        disconnectTimer = null
    }
}