package com.team11.smartgym.library

data class WorkoutTemplate(
    val id: Long,
    val name: String,
    val category: String,
    val difficulty: String,
    val durationMinutes: Int,
    val description: String
)

object WorkoutLibraryData {

    val templates: List<WorkoutTemplate> = listOf(
        WorkoutTemplate(
            id = 1L,
            name = "Knee Rehab – Easy",
            category = "Rehab",
            difficulty = "Easy",
            durationMinutes = 10,
            description = "Gentle warm-up with low intensity walking and controlled movements. Ideal for early-stage recovery."
        ),
        WorkoutTemplate(
            id = 2L,
            name = "Knee Stability – Intermediate",
            category = "Rehab",
            difficulty = "Medium",
            durationMinutes = 20,
            description = "Focus on stability and controlled load with moderate pacing. Great for building joint confidence."
        ),
        WorkoutTemplate(
            id = 3L,
            name = "Cardio – Light",
            category = "Cardio",
            difficulty = "Easy",
            durationMinutes = 15,
            description = "Low-impact cardio aimed at gently elevating heart rate while staying joint-friendly."
        ),
        WorkoutTemplate(
            id = 4L,
            name = "Cardio – Endurance",
            category = "Cardio",
            difficulty = "Medium",
            durationMinutes = 30,
            description = "Sustained mid-intensity session to build endurance and track heart rate and recovery."
        ),
        WorkoutTemplate(
            id = 5L,
            name = "Intervals – HR Focus",
            category = "Cardio",
            difficulty = "Hard",
            durationMinutes = 25,
            description = "Intervals designed around heart-rate zones. Great once you’re comfortable with steady-state sessions."
        ),
        WorkoutTemplate(
            id = 6L,
            name = "Recovery Day Walk",
            category = "Recovery",
            difficulty = "Easy",
            durationMinutes = 20,
            description = "Easy walk to promote blood flow, recovery, and consistent habit formation."
        )
    )

    val categories: List<String> =
        listOf("All") + templates.map { it.category }.distinct()

    val difficulties: List<String> =
        listOf("All") + templates.map { it.difficulty }.distinct()
}
