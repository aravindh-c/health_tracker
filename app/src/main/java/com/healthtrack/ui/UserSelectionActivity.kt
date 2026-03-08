package com.healthtrack.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.healthtrack.R
import com.healthtrack.data.model.LlmProvider
import com.healthtrack.data.model.UserProfile
import com.healthtrack.databinding.ActivityUserSelectionBinding
import com.healthtrack.utils.SecurePrefs
import com.healthtrack.utils.UserProfileManager

class UserSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserSelectionBinding
    private lateinit var securePrefs: SecurePrefs
    private lateinit var profileManager: UserProfileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securePrefs = SecurePrefs(this)
        profileManager = UserProfileManager(this)

        // Show setup dialog if first launch
        if (!securePrefs.isSetupDone) {
            showSetupDialog()
        } else {
            showUserSelection()
        }
    }

    private fun showUserSelection() {
        val profiles = profileManager.loadProfiles()
        binding.recyclerUsers.layoutManager = LinearLayoutManager(this)
        binding.recyclerUsers.adapter = UserAdapter(profiles) { profile ->
            launchMain(profile.user_id)
        }
    }

    private fun launchMain(userId: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra(MainActivity.EXTRA_USER_ID, userId)
        startActivity(intent)
    }

    private fun showSetupDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_setup, null)
        val etApiKey = view.findViewById<TextInputEditText>(R.id.etApiKey)
        val spinnerProvider = view.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.spinnerProvider)

        val providers = LlmProvider.values().map { it.display }
        spinnerProvider.setSimpleItems(providers.toTypedArray())
        spinnerProvider.setText(providers[0], false)

        MaterialAlertDialogBuilder(this)
            .setTitle("Welcome to Health Track")
            .setMessage("Enter your API key to get started")
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("Save & Continue") { _, _ ->
                val key = etApiKey.text?.toString()?.trim() ?: ""
                val providerIndex = providers.indexOf(spinnerProvider.text.toString())
                val provider = if (providerIndex >= 0) LlmProvider.values()[providerIndex] else LlmProvider.OPENAI

                if (key.isNotBlank()) {
                    when (provider) {
                        LlmProvider.OPENAI -> securePrefs.openAiKey = key
                        LlmProvider.CLAUDE -> securePrefs.claudeKey = key
                    }
                    securePrefs.llmProvider = provider
                    securePrefs.isSetupDone = true
                    showUserSelection()
                } else {
                    showSetupDialog() // re-show if empty
                }
            }
            .show()
    }
}

class UserAdapter(
    private val profiles: List<UserProfile>,
    private val onSelect: (UserProfile) -> Unit
) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardUser)
        val tvName: TextView = view.findViewById(R.id.tvUserName)
        val tvInfo: TextView = view.findViewById(R.id.tvUserInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val profile = profiles[position]
        holder.tvName.text = profile.display_name
        val conditions = if (profile.medical_conditions.isEmpty()) "No conditions"
        else profile.medical_conditions.take(2).joinToString(", ")
        holder.tvInfo.text = "Age ${profile.age} • $conditions"
        holder.card.setOnClickListener { onSelect(profile) }
    }

    override fun getItemCount() = profiles.size
}
