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

class ClaudeLlmService(private val apiKey: String) : LlmService {

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

Task:
Estimate the nutritional values for the food eaten.

Rules:
1. Interpret natural descriptions like "little rice", "small bowl", "2 idli", etc.
2. Estimate reasonable portion sizes if exact quantity is missing.
3. Classify carbohydrates as:
   - carbs_simple_g: sugars or rapidly absorbed carbs (fruit sugars, added sugar, sweets).
   - carbs_complex_g: starch-based carbs such as rice, wheat, oats, millets, lentils, idli, dosa, chapathi.
4. Fiber should represent dietary fiber from grains, vegetables, legumes, etc.
5. If food contains both types of carbs, split values accordingly.


Return ONLY valid JSON with no explanation or extra text:
{
  "protein_g": <number>,
  "fat_g": <number>,
  "fiber_g": <number>,
  "carbs_simple_g": <number>,
  "carbs_complex_g": <number>,
  "calories_kcal": <number>
}
            """.trimIndent()

            val response = callClaude(prompt)
            parseNutrientResponse(response)
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
            val remaining = if (remainingMealTypes.isEmpty()) "All meals done - suggest healthy bedtime snack if needed"
            else remainingMealTypes.joinToString(", ")
            val logged = if (loggedMealTypes.isEmpty()) "None yet" else loggedMealTypes.joinToString(", ")

            val weeklySection = if (weeklyContext.isNotBlank()) "\n== 7-Day Pattern ==\n$weeklyContext\n" else ""
            val prevSection = if (previousSuggestions.isNotEmpty())
                "\n== Avoid Repeating These Recent Suggestions ==\n${previousSuggestions.joinToString("\n---\n")}\n"
            else ""

            val remainingCal = (targets.calories_kcal - consumed.calories_kcal).coerceAtLeast(0.0).toInt()
            val remainingProtein = (targets.protein_g - consumed.protein_g).coerceAtLeast(0.0).toInt()
            val remainingFiber = (targets.fiber_g - consumed.fiber_g).coerceAtLeast(0.0).toInt()

            val prompt = """
You are a medical nutrition assistant helping an Indian user plan meals.

== Medical Profile ==
${buildMedicalContext(userProfile)}

== Today's Nutrition Progress ==
- Calories: ${consumed.calories_kcal.toInt()} / ${targets.calories_kcal.toInt()} kcal → $remainingCal kcal remaining
- Protein: ${consumed.protein_g.toInt()}g / ${targets.protein_g.toInt()}g → $remainingProtein g remaining
- Fiber: ${consumed.fiber_g.toInt()}g / ${targets.fiber_g.toInt()}g → $remainingFiber g remaining
- Fat: ${consumed.fat_g.toInt()}g / ${targets.fat_g.toInt()}g target
- Simple Carbs: ${consumed.carbs_simple_g.toInt()}g (max ${targets.simple_carbs_max_g.toInt()}g)
$weeklySection
== Meals Today ==
Already logged: $logged
Remaining meals to plan: $remaining

== Food Preferences ==
$preferencesJson
$prevSection
== Goal: Close the Day Right ==
Start with: "You need ~${remainingCal} kcal, ${remainingProtein}g protein, ${remainingFiber}g fiber more today."
Suggestions MUST collectively cover these remaining needs.

== Instructions ==
- Suggest ONE meal per remaining meal type above
- Prefer south Indian / Indian home food style
- Use the 7-day pattern to address recurring deficiencies
- Do NOT repeat meals from "Avoid Repeating" section
- Avoid foods worsening prediabetes, fatty liver, elevated uric acid, or kidney stones
- Format EXACTLY:

**[Meal Type]: [Meal Name]**
• [food item] — [health reason + rough cal/protein]
• [food item] — [health reason]

Max 3 bullets per meal. Keep it practical and home-cook friendly.
            """.trimIndent()

            callClaude(prompt, maxTokens = 1000)
        }
    }

    override suspend fun getInsights(
        mealNutrients: NutrientData,
        todayTotal: NutrientData,
        userProfile: UserProfile
    ): String {
        return withContext(Dispatchers.IO) {
            val t = userProfile.targets
            fun pct(v: Double, max: Double) = if (max > 0) ((v / max) * 100).toInt() else 0
            val prompt = """
You are a concise medical nutrition assistant.

== Medical Profile ==
${buildMedicalContext(userProfile)}

== This Meal ==
Protein: ${mealNutrients.protein_g.toInt()}g | Fat: ${mealNutrients.fat_g.toInt()}g | Fiber: ${mealNutrients.fiber_g.toInt()}g | Calories: ${mealNutrients.calories_kcal.toInt()} kcal | Simple Carbs: ${mealNutrients.carbs_simple_g.toInt()}g

== Today's Running Total (after this meal) ==
- Calories: ${todayTotal.calories_kcal.toInt()} / ${t.calories_kcal.toInt()} kcal (${pct(todayTotal.calories_kcal, t.calories_kcal)}%)
- Protein: ${todayTotal.protein_g.toInt()}g / ${t.protein_g.toInt()}g (${pct(todayTotal.protein_g, t.protein_g)}%)
- Fiber: ${todayTotal.fiber_g.toInt()}g / ${t.fiber_g.toInt()}g (${pct(todayTotal.fiber_g, t.fiber_g)}%)
- Simple Carbs: ${todayTotal.carbs_simple_g.toInt()}g / max ${t.simple_carbs_max_g.toInt()}g

Write exactly 2-3 plain-text sentences (no markdown, no bullets):
1. Celebrate one win OR flag the biggest concern for this meal.
2. State the remaining calorie/protein budget for the day.
3. Give ONE specific tip for the next meal.
Keep it under 45 words total.
            """.trimIndent()
            callClaude(prompt, maxTokens = 120)
        }
    }

    override suspend fun isFoodHealthyForUser(food: String, userProfile: UserProfile): Boolean {
        return withContext(Dispatchers.IO) {
            val conditions = userProfile.medical_conditions.joinToString(", ")
            val prompt = "Medical conditions: $conditions\nIs \"$food\" healthy for someone with these conditions?\nReply ONLY: YES or NO"
            callClaude(prompt, maxTokens = 5).trim().uppercase().startsWith("YES")
        }
    }

    private fun callClaude(prompt: String, maxTokens: Int = 600): String {
        val body = JSONObject().apply {
            put("model", "claude-sonnet-4-6")
            put("max_tokens", maxTokens)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response from Claude")

        if (!response.isSuccessful) {
            val errorMsg = try {
                JSONObject(responseBody).getJSONObject("error").getString("message")
            } catch (e: Exception) {
                responseBody
            }
            throw Exception("Claude error: $errorMsg")
        }

        return JSONObject(responseBody)
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
            .trim()
    }

    private fun parseNutrientResponse(response: String): NutrientData {
        val jsonStr = extractJson(response)
        val json = JSONObject(jsonStr)
        return NutrientData(
            protein_g = json.optDouble("protein_g", 0.0),
            fat_g = json.optDouble("fat_g", 0.0),
            fiber_g = json.optDouble("fiber_g", 0.0),
            carbs_simple_g = json.optDouble("carbs_simple_g", 0.0),
            carbs_complex_g = json.optDouble("carbs_complex_g", 0.0),
            calories_kcal = json.optDouble("calories_kcal", 0.0)
        )
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }

    private fun buildMedicalContext(profile: UserProfile): String {
        val conditions = if (profile.medical_conditions.isEmpty()) "None"
        else profile.medical_conditions.joinToString(", ")

        val reports = if (profile.latest_reports.isEmpty()) "None"
        else profile.latest_reports.entries.joinToString(", ") { "${it.key}: ${it.value}" }

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
