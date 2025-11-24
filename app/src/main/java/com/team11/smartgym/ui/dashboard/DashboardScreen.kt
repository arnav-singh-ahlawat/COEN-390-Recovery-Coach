@file:OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)

package com.team11.smartgym.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team11.smartgym.ble.BleConnectionState
import com.team11.smartgym.ble.LiveHrViewModel
import com.team11.smartgym.ble.WorkoutSummary
import com.team11.smartgym.environment.EnvironmentAdvisor
import com.team11.smartgym.ui.permissions.rememberBlePermissionsState
import com.team11.smartgym.ui.workout.WorkoutFlowScreen

@Composable
fun DashboardScreen(
    viewModel: LiveHrViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val devices by viewModel.devices.collectAsState()

    val hr by viewModel.heartRate.collectAsState()
    val temp by viewModel.temperatureC.collectAsState()
    val hum by viewModel.humidity.collectAsState()

    val isWorkoutActive by viewModel.isWorkoutActive.collectAsState()
    val elapsed by viewModel.workoutElapsedSeconds.collectAsState()
    val summary by viewModel.lastWorkoutSummary.collectAsState()

    val blePermissions = rememberBlePermissionsState()
    val scrollState = rememberScrollState()

    var showWorkoutFlow by remember { mutableStateOf(false) }

    // 🔍 compute environment suitability whenever temp/hum change
    val suitability = remember(temp, hum) {
        EnvironmentAdvisor.assess(
            tempC = temp,
            humidityPercent = hum
        )
    }

    if (showWorkoutFlow) {
        // Full-screen workout flow inside the dashboard tab
        WorkoutFlowScreen(
            modifier = Modifier.fillMaxSize(),
            onExitWorkout = { showWorkoutFlow = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 16.dp)
    ) {
        Text(
            "Dashboard",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        // --- BLE section: scan / connect directly on dashboard ---
        Text(
            text = "Device",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(4.dp))

        if (!blePermissions.allPermissionsGranted) {
            Text(
                text = "Bluetooth permissions required to scan and connect to NanoHR.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = { blePermissions.launchMultiplePermissionRequest() }) {
                Text("Grant Bluetooth Permissions")
            }
        } else {
            // connection state label
            val stateLabel = when (connectionState) {
                is BleConnectionState.Connected -> "Connected to NanoHR ✔"
                is BleConnectionState.Connecting -> "Connecting…"
                is BleConnectionState.Scanning -> "Scanning…"
                is BleConnectionState.Disconnecting -> "Disconnecting…"
                is BleConnectionState.Error -> "Error connecting"
                BleConnectionState.Idle -> "Not connected"
            }

            Text(
                text = stateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            when (connectionState) {
                is BleConnectionState.Idle -> {
                    Button(onClick = { viewModel.startScan() }) {
                        Text("Scan for NanoHR")
                    }
                }

                is BleConnectionState.Scanning -> {
                    Button(onClick = { viewModel.stopScan() }) {
                        Text("Stop Scan")
                    }
                }

                is BleConnectionState.Connecting -> {
                    // no extra controls
                }

                is BleConnectionState.Connected -> {
                    Button(onClick = { viewModel.disconnect() }) {
                        Text("Disconnect")
                    }
                }

                is BleConnectionState.Error -> {
                    Button(onClick = { viewModel.startScan() }) {
                        Text("Retry Scan")
                    }
                }

                BleConnectionState.Disconnecting -> {
                    // no extra controls
                }
            }

            // Devices list while scanning
            if (devices.isNotEmpty() && connectionState is BleConnectionState.Scanning) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Devices found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                devices.forEach { d ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        onClick = { viewModel.connect(d.address, d.name) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = d.name ?: "Unknown",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = d.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tap to connect →",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- HR Gauge (Centered) ---
        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            HRGauge(
                bpm = hr ?: 0,
                modifier = Modifier.size(230.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        // Start Workout button (opens the new multi-activity flow)
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showWorkoutFlow = true },
            enabled = connectionState is BleConnectionState.Connected
        ) {
            Text("Start Workout")
        }

        Spacer(Modifier.height(24.dp))

        // --- Environment Card + suitability advice ---
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF111111)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Environment", color = Color.Gray, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${temp?.let { "%.1f".format(it) } ?: "--"}°C",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${hum?.let { "%.0f".format(it) } ?: "--"}%",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                suitability?.let { s ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = s.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = s.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Small Update Env button under card
        Button(
            modifier = Modifier.align(Alignment.Start),
            onClick = { viewModel.readEnv() }
        ) {
            Text("Update Env")
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun HRGauge(
    bpm: Int,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateFloatAsState(
        targetValue = bpm / 200f, // normalized (200 bpm max)
        animationSpec = tween(600),
        label = ""
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {

            val thickness = size.minDimension * 0.09f

            // Background circle
            drawArc(
                color = Color(0xFF1A1A1A),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(thickness, cap = StrokeCap.Round)
            )

            // Neon green ring
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFF00F57A),
                        Color(0xFF00C55E),
                        Color(0xFF00F57A)
                    )
                ),
                startAngle = -90f,
                sweepAngle = animatedValue * 360f,
                useCenter = false,
                style = Stroke(thickness, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = bpm.toString(),
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "BPM",
                color = Color.Gray,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun WorkoutStatusCard(
    isActive: Boolean,
    elapsed: Long,
    summary: WorkoutSummary?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(20.dp)) {

            if (isActive) {
                Text("Workout in progress", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Elapsed: ${formatTime(elapsed)}",
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onStop) { Text("Stop Workout") }
            } else {
                Text("Ready to train?", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onStart) { Text("Start Workout") }

                summary?.let {
                    Spacer(Modifier.height(16.dp))
                    Text("Last Session", fontWeight = FontWeight.Bold)
                    Text("Duration: ${formatTime(it.durationSeconds)}")
                    Text("Avg HR: ${it.avgHeartRate ?: "--"} BPM")
                    Text("Steps: ${it.stepsDelta ?: "--"}")
                }
            }
        }
    }
}

private fun formatTime(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}
