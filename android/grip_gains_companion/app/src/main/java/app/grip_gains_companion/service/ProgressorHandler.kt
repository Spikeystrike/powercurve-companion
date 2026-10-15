package app.grip_gains_companion.service

import app.grip_gains_companion.config.AppConstants
import app.grip_gains_companion.model.ForceHistoryEntry
import app.grip_gains_companion.model.ProgressorState
import app.grip_gains_companion.model.TimestampedSample
import app.grip_gains_companion.util.StatisticsUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date

/**
 * State machine for processing Tindeq Progressor force samples
 */
class ProgressorHandler {

    // MARK: - Published State

    private val _state = MutableStateFlow<ProgressorState>(ProgressorState.WaitingForSamples)
    val state: StateFlow<ProgressorState> = _state.asStateFlow()

    private val _currentForce = MutableStateFlow(0.0)
    val currentForce: StateFlow<Double> = _currentForce.asStateFlow()
    private val readout=ForceReadout()
    private val _displayForce=MutableStateFlow(0.0)
    val displayForce: StateFlow<Double> = _displayForce.asStateFlow()

    private val _calibrationTimeRemaining = MutableStateFlow(AppConstants.CALIBRATION_DURATION_MS)
    val calibrationTimeRemaining: StateFlow<Long> = _calibrationTimeRemaining.asStateFlow()

    private val _weightMedian = MutableStateFlow<Double?>(null)
    val weightMedian: StateFlow<Double?> = _weightMedian.asStateFlow()

    private val _sessionMean = MutableStateFlow<Double?>(null)
    val sessionMean: StateFlow<Double?> = _sessionMean.asStateFlow()

    private val _sessionStdDev = MutableStateFlow<Double?>(null)
    val sessionStdDev: StateFlow<Double?> = _sessionStdDev.asStateFlow()

    private val graphHistory = ForceGraphHistory()
    private val _forceHistory = MutableStateFlow<List<ForceHistoryEntry>>(emptyList())
    val forceHistory: StateFlow<List<ForceHistoryEntry>> = _forceHistory.asStateFlow()

    private val _isOffTarget = MutableStateFlow(false)
    val isOffTarget: StateFlow<Boolean> = _isOffTarget.asStateFlow()

    private val _offTargetDirection = MutableStateFlow<Double?>(null)
    val offTargetDirection: StateFlow<Double?> = _offTargetDirection.asStateFlow()

    // MARK: - Events (SharedFlow for one-shot events)

    private val _calibrationCompleted = MutableSharedFlow<Unit>()
    val calibrationCompleted = _calibrationCompleted.asSharedFlow()

    private val _gripFailed = MutableSharedFlow<Unit>()
    val gripFailed = _gripFailed.asSharedFlow()

    private val _gripDisengaged = MutableSharedFlow<Pair<Double, List<Double>>>()
    val gripDisengaged = _gripDisengaged.asSharedFlow()

    private val _offTargetChanged = MutableSharedFlow<Pair<Boolean, Double?>>()
    val offTargetChanged = _offTargetChanged.asSharedFlow()

    // MARK: - External Input

    var canEngage: Boolean = false
    private var manualCalibration = false
    var enableCalibration: Boolean = true
        set(value) {
            field = value
            if (!value && !manualCalibration && calibrating) {
                _state.value = ProgressorState.Idle(0.0)
                _calibrationTimeRemaining.value = 0
            }
        }
    var engageThreshold: Double = AppConstants.DEFAULT_ENGAGE_THRESHOLD
    var failThreshold: Double = AppConstants.DEFAULT_FAIL_THRESHOLD
    var targetWeight: Double? = null
    var weightTolerance: Double = AppConstants.DEFAULT_WEIGHT_TOLERANCE

    // Percentage-based thresholds
    var enablePercentageThresholds: Boolean = AppConstants.DEFAULT_ENABLE_PERCENTAGE_THRESHOLDS
    var engagePercentage: Double = AppConstants.DEFAULT_ENGAGE_PERCENTAGE
    var disengagePercentage: Double = AppConstants.DEFAULT_DISENGAGE_PERCENTAGE
    var tolerancePercentage: Double = AppConstants.DEFAULT_TOLERANCE_PERCENTAGE

    // Percentage threshold bounds
    var engageFloor: Double = AppConstants.DEFAULT_ENGAGE_FLOOR
    var engageCeiling: Double = AppConstants.DEFAULT_ENGAGE_CEILING
    var disengageFloor: Double = AppConstants.DEFAULT_DISENGAGE_FLOOR
    var disengageCeiling: Double = AppConstants.DEFAULT_DISENGAGE_CEILING
    var toleranceFloor: Double = AppConstants.DEFAULT_TOLERANCE_FLOOR
    var toleranceCeiling: Double = AppConstants.DEFAULT_TOLERANCE_CEILING

    var weightCalibrationThreshold: Double = AppConstants.DEFAULT_WEIGHT_CALIBRATION_THRESHOLD

    private val activeRepWeights = ArrayList<Double>()
    private val activeRepStatistics = RunningForceStatistics()
    private val heldWeightMedian = RunningForceMedian()

