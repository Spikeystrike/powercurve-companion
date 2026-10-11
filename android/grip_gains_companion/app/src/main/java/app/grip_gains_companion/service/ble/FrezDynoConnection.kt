package app.grip_gains_companion.service.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.Executors

/** Serialized GATT setup: MTU -> serial -> coefficient API -> notifications -> stream. */
@SuppressLint("MissingPermission")
class FrezDynoConnection(private val accessKey: String,private val autoTare: Boolean,
    private val status: (String)->Unit,private val connected: ()->Unit,
    private val lost: ()->Unit,private val failed: ()->Unit,private val sample: (Double,Long)->Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private val executor=Executors.newSingleThreadExecutor()
    private var gatt: BluetoothGatt?=null
    private var closed=false
    private var protocol: FrezProtocol?=null
    private var notify: BluetoothGattCharacteristic?=null
    private var write: BluetoothGattCharacteristic?=null
    private val timeout=Runnable {fail("FREZ setup timed out. Reconnect to retry.")}
    fun connect(context: Context,device: BluetoothDevice) {
        status("Connecting to FREZ Dyno…")
        gatt=device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE)
        handler.postDelayed(timeout,30000)
    }
    fun tare() {handler.post {if(!closed && protocol!=null) {protocol?.tare();status("Keep FREZ Dyno unloaded: zeroing 100 samples…")}}}
    fun close() {
        closed=true;handler.removeCallbacks(timeout);executor.shutdownNow()
        gatt?.disconnect();gatt?.close();gatt=null
    }
    private fun fail(message: String) {if(closed)return;status(message);close();failed()}
    private fun post(g: BluetoothGatt,action: ()->Unit) {handler.post {if(!closed && g===gatt) action()}}
    private val callback=object: BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt,s: Int,state: Int) = post(g) {
            if(s!=BluetoothGatt.GATT_SUCCESS || state==BluetoothProfile.STATE_DISCONNECTED) {close();lost()}
            else if(state==BluetoothProfile.STATE_CONNECTED && !g.requestMtu(185)) fail("FREZ MTU negotiation could not start.")
        }
        override fun onMtuChanged(g: BluetoothGatt,mtu: Int,s: Int) = post(g) {
            if(s!=BluetoothGatt.GATT_SUCCESS || mtu<77) fail("FREZ requires a Bluetooth MTU of at least 77 bytes.")
            else if(!g.discoverServices()) fail("FREZ service discovery failed.")
        }
        override fun onServicesDiscovered(g: BluetoothGatt,s: Int) = post(g) {
            val service=g.getService(SERVICE)
            notify=service?.getCharacteristic(NOTIFY);write=service?.getCharacteristic(WRITE)
            if(s!=BluetoothGatt.GATT_SUCCESS || notify==null || write==null) {fail("FREZ protocol v1 service is unavailable. Check device firmware.");return@post}
            val serial=g.getService(uuid("180a"))?.getCharacteristic(uuid("2a25"))
            if(serial==null || !g.readCharacteristic(serial)) fail("FREZ serial number is unavailable. Check device firmware.")
        }
        override fun onCharacteristicRead(g: BluetoothGatt,c: BluetoothGattCharacteristic,value: ByteArray,s: Int) = post(g) {readSerial(g,value,s)}
        @Deprecated("Legacy Android callback")
        override fun onCharacteristicRead(g: BluetoothGatt,c: BluetoothGattCharacteristic,s: Int) {
            @Suppress("DEPRECATION") val value=c.value ?: byteArrayOf()
            post(g) {readSerial(g,value,s)}
        }
        override fun onDescriptorWrite(g: BluetoothGatt,d: BluetoothGattDescriptor,s: Int) = post(g) {
            if(s!=BluetoothGatt.GATT_SUCCESS) {fail("FREZ notifications could not be enabled.");return@post}
            val c=write ?: return@post
            @Suppress("DEPRECATION")
            c.value=byteArrayOf(1,0)
            c.writeType=BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            if(!g.writeCharacteristic(c)) fail("FREZ measurement could not start.")
        }
        override fun onCharacteristicWrite(g: BluetoothGatt,c: BluetoothGattCharacteristic,s: Int) = post(g) {
            if(s!=BluetoothGatt.GATT_SUCCESS) {fail("FREZ start command failed.");return@post}
            handler.removeCallbacks(timeout);connected()
            if(autoTare) tare() else status("FREZ connected. Unload the sensor and tap Zero FREZ before measuring.")
        }
        override fun onCharacteristicChanged(g: BluetoothGatt,c: BluetoothGattCharacteristic,value: ByteArray) = post(g) {receive(c,value)}
        @Deprecated("Legacy Android callback")
        override fun onCharacteristicChanged(g: BluetoothGatt,c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") val value=c.value?.clone() ?: return
            post(g) {receive(c,value)}
        }
    }
    private fun readSerial(g: BluetoothGatt,bytes: ByteArray,result: Int) {
        val serial=String(bytes,Charsets.UTF_8).trim('\u0000',' ','\n','\r')
        if(result!=BluetoothGatt.GATT_SUCCESS || serial.isBlank() || serial.length>128) {fail("FREZ serial number could not be read.");return}
        status("Loading FREZ calibration factor…")
        executor.submit {
            var connection: HttpsURLConnection?=null
            try {
                connection=URL("https://api.frez.app/functions/v1/dyno-coefficient?serial="+URLEncoder.encode(serial,"UTF-8")).openConnection() as HttpsURLConnection
                connection.instanceFollowRedirects=false;connection.connectTimeout=10000;connection.readTimeout=10000
                connection.setRequestProperty("X-Frez-Access-Key",accessKey)
                val code=connection.responseCode
                if(code!=200) {
                    val message=when(code) {401,403->"FREZ key rejected or device not authorized. Check your Developer Program key and registered device.";429->"FREZ request limit reached. Try again later.";else->"FREZ calibration request failed (HTTP $code)."}
                    post(g) {fail(message)};return@submit
                }
                val body=connection.inputStream.bufferedReader().use {it.readText()}
                val value=JSONObject(body).get("a")
                require(value is Number)
                val engine=FrezProtocol(value.toDouble())
                post(g) {
                    protocol=engine
                    val c=notify!!;val descriptor=c.getDescriptor(uuid("2902"))
                    if(descriptor==null || !g.setCharacteristicNotification(c,true)) {fail("FREZ notification configuration is unavailable.");return@post}
                    @Suppress("DEPRECATION")
                    descriptor.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    if(!g.writeDescriptor(descriptor)) fail("FREZ notification setup failed.")
                }
            } catch(_: Exception) {post(g) {fail("FREZ calibration unavailable. Check internet access and your developer key, then reconnect.")}}
            finally {connection?.disconnect()}
        }
    }
    private fun receive(c: BluetoothGattCharacteristic,bytes: ByteArray) {
        if(c.uuid!=NOTIFY)return
        val engine=protocol ?: return
        val wasReady=engine.ready
        val samples=engine.packet(bytes)
        if(!wasReady && engine.ready) status("FREZ Dyno ready")
        samples.forEach {(kg,time)->sample(kg,time)}
    }
    companion object {
        val SERVICE=UUID.fromString("da8a6c41-154b-4b9a-9b00-2f84dfcebfe9")
        val NOTIFY=UUID.fromString("da8a6c42-154b-4b9a-9b00-2f84dfcebfe9")
        val WRITE=UUID.fromString("da8a6c43-154b-4b9a-9b00-2f84dfcebfe9")
        private fun uuid(short: String)=UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")
    }
}
