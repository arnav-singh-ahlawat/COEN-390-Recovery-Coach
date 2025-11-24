package com.team11.smartgym.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team11.smartgym.auth.AuthViewModel
import com.team11.smartgym.auth.UserProfile
import com.team11.smartgym.auth.WorkoutFrequency
import com.team11.smartgym.ble.LiveHrViewModel
import com.team11.smartgym.ble.WorkoutSession
import com.team11.smartgym.workout.CalorieEstimator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun ProfileScreen(
    userProfile: UserProfile?,
    viewModel: LiveHrViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val authState by authViewModel.uiState.collectAsState()

    val totalWorkouts = sessions.size
    val totalDuration = sessions.sumOf { it.durationSeconds }
    val totalSteps = sessions.mapNotNull { it.steps }.sum()
    val avgHrAll = sessions.mapNotNull { it.avgHeartRate }
        .takeIf { it.isNotEmpty() }
        ?.average()

    // Total calories from sessions that have activities
    val totalCalories = sessions.sumOf { session ->
        if (session.activities.isNotEmpty()) {
            CalorieEstimator.estimateWorkoutCalories(session.activities)
        } else {
            0.0
        }
    }

    val lastSession: WorkoutSession? = sessions.maxByOrNull { it.startedAtMillis }

    var isEditing by remember { mutableStateOf(false) }

    // Local editable fields (populated when entering edit mode)
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("male") }
    var freq by remember { mutableStateOf(WorkoutFrequency.INACTIVE) }

    val errorMessage = authState.errorMessage

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Profile",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        if (userProfile != null) {
            if (!isEditing) {
                // VIEW MODE
                Text(
                    text = "${userProfile.firstName} ${userProfile.lastName}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Email: ${userProfile.email}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Height: ${userProfile.heightCm} cm")
                        Text("Weight: ${userProfile.weightKg} kg")
                        Text("Sex: ${userProfile.sex}")
                        Text("Workout frequency: ${formatFrequency(userProfile)}")
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            // Populate edit fields from current profile
                            firstName = userProfile.firstName
                            lastName = userProfile.lastName
                            heightText = userProfile.heightCm.toString()
                            weightText = userProfile.weightKg.toString()
                            sex = userProfile.sex
                            freq = userProfile.workoutFrequency
                            isEditing = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Edit profile")
                    }

                    OutlinedButton(
                        onClick = { authViewModel.logout() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Log out")
                    }
                }
            } else {
                // EDIT MODE
                Text(
                    text = "Edit profile",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("First name") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Last name") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it },
                        label = { Text("Height (cm)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text("Weight (kg)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text("Sex", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { sex = "male" },
                        enabled = sex != "male"
                    ) { Text("Male") }
                    OutlinedButton(
                        onClick = { sex = "female" },
                        enabled = sex != "female"
                    ) { Text("Female") }
                }

                Spacer(Modifier.height(8.dp))

                Text("Workout frequency", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FrequencyOptionRow(
                        label = "Inactive (0–1 days/week)",
                        selected = freq == WorkoutFrequency.INACTIVE,
                        onClick = { freq = WorkoutFrequency.INACTIVE }
                    )
                    FrequencyOptionRow(
                        label = "Moderately active (2–4 days/week)",
                        selected = freq == WorkoutFrequency.MODERATELY_ACTIVE,
                        onClick = { freq = WorkoutFrequency.MODERATELY_ACTIVE }
                    )
                    FrequencyOptionRow(
                        label = "Active (5–7 days/week)",
                        selected = freq == WorkoutFrequency.ACTIVE,
                        onClick = { freq = WorkoutFrequency.ACTIVE }
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val h = heightText.toIntOrNull()
                            val w = weightText.toFloatOrNull()
                            if (h == null || h <= 0 || w == null || w <= 0f) {
                                // You can add local validation message if you want
                                return@Button
                            }

                            authViewModel.updateProfile(
                                firstName = firstName.trim(),
                                lastName = lastName.trim(),
                                heightCm = h,
                                weightKg = w,
                                sex = sex,
                                workoutFrequency = freq
                            )
                            isEditing = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                    OutlinedButton(
                        onClick = { isEditing = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        } else {
            Text(
                text = "SmartGym athlete",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }

        // Summary metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Workouts",
                value = totalWorkouts.toString(),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Total Steps",
                value = totalSteps.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        MetricCard(
            title = "Total Time",
            value = formatDuration(totalDuration),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        MetricCard(
            title = "Total Calories",
            value = "${totalCalories.toInt()} kcal",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        if (avgHrAll != null) {
            MetricCard(
                title = "Avg HR (all sessions)",
                value = "${avgHrAll.toInt()} bpm",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }

        // Last session details
        if (lastSession != null) {
            Spacer(Modifier.height(16.dp))
            LastSessionCard(session = lastSession)
        } else {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No workouts yet. Start one from the dashboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.heightIn(min = 90.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LastSessionCard(session: WorkoutSession) {
    val formatter = rememberDateFormatter()
    val dateLabel = formatter.format(Date(session.startedAtMillis))

    val durationLabel = formatDuration(session.durationSeconds)
    val avgHr = session.avgHeartRate?.let { "$it bpm" } ?: "--"
    val stepsLabel = session.steps?.toString() ?: "--"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Last Workout", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text("Duration: $durationLabel")
            Text("Avg HR: $avgHr")
            Text("Steps: $stepsLabel")
        }
    }
}

@Composable
private fun FrequencyOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !selected,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }
}

@Composable
private fun rememberDateFormatter(): SimpleDateFormat {
    return remember {
        SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
    }
}

private fun formatDuration(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}

private fun formatFrequency(profile: UserProfile): String {
    return when (profile.workoutFrequency) {
        WorkoutFrequency.INACTIVE ->
            "Inactive (0–1 days/week)"
        WorkoutFrequency.MODERATELY_ACTIVE ->
            "Moderately active (2–4 days/week)"
        WorkoutFrequency.ACTIVE ->
            "Active (5–7 days/week)"
    }
}
