package com.healthtrack.ui.foodlog

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtrack.data.model.MealLogResult
import com.healthtrack.data.model.NutrientData
import com.healthtrack.data.repository.NutritionRepository
import com.healthtrack.utils.SecurePrefs
import com.healthtrack.utils.UserProfileManager
import kotlinx.coroutines.launch

class FoodLogViewModel(
    private val userId: String,
    private val securePrefs: SecurePrefs,
    private val profileManager: UserProfileManager,
    private val context: Context
) : ViewModel() {

    private val repository = NutritionRepository(securePrefs, context)

    private val _state = MutableLiveData<FoodLogState>(FoodLogState.Idle)
    val state: LiveData<FoodLogState> = _state

    var lastResult: MealLogResult? = null
        private set

    fun logMeal(mealType: String, foodText: String, date: String) {
        if (foodText.isBlank()) {
            _state.value = FoodLogState.Error("Please enter what you ate")
            return
        }

        val profile = profileManager.getProfile(userId)
        if (profile == null) {
            _state.value = FoodLogState.Error("User profile not found")
            return
        }

        _state.value = FoodLogState.Loading

        viewModelScope.launch {
            val result = repository.logMeal(userId, mealType, foodText, profile, date)
            result.fold(
                onSuccess = { r ->
                    lastResult = r
                    _state.value = FoodLogState.Success(r.nutrients, r.insights)
                },
                onFailure = { e -> _state.value = FoodLogState.Error(e.message ?: "Unknown error") }
            )
        }
    }

    fun logFullDay(foodText: String, date: String) {
        if (foodText.isBlank()) {
            _state.value = FoodLogState.Error("Please describe your day's meals")
            return
        }
        val profile = profileManager.getProfile(userId) ?: run {
            _state.value = FoodLogState.Error("User profile not found")
            return
        }
        _state.value = FoodLogState.Loading
        viewModelScope.launch {
            val result = repository.logFullDay(userId, foodText, profile, date)
            result.fold(
                onSuccess = { r ->
                    lastResult = r
                    _state.value = FoodLogState.Success(r.nutrients, r.insights)
                },
                onFailure = { e -> _state.value = FoodLogState.Error(e.message ?: "Unknown error") }
            )
        }
    }

    fun reset() {
        _state.value = FoodLogState.Idle
    }
}

sealed class FoodLogState {
    object Idle : FoodLogState()
    object Loading : FoodLogState()
    data class Success(val nutrients: NutrientData, val insights: String) : FoodLogState()
    data class Error(val message: String) : FoodLogState()
}
