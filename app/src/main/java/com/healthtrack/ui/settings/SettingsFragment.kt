package com.healthtrack.ui.settings

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.healthtrack.data.model.LlmProvider
import com.healthtrack.databinding.FragmentSettingsBinding
import com.healthtrack.ui.MainActivity
import com.healthtrack.utils.FoodPreferenceManager
import com.healthtrack.utils.SecurePrefs
import java.io.File

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var securePrefs: SecurePrefs
    private lateinit var userId: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        securePrefs = SecurePrefs(requireContext())
        userId = (activity as? MainActivity)?.userId ?: "aravindh"

        // Show masked keys (don't expose full key in UI)
        binding.etOpenAiKey.setText(securePrefs.openAiKey.takeMasked())
        binding.etClaudeKey.setText(securePrefs.claudeKey.takeMasked())

        val providers = LlmProvider.values().map { it.display }
        binding.spinnerProvider.setSimpleItems(providers.toTypedArray())
        binding.spinnerProvider.setText(securePrefs.llmProvider.display, false)

        binding.btnSave.setOnClickListener {
            authenticateAndSave(providers)
        }

        binding.btnExportPreferences.setOnClickListener {
            exportFoodPreferences()
        }
    }

    private fun exportFoodPreferences() {
        val prefManager = FoodPreferenceManager(requireContext())
        val json = prefManager.loadPreferencesJson(userId)
        val fileName = "food_preferences_${userId}_export.json"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ — use MediaStore (no permission needed)
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = requireContext().contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                )
                uri?.let {
                    requireContext().contentResolver.openOutputStream(it)?.use { out ->
                        out.write(json.toByteArray())
                    }
                    Snackbar.make(binding.root, "Exported to Downloads/$fileName", Snackbar.LENGTH_LONG).show()
                } ?: Snackbar.make(binding.root, "Export failed", Snackbar.LENGTH_SHORT).show()
            } else {
                // Android 9 and below — write directly to Downloads dir
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloadsDir.mkdirs()
                File(downloadsDir, fileName).writeText(json)
                Snackbar.make(binding.root, "Exported to Downloads/$fileName", Snackbar.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Export failed: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun authenticateAndSave(providers: List<String>) {
        val biometricManager = BiometricManager.from(requireContext())
        val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // No biometric/PIN set up on device — save directly
            saveSettings(providers)
            return
        }

        val executor = ContextCompat.getMainExecutor(requireContext())
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                saveSettings(providers)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Snackbar.make(binding.root, "Authentication cancelled", Snackbar.LENGTH_SHORT).show()
            }

            override fun onAuthenticationFailed() {
                Snackbar.make(binding.root, "Authentication failed. Try again.", Snackbar.LENGTH_SHORT).show()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm your identity")
            .setSubtitle("Authentication required to change API settings")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun saveSettings(providers: List<String>) {
        val newOpenAiKey = binding.etOpenAiKey.text?.toString()?.trim() ?: ""
        val newClaudeKey = binding.etClaudeKey.text?.toString()?.trim() ?: ""
        val providerIndex = providers.indexOf(binding.spinnerProvider.text.toString())
        val provider = if (providerIndex >= 0) LlmProvider.values()[providerIndex] else LlmProvider.OPENAI

        // Only update key if user actually typed a new one (not the masked placeholder)
        if (!newOpenAiKey.contains("•")) securePrefs.openAiKey = newOpenAiKey
        if (!newClaudeKey.contains("•")) securePrefs.claudeKey = newClaudeKey
        securePrefs.llmProvider = provider

        Snackbar.make(binding.root, "Saved. Using ${provider.display}.", Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Show last 4 chars, mask the rest: "sk-...abcd"
    private fun String.takeMasked(): String {
        if (length <= 4) return this
        return "••••••••••" + takeLast(4)
    }
}
