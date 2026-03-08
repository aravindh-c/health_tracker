package com.healthtrack.ui.tips

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtrack.data.repository.NutritionRepository
import com.healthtrack.utils.SecurePrefs
import com.healthtrack.utils.UserProfileManager
import kotlinx.coroutines.launch

data class TipsUiState(
    val loggedMeals: List<String> = emptyList(),
    val remainingMeals: List<String> = emptyList(),
    val suggestions: String = "",
    val loading: Boolean = false,
    val error: String? = null
)

class TipsViewModel(
    private val userId: String,
    private val securePrefs: SecurePrefs,
    private val profileManager: UserProfileManager,
    private val context: Context
) : ViewModel() {

    private val repository = NutritionRepository(securePrefs, context)

    private val _state = MutableLiveData(TipsUiState())
    val state: LiveData<TipsUiState> = _state

    fun loadTodayProgress() {
        viewModelScope.launch {
            val result = repository.getFoodHistory(userId)
            result.onSuccess { history ->
                val loggedMeals = history.map { it.meal_type }.distinct()
                val allMeals = listOf("Gym Pre-Workout", "Breakfast", "Mid-Day", "Lunch", "Evening Snack", "Dinner")
                val remaining = allMeals.filter { it !in loggedMeals }
                _state.value = _state.value?.copy(loggedMeals = loggedMeals, remainingMeals = remaining)
            }
        }
    }

    fun loadSuggestions() {
        val profile = profileManager.getProfile(userId) ?: return
        _state.value = _state.value?.copy(loading = true, error = null)
        viewModelScope.launch {
            launch { repository.autoUpdateFoodPreferences(profile) }
            val result = repository.getMealSuggestions(userId, profile)
            result.fold(
                onSuccess = { suggestions ->
                    val historyResult = repository.getFoodHistory(userId)
                    val loggedMeals = historyResult.getOrDefault(emptyList()).map { it.meal_type }.distinct()
                    val allMeals = listOf("Gym Pre-Workout", "Breakfast", "Mid-Day", "Lunch", "Evening Snack", "Dinner")
                    val remaining = allMeals.filter { it !in loggedMeals }
                    _state.value = _state.value?.copy(
                        suggestions = suggestions, loggedMeals = loggedMeals,
                        remainingMeals = remaining, loading = false
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value?.copy(loading = false, error = e.message)
                }
            )
        }
    }
}
