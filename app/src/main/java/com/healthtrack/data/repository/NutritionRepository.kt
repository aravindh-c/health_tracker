package com.healthtrack.data.repository

import com.healthtrack.data.api.ClaudeLlmService
import com.healthtrack.data.api.LlmService
import com.healthtrack.data.api.OpenAiLlmService
import com.healthtrack.data.api.SheetsService
import com.healthtrack.data.model.DailyScore
import com.healthtrack.data.model.LlmProvider
import com.healthtrack.data.model.MealHistory
import com.healthtrack.data.model.NutrientData
import com.healthtrack.data.model.UserProfile
import com.healthtrack.utils.ScoreCalculator
import com.healthtrack.utils.SecurePrefs
import java.time.LocalDate

class NutritionRepository(private val securePrefs: SecurePrefs) {

    private val sheetsService = SheetsService()

    private fun getLlmService(): LlmService {
        val key = securePrefs.getActiveApiKey()
        return when (securePrefs.llmProvider) {
            LlmProvider.OPENAI -> OpenAiLlmService(key)
            LlmProvider.CLAUDE -> ClaudeLlmService(key)
        }
    }

    suspend fun logMeal(
        userId: String,
        mealType: String,
        foodText: String,
        userProfile: UserProfile
    ): Result<NutrientData> {
        return try {
            val today = LocalDate.now().toString()

            // 1. Save food text to sheet
            sheetsService.logFood(today, userId, mealType, foodText)

            // 2. Estimate nutrients via LLM
            val nutrients = getLlmService().estimateNutrients(foodText, userProfile)

            // 3. Save nutrient data to sheet
            sheetsService.logNutrients(today, userId, mealType, nutrients)

            Result.success(nutrients)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDailyReport(userId: String): Result<NutrientData> {
        return try {
            val today = LocalDate.now().toString()
            val data = sheetsService.getDailyReport(userId, today)
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDailyScore(userId: String, userProfile: UserProfile): Result<DailyScore> {
        return try {
            val today = LocalDate.now().toString()
            val consumed = sheetsService.getDailyReport(userId, today)
            val score = ScoreCalculator.calculate(consumed, userProfile.targets)
            Result.success(score)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFoodHistory(userId: String): Result<List<MealHistory>> {
        return try {
            val today = LocalDate.now().toString()
            val history = sheetsService.getFoodHistory(userId, today)
            Result.success(history)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMealSuggestions(userId: String, userProfile: UserProfile): Result<String> {
        return try {
            val today = LocalDate.now().toString()
            val consumed = sheetsService.getDailyReport(userId, today)
            val suggestions = getLlmService().getMealSuggestions(consumed, userProfile.targets, userProfile)
            Result.success(suggestions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
