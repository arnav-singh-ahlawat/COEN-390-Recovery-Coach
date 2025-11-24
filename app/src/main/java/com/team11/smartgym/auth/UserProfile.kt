package com.team11.smartgym.auth

enum class WorkoutFrequency {
    INACTIVE,          // 0–1 days/week
    MODERATELY_ACTIVE, // 2–4 days/week
    ACTIVE             // 5–7 days/week
}

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val heightCm: Int = 0,
    val weightKg: Float = 0f,
    val sex: String = "", // "male" or "female"
    val workoutFrequency: WorkoutFrequency = WorkoutFrequency.INACTIVE
)
