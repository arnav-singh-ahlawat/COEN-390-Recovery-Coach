package com.team11.smartgym.ble

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.team11.smartgym.workout.RecoveryTechnique
import com.team11.smartgym.workout.WorkoutActivityEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.team11.smartgym.workout.ActivityType
import kotlinx.coroutines.tasks.await

data class WorkoutSummary(
    val durationSeconds: Long,
    val avgHeartRate: Int?,
    val stepsDelta: Long?
)

/**
 * History entry used by History/Profile screens.
 * Represents a whole workout (which may internally contain
 * multiple activities in the new flow).
 */
data class WorkoutSession(
    val id: Long,
    val startedAtMillis: Long,
    val durationSeconds: Long,
    val avgHeartRate: Int?,
    val steps: Long?,
    val activities: List<WorkoutActivityEntry> = emptyList(),
    val recoveryTechniques: List<RecoveryTechnique> = emptyList()
)

class LiveHrViewModel(app: Application) : AndroidViewModel(app) {

    private val bleManager = BleManager.getInstance(app)

    // BLE state exposed directly to the UI
    val connectionState = bleManager.connectionState
    val devices = bleManager.devices

    val heartRate = bleManager.heartRate
    val temperatureC = bleManager.temperatureC
    val humidity = bleManager.humidity
    val steps = bleManager.steps

    // Workout session state (old single-session flow, still used on Dashboard)
    private val _isWorkoutActive = MutableStateFlow(false)
    val isWorkoutActive: StateFlow<Boolean> = _isWorkoutActive.asStateFlow()

    private val _workoutElapsedSeconds = MutableStateFlow(0L)
    val workoutElapsedSeconds: StateFlow<Long> = _workoutElapsedSeconds.asStateFlow()

    private val _lastWorkoutSummary = MutableStateFlow<WorkoutSummary?>(null)
    val lastWorkoutSummary: StateFlow<WorkoutSummary?> = _lastWorkoutSummary.asStateFlow()

    // Full history of workouts (used by History/Profile)
    private val _sessions = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val sessions: StateFlow<List<WorkoutSession>> = _sessions.asStateFlow()

    private var workoutJob: Job? = null

    // HR sampling during old workout session (for averaging)
    private val hrSamples = mutableListOf<Int>()

    // Firebase for remote history sync
    private val firestore = Firebase.firestore
    private val auth = Firebase.auth

    init {
        // If user already logged in, load existing workouts from Firestore
        viewModelScope.launch {
            loadRemoteWorkouts()
        }
    }

    // ---------------------------------------------------------------------
    // BLE wrapper methods for UI
    // ---------------------------------------------------------------------

    fun startScan() = bleManager.startScan()
    fun stopScan() = bleManager.stopScan()

    fun connect(address: String, name: String?) = bleManager.connectToDevice(address, name)

    fun disconnect() {
        bleManager.disconnect()
        stopWorkoutSession() // auto-stop if BLE disconnects
    }

    fun readEnv() = bleManager.requestEnvMeasurement()

    // Lightweight wrappers used from the new multi-activity workout flow
    // to control IMU ON/OFF for walking/running.
    fun startImuLocomotion() {
        bleManager.startWorkoutImu()
    }

    fun stopImuLocomotion() {
        bleManager.stopWorkoutImu()
    }

    // ---------------------------------------------------------------------
    // OLD WORKOUT SESSION LOGIC (still used by the simple Dashboard card)
    // ---------------------------------------------------------------------

    fun startWorkoutSession() {
        if (_isWorkoutActive.value) return

        // Reset session values
        hrSamples.clear()
        _workoutElapsedSeconds.value = 0L
        _lastWorkoutSummary.value = null

        _isWorkoutActive.value = true

        // Start IMU on Arduino (this resets stepCount to 0 on the board)
        bleManager.startWorkoutImu()

        // Start session timer + HR sampling
        workoutJob?.cancel()
        workoutJob = viewModelScope.launch {
            while (_isWorkoutActive.value) {
                delay(1000L)
                _workoutElapsedSeconds.value++

                heartRate.value?.let { bpm ->
                    if (bpm > 0) hrSamples.add(bpm)
                }
            }
        }
    }

    fun stopWorkoutSession() {
        if (!_isWorkoutActive.value) return

        _isWorkoutActive.value = false

        // Stop IMU on Arduino
        bleManager.stopWorkoutImu()

        // Stop timer job
        workoutJob?.cancel()
        workoutJob = null

        val duration = _workoutElapsedSeconds.value

        val avgHr = if (hrSamples.isNotEmpty()) {
            (hrSamples.sum() / hrSamples.size.toFloat()).toInt()
        } else null

        // Arduino stepCount is already per-session (starts at 0 on CMD_START_IMU)
        val stepDelta = steps.value?.coerceAtLeast(0L)

        _lastWorkoutSummary.value = WorkoutSummary(
            durationSeconds = duration,
            avgHeartRate = avgHr,
            stepsDelta = stepDelta
        )

        // Also log this simple session into history (with no activities or recovery techniques)
        val now = System.currentTimeMillis()
        val startTime = now - duration * 1000

        val session = WorkoutSession(
            id = now,
            startedAtMillis = startTime,
            durationSeconds = duration,
            avgHeartRate = avgHr,
            steps = stepDelta,
            activities = emptyList(),
            recoveryTechniques = emptyList()
        )

        _sessions.value = _sessions.value + session

        // Sync to Firestore (best-effort)
        viewModelScope.launch {
            uploadWorkoutToFirestore(session)
        }
    }

