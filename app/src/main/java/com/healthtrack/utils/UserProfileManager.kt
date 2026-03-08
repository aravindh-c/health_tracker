package com.healthtrack.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.healthtrack.data.model.UserProfile
import java.io.File

class UserProfileManager(private val context: Context) {

    private val gson = Gson()
    private val profilesFile = File(context.filesDir, "user_profiles.json")

    fun loadProfiles(): List<UserProfile> {
        // Use internal storage copy if it exists (edited by user), else load from assets
        return if (profilesFile.exists()) {
            val json = profilesFile.readText()
            parseProfiles(json)
        } else {
            val json = context.assets.open("user_profiles.json").bufferedReader().readText()
            parseProfiles(json)
        }
    }

    fun getProfile(userId: String): UserProfile? {
        return loadProfiles().find { it.user_id == userId }
    }

    fun saveProfile(updated: UserProfile) {
        val profiles = loadProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.user_id == updated.user_id }
        if (index >= 0) profiles[index] = updated else profiles.add(updated)
        profilesFile.writeText(gson.toJson(profiles))
    }

    private fun parseProfiles(json: String): List<UserProfile> {
        val type = object : TypeToken<List<UserProfile>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}
