package app.grip_gains_companion.service.offline

import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.roundToInt

/** The website's weight-in-pounds model and supported training zones. */
class OfflineCurve(private val data: JSONObject) {
    data class Zone(val key: String, val label: String, val reps: Int)
    data class Match(val weight: Double, val seconds: Int, val zone: Zone)
    companion object {
        val zones = listOf(Zone("power", "Power", 6), Zone("power_strength", "Power Strength", 5), Zone("strength", "Strength", 5), Zone("strength_endurance", "Strength Endurance", 4), Zone("endurance", "Endurance", 4))
        fun zone(seconds: Double): Int = when { seconds < 48 -> 0; seconds < 82 -> 1; seconds < 129 -> 2; seconds < 180 -> 3; else -> 4 }
    }
    private val params = data.getJSONObject("params")
    private val a=params.getDouble("a")
    private val b=params.getDouble("b")
    private val x0=params.getDouble("x0")
    private val c=params.getDouble("c")
    private val d=params.getDouble("d")
    data class PlotPoint(val weightPounds: Double, val seconds: Double)
    data class PlotRange(val minimum: Double, val maximum: Double, val curveMinimum: Double) {
        val span: Double get() = maximum-minimum
    }
    fun plotRange(): PlotRange {
        val low=pounds(300.0)
        val high=pounds(30.0).coerceAtLeast(low+0.001)
        return PlotRange((low*0.95).coerceAtLeast(0.0),high,low)
    }
    fun inspectPlot(fraction: Double, range: PlotRange = plotRange()): PlotPoint {
        val weight=(range.minimum+fraction.coerceIn(0.0,1.0)*range.span).coerceIn(range.curveMinimum,range.maximum)
        return PlotPoint(weight,hold(weight).coerceIn(30.0,300.0))
    }
    fun plotSegments(): List<List<PlotPoint>> {
        val bounds=listOf(30.0,48.0,82.0,129.0,180.0,300.0)
        return (0..4).map { index ->
            val low=pounds(bounds[index+1]); val high=pounds(bounds[index])
            (0..40).map { step -> val weight=low+(high-low)*step/40; PlotPoint(weight,hold(weight)) }
        }
    }
    fun hold(pounds: Double): Double = a * exp(-b * (pounds - x0)) - c * pounds + d
    fun pounds(seconds: Double): Double {
        var low=0.0; var high=500.0
        repeat(40) { val mid=(low+high)/2; if(hold(mid)>seconds) low=mid else high=mid }
        return (low+high)/2
    }
    fun supported(index: Int): Boolean {
        val points=data.optJSONArray("points") ?: return false
        val evidence=(0 until points.length()).mapNotNull { points.optJSONObject(it)?.optDouble("hold")?.takeIf(Double::isFinite)?.let(::zone) }.toSet()
        return ((index-1).coerceAtLeast(0)..(index+1).coerceAtMost(4)).all { it in evidence }
    }
    fun estimate(weight: Double, lbs: Boolean): Match? {
        if(!weight.isFinite() || weight<=0) return null
        val seconds=hold(if(lbs) weight else weight/0.45359237)
        if(!seconds.isFinite() || seconds < 0.5 || seconds >= 400.5) return null
        val rounded=seconds.roundToInt(); val index=zone(rounded.toDouble())
        return if(supported(index)) Match(weight,rounded,zones[index]) else null
    }
    fun matchTime(seconds: Double, lbs: Boolean): Match? {
        if(!seconds.isFinite() || seconds !in 1.0..400.0) return null
        val weight=pounds(seconds)
        // Do not present a clipped inverse as a valid match for an unreachable time.
        if(weight<0.000001 || kotlin.math.abs(hold(weight)-seconds)>0.001) return null
        return estimate(weight*(if(lbs) 1.0 else 0.45359237),lbs)
    }
    fun match(index: Int, lbs: Boolean): Match? {
        if(!supported(index)) return null
        val target=data.optJSONObject("zone_characteristic_times")?.optDouble(zones[index].key) ?: return null
        if(!target.isFinite() || target !in 30.0..300.0) return null
        val step=if(data.optString("gripper")=="prime") {if(lbs) 0.1 else 0.05} else {if(lbs) 1.0 else 0.5}
        val nearest=(pounds(target)*(if(lbs) 1.0 else 0.45359237)/step).roundToInt()
        for(distance in 0..20) for(offset in if(distance==0) listOf(0) else listOf(-distance,distance)) {
            val weight=(nearest+offset)*step
            if(weight<=0) continue
            val estimate=estimate(weight,lbs)
            if(estimate!=null && estimate.zone==zones[index]) return estimate
        }
        return null
    }
}