    // Internal state
    private var lastTimestamp: Long = 0
    private var offTargetTimerRunning = false

    // Convenience properties
    val engaged: Boolean get() = _state.value.isEngaged
    val calibrating: Boolean get() = _state.value.isCalibrating
    val waitingForSamples: Boolean get() = _state.value.isWaitingForSamples

    val gripElapsedSeconds: Int
        get() {
            val currentState = _state.value
            return if (currentState is ProgressorState.Gripping) {
                ((lastTimestamp - currentState.startTimestamp) / 1_000_000).toInt()
            } else 0
        }

    // MARK: - Effective Thresholds

    private fun applyBounds(value: Double, floor: Double, ceiling: Double): Double {
        val floored = if (floor > 0) maxOf(value, floor) else value
        return if (ceiling > 0) minOf(floored, ceiling) else floored
    }

    private val effectiveEngageThreshold: Double
        get() {
            if (enablePercentageThresholds) {
                targetWeight?.let { target ->
                    return applyBounds(target * engagePercentage, engageFloor, engageCeiling)
                }
            }
            return engageThreshold
        }

    private val effectiveFailThreshold: Double
        get() {
            if (enablePercentageThresholds) {
                targetWeight?.let { target ->
                    return applyBounds(target * disengagePercentage, disengageFloor, disengageCeiling)
                }
            }
            return failThreshold
        }

    private val effectiveTolerance: Double
        get() {
            if (enablePercentageThresholds) {
                targetWeight?.let { target ->
                    return applyBounds(target * tolerancePercentage, toleranceFloor, toleranceCeiling)
                }
            }
            return weightTolerance
        }

    // MARK: - Public Methods

    /**
     * Process a single force sample from the BLE device
     */
    suspend fun processSample(rawWeight: Double, timestamp: Long) {
        if (!rawWeight.isFinite()) return
        lastTimestamp = timestamp

        // Process state transition first (may establish/update baseline)
        processStateTransition(rawWeight, timestamp)

        // Compute tared weight for display based on current state's baseline
        val baseline = when (val s = _state.value) {
            is ProgressorState.Idle -> s.baselineValue
            is ProgressorState.Gripping -> s.baselineValue
            is ProgressorState.WeightCalibration -> s.baselineValue
            else -> null
        }
        val displayWeight = if (baseline != null) rawWeight - baseline else rawWeight

        // Update published force with tared value
        _currentForce.value = displayWeight
        readout.sample(displayWeight,timestamp/1000)?.let {_displayForce.value=it}

        graphHistory.add(displayWeight)?.let { _forceHistory.value = it }
    }

    /**
     * Reset handler state for a new session
     */
    fun reset() {
        manualCalibration = false
        resetCommonState()
        _currentForce.value = 0.0
    }

    /**
     * Trigger recalibration
     */
    fun recalibrate() {
        manualCalibration = true
        resetCommonState()
    }

    private fun resetCommonState() {
        activeRepWeights.clear()
        activeRepStatistics.clear()
        heldWeightMedian.clear()
        graphHistory.clear()
        readout.reset();_displayForce.value=0.0
        stopOffTargetTimer()
        _state.value = ProgressorState.WaitingForSamples
        _calibrationTimeRemaining.value = AppConstants.CALIBRATION_DURATION_MS
        _weightMedian.value = null
        _isOffTarget.value = false
        _offTargetDirection.value = null
        _sessionMean.value = null
        _sessionStdDev.value = null
        _forceHistory.value = emptyList()
    }

    // MARK: - State Machine Logic

