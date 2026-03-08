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

Estimate the nutrients for this food. Return ONLY valid JSON with no extra text:
{
  "protein_g": <number>,
  "fat_g": <number>,
  "fiber_g": <number>,
  "carbs_simple_g": <number>,
  "carbs_complex_g": <number>,
  "calories_kcal": <number>
}
            """.trimIndent()

            val response = callOpenAI(prompt)
            parseNutrientResponse(response)
        }
    }

    override suspend fun getMealSuggestions(
        consumed: NutrientData,
        targets: NutrientTargets,
        userProfile: UserProfile
    ): String {
        return withContext(Dispatchers.IO) {
            val prompt = """
User medical context:
${buildMedicalContext(userProfile)}

Nutrients consumed today:
- Protein: ${consumed.protein_g.toInt()}g / target ${targets.protein_g.toInt()}g
- Fat: ${consumed.fat_g.toInt()}g / target ${targets.fat_g.toInt()}g
- Fiber: ${consumed.fiber_g.toInt()}g / target ${targets.fiber_g.toInt()}g
- Calories: ${consumed.calories_kcal.toInt()} kcal / target ${targets.calories_kcal.toInt()} kcal
- Simple Carbs: ${consumed.carbs_simple_g.toInt()}g / max ${targets.simple_carbs_max_g.toInt()}g
- Complex Carbs: ${consumed.carbs_complex_g.toInt()}g

Based on remaining daily targets and the user's medical conditions, suggest 2-3 practical meal options for the next meal. Keep suggestions concise. Respect all medical restrictions.
            """.trimIndent()

            callOpenAI(prompt)
        }
    }

    private fun callOpenAI(prompt: String): String {
        val body = JSONObject().apply {
            put("model", "gpt-4o")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.3)
            put("max_tokens", 600)
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
            val errorMsg = try {
                JSONObject(responseBody).getJSONObject("error").getString("message")
            } catch (e: Exception) {
                responseBody
            }
            throw Exception("OpenAI error: $errorMsg")
        }

        return JSONObject(responseBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
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

        return """
Name: ${profile.display_name}
Age: ${profile.age}, Height: ${profile.height_cm}cm, Weight: ${profile.weight_kg}kg
Medical conditions: $conditions
Latest reports: $reports
Health goals: ${profile.health_goals.joinToString(", ")}
        """.trimIndent()
    }
}
