package app.grip_gains_companion.service

import app.grip_gains_companion.model.ProgressorState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProgressorHandlerTest {
    @Test fun disabledAutoCalibrationPreservesRawForceAcrossReconnects() = runBlocking {
        val handler=ProgressorHandler()
        handler.enableCalibration=false
        repeat(2) {
            handler.reset()
            handler.processSample(20.0,1000)
            assertFalse(handler.calibrating)
            assertEquals(20.0,handler.currentForce.value,0.0)
            assertEquals(0L,handler.calibrationTimeRemaining.value)
        }
    }
    @Test fun manualCalibrationStillWorksWithAutoCalibrationDisabled() = runBlocking {
        val handler=ProgressorHandler()
        handler.enableCalibration=false
        handler.recalibrate()
        handler.processSample(5.0,1000)
        assertTrue(handler.calibrating)
        handler.enableCalibration=false
        assertTrue(handler.calibrating)
        handler.reset()
        handler.processSample(5.0,2000)
        assertFalse(handler.calibrating)
    }
    @Test fun disablingAutomaticCalibrationCancelsAlreadyStartedCalibration() = runBlocking {
        val handler=ProgressorHandler()
        handler.processSample(15.0,1000)
        assertTrue(handler.calibrating)
        handler.enableCalibration=false
        assertTrue(handler.state.value is ProgressorState.Idle)
        handler.processSample(16.0,2000)
        assertFalse(handler.calibrating)
        assertEquals(16.0,handler.currentForce.value,0.0)
    }
}
