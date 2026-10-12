package app.grip_gains_companion.service

import org.junit.Assert.*
import org.junit.Test

class ForceGraphHistoryTest {
    @Test fun longSessionHasBoundedHistoryAndTwentyUpdatesPerSecond() {
        val history = ForceGraphHistory()
        var updates = 0
        var snapshot = emptyList<app.grip_gains_companion.model.ForceHistoryEntry>()
        repeat(200001) { i ->
            history.add(i.toDouble(), i * 10L)?.let {
                updates++
                snapshot = it
                assertTrue(it.size <= 6000)
            }
        }
        assertEquals(40001, updates)
        assertEquals(6000, snapshot.size)
        assertEquals(200000.0, snapshot.last().force, 0.0)
        assertEquals(501, visibleForceHistory(snapshot, 5).size)
    }

    @Test fun windowUsesLatestSampleAndRetainsReceptionGaps() {
        val history = ForceGraphHistory()
        history.add(10.0, 0)
        history.add(20.0, 100)
        val snapshot = history.add(30.0, 3000)!!
        assertEquals(3, visibleForceHistory(snapshot, 5).size)
        assertEquals(2900L, snapshot.last().timestamp.time - snapshot[1].timestamp.time)
        assertEquals(1, visibleForceHistory(snapshot, 1).size)
        history.clear()
        assertEquals(1, history.add(5.0, 3010)!!.size)
    }

    @Test fun invalidMeasurementsDoNotEnterHistory() {
        val history = ForceGraphHistory()
        assertNull(history.add(Double.NaN, 0))
        assertNull(history.add(Double.POSITIVE_INFINITY, 1))
        assertEquals(1, history.add(2.0, 2)!!.size)
    }
}
