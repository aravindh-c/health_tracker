package com.healthtrack.ui.tips

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtrack.data.repository.NutritionRepository
import com.healthtrack.utils.SecurePrefs
import com.healthtrack.utils.UserProfileManager
import kotlinx.coroutines.launch

class TipsViewModel(
    private val userId: String,
    private val securePrefs: SecurePrefs,
    private val profileManager: UserProfileManager
) : ViewModel() {

    private val repository = NutritionRepository(securePrefs)

    private val _suggestions = MutableLiveData<String>()
    val suggestions: LiveData<String> = _suggestions

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadSuggestions() {
        val profile = profileManager.getProfile(userId) ?: return
        _loading.value = true

        viewModelScope.launch {
            val result = repository.getMealSuggestions(userId, profile)
            result.fold(
                onSuccess = { _suggestions.value = it },
                onFailure = { _error.value = it.message }
            )
            _loading.value = false
        }
    }
}
