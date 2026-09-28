package app.grip_gains_companion.service.offline

import app.grip_gains_companion.service.ForceDropDetector
import kotlin.math.roundToInt

data class RealForceResult(val rep: Int, val seconds: Int, val pounds: Double)

/** Only actual, calibrated readings between the start threshold and confirmed end are measured. */
class RealForceRep(private val targetKg: Double, private val method: String, private val drop: Double, private val holdMs: Long) {
    private val detector=ForceDropDetector()
    private val readings=ArrayList<Double>()
    private var sum=0.0
    var startedAt: Long?=null; private set
    var lastSampleAt: Long?=null; private set
    private var completed=false
    fun sample(kg: Double, now: Long): RealForceResult? {
        if(completed || !kg.isFinite() || (lastSampleAt!=null && now<lastSampleAt!!)) return null
        lastSampleAt=now
        if(startedAt==null) {
            if(kg<targetKg*0.9) return null
            startedAt=now
        }
        val force=kg.coerceAtLeast(0.0)
        readings.add(force);sum+=force
        if(detector.sample(force,now,"real-force",true,targetKg,drop,holdMs)) return finish(now)
        return null
    }
    private fun finish(now: Long): RealForceResult {
        completed=true
        // Exclude the final release/confirmation tail so letting go does not become training weight.
        val limit=targetKg*(1.0-drop.coerceIn(0.1,0.8))
        while(readings.size>1 && readings.last()<=limit) sum-=readings.removeAt(readings.lastIndex)
        val kg=if(method=="median") {
            readings.sort()
            val middle=readings.size/2
            if(readings.size%2==0) (readings[middle-1]+readings[middle])/2 else readings[middle]
        } else sum/readings.size
        return RealForceResult(0,((now-startedAt!!)/1000.0).roundToInt().coerceAtLeast(1),kg/0.45359237)
    }
}
