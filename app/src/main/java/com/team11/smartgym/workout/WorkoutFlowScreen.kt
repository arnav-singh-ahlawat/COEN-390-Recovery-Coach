package com.team11.smartgym.ui.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team11.smartgym.ble.LiveHrViewModel
import com.team11.smartgym.workout.ActivityType
import com.team11.smartgym.workout.CalorieEstimator
import com.team11.smartgym.workout.RecoveryAdvisor
import com.team11.smartgym.workout.RecoveryTechnique
import com.team11.smartgym.workout.WorkoutActivityEntry
import kotlinx.coroutines.delay

private sealed class WorkoutUiState {
    object ChooseActivity : WorkoutUiState()
    data class Countdown(
        val type: ActivityType,
        val secondsLeft: Int
    ) : WorkoutUiState()

    data class Active(
        val type: ActivityType,
        val elapsedSeconds: Long,
        val isPaused: Boolean,
        val stepBaseline: Long? // baseline steps for this activity
    ) : WorkoutUiState()

    data class Summary(
        val activities: List<WorkoutActivityEntry>,
        val totalDurationSeconds: Long,
        val avgHeartRate: Int?,
        val totalSteps: Long?,
        val totalCalories: Double,
        val recovery: List<RecoveryTechnique>
    ) : WorkoutUiState()
}

/**
 * Multi-activity workout flow:
 *  - IMU only for WALKING and RUNNING
 *  - Steps are activity-local because Arduino resets stepCount to 0 on CMD_START_IMU
 *  - At "End Workout", shows summary + recovery, then logs to history
 */
