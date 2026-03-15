package com.healthtrack.data.api

import com.healthtrack.data.model.MealHistory
import com.healthtrack.data.model.NutrientData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SheetsService {

    companion object {
        const val APPS_SCRIPT_URL = "https://script.google.com/macros/s/AKfycbzoQa0dJAEll0KOTpihHkscA9UqiULoTleuu2Lip-TDUMME3yIQNWlsOHYYOjHgyCoQvg/exec"
    }

    // OkHttp client that follows POST redirects (Apps Script requires this)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(PostRedirectInterceptor())
        .build()

    suspend fun logFood(
        date: String,
        userId: String,
        mealType: String,
        foodText: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("action", "log_food")
                put("date", date)
                put("user_id", userId)
                put("meal_type", mealType)
                put("food_text", foodText)
            }
            post(body)
        }
    }

    suspend fun logNutrients(
        date: String,
        userId: String,
        mealType: String,
        nutrients: NutrientData
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("action", "log_nutrients")
                put("date", date)
                put("user_id", userId)
                put("meal_type", mealType)
                put("protein_g", nutrients.protein_g)
                put("fat_g", nutrients.fat_g)
                put("fiber_g", nutrients.fiber_g)
                put("carbs_simple_g", nutrients.carbs_simple_g)
                put("carbs_complex_g", nutrients.carbs_complex_g)
                put("calories_kcal", nutrients.calories_kcal)
            }
            post(body)
        }
    }

    suspend fun getDailyReport(userId: String, date: String): NutrientData {
        return withContext(Dispatchers.IO) {
            val url = "$APPS_SCRIPT_URL?action=daily_report&user_id=$userId&date=$date"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext NutrientData()

            val json = JSONObject(body)
            NutrientData(
                protein_g = json.optDouble("protein_g", 0.0),
                fat_g = json.optDouble("fat_g", 0.0),
                fiber_g = json.optDouble("fiber_g", 0.0),
                carbs_simple_g = json.optDouble("carbs_simple_g", 0.0),
                carbs_complex_g = json.optDouble("carbs_complex_g", 0.0),
                calories_kcal = json.optDouble("calories_kcal", 0.0)
            )
        }
    }

    suspend fun getFoodHistory(userId: String, date: String): List<MealHistory> {
        return withContext(Dispatchers.IO) {
            val url = "$APPS_SCRIPT_URL?action=food_history&user_id=$userId&date=$date"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            val json = JSONObject(body)
            val mealsArray: JSONArray = json.optJSONArray("meals") ?: return@withContext emptyList()

            (0 until mealsArray.length()).map { i ->
                val meal = mealsArray.getJSONObject(i)
                MealHistory(
                    meal_type = meal.optString("meal_type"),
                    food_text = meal.optString("food_text"),
                    timestamp = meal.optString("timestamp")
                )
            }
        }
    }

    suspend fun logWeight(date: String, userId: String, weightKg: Double): Boolean {
        return withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("action", "log_weight")
                put("date", date)
                put("user_id", userId)
                put("weight_kg", weightKg)
            }
            post(body)
        }
    }

    suspend fun getWeightHistory(userId: String): List<com.healthtrack.data.model.WeightEntry> {
        return withContext(Dispatchers.IO) {
            val url = "$APPS_SCRIPT_URL?action=weight_history&user_id=$userId"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val arr = json.optJSONArray("entries") ?: return@withContext emptyList()
            (0 until arr.length()).map { i ->
                val entry = arr.getJSONObject(i)
                com.healthtrack.data.model.WeightEntry(
                    date = entry.optString("date"),
                    weight_kg = entry.optDouble("weight_kg", 0.0)
                )
            }
        }
    }

    private fun post(body: JSONObject): Boolean {
        val request = Request.Builder()
            .url(APPS_SCRIPT_URL)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        return response.isSuccessful
    }
}

// Handles POST → redirect → re-POST (Apps Script redirects POST requests)
class PostRedirectInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        var response = chain.proceed(request)

        if (response.code in listOf(301, 302, 303, 307, 308) && request.method == "POST") {
            val location = response.header("Location") ?: return response
            response.close()
            val newRequest = request.newBuilder().url(location).build()
            response = chain.proceed(newRequest)
        }

        return response
    }
}
