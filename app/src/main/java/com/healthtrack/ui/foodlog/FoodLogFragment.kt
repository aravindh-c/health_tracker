package com.healthtrack.ui.foodlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.healthtrack.data.model.MealType
import com.healthtrack.data.model.NutrientData
import com.healthtrack.databinding.FragmentFoodLogBinding
import com.healthtrack.ui.MainActivity
import com.healthtrack.utils.PopupMessageHelper
import com.healthtrack.utils.SecurePrefs
import com.healthtrack.utils.UserProfileManager

class FoodLogFragment : Fragment() {

    private var _binding: FragmentFoodLogBinding? = null
    private val binding get() = _binding!!

    // Keep adapter as field so we can reset it after each log
    private lateinit var meals: List<String>
    private lateinit var mealAdapter: ArrayAdapter<String>

    private val viewModel: FoodLogViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val activity = requireActivity() as MainActivity
                @Suppress("UNCHECKED_CAST")
                return FoodLogViewModel(
                    userId = activity.userId,
                    securePrefs = SecurePrefs(requireContext()),
                    profileManager = UserProfileManager(requireContext()),
                    context = requireContext()
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

        binding.cardResult.visibility = View.GONE

        meals = MealType.displayList()
        mealAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, meals) {
            override fun getFilter(): Filter = object : Filter() {
                override fun performFiltering(constraint: CharSequence?) = FilterResults().apply {
                    values = meals; count = meals.size
                }
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) = notifyDataSetChanged()
            }
        }
        resetMealDropdown()

        binding.btnLog.setOnClickListener {
            val mealType = binding.spinnerMeal.text.toString()
            val foodText = binding.etFoodText.text?.toString()?.trim() ?: ""
            viewModel.logMeal(mealType, foodText)
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is FoodLogState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLog.isEnabled = true
                    // cardResult intentionally NOT hidden — preserve result visible after reset
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
                    showMotivationalPopup(state.nutrients)
                    viewModel.reset() // reset immediately — prevents re-trigger on tab re-navigation
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

    // Reset dropdown so all meal options are visible again
    private fun resetMealDropdown() {
        binding.spinnerMeal.setAdapter(mealAdapter)
        binding.spinnerMeal.setText(meals[1], false) // Default: Breakfast
        binding.spinnerMeal.dismissDropDown()
    }

    private fun showNutrientResult(n: com.healthtrack.data.model.NutrientData) {
        binding.cardResult.visibility = View.VISIBLE
        binding.tvProtein.text = "Protein: ${n.protein_g.toInt()} g"
        binding.tvFat.text = "Fat: ${n.fat_g.toInt()} g"
        binding.tvFiber.text = "Fiber: ${n.fiber_g.toInt()} g"
        binding.tvCalories.text = "Calories: ${n.calories_kcal.toInt()} kcal"
        binding.tvSimpleCarbs.text = "Simple Carbs: ${n.carbs_simple_g.toInt()} g"
        binding.tvComplexCarbs.text = "Complex Carbs: ${n.carbs_complex_g.toInt()} g"

        // Clear food input and reset dropdown for next entry
        binding.etFoodText.setText("")
        resetMealDropdown()
    }

    private fun showMotivationalPopup(nutrients: NutrientData) {
        val activity = requireActivity() as MainActivity
        val profile = UserProfileManager(requireContext()).getProfile(activity.userId) ?: return
        val message = PopupMessageHelper(requireContext()).getMessage(nutrients, profile)

        MaterialAlertDialogBuilder(requireContext())
            .setMessage(message)
            .setPositiveButton("Keep Going!") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
