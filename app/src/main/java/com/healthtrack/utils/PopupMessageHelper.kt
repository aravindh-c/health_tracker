package com.healthtrack.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.healthtrack.data.model.NutrientData
import com.healthtrack.data.model.UserProfile

/**
 * Generates context-aware popup messages shown immediately after food logging.
 * Messages are selected from static templates in notifications.json based on
 * the nutrient content of the logged meal vs the user's daily targets.
 */
class PopupMessageHelper(private val context: Context) {

    private val gson = Gson()
    private val messages: Map<String, List<String>> by lazy { loadMessages() }

    /**
     * Determine popup scenario based on single-meal nutrients vs daily targets.
     * Returns a personalized message string.
     */
    fun getMessage(mealNutrients: NutrientData, profile: UserProfile): String {
        val name = profile.display_name
        val targets = profile.targets

        // Thresholds for a single meal (assume ~3 main meals + snacks = use 30% of daily target per meal as reference)
        val mealCalTarget = targets.calories_kcal * 0.35

        val category = when {
            // Single meal provides >55% of daily calorie target → high calorie
            mealNutrients.calories_kcal > targets.calories_kcal * 0.55 ->
                "popup_correction"

            // Meal has very little protein (<5g)
            mealNutrients.protein_g < 5.0 ->
                "popup_protein_low"

            // Meal has good protein and reasonable calories
            mealNutrients.protein_g >= 15.0 && mealNutrients.calories_kcal <= mealCalTarget ->
                "popup_positive"

            // Default: encouragement
            else -> "popup_encouragement"
        }

        val raw = messages[category]?.random() ?: return "Well logged, $name!"
        return personalize(raw, name, profile.user_id)
    }

    private fun personalize(template: String, name: String, userId: String): String {
        val extras = when (userId) {
            "aravindh" -> mapOf("spouse_name" to "Deepa", "child_name" to "Aadhiran")
            "deepa" -> mapOf("spouse_name" to "Aravindh", "child_name" to "Aadhiran")
            else -> emptyMap()
        }
        return template
            .replace("{name}", name)
            .replace("{spouse_name}", extras["spouse_name"] ?: "your partner")
            .replace("{child_name}", extras["child_name"] ?: "your child")
    }

    private fun loadMessages(): Map<String, List<String>> {
        return try {
            val json = context.assets.open("notifications.json").bufferedReader().readText()
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
