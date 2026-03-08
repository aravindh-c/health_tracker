package com.healthtrack.data.repository

import android.content.Context
import com.healthtrack.data.api.ClaudeLlmService
import com.healthtrack.data.api.LlmService
import com.healthtrack.data.api.OpenAiLlmService
import com.healthtrack.data.api.SheetsService
import com.healthtrack.data.model.DailyNutrientPoint
import com.healthtrack.data.model.DailyScore
import com.healthtrack.data.model.LlmProvider
import com.healthtrack.data.model.MealHistory
import com.healthtrack.data.model.MealType
import com.healthtrack.data.model.NutrientData
import com.healthtrack.data.model.UserProfile
import com.healthtrack.utils.FoodPreferenceManager
import com.healthtrack.utils.ScoreCalculator
import com.healthtrack.utils.SecurePrefs
import com.healthtrack.utils.SuggestionMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

class NutritionRepository(
    private val securePrefs: SecurePrefs,
    private val context: Context
) {
    private val sheetsService = SheetsService()
    private val prefManager = FoodPreferenceManager(context)
    private val suggestionMemory = SuggestionMemory(context)

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
        userProfile: UserProfile,
        date: String = LocalDate.now().toString()
    ): Result<NutrientData> {
        return try {
            sheetsService.logFood(date, userId, mealType, foodText)
            val nutrients = getLlmService().estimateNutrients(foodText, userProfile)
            sheetsService.logNutrients(date, userId, mealType, nutrients)

            withContext(Dispatchers.IO) {
                prefManager.trackFoodText(userId, foodText)
            }

            Result.success(nutrients)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDailyReport(userId: String): Result<NutrientData> {
        return try {
            val data = sheetsService.getDailyReport(userId, LocalDate.now().toString())
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDailyScore(userId: String, userProfile: UserProfile): Result<DailyScore> {
        return try {
            val consumed = sheetsService.getDailyReport(userId, LocalDate.now().toString())
            Result.success(ScoreCalculator.calculate(consumed, userProfile.targets))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFoodHistory(userId: String): Result<List<MealHistory>> {
        return try {
            val history = sheetsService.getFoodHistory(userId, LocalDate.now().toString())
            Result.success(history)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Monthly data: fetches each day of the given month in parallel ─────────
    suspend fun getMonthlyData(userId: String, yearMonth: YearMonth): Result<List<DailyNutrientPoint>> {
        return try {
            val daysInMonth = yearMonth.lengthOfMonth()
            val points = withContext(Dispatchers.IO) {
                (1..daysInMonth).map { day ->
                    async {
                        val date = yearMonth.atDay(day).toString()
                        try {
                            val nutrients = sheetsService.getDailyReport(userId, date)
                            DailyNutrientPoint(date, nutrients)
                        } catch (e: Exception) {
                            DailyNutrientPoint(date, NutrientData())
                        }
                    }
                }.awaitAll()
            }
            Result.success(points)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Weekly averages: last 7 days, for LLM memory context ─────────────────
    suspend fun getWeeklyAverages(userId: String): NutrientData {
        return try {
            val today = LocalDate.now()
            val days = (0..6).map { today.minusDays(it.toLong()).toString() }
            val dailyData = withContext(Dispatchers.IO) {
                days.map { date ->
                    async {
                        try { sheetsService.getDailyReport(userId, date) }
                        catch (e: Exception) { NutrientData() }
                    }
                }.awaitAll()
            }
            val nonEmpty = dailyData.filter { it.calories_kcal > 0 }
            if (nonEmpty.isEmpty()) return NutrientData()
            val count = nonEmpty.size.toDouble()
            NutrientData(
                protein_g = nonEmpty.sumOf { it.protein_g } / count,
                fat_g = nonEmpty.sumOf { it.fat_g } / count,
                fiber_g = nonEmpty.sumOf { it.fiber_g } / count,
                carbs_simple_g = nonEmpty.sumOf { it.carbs_simple_g } / count,
                carbs_complex_g = nonEmpty.sumOf { it.carbs_complex_g } / count,
                calories_kcal = nonEmpty.sumOf { it.calories_kcal } / count
            )
        } catch (e: Exception) {
            NutrientData()
        }
    }

    suspend fun getMealSuggestions(userId: String, userProfile: UserProfile): Result<String> {
        return try {
            val today = LocalDate.now().toString()
            val consumed = sheetsService.getDailyReport(userId, today)
            val history = sheetsService.getFoodHistory(userId, today)

            val loggedMealTypes = history.map { it.meal_type }.distinct()
            val allMealTypes = MealType.values()
                .filter { it != MealType.OTHER }
                .map { it.display }
            val remainingMealTypes = allMealTypes.filter { it !in loggedMealTypes }

            val prefsJson = prefManager.loadPreferencesJson(userId)

            // LLM memory: weekly pattern + previous suggestions
            val weekly = getWeeklyAverages(userId)
            val weeklyContext = if (weekly.calories_kcal > 0) buildWeeklyContext(weekly, userProfile) else ""
            val previousSuggestions = suggestionMemory.getRecent(userId, count = 3)

            val suggestions = getLlmService().getMealSuggestions(
                consumed = consumed,
                targets = userProfile.targets,
                userProfile = userProfile,
                loggedMealTypes = loggedMealTypes,
                remainingMealTypes = remainingMealTypes,
                preferencesJson = prefsJson,
                weeklyContext = weeklyContext,
                previousSuggestions = previousSuggestions
            )

            // Save to memory for future deduplication
            suggestionMemory.save(userId, suggestions)

            Result.success(suggestions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildWeeklyContext(avg: NutrientData, profile: UserProfile): String {
        val t = profile.targets
        fun pct(actual: Double, target: Double) = if (target > 0) ((actual / target) * 100).toInt() else 0
        return buildString {
            append("7-day daily averages (% of your target):\n")
            append("• Calories: ${avg.calories_kcal.toInt()} kcal (${pct(avg.calories_kcal, t.calories_kcal)}%)\n")
            append("• Protein: ${avg.protein_g.toInt()}g (${pct(avg.protein_g, t.protein_g)}%)\n")
            append("• Fiber: ${avg.fiber_g.toInt()}g (${pct(avg.fiber_g, t.fiber_g)}%)\n")
            append("• Fat: ${avg.fat_g.toInt()}g (${pct(avg.fat_g, t.fat_g)}%)\n")
            append("• Simple Carbs: ${avg.carbs_simple_g.toInt()}g (limit ${t.simple_carbs_max_g.toInt()}g)")
        }
    }

    // Auto-scale: check frequent new foods against LLM health filter and add to prefs
    suspend fun autoUpdateFoodPreferences(userProfile: UserProfile) {
        withContext(Dispatchers.IO) {
            val newFoods = prefManager.getNewFrequentFoods(userProfile.user_id, threshold = 5)
            if (newFoods.isEmpty()) return@withContext
            val llm = getLlmService()
            newFoods.forEach { food ->
                try {
                    val isHealthy = llm.isFoodHealthyForUser(food, userProfile)
                    if (isHealthy) prefManager.addFoodToPreferences(userProfile.user_id, food)
                } catch (e: Exception) {
                    // Silently skip — don't break the UI for background task
                }
            }
        }
    }
}
