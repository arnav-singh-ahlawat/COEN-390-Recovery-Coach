package com.team11.smartgym.workout

/**
 * The type of activity inside a workout session.
 * IMU will only be used for WALKING and RUNNING.
 */
enum class ActivityType {
    WALKING,
    RUNNING,
    CYCLING,
    YOGA,
    WEIGHTLIFTING,
    OTHER
}

/**
 * One contiguous activity segment inside a workout.
 * Example: WALKING for 7 minutes, then RUNNING for 5 minutes, etc.
 */
data class WorkoutActivityEntry(
    val id: Long = System.currentTimeMillis(),
    val type: ActivityType,
    val durationSeconds: Long,
    val avgHeartRate: Int? = null,
    val steps: Long? = null
)

/**
 * A recovery technique we suggest at the end of a workout.
 */
data class RecoveryTechnique(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val description: String
)

/**
 * A richer workout model (not yet wired fully, but available if we
 * later want a detailed object with nested activities & recovery).
 */
data class RichWorkoutSession(
    val id: Long = System.currentTimeMillis(),
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val totalDurationSeconds: Long,
    val avgHeartRate: Int?,
    val totalSteps: Long?,
    val activities: List<WorkoutActivityEntry>,
    val recoveryTechniques: List<RecoveryTechnique>,
    val totalCalories: Double? = null
)

/**
 * Utility for estimating calories burned for activities & workouts.
 *
 * This uses a simple MET-based model:
 *
 *   calories = MET * weightKg * (durationHours) * intensityFactor
 *
 * where MET is chosen per activity type and intensityFactor is optionally
 * adjusted using average heart rate.
 *
 * For WALKING and RUNNING we optionally factor in steps as a small bonus.
 */
object CalorieEstimator {

    // Baseline MET values per activity type (rough, can be tuned)
    private fun baseMetForActivity(type: ActivityType): Double = when (type) {
        ActivityType.WALKING       -> 3.5   // light-moderate
        ActivityType.RUNNING       -> 8.0   // moderate run
        ActivityType.CYCLING       -> 6.0
        ActivityType.YOGA          -> 2.5
        ActivityType.WEIGHTLIFTING -> 3.5
        ActivityType.OTHER         -> 3.0
    }

    /**
     * Estimate calories burned for a single activity segment.
     */
    fun estimateActivityCalories(
        activityType: ActivityType,
        durationSeconds: Long,
        avgHeartRate: Int?,
        steps: Long?,
        weightKg: Double = 75.0
    ): Double {
        if (durationSeconds <= 0L || weightKg <= 0.0) return 0.0

        val baseMet = baseMetForActivity(activityType)

        // Adjust intensity based on HR, if we have it.
        val hrFactor = avgHeartRate?.let { hr ->
            (hr / 140.0).coerceIn(0.6, 1.6)
        } ?: 1.0

        val durationHours = durationSeconds / 3600.0
        var calories = baseMet * weightKg * durationHours * hrFactor

        // For walking/running, add a small step-based bonus.
        if (activityType == ActivityType.WALKING || activityType == ActivityType.RUNNING) {
            if (steps != null && steps > 0) {
                // About ~0.04 kcal per step as a small correction
                calories += steps * 0.04
            }
        }

        return calories
    }

    /**
     * Estimate calories for a list of recorded activity segments.
     */
    fun estimateWorkoutCalories(
        activities: List<WorkoutActivityEntry>,
        weightKg: Double = 75.0
    ): Double {
        if (activities.isEmpty()) return 0.0

        return activities.sumOf { entry ->
            estimateActivityCalories(
                activityType = entry.type,
                durationSeconds = entry.durationSeconds,
                avgHeartRate = entry.avgHeartRate,
                steps = entry.steps,
                weightKg = weightKg
            )
        }
    }
}

/**
 * Simple rule-based recovery advisor. Uses:
 *  - total duration
 *  - average HR
 *  - total steps
 *  - mix of activities
 *
 * to propose a small set of recovery techniques.
 */
object RecoveryAdvisor {

    fun suggestRecovery(
        activities: List<WorkoutActivityEntry>,
        avgHeartRate: Int?,
        totalDurationSeconds: Long,
        totalSteps: Long?
    ): List<RecoveryTechnique> {
        if (activities.isEmpty() || totalDurationSeconds <= 0L) {
            return emptyList()
        }

        val techniques = mutableListOf<RecoveryTechnique>()

        val minutes = totalDurationSeconds / 60.0
        val hr = avgHeartRate ?: 0
        val steps = totalSteps ?: 0L

        val hasRunning = activities.any { it.type == ActivityType.RUNNING }
        val hasWalking = activities.any { it.type == ActivityType.WALKING }
        val hasWeights = activities.any { it.type == ActivityType.WEIGHTLIFTING }
        val hasYoga = activities.any { it.type == ActivityType.YOGA }

        val intensityScore = hr + (minutes * 0.8).toInt() + (steps / 300).toInt()

        // Base suggestions
        techniques += RecoveryTechnique(
            title = "Hydration",
            description = "Drink 300–500 ml of water in the next 15 minutes to support recovery."
        )

        if (intensityScore < 140) {
            // Light session
            techniques += RecoveryTechnique(
                title = "Light Stretching",
                description = "Spend 5–8 minutes doing gentle full-body stretching, focusing on any tight areas."
            )
        } else if (intensityScore in 140..200) {
            // Moderate session
            techniques += RecoveryTechnique(
                title = "Mobility + Breathing",
                description = "Do 5 minutes of easy mobility work, then 3–5 minutes of slow nasal breathing to bring your heart rate down."
            )
        } else {
            // Hard session
            techniques += RecoveryTechnique(
                title = "Deep Recovery",
                description = "Your session was intense. Do 10–15 minutes of light walking or stretching, followed by 5 minutes of deep breathing."
            )
            techniques += RecoveryTechnique(
                title = "Sleep Priority",
                description = "Aim for at least 7–9 hours of sleep tonight to support full recovery."
            )
        }

        if (hasRunning || hasWalking) {
            techniques += RecoveryTechnique(
                title = "Lower Body Release",
                description = "Foam roll or massage your calves, hamstrings, and quads for 1–2 minutes each."
            )
        }

        if (hasWeights) {
            techniques += RecoveryTechnique(
                title = "Muscle Relax",
                description = "Do gentle range-of-motion work for the major muscle groups you trained today."
            )
        }

        if (!hasYoga) {
            techniques += RecoveryTechnique(
                title = "Breathing Reset",
                description = "Lie down and do 8–10 slow breaths, 4s inhale, 6s exhale, to signal your nervous system to relax."
            )
        }

        // Avoid duplicates by title
        return techniques
            .distinctBy { it.title }
            .take(5)
    }
}
