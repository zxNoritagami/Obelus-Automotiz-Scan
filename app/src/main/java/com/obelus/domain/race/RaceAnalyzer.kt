package com.obelus.domain.race

import com.obelus.data.local.entity.RaceTelemetryPoint
import com.obelus.obelusscan.domain.model.SplitTime
import kotlin.math.abs

/**
 * Result of post-race analysis, passed to the UI for display.
 */
data class RaceAnalysisResult(
    /** Peak instantaneous G-force (acceleration axis) */
    val maxGForce: Float,
    /** Estimated peak power in horsepower — 0 if weight not configured */
    val estimatedHp: Float,
    /** Delay in ms between first full-throttle input and first speed > 1 km/h */
    val reactionTimeMs: Long,
    /** Speed vs time data for the line chart [(timeMs, speedKmh)] */
    val speedTimeSeries: List<Pair<Long, Int>>,
    /** 10 km/h splits with partial and cumulative times */
    val splits: List<SplitTime>
)

/**
 * Pure domain class for post-race analysis.
 * No Android dependencies — unit testable.
 */
object RaceAnalyzer {

    // ──────────────────────────────────────────────────────────────────────
    // HP Estimation
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Estimates peak power using kinetic energy / time:
     *   HP = (m × v²) / (2 × t × 745.7)
     *
     * Where:
     *   m = vehicle mass in kg
     *   v = final speed in m/s
     *   t = elapsed time in seconds
     *   745.7 = W per horsepower
     *
     * NOTE: This is a rough estimation assuming constant acceleration and
     * no drivetrain losses. Real dyno measurements will differ.
     *
     * @param finalTimeS   Total race time in seconds
     * @param finalSpeedKmh Final speed at finish line in km/h
     * @param vehicleWeightKg Vehicle mass in kg (0 = don't calculate)
     * @return Estimated HP, or 0f if weight is zero or time is zero
     */
    fun estimatedHp(finalTimeS: Float, finalSpeedKmh: Int, vehicleWeightKg: Int): Float {
        if (vehicleWeightKg <= 0 || finalTimeS <= 0f || finalSpeedKmh <= 0) return 0f
        val vMs = finalSpeedKmh * 0.27778f           // km/h → m/s
        val joules = 0.5f * vehicleWeightKg * vMs * vMs
        val watts  = joules / finalTimeS
        return watts / 745.7f
    }

    // ──────────────────────────────────────────────────────────────────────
    // Max G-Force
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns the maximum absolute G-force recorded in the telemetry.
     * Minimum of 2 contiguous points needed.
     */
    fun maxGForce(telemetry: List<RaceTelemetryPoint>): Float {
        if (telemetry.size < 2) return 0f
        var max = 0f
        for (i in 1 until telemetry.size) {
            val deltaV = (telemetry[i].speedKmh - telemetry[i - 1].speedKmh) * 0.27778f
            val deltaT = (telemetry[i].timestampOffset - telemetry[i - 1].timestampOffset) / 1000f
            if (deltaT > 0f) {
                val g = abs(deltaV / deltaT) / 9.81f
                if (g > max) max = g
            }
        }
        return max
    }

    // ──────────────────────────────────────────────────────────────────────
    // Reaction Time
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Estimates driver reaction time:
     *   = timestamp when speed first exceeds 1 km/h
     *     MINUS the timestamp when throttle first hit 100%
     *
     * Returns 0 if throttle data is unavailable (-1) or if conditions aren't met.
     */
    fun reactionTimeMs(telemetry: List<RaceTelemetryPoint>): Long {
        val fullThrottleTs = telemetry.firstOrNull { it.throttlePct >= 90 }?.timestampOffset ?: return 0L
        val firstMoveTs    = telemetry.firstOrNull { it.speedKmh > 1 }?.timestampOffset ?: return 0L
        val diff = firstMoveTs - fullThrottleTs
        return if (diff >= 0) diff else 0L
    }

    // ──────────────────────────────────────────────────────────────────────
    // Speed/Time series for chart
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns a down-sampled list of (timeMs, speedKmh) pairs suitable for
     * drawing the speed vs time line chart in the UI.
     * Down-samples to at most [maxPoints] points.
     */
    fun buildSpeedTimeSeries(
        telemetry: List<RaceTelemetryPoint>,
        maxPoints: Int = 200
    ): List<Pair<Long, Int>> {
        if (telemetry.isEmpty()) return emptyList()
        val step   = (telemetry.size / maxPoints).coerceAtLeast(1)
        return telemetry.filterIndexed { idx, _ -> idx % step == 0 }
            .map { it.timestampOffset to it.speedKmh }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Splits table
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Builds a list of [SplitTime] (each 10 km/h bracket) from telemetry.
     * For acceleration: 0-10, 10-20, 20-30, …
     * For braking: 100-90, 90-80, …
     */
    fun buildSplitsTable(telemetry: List<RaceTelemetryPoint>): List<SplitTime> {
        if (telemetry.isEmpty()) return emptyList()
        val splits   = mutableListOf<SplitTime>()
        val startTs  = telemetry.first().timestampOffset
        var lastSpeed = telemetry.first().speedKmh
        var lastFactor = lastSpeed / 10

        for (point in telemetry) {
            val factor = point.speedKmh / 10
            if (factor != lastFactor) {
                val bracket = factor * 10
                val splitTs = point.timestampOffset - startTs
                splits.add(
                    SplitTime(
                        speedFrom = lastFactor * 10,
                        speedTo   = bracket,
                        timeMs    = splitTs
                    )
                )
                lastFactor = factor
            }
            lastSpeed = point.speedKmh
        }
        return splits
    }

    // ──────────────────────────────────────────────────────────────────────
    // Full analysis
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Convenience method that runs all analyses at once.
     */
    fun analyze(
        telemetry: List<RaceTelemetryPoint>,
        finalTimeS: Float,
        finalSpeedKmh: Int,
        vehicleWeightKg: Int
    ): RaceAnalysisResult = RaceAnalysisResult(
        maxGForce       = maxGForce(telemetry),
        estimatedHp     = estimatedHp(finalTimeS, finalSpeedKmh, vehicleWeightKg),
        reactionTimeMs  = reactionTimeMs(telemetry),
        speedTimeSeries = buildSpeedTimeSeries(telemetry),
        splits          = buildSplitsTable(telemetry)
    )
}
