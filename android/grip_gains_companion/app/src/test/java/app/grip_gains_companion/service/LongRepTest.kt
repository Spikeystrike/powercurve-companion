package app.grip_gains_companion.service

import app.grip_gains_companion.util.StatisticsUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class LongRepTest {
    @Test(timeout = 30000) fun tenMinuteRepPreservesStatisticsAndCompletedSamples() = runBlocking {
        val handler = ProgressorHandler().apply { enableCalibration = false; canEngage = true }
        handler.processSample(0.0, 0)
        val values = (1..60000).map { 10.0 + it % 7 }
        handler.processSample(values.first(), 10000)
        val state = handler.state.value
        for (i in 1 until values.size) handler.processSample(values[i], (i + 1) * 10000L)
        assertSame(state, handler.state.value)
        assertEquals(StatisticsUtils.mean(values), handler.sessionMean.value!!, 1e-9)
        assertEquals(StatisticsUtils.standardDeviation(values), handler.sessionStdDev.value!!, 1e-9)
        val completed = async(start = CoroutineStart.UNDISPATCHED) { handler.gripDisengaged.first() }
        handler.processSample(0.0, 600010000)
        assertEquals(600.0, completed.await().first, 0.0)
        // Preserve this app's existing result convention, including the release sample.
        assertEquals(values + 0.0, completed.await().second)
        handler.processSample(20.0, 600020000)
        handler.processSample(22.0, 600030000)
        assertEquals(21.0, handler.sessionMean.value!!, 0.0)
    }

    @Test(timeout = 30000) fun longHoldMedianAndResetAreExact() = runBlocking {
        val handler = ProgressorHandler().apply { enableCalibration = false }
        handler.processSample(0.0, 0)
        val values = (1..30000).map { 10.0 + it * 17 % 103 }
        values.forEachIndexed { i, value -> handler.processSample(value, (i + 1) * 10000L) }
        assertEquals(StatisticsUtils.median(values), handler.weightMedian.value!!, 0.0)
        handler.processSample(0.0, 300010000)
        assertEquals(StatisticsUtils.trimmedMedian(values), handler.weightMedian.value!!, 0.0)
        handler.processSample(25.0, 300020000)
        handler.processSample(27.0, 300030000)
        assertEquals(26.0, handler.weightMedian.value!!, 0.0)
        handler.reset()
        handler.processSample(0.0, 0)
        handler.canEngage = true
        handler.processSample(40.0, 10000)
        handler.processSample(42.0, 20000)
        assertEquals(41.0, handler.sessionMean.value!!, 0.0)
    }

    @Test fun incrementalStatisticsMatchEveryPrefix() {
        val stats = RunningForceStatistics()
        val median = RunningForceMedian()
        val values = ArrayList<Double>()
        repeat(1000) { i ->
            val value = ((i * 7919) % 101 - 50).toDouble()
            values.add(value); stats.add(value); median.add(value)
            assertEquals(StatisticsUtils.mean(values), stats.mean, 1e-10)
            assertEquals(StatisticsUtils.standardDeviation(values), stats.standardDeviation, 1e-10)
            assertEquals(StatisticsUtils.median(values), median.value, 0.0)
        }
    }
}