    private suspend fun processStateTransition(rawWeight: Double, timestamp: Long) {
        val sample = TimestampedSample(rawWeight, timestamp)

        when (val currentState = _state.value) {
            is ProgressorState.WaitingForSamples -> {
                // Don't clear force history if we already have some (preserves graph during reconnection)
                if (_forceHistory.value.isEmpty()) {
                    _forceHistory.value = emptyList()
                }
                if (enableCalibration || manualCalibration) {
                    _state.value = ProgressorState.Calibrating(
                        startTimeMs = System.currentTimeMillis(),
                        samples = listOf(sample)
                    )
                    _calibrationTimeRemaining.value = AppConstants.CALIBRATION_DURATION_MS
                } else {
                    _state.value = ProgressorState.Idle(baselineValue = 0.0)
                    _calibrationTimeRemaining.value = 0
                    _calibrationCompleted.emit(Unit)
                }
            }

            is ProgressorState.Calibrating -> {
                val newSamples = currentState.samples + sample
                val elapsed = System.currentTimeMillis() - currentState.startTimeMs
                _calibrationTimeRemaining.value = maxOf(0, AppConstants.CALIBRATION_DURATION_MS - elapsed)

                if (elapsed >= AppConstants.CALIBRATION_DURATION_MS) {
                    val baselineAvg = newSamples.map { it.weight }.average()
                    manualCalibration = false
                    _state.value = ProgressorState.Idle(baselineAvg)
                    _calibrationTimeRemaining.value = 0
                    _calibrationCompleted.emit(Unit)
                } else {
                    _state.value = ProgressorState.Calibrating(currentState.startTimeMs, newSamples)
                }
            }

            is ProgressorState.Idle -> {
                val taredWeight = rawWeight - currentState.baselineValue
                handleIdleState(rawWeight, taredWeight, currentState.baselineValue, timestamp)
            }

            is ProgressorState.Gripping -> {
                val taredWeight = rawWeight - currentState.baselineValue

                activeRepWeights.add(taredWeight)
                activeRepStatistics.add(taredWeight)
                _sessionMean.value = activeRepStatistics.mean
                _sessionStdDev.value = activeRepStatistics.standardDeviation

                if (taredWeight < effectiveFailThreshold) {
                    // Grip failed
                    stopOffTargetTimer()
                    val duration = (timestamp - currentState.startTimestamp) / 1_000_000.0
                    _state.value = ProgressorState.Idle(currentState.baselineValue)
                    _isOffTarget.value = false
                    _offTargetDirection.value = null
                    val taredWeights = activeRepWeights.toList()
                    activeRepWeights.clear()
                    activeRepStatistics.clear()
                    _gripFailed.emit(Unit)
                    _gripDisengaged.emit(Pair(duration, taredWeights))
                } else {
                    checkOffTarget(taredWeight)
                }
            }

            is ProgressorState.WeightCalibration -> {
                val taredWeight = rawWeight - currentState.baselineValue
                handleWeightCalibrationState(
                    rawWeight, taredWeight, currentState.baselineValue,
                    currentState.isHolding, timestamp
                )
            }
        }
    }

    private fun startGrip(baseline: Double, timestamp: Long, weight: Double) {
        activeRepWeights.clear()
        activeRepStatistics.clear()
        heldWeightMedian.clear()
        activeRepWeights.add(weight)
        activeRepStatistics.add(weight)
        _state.value = ProgressorState.Gripping(baseline, timestamp)
    }

    private suspend fun handleIdleState(
        rawWeight: Double,
        taredWeight: Double,
        baseline: Double,
        timestamp: Long
    ) {
        val sample = TimestampedSample(rawWeight, timestamp)

        if (canEngage && taredWeight >= effectiveEngageThreshold) {
            // Start real grip session
            _weightMedian.value = null
            startGrip(baseline, timestamp, taredWeight)
        } else if (!canEngage && taredWeight >= weightCalibrationThreshold) {
            // Start weight calibration
            heldWeightMedian.clear()
            heldWeightMedian.add(taredWeight)
            _state.value = ProgressorState.WeightCalibration(baseline, true)
            _weightMedian.value = taredWeight
        }
    }

    private fun handleWeightCalibrationState(
        rawWeight: Double,
        taredWeight: Double,
        baseline: Double,
        isHolding: Boolean,
        timestamp: Long
    ) {
        val sample = TimestampedSample(rawWeight, timestamp)

        if (canEngage && taredWeight >= effectiveEngageThreshold) {
            // Switch to real grip session
            _weightMedian.value = null
            startGrip(baseline, timestamp, taredWeight)
        } else if (taredWeight >= weightCalibrationThreshold) {
            // Continue measuring
            if (isHolding) {
                heldWeightMedian.add(taredWeight)
                _weightMedian.value = heldWeightMedian.value
            } else {
                // Re-engaging weight
                heldWeightMedian.clear()
                heldWeightMedian.add(taredWeight)
                _state.value = ProgressorState.WeightCalibration(baseline, true)
                _weightMedian.value = taredWeight
            }
        } else if (taredWeight < weightCalibrationThreshold && isHolding) {
            // Symmetric trimming preserves the middle values, so the exact median is unchanged.
            _weightMedian.value = heldWeightMedian.value
            heldWeightMedian.clear()
            _state.value = ProgressorState.WeightCalibration(baseline, false)
        } else if (taredWeight < effectiveFailThreshold) {
            // Completely released - back to idle
            _state.value = ProgressorState.Idle(baseline)
        }
    }

    // MARK: - Target Weight Checking

    private suspend fun checkOffTarget(rawWeight: Double) {
        val target = targetWeight ?: run {
            stopOffTargetTimer()
            if (_isOffTarget.value) {
                _isOffTarget.value = false
                _offTargetDirection.value = null
                _offTargetChanged.emit(Pair(false, null))
            }
            return
        }

        val difference = rawWeight - target
        val wasOffTarget = _isOffTarget.value

        if (kotlin.math.abs(difference) >= effectiveTolerance) {
            _isOffTarget.value = true
            _offTargetDirection.value = difference
            if (!wasOffTarget) {
                // Just went off target - emit event
                _offTargetChanged.emit(Pair(true, difference))
            }
        } else {
            stopOffTargetTimer()
            _isOffTarget.value = false
            _offTargetDirection.value = null
            if (wasOffTarget) {
                _offTargetChanged.emit(Pair(false, null))
            }
        }
    }

    private fun stopOffTargetTimer() {
        offTargetTimerRunning = false
    }
}
