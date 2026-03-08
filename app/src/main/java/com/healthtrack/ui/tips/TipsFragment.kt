package com.healthtrack.ui.tips

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.BulletSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.chip.Chip
import com.healthtrack.R
import com.healthtrack.databinding.FragmentTipsBinding
import com.healthtrack.ui.MainActivity
import com.healthtrack.utils.SecurePrefs
import com.healthtrack.utils.UserProfileManager

class TipsFragment : Fragment() {

    private var _binding: FragmentTipsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TipsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val activity = requireActivity() as MainActivity
                @Suppress("UNCHECKED_CAST")
                return TipsViewModel(
                    userId = activity.userId,
                    securePrefs = SecurePrefs(requireContext()),
                    profileManager = UserProfileManager(requireContext()),
                    context = requireContext()
                ) as T
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTipsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnGetSuggestions.setOnClickListener { viewModel.loadSuggestions() }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            // Progress bar
            binding.progressBar.visibility = if (state.loading) View.VISIBLE else View.GONE
            binding.btnGetSuggestions.isEnabled = !state.loading

            // Meal chips
            if (state.loggedMeals.isNotEmpty() || state.remainingMeals.isNotEmpty()) {
                updateMealChips(state.loggedMeals, state.remainingMeals)
                binding.cardMealProgress.visibility = View.VISIBLE
                val total = state.loggedMeals.size + state.remainingMeals.size
                binding.tvMealCount.text = "${state.loggedMeals.size} of $total meals logged today"
            }

            // Error
            if (state.error != null) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = state.error
            } else {
                binding.tvError.visibility = View.GONE
            }

            // Suggestions
            if (state.suggestions.isNotEmpty()) {
                binding.tvSuggestions.text = formatSuggestions(state.suggestions)
                binding.cardSuggestions.visibility = View.VISIBLE

                val remaining = state.remainingMeals.joinToString(", ")
                binding.tvSuggestionsHeader.text = if (remaining.isNotEmpty())
                    "Suggestions for: $remaining" else "Today's meal suggestions"
            }
        }

        // Load today's progress on open
        viewModel.loadTodayProgress()
    }

    private fun updateMealChips(logged: List<String>, remaining: List<String>) {
        binding.chipGroupMeals.removeAllViews()
        logged.forEach { meal ->
            binding.chipGroupMeals.addView(makeChip(meal, done = true))
        }
        remaining.forEach { meal ->
            binding.chipGroupMeals.addView(makeChip(meal, done = false))
        }
    }

    private fun makeChip(label: String, done: Boolean): Chip {
        return Chip(requireContext()).apply {
            text = if (done) "✓ $label" else label
            isCheckable = false
            isClickable = false
            chipBackgroundColor = if (done)
                android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary))
            else
                android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.outline_variant))
            setTextColor(
                if (done) ContextCompat.getColor(requireContext(), R.color.on_primary)
                else ContextCompat.getColor(requireContext(), R.color.on_surface_variant)
            )
        }
    }

    // Render **bold** text and bullet points (•) nicely
    private fun formatSuggestions(raw: String): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        raw.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("**") && trimmed.endsWith("**") -> {
                    val text = trimmed.removePrefix("**").removeSuffix("**")
                    val start = ssb.length
                    ssb.append("\n$text\n")
                    ssb.setSpan(StyleSpan(Typeface.BOLD), start + 1, start + 1 + text.length, 0)
                }
                trimmed.startsWith("•") -> {
                    val text = trimmed.removePrefix("•").trim()
                    val start = ssb.length
                    ssb.append("  $text\n")
                    ssb.setSpan(BulletSpan(16), start, ssb.length, 0)
                }
                trimmed.isNotEmpty() -> {
                    // Handle inline **bold** within a line
                    ssb.append(renderInlineBold(trimmed))
                    ssb.append("\n")
                }
            }
        }
        return ssb
    }

    private fun renderInlineBold(line: String): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        val parts = line.split("**")
        parts.forEachIndexed { i, part ->
            val start = ssb.length
            ssb.append(part)
            if (i % 2 == 1) ssb.setSpan(StyleSpan(Typeface.BOLD), start, ssb.length, 0)
        }
        return ssb
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
