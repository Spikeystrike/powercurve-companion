package app.grip_gains_companion.service.offline

import org.junit.Assert.*
import org.junit.Test

class RealForceRepTest {
    private fun measured(method: String): RealForceResult {
        val rep=RealForceRep(20.0,method,0.5,250)
        assertNull(rep.sample(17.99,0));assertNull(rep.startedAt)
        assertNull(rep.sample(Double.NaN,50));assertNull(rep.startedAt)
        repeat(20) {assertNull(rep.sample(if(it==18) 24.0 else if(it==19) 26.0 else 20.0,100L+it*100))}
        repeat(4) {assertNull(rep.sample(8.0,2100L+it*100))}
        val result=rep.sample(8.0,2500)!!
        assertEquals(100L,rep.startedAt)
        assertEquals(2,result.seconds)
        assertNull(rep.sample(8.0,2700))
        return result
    }
    @Test fun gapsPreserveTimeAndArmingWithoutInventingWeightSamples() {
        val rep=RealForceRep(20.0,"average",0.5,250)
        repeat(11) {assertNull(rep.sample(20.0,it*100L))}
        assertNull(rep.sample(20.0,5000))
        assertNull(rep.sample(0.0,10000))
        assertNull(rep.sample(0.0,10100))
        assertNull(rep.sample(0.0,10200))
        assertNull(rep.sample(0.0,10300))
        assertNull(rep.sample(0.0,10400))
        val result=rep.sample(0.0,10500)!!
        assertEquals(11,result.seconds)
        assertEquals(20.0,result.pounds*0.45359237,0.000001)
    }
    @Test fun medianExcludesReleaseAndFiresOnce() {
        assertEquals(20.0,measured("median").pounds*0.45359237,0.000001)
    }
    @Test fun averageUsesAllActualReadingsRatherThanTargetOrPeak() {
        assertEquals(20.5,measured("average").pounds*0.45359237,0.000001)
    }
    @Test fun thresholdIsInclusiveAndSingleDropSpikeDoesNotEndRep() {
        val rep=RealForceRep(20.0,"median",0.5,250)
        rep.sample(18.0,0);assertEquals(0L,rep.startedAt)
        repeat(10) {assertNull(rep.sample(20.0,100L+it*100))}
        assertNull(rep.sample(0.0,1100))
        repeat(5) {assertNull(rep.sample(20.0,1200L+it*100))}
    }
}
