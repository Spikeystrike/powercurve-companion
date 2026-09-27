package app.grip_gains_companion.service.ble

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Looper
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.model.DeviceType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BluetoothManagerTest {
    @Test fun savedScaleCanStartLaterWithoutAdvertisingItsNameAndReconnectAfterSilence() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setState(BluetoothAdapter.STATE_ON)
        val address = "00:11:22:33:44:55"
        val prefs = app.getSharedPreferences("bluetooth_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("last_connected_device", address)
            .putString("selected_device_type", DeviceType.WEIHENG_WHC06.name).commit()
        val manager = BluetoothManager(app)
        manager.startScanning()
        assertEquals(ConnectionState.Connecting, manager.connectionState.value)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertEquals(ConnectionState.Reconnecting, manager.connectionState.value)
        val field = BluetoothManager::class.java.getDeclaredField("scanCallback").apply { isAccessible = true }
        val callback = field.get(manager) as ScanCallback
        val payload = ByteArray(15).apply { this[10] = 7; this[11] = 0xd0.toByte(); this[14] = 1 }
        val bytes = byteArrayOf(18, 0xff.toByte(), 0, 1) + payload
        val method = ScanRecord::class.java.getDeclaredMethod("parseFromBytes", ByteArray::class.java).apply { isAccessible = true }
        val record = method.invoke(null, bytes) as ScanRecord
        val result = ScanResult(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address), record, -50, 1_000_000)
        var weight = 0.0
        manager.onForceSample = { kg, _ -> weight = kg }
        callback.onScanResult(1, result)
        assertEquals(ConnectionState.Connected, manager.connectionState.value)
        assertEquals(20.0, weight, 0.001)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
        assertEquals(ConnectionState.Reconnecting, manager.connectionState.value)
        callback.onScanResult(1, result)
        assertEquals(ConnectionState.Connected, manager.connectionState.value)
        callback.onScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
        callback.onScanResult(1, result)
        assertEquals(ConnectionState.Connected, manager.connectionState.value)
        manager.disconnect(preserveAutoReconnect = true)
        assertEquals(address, prefs.getString("last_connected_device", null))
        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
    }
}