    // ---------------------------------------------------------------------
    // NEW: LOGGING MULTI-ACTIVITY WORKOUTS FROM WORKOUT FLOW SCREEN
    // ---------------------------------------------------------------------

    /**
     * Called by the new multi-activity WorkoutFlowScreen when the user taps
     * "Save & Close" on the summary. We store:
     *  - the list of activities
     *  - recovery techniques suggested
     */
    fun logMultiActivityWorkoutFromFlow(
        activities: List<WorkoutActivityEntry>,
        recovery: List<RecoveryTechnique>
    ) {
        if (activities.isEmpty()) return

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

        val now = System.currentTimeMillis()
        val startTime = now - totalDuration * 1000

        val session = WorkoutSession(
            id = now,
            startedAtMillis = startTime,
            durationSeconds = totalDuration,
            avgHeartRate = avgHrAll,
            steps = totalStepsAll,
            activities = activities,
            recoveryTechniques = recovery
        )

        _sessions.value = _sessions.value + session

        // Sync to Firestore
        viewModelScope.launch {
            uploadWorkoutToFirestore(session)
        }
    }

    // ---------------------------------------------------------------------
    // Firebase sync helpers
    // ---------------------------------------------------------------------

    private suspend fun loadRemoteWorkouts() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("workouts")
                .orderBy("startedAtMillis")
                .get()
                .await()

            val remoteSessions = snapshot.documents.mapNotNull { it.toWorkoutSessionOrNull() }
            _sessions.value = remoteSessions
        } catch (_: Exception) {
            // Ignore for now; keep local list (likely empty on first launch)
        }
    }

    private suspend fun uploadWorkoutToFirestore(session: WorkoutSession) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val workoutData = mutableMapOf<String, Any>(
                "id" to session.id,
                "startedAtMillis" to session.startedAtMillis,
                "durationSeconds" to session.durationSeconds
            )

            session.avgHeartRate?.let { workoutData["avgHeartRate"] = it }
            session.steps?.let { workoutData["steps"] = it }

            if (session.activities.isNotEmpty()) {
                workoutData["activities"] = session.activities.map { entry ->
                    mapOf(
                        "type" to entry.type.name,
                        "durationSeconds" to entry.durationSeconds,
                        "avgHeartRate" to (entry.avgHeartRate ?: 0),
                        "steps" to (entry.steps ?: 0L)
                    )
                }
            }

            if (session.recoveryTechniques.isNotEmpty()) {
                workoutData["recoveryTechniques"] = session.recoveryTechniques.map { tech ->
                    mapOf(
                        "title" to tech.title,
                        "description" to tech.description
                    )
                }
            }

            firestore.collection("users")
                .document(uid)
                .collection("workouts")
                .document(session.id.toString())
                .set(workoutData)
                .await()
        } catch (_: Exception) {
            // You can log this if you want to debug sync issues
        }
    }

    private fun DocumentSnapshot.toWorkoutSessionOrNull(): WorkoutSession? {
        return try {
            val id = getLong("id") ?: return null
            val startedAtMillis = getLong("startedAtMillis") ?: return null
            val durationSeconds = getLong("durationSeconds") ?: 0L
            val avgHeartRate = getLong("avgHeartRate")?.toInt()
            val steps = getLong("steps")

            val activitiesRaw = get("activities") as? List<*>
            val activities = activitiesRaw
                ?.mapNotNull { raw ->
                    (raw as? Map<*, *>)?.let { map ->
                        val typeStr = map["type"] as? String ?: return@let null
                        val type = runCatching { ActivityType.valueOf(typeStr) }.getOrNull()
                            ?: return@let null
                        val dur = (map["durationSeconds"] as? Number)?.toLong() ?: 0L
                        val hr = (map["avgHeartRate"] as? Number)?.toInt()
                        val st = (map["steps"] as? Number)?.toLong()
                        WorkoutActivityEntry(
                            type = type,
                            durationSeconds = dur,
                            avgHeartRate = hr,
                            steps = st
                        )
                    }
                }
                ?: emptyList()

            val recoveryRaw = get("recoveryTechniques") as? List<*>
            val recovery = recoveryRaw
                ?.mapNotNull { raw ->
                    (raw as? Map<*, *>)?.let { map ->
                        val title = map["title"] as? String ?: return@let null
                        val description = map["description"] as? String ?: ""
                        RecoveryTechnique(
                            title = title,
                            description = description
                        )
                    }
                }
                ?: emptyList()

            WorkoutSession(
                id = id,
                startedAtMillis = startedAtMillis,
                durationSeconds = durationSeconds,
                avgHeartRate = avgHeartRate,
                steps = steps,
                activities = activities,
                recoveryTechniques = recovery
            )
        } catch (_: Exception) {
            null
        }
    }
}
