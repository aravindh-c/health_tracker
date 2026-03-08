package com.healthtrack.data.model

data class UserProfile(
    val user_id: String,
    val display_name: String,
    val age: Int,
    val height_cm: Int,
    val weight_kg: Double,
    val medical_conditions: List<String>,
    val latest_reports: Map<String, String>,
    val health_goals: List<String>,
    val targets: NutrientTargets,
    // Optional extra fields — Gson ignores unknown, these are passed to LLM for richer context
    val reported_symptoms: List<String>? = null,
    val risk_flags: List<String>? = null
)

data class NutrientTargets(
    val protein_g: Double = 100.0,
    val fat_g: Double = 60.0,
    val fiber_g: Double = 30.0,
    val calories_kcal: Double = 1800.0,
    val simple_carbs_max_g: Double = 40.0
)

data class NutrientData(
    val protein_g: Double = 0.0,
    val fat_g: Double = 0.0,
    val fiber_g: Double = 0.0,
    val carbs_simple_g: Double = 0.0,
    val carbs_complex_g: Double = 0.0,
    val calories_kcal: Double = 0.0
) {
    operator fun plus(other: NutrientData) = NutrientData(
        protein_g = this.protein_g + other.protein_g,
        fat_g = this.fat_g + other.fat_g,
        fiber_g = this.fiber_g + other.fiber_g,
        carbs_simple_g = this.carbs_simple_g + other.carbs_simple_g,
        carbs_complex_g = this.carbs_complex_g + other.carbs_complex_g,
        calories_kcal = this.calories_kcal + other.calories_kcal
    )
}

data class MealHistory(
    val meal_type: String,
    val food_text: String,
    val timestamp: String
)

data class DailyScore(
    val overall: Double,
    val proteinScore: Double,
    val fiberScore: Double,
    val calorieScore: Double,
    val simpleCarbScore: Double,
    val fatScore: Double
)

enum class MealType(val display: String) {
    GYM_PRE_WORKOUT("Gym Pre-Workout"),
    BREAKFAST("Breakfast"),
    MID_DAY("Mid-Day"),
    LUNCH("Lunch"),
    EVENING_SNACK("Evening Snack"),
    DINNER("Dinner"),
    OTHER("Other");

    companion object {
        fun displayList() = values().map { it.display }
    }
}

enum class LlmProvider(val display: String) {
    OPENAI("OpenAI GPT-4o"),
    CLAUDE("Claude (Anthropic)")
}

data class DailyNutrientPoint(
    val date: String,          // "2026-03-08"
    val nutrients: NutrientData
)
