package com.healthtrack.utils

import com.healthtrack.data.model.DailyScore
import com.healthtrack.data.model.NutrientData
import com.healthtrack.data.model.NutrientTargets
import kotlin.math.min

object ScoreCalculator {

    // Weights must sum to 1.0
    private const val WEIGHT_PROTEIN = 0.30
    private const val WEIGHT_FIBER = 0.25
    private const val WEIGHT_CALORIES = 0.20
    private const val WEIGHT_SIMPLE_CARBS = 0.15
    private const val WEIGHT_FAT = 0.10

    fun calculate(consumed: NutrientData, targets: NutrientTargets): DailyScore {
        // For nutrients where "more is better" → score = min(achieved/target, 1.0)
        val proteinScore = scoreAchievement(consumed.protein_g, targets.protein_g)
        val fiberScore = scoreAchievement(consumed.fiber_g, targets.fiber_g)

        // Calories: within 10% of target is perfect
        val calorieScore = scoreCalories(consumed.calories_kcal, targets.calories_kcal)

        // Simple carbs: lower is better — score = 1.0 if under limit
        val simpleCarbScore = scoreInverse(consumed.carbs_simple_g, targets.simple_carbs_max_g)

        // Fat: balance within target range
        val fatScore = scoreAchievement(consumed.fat_g, targets.fat_g)

        val overall = (proteinScore * WEIGHT_PROTEIN) +
                (fiberScore * WEIGHT_FIBER) +
                (calorieScore * WEIGHT_CALORIES) +
                (simpleCarbScore * WEIGHT_SIMPLE_CARBS) +
                (fatScore * WEIGHT_FAT)

        return DailyScore(
            overall = overall * 100,
            proteinScore = proteinScore * 100,
            fiberScore = fiberScore * 100,
            calorieScore = calorieScore * 100,
            simpleCarbScore = simpleCarbScore * 100,
            fatScore = fatScore * 100
        )
    }

    // Score from 0–1: achieved vs target (capped at 1.0)
    private fun scoreAchievement(achieved: Double, target: Double): Double {
        if (target <= 0) return 1.0
        return min(achieved / target, 1.0)
    }

    // Calories: 90–110% of target = 100%, degrades outside
    private fun scoreCalories(consumed: Double, target: Double): Double {
        if (target <= 0) return 1.0
        val ratio = consumed / target
        return when {
            ratio in 0.9..1.1 -> 1.0
            ratio < 0.9 -> ratio / 0.9
            else -> 1.0 - ((ratio - 1.1) / 0.3).coerceAtMost(1.0)
        }
    }

    // Inverse: under limit = 1.0, over limit degrades
    private fun scoreInverse(consumed: Double, limit: Double): Double {
        if (limit <= 0) return 1.0
        return if (consumed <= limit) 1.0 else {
            val overage = consumed - limit
            (1.0 - (overage / limit)).coerceAtLeast(0.0)
        }
    }
}
