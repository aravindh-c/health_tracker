package com.healthtrack.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Persists recent meal suggestions per user so the LLM can avoid repeating them.
 * Stores the last 5 suggestion texts (truncated to 300 chars each) in filesDir.
 */
class SuggestionMemory(private val context: Context) {

    private val gson = Gson()
    private fun file(userId: String) = File(context.filesDir, "suggestion_memory_$userId.json")

    fun getRecent(userId: String, count: Int = 3): List<String> {
        val f = file(userId)
        if (!f.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(f.readText(), type)?.takeLast(count) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(userId: String, suggestion: String) {
        val current = getRecent(userId, 10).toMutableList()
        current.add(suggestion.take(300))
        val trimmed = if (current.size > 5) current.takeLast(5) else current
        file(userId).writeText(gson.toJson(trimmed))
    }
}
