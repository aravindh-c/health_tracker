package com.healthtrack.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File

class FoodPreferenceManager(private val context: Context) {

    private val gson = Gson()

    // Per-user file paths in internal storage (survives app updates, deleted on uninstall)
    private fun prefsFile(userId: String) = File(context.filesDir, "food_preferences_$userId.json")
    private fun freqFile(userId: String) = File(context.filesDir, "food_frequency_$userId.json")

    // ── Load preference JSON for a specific user ─────────────────────────────
    // Priority: internal storage (edited/learned) → assets (default)
    fun loadPreferencesJson(userId: String): String {
        val userFile = prefsFile(userId)
        return when {
            userFile.exists() -> userFile.readText()
            assetExists("food_preferences_$userId.json") ->
                context.assets.open("food_preferences_$userId.json").bufferedReader().readText()
            else ->
                // Fallback to generic file for any new user
                context.assets.open("food_preferences_aravindh.json").bufferedReader().readText()
        }
    }

    // ── Save updated preferences for a user ──────────────────────────────────
    fun savePreferences(userId: String, json: String) {
        prefsFile(userId).writeText(json)
    }

    // ── Track food frequency per user ────────────────────────────────────────
    fun trackFoodText(userId: String, foodText: String) {
        val counts = loadFrequency(userId).toMutableMap()
        extractFoodTokens(foodText).forEach { token ->
            counts[token] = (counts[token] ?: 0) + 1
        }
        freqFile(userId).writeText(gson.toJson(counts))
    }

    // Returns foods logged > threshold times not already in this user's preferences
    fun getNewFrequentFoods(userId: String, threshold: Int = 5): List<String> {
        val counts = loadFrequency(userId)
        val existing = getExistingPreferenceFoods(userId)
        return counts.filter { (food, count) ->
            count > threshold && !existing.any { it.equals(food, ignoreCase = true) }
        }.keys.toList()
    }

    // ── Add a healthy food to user's preferences ──────────────────────────────
    fun addFoodToPreferences(userId: String, food: String, category: String = "preferred_foods") {
        val json = loadPreferencesJson(userId)
        val obj = JsonParser.parseString(json).asJsonObject
        val array = obj.getAsJsonArray(category) ?: return
        val exists = (0 until array.size()).any { array[it].asString.equals(food, ignoreCase = true) }
        if (!exists) {
            array.add(food)
            savePreferences(userId, gson.toJson(obj))
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────
    private fun assetExists(filename: String): Boolean {
        return try { context.assets.open(filename).close(); true } catch (e: Exception) { false }
    }

    private fun loadFrequency(userId: String): Map<String, Int> {
        val file = freqFile(userId)
        if (!file.exists()) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun getExistingPreferenceFoods(userId: String): List<String> {
        return try {
            val obj = JsonParser.parseString(loadPreferencesJson(userId)).asJsonObject
            listOf("preferred_foods", "snack_preferences", "protein_sources",
                "carb_sources", "vegetable_choices", "fruit_preferences").flatMap { key ->
                val arr = obj.getAsJsonArray(key) ?: return@flatMap emptyList()
                (0 until arr.size()).map { arr[it].asString }
            }
        } catch (e: Exception) { emptyList() }
    }

    // Simple food tokenizer: splits on +, ,, and/with, removes quantities
    private fun extractFoodTokens(text: String): List<String> {
        return text
            .lowercase()
            .split(Regex("[+,&]|\\band\\b|\\bwith\\b"))
            .map { it.trim() }
            .map { it.replace(Regex("^\\d+\\s*(g|kg|ml|l|cup|bowl|piece|pieces|small|medium|large|little|half)?\\s*"), "") }
            .map { it.trim() }
            .filter { it.length > 2 }
    }
}