@Composable
fun WorkoutFlowScreen(
    modifier: Modifier = Modifier,
    onExitWorkout: () -> Unit,
    viewModel: LiveHrViewModel = viewModel()
) {
    var uiState by remember { mutableStateOf<WorkoutUiState>(WorkoutUiState.ChooseActivity) }

    // Completed activities in this workout session
    val completedActivities = remember { mutableStateListOf<WorkoutActivityEntry>() }

    // Live BLE data
    val hr by viewModel.heartRate.collectAsState()
    val steps by viewModel.steps.collectAsState()

    // ---- Handle countdown animation ----
    LaunchedEffect(uiState) {
        val countdown = uiState as? WorkoutUiState.Countdown ?: return@LaunchedEffect
        var sec = countdown.secondsLeft
        while (sec > 0) {
            delay(1000L)
            sec--
            if (sec > 0) {
                uiState = countdown.copy(secondsLeft = sec)
            } else {
                // At the moment we start the activity, Arduino will reset stepCount to 0
                // when we send CMD_START_IMU for walking/running, so our baseline is 0.
                val baselineSteps: Long? = when (countdown.type) {
                    ActivityType.WALKING, ActivityType.RUNNING -> 0L
                    else -> null
                }

                // Start IMU for walking/running ONLY
                if (countdown.type == ActivityType.WALKING || countdown.type == ActivityType.RUNNING) {
                    viewModel.startImuLocomotion()
                }

                uiState = WorkoutUiState.Active(
                    type = countdown.type,
                    elapsedSeconds = 0L,
                    isPaused = false,
                    stepBaseline = baselineSteps
                )
            }
        }
    }

    // ---- Handle active timer ----
    LaunchedEffect(uiState) {
        val active = uiState as? WorkoutUiState.Active ?: return@LaunchedEffect
        while (true) {
            delay(1000L)
            val current = uiState
            if (current is WorkoutUiState.Active && !current.isPaused && current.type == active.type) {
                uiState = current.copy(elapsedSeconds = current.elapsedSeconds + 1)
            } else {
                break
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "Workout Session",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Build your workout with multiple activities.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        when (val state = uiState) {
            is WorkoutUiState.ChooseActivity -> {
                ChooseActivitySection(
                    onSelect = { type ->
                        uiState = WorkoutUiState.Countdown(type, secondsLeft = 5)
                    }
                )
            }

            is WorkoutUiState.Countdown -> {
                CountdownSection(
                    type = state.type,
                    secondsLeft = state.secondsLeft,
                    onCancel = {
                        uiState = WorkoutUiState.ChooseActivity
                    }
                )
            }

            is WorkoutUiState.Active -> {
                ActiveActivitySection(
                    state = state,
                    currentHr = hr,
                    currentSteps = steps,
                    onPauseResume = {
                        uiState = state.copy(isPaused = !state.isPaused)
                    },
                    onEndActivity = {
                        // Stop IMU if this activity was walking/running
                        if (state.type == ActivityType.WALKING || state.type == ActivityType.RUNNING) {
                            viewModel.stopImuLocomotion()
                        }

                        val currentStepsValue = steps

                        // Arduino stepCount is 0-based for this activity when IMU started,
                        // so delta = currentSteps - baseline (baseline = 0 for walking/running).
                        val stepsDelta: Long? =
                            if (state.stepBaseline != null && currentStepsValue != null) {
                                (currentStepsValue - state.stepBaseline).coerceAtLeast(0L)
                            } else null

                        // Save this activity entry locally
                        completedActivities += WorkoutActivityEntry(
                            type = state.type,
                            durationSeconds = state.elapsedSeconds,
                            avgHeartRate = hr,
                            steps = stepsDelta
                        )
                        uiState = WorkoutUiState.ChooseActivity
                    }
                )
            }

            is WorkoutUiState.Summary -> {
                WorkoutSummarySection(
                    summary = state,
                    onSaveAndClose = {
                        // Log to history including recovery techniques
                        viewModel.logMultiActivityWorkoutFromFlow(
                            activities = state.activities,
                            recovery = state.recovery
                        )
                        // Ensure IMU is off
                        viewModel.stopImuLocomotion()
                        onExitWorkout()
                    },
                    onBackToActivities = {
                        uiState = WorkoutUiState.ChooseActivity
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // If we're in summary, don't show activity list & End Workout button again.
        if (uiState is WorkoutUiState.Summary) {
            return@Column
        }

        // List of completed activities in this workout
        if (completedActivities.isNotEmpty()) {
            Text(
                text = "Activities in this workout",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(completedActivities) { entry ->
                    ActivitySummaryCard(entry)
                }
            }

            Spacer(Modifier.height(16.dp))
        } else {
            Spacer(Modifier.weight(1f))
        }

        // Bottom: End workout → compute summary + recovery
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (completedActivities.isEmpty()) {
                    // nothing to save, just exit
                    viewModel.stopImuLocomotion()
                    onExitWorkout()
                    return@OutlinedButton
                }

                val activities = completedActivities.toList()
                val totalDuration = activities.sumOf { it.durationSeconds }
                val avgHrAll = activities
                    .mapNotNull { it.avgHeartRate }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.toInt()
                val totalStepsAll = activities
                    .mapNotNull { it.steps }
                    .takeIf { it.isNotEmpty() }
                    ?.sum()

                val totalCalories = CalorieEstimator.estimateWorkoutCalories(activities)
                val recovery = RecoveryAdvisor.suggestRecovery(
                    activities = activities,
                    avgHeartRate = avgHrAll,
                    totalDurationSeconds = totalDuration,
                    totalSteps = totalStepsAll
                )

                uiState = WorkoutUiState.Summary(
                    activities = activities,
                    totalDurationSeconds = totalDuration,
                    avgHeartRate = avgHrAll,
                    totalSteps = totalStepsAll,
                    totalCalories = totalCalories,
                    recovery = recovery
                )
            }
        ) {
            Text("End Workout")
        }
    }
}

@Composable
private fun ChooseActivitySection(
    onSelect: (ActivityType) -> Unit
) {
    Column {
        Text(
            text = "Choose activity type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityTypeButton("Walking", ActivityType.WALKING, onSelect)
            ActivityTypeButton("Running", ActivityType.RUNNING, onSelect)
            ActivityTypeButton("Cycling", ActivityType.CYCLING, onSelect)
            ActivityTypeButton("Weightlifting", ActivityType.WEIGHTLIFTING, onSelect)
            ActivityTypeButton("Yoga", ActivityType.YOGA, onSelect)
            ActivityTypeButton("Other", ActivityType.OTHER, onSelect)
        }
    }
}

@Composable
private fun ActivityTypeButton(
    label: String,
    type: ActivityType,
    onSelect: (ActivityType) -> Unit
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onSelect(type) }
    ) {
        Text(label)
    }
}

@Composable
private fun CountdownSection(
    type: ActivityType,
    secondsLeft: Int,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Get ready for",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = prettyActivityName(type),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = secondsLeft.toString(),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ActiveActivitySection(
    state: WorkoutUiState.Active,
    currentHr: Int?,
    currentSteps: Long?,
    onPauseResume: () -> Unit,
    onEndActivity: () -> Unit
) {
    val currentStepsValue = currentSteps

    val stepsDelta: Long? =
        if (state.stepBaseline != null && currentStepsValue != null) {
            (currentStepsValue - state.stepBaseline).coerceAtLeast(0L)
        } else null

    val calories = CalorieEstimator.estimateActivityCalories(
        activityType = state.type,
        durationSeconds = state.elapsedSeconds,
        avgHeartRate = currentHr,
        steps = stepsDelta
    )

    val showSteps = state.type == ActivityType.WALKING || state.type == ActivityType.RUNNING

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = prettyActivityName(state.type),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Elapsed: ${formatDuration(state.elapsedSeconds)}",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Heart Rate: ${currentHr ?: "--"} bpm",
            style = MaterialTheme.typography.bodyMedium
        )

        if (showSteps) {
            Text(
                text = "Steps: ${stepsDelta ?: 0}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Text(
            text = "Calories: ${"%.1f".format(calories)} kcal",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPauseResume) {
                Text(if (state.isPaused) "Resume" else "Pause")
            }
            OutlinedButton(onClick = onEndActivity) {
                Text("End Activity")
            }
        }
    }
}

