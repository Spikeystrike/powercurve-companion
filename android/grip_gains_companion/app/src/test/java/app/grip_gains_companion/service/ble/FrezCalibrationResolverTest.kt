package app.grip_gains_companion.service.ble

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class FrezCalibrationResolverTest {
    @Test fun onlineRefreshIsSavedPerDeviceAndAvailableOffline() {
        val cache = mutableMapOf("A" to 0.01, "B" to -0.02)
        val save: (String, Double) -> Boolean = { serial, factor -> cache[serial] = factor; true }
        val online = FrezCalibrationResolver(cache::get, save) { 0.03 }.load("A")
        assertEquals(0.03, online.coefficient, 0.0)
        assertFalse(online.offline)
        assertTrue(online.saved)
        val offline = FrezCalibrationResolver(cache::get, save) { throw IOException("offline") }
        assertEquals(0.03, offline.load("A").coefficient, 0.0)
        assertEquals(-0.02, offline.load("B").coefficient, 0.0)
        assertTrue(offline.load("A").offline)
        assertThrows(IOException::class.java) { offline.load("unknown") }
    }
    @Test fun apiErrorsDoNotFallBackOrOverwriteSavedFactors() {
        var saved = false
        val resolver = FrezCalibrationResolver({ 0.01 }, { _, _ -> saved = true; true }) { throw IllegalStateException("HTTP 403") }
        assertThrows(IllegalStateException::class.java) { resolver.load("A") }
        assertFalse(saved)
    }
    @Test fun invalidFreshFactorsCannotReplaceTheCache() {
        for (value in listOf(0.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            var saved = false
            val resolver = FrezCalibrationResolver({ 0.01 }, { _, _ -> saved = true; true }) { value }
            assertThrows(IllegalArgumentException::class.java) { resolver.load("A") }
            assertFalse(saved)
        }
    }
    @Test fun invalidSavedFactorsCannotBeUsedOffline() {
        for (value in listOf(null, 0.0, Double.NaN, Double.NEGATIVE_INFINITY)) {
            val resolver = FrezCalibrationResolver({ value }, { _, _ -> true }) { throw IOException() }
            assertThrows(IOException::class.java) { resolver.load("A") }
        }
    }
    @Test fun failedDiskWriteStillUsesFreshFactorButReportsItWasNotSaved() {
        val result = FrezCalibrationResolver({ null }, { _, _ -> false }) { 0.02 }.load("A")
        assertEquals(0.02, result.coefficient, 0.0)
        assertFalse(result.saved)
        assertFalse(result.offline)
    }
}
