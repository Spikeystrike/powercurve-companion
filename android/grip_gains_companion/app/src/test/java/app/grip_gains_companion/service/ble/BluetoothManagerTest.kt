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
    @Test fun selectingDeviceFindsFrezWhileWaitingForSavedWhc06() = checkFrezDiscovery(false)
    @Test fun selectingDeviceFindsFrezWhileWhc06IsConnected() = checkFrezDiscovery(true)
    private fun checkFrezDiscovery(connected: Boolean) {
        val app=RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(android.Manifest.permission.BLUETOOTH_SCAN,android.Manifest.permission.BLUETOOTH_CONNECT)
        val adapter=BluetoothAdapter.getDefaultAdapter()
        shadowOf(adapter).setState(BluetoothAdapter.STATE_ON)
        val prefs=app.getSharedPreferences("bluetooth_prefs",Context.MODE_PRIVATE)
        prefs.edit().clear().putString("last_connected_device","00:11:22:33:44:55")
            .putString("last_connected_type",DeviceType.WEIHENG_WHC06.name).commit()
        val scanner=shadowOf(adapter.bluetoothLeScanner)
        val parser=ScanRecord::class.java.getDeclaredMethod("parseFromBytes",ByteArray::class.java).apply {isAccessible=true}
        val name="FrezDyno-123456".toByteArray()
        val record=parser.invoke(null,byteArrayOf((name.size+1).toByte(),9)+name) as ScanRecord
        val frez=ScanResult(adapter.getRemoteDevice("00:11:22:33:44:66"),record,-50,1_000_000)
        scanner.addScanResult(frez)
        val manager=BluetoothManager(app)
        manager.startScanning()
        assertTrue(scanner.activeScans.single().scanFilters().isNotEmpty())
        assertTrue(manager.discoveredDevices.value.none {it.type==DeviceType.FREZ_DYNO})
        if(connected) {
            val payload=ByteArray(15).apply {this[10]=7;this[11]=0xd0.toByte();this[14]=1}
            val whcRecord=parser.invoke(null,byteArrayOf(18,0xff.toByte(),0,1)+payload) as ScanRecord
            scanner.scanCallbacks.single().onScanResult(1,ScanResult(adapter.getRemoteDevice("00:11:22:33:44:55"),whcRecord,-50,1_000_000))
            assertEquals(ConnectionState.Connected,manager.connectionState.value)
        }
        manager.beginDeviceSelection()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8))
        assertTrue(scanner.activeScans.single().scanFilters().isEmpty())
        assertEquals("FrezDyno-123456",manager.discoveredDevices.value.single {it.type==DeviceType.FREZ_DYNO}.name)
        if(connected) assertEquals(ConnectionState.Connected,manager.connectionState.value)
        manager.endDeviceSelection()
        manager.disconnect(preserveAutoReconnect=true)
    }
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
        assertEquals(ConnectionState.Connecting, manager.connectionState.value)
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
        assertTrue(manager.receivingSamples.value)
        assertTrue(manager.sampleConnection.value>0L)
        val firstConnection=manager.sampleConnection.value
        callback.onScanResult(1,result)
        assertEquals(firstConnection,manager.sampleConnection.value)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        assertFalse(manager.receivingSamples.value)
        callback.onScanResult(1,result)
        assertEquals(firstConnection,manager.sampleConnection.value)
        manager.restartScanning()
        assertEquals(ConnectionState.Connected, manager.connectionState.value)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
        assertEquals(ConnectionState.Reconnecting, manager.connectionState.value)
        callback.onScanResult(1, result)
        assertEquals(ConnectionState.Connected, manager.connectionState.value)
        assertTrue(manager.sampleConnection.value>firstConnection)
        callback.onScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
        callback.onScanResult(1, result)
        assertEquals(ConnectionState.Connected, manager.connectionState.value)
        manager.disconnect(preserveAutoReconnect = true)
        assertEquals(address, prefs.getString("last_connected_device", null))
        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
    }
    @Test fun silentDiscoveryRestartsAndManualRetriesRespectCooldown() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setState(BluetoothAdapter.STATE_ON)
        app.getSharedPreferences("bluetooth_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val manager = BluetoothManager(app)
        val field = BluetoothManager::class.java.getDeclaredField("lastScanStart").apply { isAccessible = true }
        manager.startScanning()
        val initial = field.getLong(manager)
        repeat(10) { manager.restartScanning() }
        assertEquals(initial, field.getLong(manager))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8))
        assertTrue(field.getLong(manager) >= initial + 8_000)
        val manual = field.getLong(manager)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertTrue(field.getLong(manager) >= manual + 30_000)
        assertEquals(ConnectionState.Scanning, manager.connectionState.value)
        manager.disconnect(preserveAutoReconnect = true)
        val stopped = field.getLong(manager)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertEquals(stopped, field.getLong(manager))
    }

}
