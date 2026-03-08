package com.healthtrack.ui.history

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtrack.data.model.MealHistory
import com.healthtrack.data.repository.NutritionRepository
import com.healthtrack.utils.SecurePrefs
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val userId: String,
    private val securePrefs: SecurePrefs,
    private val context: Context
) : ViewModel() {

    private val repository = NutritionRepository(securePrefs, context)

    private val _meals = MutableLiveData<List<MealHistory>>(emptyList())
    val meals: LiveData<List<MealHistory>> = _meals

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun load() {
        _loading.value = true
        viewModelScope.launch {
            val result = repository.getFoodHistory(userId)
            result.fold(
                onSuccess = { _meals.value = it },
                onFailure = { _error.value = it.message }
            )
            _loading.value = false
        }
    }
}
