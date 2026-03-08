package com.healthtrack.ui.tips

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
                    profileManager = UserProfileManager(requireContext())
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

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnGetSuggestions.isEnabled = !loading
        }

        viewModel.suggestions.observe(viewLifecycleOwner) { text ->
            binding.tvSuggestions.text = text
            binding.cardSuggestions.visibility = View.VISIBLE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) binding.tvError.apply {
                visibility = View.VISIBLE
                text = error
            } else binding.tvError.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
