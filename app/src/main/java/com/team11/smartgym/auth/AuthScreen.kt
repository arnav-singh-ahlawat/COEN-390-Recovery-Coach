package com.team11.smartgym.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team11.smartgym.auth.AuthUiState
import com.team11.smartgym.auth.AuthViewModel
import com.team11.smartgym.auth.WorkoutFrequency

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel = viewModel()
) {
    val uiState by authViewModel.uiState.collectAsState()

    var isLoginMode by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = if (isLoginMode) "Welcome to SmartGym" else "Create your SmartGym account",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = if (isLoginMode)
                        "Log in to see your workouts and live stats."
                    else
                        "We’ll use your details to personalize your workout insights.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                if (isLoginMode) {
                    LoginForm(
                        uiState = uiState,
                        onSwitchToSignUp = { isLoginMode = false },
                        onLogin = { email, password ->
                            authViewModel.login(email, password)
                        }
                    )
                } else {
                    SignUpForm(
                        uiState = uiState,
                        onSwitchToLogin = { isLoginMode = true },
                        onSignUp = { email, password, firstName, lastName, heightCm, weightKg, sex, freq ->
                            authViewModel.signUp(
                                email = email,
                                password = password,
                                firstName = firstName,
                                lastName = lastName,
                                heightCm = heightCm,
                                weightKg = weightKg,
                                sex = sex,
                                workoutFrequency = freq
                            )
                        }
                    )
                }
            }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun LoginForm(
    uiState: AuthUiState,
    onSwitchToSignUp: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val errorToShow = localError ?: uiState.errorMessage

    Column {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email address") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        if (!errorToShow.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorToShow,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                localError = null
                if (email.isBlank() || password.isBlank()) {
                    localError = "Please enter both email and password."
                    return@Button
                }
                onLogin(email.trim(), password)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log in")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onSwitchToSignUp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Don't have an account? Sign up")
        }
    }
}

@Composable
private fun SignUpForm(
    uiState: AuthUiState,
    onSwitchToLogin: () -> Unit,
    onSignUp: (
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        heightCm: Int,
        weightKg: Float,
        sex: String,
        workoutFrequency: WorkoutFrequency
    ) -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("male") }
    var frequency by remember { mutableStateOf(WorkoutFrequency.INACTIVE) }

    var localError by remember { mutableStateOf<String?>(null) }
    val errorToShow = localError ?: uiState.errorMessage

    Column {
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

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email address") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

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

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Sex",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
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

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Workout frequency",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FrequencyOptionRow(
                label = "Inactive (0–1 days/week)",
                selected = frequency == WorkoutFrequency.INACTIVE,
                onClick = { frequency = WorkoutFrequency.INACTIVE }
            )
            FrequencyOptionRow(
                label = "Moderately active (2–4 days/week)",
                selected = frequency == WorkoutFrequency.MODERATELY_ACTIVE,
                onClick = { frequency == WorkoutFrequency.MODERATELY_ACTIVE
                    frequency = WorkoutFrequency.MODERATELY_ACTIVE }
            )
            FrequencyOptionRow(
                label = "Active (5–7 days/week)",
                selected = frequency == WorkoutFrequency.ACTIVE,
                onClick = { frequency = WorkoutFrequency.ACTIVE }
            )
        }

        if (!errorToShow.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorToShow,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                localError = null

                if (password != confirmPassword) {
                    localError = "Passwords do not match."
                    return@Button
                }
                val h = heightText.toIntOrNull()
                val w = weightText.toFloatOrNull()
                if (h == null || h <= 0) {
                    localError = "Please enter a valid height in cm."
                    return@Button
                }
                if (w == null || w <= 0f) {
                    localError = "Please enter a valid weight in kg."
                    return@Button
                }
                if (email.isBlank() || firstName.isBlank() || lastName.isBlank()) {
                    localError = "Please fill in all fields."
                    return@Button
                }

                onSignUp(
                    email.trim(),
                    password,
                    firstName.trim(),
                    lastName.trim(),
                    h,
                    w,
                    sex,
                    frequency
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign up")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onSwitchToLogin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Already have an account? Log in")
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
