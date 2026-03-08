package com.healthtrack.ui.report

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtrack.data.model.DailyNutrientPoint
import com.healthtrack.data.model.DailyScore
import com.healthtrack.data.model.NutrientData
import com.healthtrack.data.model.UserProfile
import com.healthtrack.data.repository.NutritionRepository
import com.healthtrack.utils.SecurePrefs
import com.healthtrack.utils.UserProfileManager
import kotlinx.coroutines.launch
import java.time.YearMonth

class ReportViewModel(
    private val userId: String,
    private val securePrefs: SecurePrefs,
    private val profileManager: UserProfileManager,
    private val context: Context
) : ViewModel() {

    private val repository = NutritionRepository(securePrefs, context)

    private val _nutrients = MutableLiveData<NutrientData>()
    val nutrients: LiveData<NutrientData> = _nutrients

    private val _score = MutableLiveData<DailyScore>()
    val score: LiveData<DailyScore> = _score

    private val _profile = MutableLiveData<UserProfile>()
    val profile: LiveData<UserProfile> = _profile

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _monthlyData = MutableLiveData<List<DailyNutrientPoint>>()
    val monthlyData: LiveData<List<DailyNutrientPoint>> = _monthlyData

    private val _monthlyLoading = MutableLiveData(false)
    val monthlyLoading: LiveData<Boolean> = _monthlyLoading

    fun load() {
        val p = profileManager.getProfile(userId) ?: return
        _profile.value = p
        _loading.value = true

        viewModelScope.launch {
            try {
                val nutrientResult = repository.getDailyReport(userId)
                val scoreResult = repository.getDailyScore(userId, p)

                nutrientResult.onSuccess { _nutrients.value = it }
                scoreResult.onSuccess { _score.value = it }

                if (nutrientResult.isFailure) {
                    _error.value = nutrientResult.exceptionOrNull()?.message
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadMonthlyData(yearMonth: YearMonth) {
        _monthlyLoading.value = true
        viewModelScope.launch {
            try {
                val result = repository.getMonthlyData(userId, yearMonth)
                result.onSuccess { _monthlyData.value = it }
                result.onFailure { _error.value = it.message }
            } finally {
                _monthlyLoading.value = false
            }
        }
    }
}
