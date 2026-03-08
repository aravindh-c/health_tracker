package com.healthtrack.ui.foodlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.healthtrack.data.model.MealType
import com.healthtrack.databinding.FragmentFoodLogBinding
import com.healthtrack.ui.MainActivity
import com.healthtrack.utils.SecurePrefs
import com.healthtrack.utils.UserProfileManager

class FoodLogFragment : Fragment() {

    private var _binding: FragmentFoodLogBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FoodLogViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val activity = requireActivity() as MainActivity
                @Suppress("UNCHECKED_CAST")
                return FoodLogViewModel(
                    userId = activity.userId,
                    securePrefs = SecurePrefs(requireContext()),
                    profileManager = UserProfileManager(requireContext())
                ) as T
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFoodLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup meal type dropdown
        val meals = MealType.displayList()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, meals)
        binding.spinnerMeal.setAdapter(adapter)
        binding.spinnerMeal.setText(meals[1], false) // Default: Breakfast

        binding.btnLog.setOnClickListener {
            val mealType = binding.spinnerMeal.text.toString()
            val foodText = binding.etFoodText.text?.toString()?.trim() ?: ""
            viewModel.logMeal(mealType, foodText)
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is FoodLogState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.cardResult.visibility = View.GONE
                    binding.btnLog.isEnabled = true
                }
                is FoodLogState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.cardResult.visibility = View.GONE
                    binding.btnLog.isEnabled = false
                }
                is FoodLogState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLog.isEnabled = true
                    showNutrientResult(state.nutrients)
                }
                is FoodLogState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLog.isEnabled = true
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    viewModel.reset()
                }
            }
        }
    }

    private fun showNutrientResult(n: com.healthtrack.data.model.NutrientData) {
        binding.cardResult.visibility = View.VISIBLE
        binding.tvProtein.text = "Protein: ${n.protein_g.toInt()} g"
        binding.tvFat.text = "Fat: ${n.fat_g.toInt()} g"
        binding.tvFiber.text = "Fiber: ${n.fiber_g.toInt()} g"
        binding.tvCalories.text = "Calories: ${n.calories_kcal.toInt()} kcal"
        binding.tvSimpleCarbs.text = "Simple Carbs: ${n.carbs_simple_g.toInt()} g"
        binding.tvComplexCarbs.text = "Complex Carbs: ${n.carbs_complex_g.toInt()} g"

        // Clear food input for next entry
        binding.etFoodText.setText("")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
