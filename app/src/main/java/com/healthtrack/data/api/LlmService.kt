package com.healthtrack.data.api

import com.healthtrack.data.model.NutrientData
import com.healthtrack.data.model.NutrientTargets
import com.healthtrack.data.model.UserProfile

interface LlmService {
    suspend fun estimateNutrients(foodText: String, userProfile: UserProfile): NutrientData

    suspend fun getMealSuggestions(
        consumed: NutrientData,
        targets: NutrientTargets,
        userProfile: UserProfile,
        loggedMealTypes: List<String>,         // e.g. ["Breakfast", "Lunch"]
        remainingMealTypes: List<String>,      // e.g. ["Evening Snack", "Dinner"]
        preferencesJson: String,               // raw food_preferences.json content
        weeklyContext: String = "",            // avg nutrient summary for last 7 days
        previousSuggestions: List<String> = emptyList() // recent suggestions to avoid repeating
    ): String

    // 2-3 sentence insight after a meal is logged — what went well, any concern, one tip
    suspend fun getInsights(
        mealNutrients: NutrientData,
        todayTotal: NutrientData,
        userProfile: UserProfile
    ): String

    // Returns true if the food is healthy and compatible with user's medical conditions
    suspend fun isFoodHealthyForUser(food: String, userProfile: UserProfile): Boolean
}
