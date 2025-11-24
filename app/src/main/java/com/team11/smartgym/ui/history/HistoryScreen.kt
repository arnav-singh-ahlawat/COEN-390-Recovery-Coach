package com.team11.smartgym.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team11.smartgym.ble.LiveHrViewModel
import com.team11.smartgym.ble.WorkoutSession
import com.team11.smartgym.workout.CalorieEstimator
import com.team11.smartgym.workout.WorkoutActivityEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: LiveHrViewModel = viewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val sorted = sessions.sortedByDescending { it.startedAtMillis }

    var selected by remember { mutableStateOf<WorkoutSession?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "History",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Tap a workout to see full details.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        if (sorted.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No workouts logged yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // Left: list of sessions
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sorted, key = { it.id }) { session ->
                    WorkoutHistoryItem(
                        session = session,
                        isSelected = selected?.id == session.id,
                        onClick = { selected = session }
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // Right: scrollable details of selected session
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (selected != null) {
                    WorkoutDetailPanel(session = selected!!)
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Select a workout to see details.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutHistoryItem(
    session: WorkoutSession,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dateStr = remember(session.startedAtMillis) {
        val sdf = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
        sdf.format(Date(session.startedAtMillis))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = dateStr,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Duration: ${formatDuration(session.durationSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            session.avgHeartRate?.let { avg ->
                Text(
                    text = "Avg HR: $avg bpm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            session.steps?.let { s ->
                Text(
                    text = "Steps: $s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (session.activities.isNotEmpty()) {
                Text(
                    text = "Activities: ${session.activities.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WorkoutDetailPanel(
    session: WorkoutSession
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(4.dp)
    ) {
        val dateStr = remember(session.startedAtMillis) {
            val sdf = SimpleDateFormat("EEEE, MMM d, yyyy • HH:mm", Locale.getDefault())
            sdf.format(Date(session.startedAtMillis))
        }

        Text(
            text = "Workout details",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = dateStr,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Duration: ${formatDuration(session.durationSeconds)}",
            style = MaterialTheme.typography.bodyMedium
        )
        session.avgHeartRate?.let { avg ->
            Text(
                text = "Average heart rate: $avg bpm",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        session.steps?.let { s ->
            Text(
                text = "Total steps: $s",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // If we have activities, compute total calories on the fly
        if (session.activities.isNotEmpty()) {
            val totalCalories = CalorieEstimator.estimateWorkoutCalories(session.activities)
            Text(
                text = "Total calories: ${"%.1f".format(totalCalories)} kcal",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(8.dp))

        if (session.activities.isNotEmpty()) {
            Text(
                text = "Activities",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            session.activities.forEach { activity ->
                ActivityDetailCard(activity)
                Spacer(Modifier.height(8.dp))
            }
        } else {
            Text(
                text = "No detailed activities recorded for this workout.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))
        Divider()
        Spacer(Modifier.height(8.dp))

        if (session.recoveryTechniques.isNotEmpty()) {
            Text(
                text = "Recovery suggestions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            session.recoveryTechniques.forEach { rec ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
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
        } else {
            Text(
                text = "No recovery suggestions were logged for this workout.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActivityDetailCard(
    entry: WorkoutActivityEntry
) {
    val calories = CalorieEstimator.estimateActivityCalories(
        activityType = entry.type,
        durationSeconds = entry.durationSeconds,
        avgHeartRate = entry.avgHeartRate,
        steps = entry.steps
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                text = entry.type.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Duration: ${formatDuration(entry.durationSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            entry.avgHeartRate?.let { avg ->
                Text(
                    text = "Avg HR: $avg bpm",
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

private fun formatDuration(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}
