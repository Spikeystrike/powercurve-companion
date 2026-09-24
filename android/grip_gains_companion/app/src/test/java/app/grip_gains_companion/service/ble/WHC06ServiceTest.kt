package app.grip_gains_companion.service.ble

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WHC06ServiceTest {
    private fun packet(id: Int, payload: ByteArray): ScanResult {
        val bytes = byteArrayOf((payload.size + 3).toByte(), 0xff.toByte(), id.toByte(), (id shr 8).toByte()) + payload
        val method = ScanRecord::class.java.getDeclaredMethod("parseFromBytes", ByteArray::class.java)
        method.isAccessible = true
        val record = method.invoke(null, bytes) as ScanRecord
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55")
        return ScanResult(device, record, -50, 1_000_000)
    }
    private fun payload(size: Int = 15, unit: Int = 1) = ByteArray(size).also {
        if (size >= 12) { it[10] = 7; it[11] = 0xd0.toByte() }
        if (size >= 15) it[14] = unit.toByte()
    }
    @Test fun alternateManufacturerAndMissingUnitReachTheForceCallback() {
        val service = WHC06Service()
        val values = mutableListOf<Double>()
        service.onForceSample = { kg, _ -> values.add(kg) }
        service.start()
        assertTrue(service.processAdvertisement(packet(0x0100, payload())))
        assertTrue(service.processAdvertisement(packet(0x1234, payload(12))))
        assertEquals(listOf(20.0, 20.0), values)
        service.stop()
    }
    @Test fun invalidPacketsDoNotReportConnectedOrEmitZeroReadings() {
        val service = WHC06Service()
        var samples = 0
        var timeouts = 0
        service.onForceSample = { _, _ -> samples++ }
        service.onDisconnect = { timeouts++ }
        service.start()
        assertFalse(service.processAdvertisement(packet(0x0100, payload(4))))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
        assertEquals(0, samples)
        assertEquals(1, timeouts)
        assertTrue(service.processAdvertisement(packet(0x0100, payload())))
        assertEquals(1, samples)
        service.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
        assertEquals(1, timeouts)
    }
    @Test fun fallbackPoundsDoNotOverrideAValidKilogramCode() {
        val service = WHC06Service().apply { assumeHardwareIsLbs = true }
        val values = mutableListOf<Double>()
        service.onForceSample = { kg, _ -> values.add(kg) }
        service.start()
        service.processAdvertisement(packet(0x0100, payload(12)))
        service.processAdvertisement(packet(0x0100, payload()))
        assertEquals(9.0718474, values[0], 0.000001)
        assertEquals(20.0, values[1], 0.000001)
        service.stop()
    }
}
