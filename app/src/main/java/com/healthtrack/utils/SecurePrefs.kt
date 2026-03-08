package com.healthtrack.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.healthtrack.data.model.LlmProvider

class SecurePrefs(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "health_track_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_CLAUDE_API_KEY = "claude_api_key"
        private const val KEY_LLM_PROVIDER = "llm_provider"
        private const val KEY_SETUP_DONE = "setup_done"
        private const val KEY_LAST_ACTIVE_USER = "last_active_user_id"
        private const val KEY_NOTIFICATIONS_SCHEDULED = "notifications_scheduled"
    }

    var openAiKey: String
        get() = prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENAI_API_KEY, value).apply()

    var claudeKey: String
        get() = prefs.getString(KEY_CLAUDE_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLAUDE_API_KEY, value).apply()

    var llmProvider: LlmProvider
        get() {
            val name = prefs.getString(KEY_LLM_PROVIDER, LlmProvider.OPENAI.name) ?: LlmProvider.OPENAI.name
            return LlmProvider.valueOf(name)
        }
        set(value) = prefs.edit().putString(KEY_LLM_PROVIDER, value.name).apply()

    var isSetupDone: Boolean
        get() = prefs.getBoolean(KEY_SETUP_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_DONE, value).apply()

    var lastActiveUserId: String
        get() = prefs.getString(KEY_LAST_ACTIVE_USER, "aravindh") ?: "aravindh"
        set(value) = prefs.edit().putString(KEY_LAST_ACTIVE_USER, value).apply()

    var notificationsScheduled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_SCHEDULED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_SCHEDULED, value).apply()

    fun getActiveApiKey(): String = when (llmProvider) {
        LlmProvider.OPENAI -> openAiKey
        LlmProvider.CLAUDE -> claudeKey
    }

    fun hasValidKey(): Boolean = getActiveApiKey().isNotBlank()
}
