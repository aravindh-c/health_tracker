package com.healthtrack.data.api

import com.healthtrack.data.model.NutrientData
import com.healthtrack.data.model.NutrientTargets
import com.healthtrack.data.model.UserProfile

interface LlmService {
    suspend fun estimateNutrients(foodText: String, userProfile: UserProfile): NutrientData
    suspend fun getMealSuggestions(
        consumed: NutrientData,
        targets: NutrientTargets,
        userProfile: UserProfile
    ): String
}
