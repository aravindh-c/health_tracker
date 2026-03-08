package com.healthtrack.ui.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.healthtrack.data.model.NutrientData
import com.healthtrack.data.model.NutrientTargets
import com.healthtrack.databinding.FragmentReportBinding
import com.healthtrack.ui.MainActivity
import com.healthtrack.utils.SecurePrefs
import com.healthtrack.utils.UserProfileManager

class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val activity = requireActivity() as MainActivity
                @Suppress("UNCHECKED_CAST")
                return ReportViewModel(
                    userId = activity.userId,
                    securePrefs = SecurePrefs(requireContext()),
                    profileManager = UserProfileManager(requireContext())
                ) as T
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRefresh.setOnClickListener { viewModel.load() }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.nutrients.observe(viewLifecycleOwner) { n ->
            val targets = viewModel.profile.value?.targets
            bindNutrients(n, targets)
        }

        viewModel.score.observe(viewLifecycleOwner) { score ->
            binding.tvScore.text = "${score.overall.toInt()}%"
            binding.tvScoreLabel.text = "Daily Nutrition Score"
            binding.progressScore.progress = score.overall.toInt()
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) binding.tvError.apply {
                visibility = View.VISIBLE
                text = error
            } else binding.tvError.visibility = View.GONE
        }

        viewModel.load()
    }

    private fun bindNutrients(n: NutrientData, targets: NutrientTargets?) {
        binding.tvProtein.text = "Protein: ${n.protein_g.toInt()} g${targets?.let { " / ${it.protein_g.toInt()} g" } ?: ""}"
        binding.tvFat.text = "Fat: ${n.fat_g.toInt()} g${targets?.let { " / ${it.fat_g.toInt()} g" } ?: ""}"
        binding.tvFiber.text = "Fiber: ${n.fiber_g.toInt()} g${targets?.let { " / ${it.fiber_g.toInt()} g" } ?: ""}"
        binding.tvCalories.text = "Calories: ${n.calories_kcal.toInt()} kcal${targets?.let { " / ${it.calories_kcal.toInt()} kcal" } ?: ""}"
        binding.tvSimpleCarbs.text = "🔴 Simple Carbs: ${n.carbs_simple_g.toInt()} g${targets?.let { " (max ${it.simple_carbs_max_g.toInt()} g)" } ?: ""}"
        binding.tvComplexCarbs.text = "🟢 Complex Carbs: ${n.carbs_complex_g.toInt()} g"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
