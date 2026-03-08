package com.healthtrack.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.healthtrack.data.model.LlmProvider
import com.healthtrack.databinding.FragmentSettingsBinding
import com.healthtrack.utils.SecurePrefs

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var securePrefs: SecurePrefs

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        securePrefs = SecurePrefs(requireContext())

        // Set current values
        binding.etOpenAiKey.setText(securePrefs.openAiKey)
        binding.etClaudeKey.setText(securePrefs.claudeKey)

        val providers = LlmProvider.values().map { it.display }
        binding.spinnerProvider.setSimpleItems(providers.toTypedArray())
        binding.spinnerProvider.setText(securePrefs.llmProvider.display, false)

        binding.btnSave.setOnClickListener {
            val openAiKey = binding.etOpenAiKey.text?.toString()?.trim() ?: ""
            val claudeKey = binding.etClaudeKey.text?.toString()?.trim() ?: ""
            val providerIndex = providers.indexOf(binding.spinnerProvider.text.toString())
            val provider = if (providerIndex >= 0) LlmProvider.values()[providerIndex] else LlmProvider.OPENAI

            securePrefs.openAiKey = openAiKey
            securePrefs.claudeKey = claudeKey
            securePrefs.llmProvider = provider

            Snackbar.make(binding.root, "Settings saved. Using ${provider.display}.", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
