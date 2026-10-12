package app.grip_gains_companion.service

import app.grip_gains_companion.model.ForceHistoryEntry
import java.util.Date
import java.util.ArrayDeque

/** Display-only rolling buffer, using monotonic reception time rather than the wall clock. */
class ForceGraphHistory {
    private val points = ArrayDeque<ForceHistoryEntry>()
    private var publishedAt: Long? = null

    fun clear() { points.clear(); publishedAt = null }

    fun add(force: Double, now: Long = graphTimeMillis()): List<ForceHistoryEntry>? {
        if (!force.isFinite()) return null
        points.addLast(ForceHistoryEntry(Date(now), force))
        while (points.size > 6000 || points.first.timestamp.time < now - 60_000) points.removeFirst()
        val previous = publishedAt
        if (previous != null && now - previous in 0..49) return null
        publishedAt = now
        return points.toList()
    }
}

fun graphTimeMillis(): Long = System.nanoTime() / 1_000_000

fun visibleForceHistory(history: List<ForceHistoryEntry>, seconds: Int): List<ForceHistoryEntry> {
    val last = history.lastOrNull()?.timestamp?.time ?: return emptyList()
    return history.filter { it.timestamp.time >= last - seconds.coerceIn(1, 60) * 1000L }
}
