package com.healthtrack.data.api

import com.healthtrack.data.model.NutrientData
import com.healthtrack.data.model.NutrientTargets
import com.healthtrack.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenAiLlmService(private val apiKey: String) : LlmService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun estimateNutrients(foodText: String, userProfile: UserProfile): NutrientData {
        return withContext(Dispatchers.IO) {
            val prompt = """
User medical context:
${buildMedicalContext(userProfile)}

Food eaten:
$foodText

Estimate the nutrients. Return ONLY valid JSON, no extra text:
{
  "protein_g": <number>,
  "fat_g": <number>,
  "fiber_g": <number>,
  "carbs_simple_g": <number>,
  "carbs_complex_g": <number>,
  "calories_kcal": <number>
}
            """.trimIndent()
            parseNutrientResponse(callOpenAI(prompt))
        }
    }

    override suspend fun getMealSuggestions(
        consumed: NutrientData,
        targets: NutrientTargets,
        userProfile: UserProfile,
        loggedMealTypes: List<String>,
        remainingMealTypes: List<String>,
        preferencesJson: String,
        weeklyContext: String,
        previousSuggestions: List<String>
    ): String {
        return withContext(Dispatchers.IO) {
            val remaining = if (remainingMealTypes.isEmpty()) "All meals done for today - suggest a healthy bedtime snack if needed"
            else remainingMealTypes.joinToString(", ")
            val logged = if (loggedMealTypes.isEmpty()) "None yet" else loggedMealTypes.joinToString(", ")

            val weeklySection = if (weeklyContext.isNotBlank()) "\n== 7-Day Pattern ==\n$weeklyContext\n" else ""
            val prevSection = if (previousSuggestions.isNotEmpty())
                "\n== Avoid Repeating These Recent Suggestions ==\n${previousSuggestions.joinToString("\n---\n")}\n"
            else ""

            val prompt = """
You are a medical nutrition assistant helping an Indian user plan meals.

== Medical Profile ==
${buildMedicalContext(userProfile)}

== Today's Nutrition Progress ==
- Protein: ${consumed.protein_g.toInt()}g / ${targets.protein_g.toInt()}g target
- Fat: ${consumed.fat_g.toInt()}g / ${targets.fat_g.toInt()}g target
- Fiber: ${consumed.fiber_g.toInt()}g / ${targets.fiber_g.toInt()}g target
- Calories: ${consumed.calories_kcal.toInt()} / ${targets.calories_kcal.toInt()} kcal
- Simple Carbs: ${consumed.carbs_simple_g.toInt()}g (max ${targets.simple_carbs_max_g.toInt()}g)
- Complex Carbs: ${consumed.carbs_complex_g.toInt()}g
$weeklySection
== Meals Today ==
Already logged: $logged
Remaining meals to plan: $remaining

== Food Preferences ==
$preferencesJson
$prevSection
== Instructions ==
- Suggest ONE meal option per remaining meal type listed above
- Prefer south Indian / Indian home food style from the preferences
- Use the 7-day pattern to address recurring deficiencies (e.g. consistently low fiber → add high-fiber foods)
- Do NOT suggest meals already listed in the "Avoid Repeating" section above
- Strictly avoid foods that worsen prediabetes, fatty liver, elevated uric acid, or kidney stones
- Format EXACTLY like this:

**[Meal Type]: [Meal Name]**
• [food item] — [health reason]
• [food item] — [health reason]

Max 3 bullet points per suggestion. Keep it practical and home-cook friendly.
            """.trimIndent()

            callOpenAI(prompt, maxTokens = 1000)
        }
    }

    override suspend fun isFoodHealthyForUser(food: String, userProfile: UserProfile): Boolean {
        return withContext(Dispatchers.IO) {
            val conditions = userProfile.medical_conditions.joinToString(", ")
            val prompt = """Medical conditions: $conditions
Is "$food" healthy and safe to eat regularly for someone with these conditions?
Reply ONLY: YES or NO"""
            callOpenAI(prompt, maxTokens = 5).trim().uppercase().startsWith("YES")
        }
    }

    private fun callOpenAI(prompt: String, maxTokens: Int = 600): String {
        val body = JSONObject().apply {
            put("model", "gpt-4o")
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
            put("temperature", 0.4)
            put("max_tokens", maxTokens)
        }
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response from OpenAI")
        if (!response.isSuccessful) {
            val errorMsg = try { JSONObject(responseBody).getJSONObject("error").getString("message") } catch (e: Exception) { responseBody }
            throw Exception("OpenAI error: $errorMsg")
        }
        return JSONObject(responseBody).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    }

    private fun parseNutrientResponse(response: String): NutrientData {
        val start = response.indexOf('{'); val end = response.lastIndexOf('}')
        val json = JSONObject(if (start >= 0 && end > start) response.substring(start, end + 1) else response)
        return NutrientData(
            protein_g = json.optDouble("protein_g", 0.0), fat_g = json.optDouble("fat_g", 0.0),
            fiber_g = json.optDouble("fiber_g", 0.0), carbs_simple_g = json.optDouble("carbs_simple_g", 0.0),
            carbs_complex_g = json.optDouble("carbs_complex_g", 0.0), calories_kcal = json.optDouble("calories_kcal", 0.0)
        )
    }

    private fun buildMedicalContext(profile: UserProfile): String {
        val conditions = if (profile.medical_conditions.isEmpty()) "None" else profile.medical_conditions.joinToString(", ")
        val reports = if (profile.latest_reports.isEmpty()) "None" else profile.latest_reports.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        val sb = StringBuilder()
        sb.append("Name: ${profile.display_name}, Age: ${profile.age}, Height: ${profile.height_cm}cm, Weight: ${profile.weight_kg}kg\n")
        sb.append("Medical conditions: $conditions\n")
        sb.append("Lab reports: $reports\n")
        sb.append("Goals: ${profile.health_goals.joinToString(", ")}")
        profile.reported_symptoms?.let { sb.append("\nSymptoms: ${it.joinToString(", ")}") }
        profile.risk_flags?.let { sb.append("\nRisk flags: ${it.joinToString("; ")}") }
        return sb.toString()
    }
}
