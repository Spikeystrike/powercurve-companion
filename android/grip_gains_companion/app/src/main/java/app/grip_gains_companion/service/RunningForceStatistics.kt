package app.grip_gains_companion.service

import java.util.PriorityQueue
import kotlin.math.sqrt

/** Welford population statistics: constant work per sample, including long holds. */
class RunningForceStatistics {
    var count = 0L; private set
    var mean = 0.0; private set
    private var squaredDifferences = 0.0
    val standardDeviation get() = if (count < 2) 0.0 else sqrt((squaredDifferences / count).coerceAtLeast(0.0))
    fun add(value: Double) {
        count++
        val delta = value - mean
        mean += delta / count
        squaredDifferences += delta * (value - mean)
    }
    fun clear() { count = 0; mean = 0.0; squaredDifferences = 0.0 }
}

/** Exact median with logarithmic insertion instead of sorting the whole hold repeatedly. */
class RunningForceMedian {
    private val lower = PriorityQueue<Double>(compareByDescending { it })
    private val upper = PriorityQueue<Double>()
    val value: Double get() = when {
        lower.isEmpty() -> 0.0
        lower.size == upper.size -> (lower.element() + upper.element()) / 2.0
        else -> lower.element()
    }
    fun add(value: Double) {
        if (lower.isEmpty() || value <= lower.element()) lower.add(value) else upper.add(value)
        if (lower.size > upper.size + 1) upper.add(lower.remove())
        if (upper.size > lower.size) lower.add(upper.remove())
    }
    fun clear() { lower.clear(); upper.clear() }
}
