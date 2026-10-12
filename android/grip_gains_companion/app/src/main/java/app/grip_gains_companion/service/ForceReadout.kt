package app.grip_gains_companion.service

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.exp

/** Display-only filter; never use its output for measurements or rep detection. */
class ForceReadout {
    private var time: Long?=null
    private var publishedAt=0L
    private var filtered=0.0
    fun reset() {time=null;publishedAt=0;filtered=0.0}
    /** 200 ms time constant; refresh the number at most 10 Hz. */
    fun sample(kg: Double, milliseconds: Long): Double? {
        if(!kg.isFinite()) return null
        val previous=time
        time=milliseconds
        if(previous==null || milliseconds<previous || milliseconds-previous>1500) {
            filtered=kg;publishedAt=milliseconds;return filtered
        }
        filtered+=(kg-filtered)*(1.0-exp(-(milliseconds-previous)/200.0))
        if(milliseconds-publishedAt<100) return null
        publishedAt=milliseconds
        return filtered
    }
    companion object {
        fun format(value: Double): String {
            if(!value.isFinite()) return "—"
            val rounded=BigDecimal.valueOf(value).setScale(1,RoundingMode.HALF_UP)
            return if(rounded.signum()==0) "0.0" else rounded.toPlainString()
        }
    }
}
