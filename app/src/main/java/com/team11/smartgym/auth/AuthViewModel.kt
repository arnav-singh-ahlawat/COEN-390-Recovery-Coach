package com.team11.smartgym.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val currentUser: UserProfile? = null,
    val errorMessage: String? = null
)

class AuthViewModel : ViewModel() {

    private val auth = Firebase.auth
    private val firestore = Firebase.firestore

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // If already logged in, load profile
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            viewModelScope.launch {
                _uiState.value = AuthUiState(isLoading = true)
                val profile = loadUserProfile(firebaseUser.uid)
                _uiState.value = AuthUiState(
                    isLoading = false,
                    isLoggedIn = profile != null,
                    currentUser = profile
                )
            }
        }
    }

    fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        heightCm: Int,
        weightKg: Float,
        sex: String,
        workoutFrequency: WorkoutFrequency
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val uid = result.user?.uid
                    ?: throw IllegalStateException("Failed to get UID from FirebaseAuth")

                val userDoc = mapOf(
                    "uid" to uid,
                    "email" to email,
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "heightCm" to heightCm,
                    "weightKg" to weightKg,
                    "sex" to sex,
                    "workoutFrequency" to workoutFrequency.name
                )

                firestore.collection("users")
                    .document(uid)
                    .set(userDoc)
                    .await()

                // After sign up, go back to login screen (logged out)
                auth.signOut()

                _uiState.value = AuthUiState(
                    isLoading = false,
                    isLoggedIn = false,
                    currentUser = null,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Sign up failed"
                )
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

                val result = auth.signInWithEmailAndPassword(email, password).await()
                val uid = result.user?.uid
                    ?: throw IllegalStateException("Failed to get UID from FirebaseAuth")

                val profile = loadUserProfile(uid)
                    ?: throw IllegalStateException("User profile not found")

                _uiState.value = AuthUiState(
                    isLoading = false,
                    isLoggedIn = true,
                    currentUser = profile,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoggedIn = false,
                    currentUser = null,
                    errorMessage = e.message ?: "Login failed"
                )
            }
        }
    }

    private suspend fun loadUserProfile(uid: String): UserProfile? {
        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .get()
                .await()

            if (!snapshot.exists()) return null
            val data = snapshot.data ?: return null

            UserProfile(
                uid = uid,
                email = data["email"] as? String ?: "",
                firstName = data["firstName"] as? String ?: "",
                lastName = data["lastName"] as? String ?: "",
                heightCm = (data["heightCm"] as? Number)?.toInt() ?: 0,
                weightKg = (data["weightKg"] as? Number)?.toFloat() ?: 0f,
                sex = data["sex"] as? String ?: "",
                workoutFrequency = (data["workoutFrequency"] as? String)
                    ?.let { runCatching { WorkoutFrequency.valueOf(it) }.getOrDefault(WorkoutFrequency.INACTIVE) }
                    ?: WorkoutFrequency.INACTIVE
            )
        } catch (_: Exception) {
            null
        }
    }

    fun updateProfile(
        firstName: String,
        lastName: String,
        heightCm: Int,
        weightKg: Float,
        sex: String,
        workoutFrequency: WorkoutFrequency
    ) {
        val uid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

                val docRef = firestore.collection("users").document(uid)
                val updates = mapOf(
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "heightCm" to heightCm,
                    "weightKg" to weightKg,
                    "sex" to sex,
                    "workoutFrequency" to workoutFrequency.name
                )

                docRef.update(updates).await()

                val current = _uiState.value.currentUser
                val updatedProfile = (current ?: UserProfile(uid = uid)).copy(
                    firstName = firstName,
                    lastName = lastName,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    sex = sex,
                    workoutFrequency = workoutFrequency
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentUser = updatedProfile,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to update profile"
                )
            }
        }
    }

    fun logout() {
        auth.signOut()
        _uiState.value = AuthUiState(
            isLoading = false,
            isLoggedIn = false,
            currentUser = null,
            errorMessage = null
        )
    }
}
