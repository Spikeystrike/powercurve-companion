package app.grip_gains_companion.service.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** FREZ protocol v1: 2-byte header followed by nine signed ADC / unsigned ms pairs. */
class FrezProtocol(private val coefficient: Double) {
    init { require(coefficient.isFinite() && coefficient!=0.0) }
    data class Sample(val adc: Int,val milliseconds: Long)
    private var lastTime: Long?=null
    private var baseline: Double?=null
    private var tareSum=0.0
    private var tareCount=0
    var taring=false; private set
    val ready get()=baseline!=null && !taring
    fun tare() { baseline=null;tareSum=0.0;tareCount=0;taring=true }
    fun packet(bytes: ByteArray): List<Pair<Double,Long>> {
        val samples=decode(bytes) ?: return emptyList()
        if(samples.first().milliseconds <= (lastTime ?: -1)) return emptyList()
        lastTime=samples.last().milliseconds
        return samples.mapNotNull { sample ->
            if(taring) {
                tareSum+=sample.adc;tareCount++
                if(tareCount==100) {baseline=tareSum/100;taring=false}
                null
            } else baseline?.let { (coefficient*(sample.adc-it)) to (sample.milliseconds*1000) }
        }.filter {it.first.isFinite()}
    }
    companion object {
        fun decode(bytes: ByteArray): List<Sample>? {
            if(bytes.size!=74 || bytes[0]!=1.toByte() || bytes[1]!=0.toByte()) return null
            val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);buffer.position(2)
            val samples=List(9) {Sample(buffer.int,buffer.int.toLong() and 0xffffffffL)}
            if(samples.zipWithNext().any {(a,b)->b.milliseconds<=a.milliseconds}) return null
            return samples
        }
    }
}