@Composable
private fun ActivitySummaryCard(entry: WorkoutActivityEntry) {
    val calories = CalorieEstimator.estimateActivityCalories(
        activityType = entry.type,
        durationSeconds = entry.durationSeconds,
        avgHeartRate = entry.avgHeartRate,
        steps = entry.steps
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = prettyActivityName(entry.type),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Duration: ${formatDuration(entry.durationSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            entry.avgHeartRate?.let { avgHr ->
                Text(
                    text = "Avg HR: $avgHr bpm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            entry.steps?.let { s ->
                Text(
                    text = "Steps: $s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Calories: ${"%.1f".format(calories)} kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorkoutSummarySection(
    summary: WorkoutUiState.Summary,
    onSaveAndClose: () -> Unit,
    onBackToActivities: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        Text(
            text = "Workout Summary",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Total duration: ${formatDuration(summary.totalDurationSeconds)}",
            style = MaterialTheme.typography.bodyMedium
        )

        summary.avgHeartRate?.let { avg ->
            Text(
                text = "Average heart rate: $avg bpm",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        summary.totalSteps?.let { steps ->
            Text(
                text = "Total steps: $steps",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Text(
            text = "Total calories: ${"%.1f".format(summary.totalCalories)} kcal",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(16.dp))

        if (summary.recovery.isNotEmpty()) {
            Text(
                text = "Recovery suggestions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            summary.recovery.forEach { rec ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = rec.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = rec.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onBackToActivities
            ) {
                Text("Back")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = onSaveAndClose
            ) {
                Text("Save & Close")
            }
        }
    }
}

private fun prettyActivityName(type: ActivityType): String =
    type.name.lowercase().replaceFirstChar { it.uppercase() }

private fun formatDuration(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}
