package app.grip_gains_companion.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ForceReadoutTest {
    @Test fun roundedZeroNeverHasMinusButRealNegativeForceDoes() {
        for(value in listOf(-0.0,-0.049,0.0,0.049)) assertEquals("0.0",ForceReadout.format(value))
        assertEquals("-0.1",ForceReadout.format(-0.06))
        assertEquals("0.1",ForceReadout.format(0.06))
    }
    @Test fun oscillationsAreReducedAndUpdatesLimitedWithoutHidingRealSteps() {
        val readout=ForceReadout();readout.sample(20.0,0)
        var updates=0
        for(i in 1..100) readout.sample(if(i%2==0) 21.0 else 19.0,i*10L)?.let {
            updates++;assertTrue(it in 19.7..20.3)
        }
        assertEquals(10,updates)
        var latest=20.0
        for(i in 101..160) readout.sample(30.0,i*10L)?.let {latest=it}
        assertTrue(latest>29.0)
        for(i in 161..220) readout.sample(0.0,i*10L)?.let {latest=it}
        assertTrue(latest<1.6)
    }
    @Test fun gapsClockResetsAndRecalibrationDiscardOldSmoothing() {
        val readout=ForceReadout();readout.sample(20.0,100)
        assertEquals(0.0,readout.sample(0.0,2000)!!,0.0)
        assertEquals(5.0,readout.sample(5.0,0)!!,0.0)
        readout.reset();assertEquals(0.0,readout.sample(0.0,10)!!,0.0)
    }
    @Test fun smoothingDoesNotChangeRawForceOrRecordedHistory() = runBlocking {
        val handler=ProgressorHandler();handler.enableCalibration=false
        handler.processSample(20.0,0)
        handler.processSample(30.0,100000)
        assertEquals(30.0,handler.currentForce.value,0.0)
        assertEquals(30.0,handler.forceHistory.value.last().force,0.0)
        assertTrue(handler.displayForce.value in 20.0..25.0)
        handler.reset();assertEquals(0.0,handler.displayForce.value,0.0)
    }
}
