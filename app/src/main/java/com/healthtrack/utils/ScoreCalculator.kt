package com.healthtrack.utils

import com.healthtrack.data.model.DailyScore
import com.healthtrack.data.model.NutrientData
import com.healthtrack.data.model.NutrientTargets

object ScoreCalculator {

    // Weights must sum to 1.0
    private const val WEIGHT_PROTEIN      = 0.30
    private const val WEIGHT_FIBER        = 0.20
    private const val WEIGHT_CALORIES     = 0.20
    private const val WEIGHT_SIMPLE_CARBS = 0.20
    private const val WEIGHT_FAT          = 0.10

    fun calculate(consumed: NutrientData, targets: NutrientTargets): DailyScore {
        // No punishment for exceeding: protein and fiber cap at 100%
        val proteinScore    = achievementScore(consumed.protein_g, targets.protein_g)
        val fiberScore      = achievementScore(consumed.fiber_g, targets.fiber_g)

        // Moderate punishment for calorie overage
        val calorieScore    = calorieScore(consumed.calories_kcal, targets.calories_kcal)

        // Heavy punishment for fat overage
        val fatScore        = penalisedScore(consumed.fat_g, targets.fat_g, penaltyRate = 2.5)

        // Heaviest punishment for simple carb overage
        val simpleCarbScore = penalisedScore(consumed.carbs_simple_g, targets.simple_carbs_max_g, penaltyRate = 4.0)

        val overall = proteinScore * WEIGHT_PROTEIN +
                      fiberScore   * WEIGHT_FIBER   +
                      calorieScore * WEIGHT_CALORIES +
                      simpleCarbScore * WEIGHT_SIMPLE_CARBS +
                      fatScore     * WEIGHT_FAT

        return DailyScore(
            overall         = overall,
            proteinScore    = proteinScore,
            fiberScore      = fiberScore,
            calorieScore    = calorieScore,
            simpleCarbScore = simpleCarbScore,
            fatScore        = fatScore
        )
    }

    /** 0–100, no penalty for going over target. */
    private fun achievementScore(achieved: Double, target: Double): Double {
        if (target <= 0) return 100.0
        return (achieved / target * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Calories: 90–110% of target = 100.
     * Under 90% → ramps from 0 to 100.
     * Over 110% → -2 points per 1% over (can go well negative).
     */
    private fun calorieScore(consumed: Double, target: Double): Double {
        if (target <= 0) return 100.0
        val ratio = consumed / target
        return when {
            ratio <= 0.9  -> ratio / 0.9 * 100.0
            ratio <= 1.1  -> 100.0
            else          -> 100.0 - (ratio - 1.1) * 1000.0   // -10 per 1% over 110%
        }
    }

    /**
     * Generic penalised score: full score up to target, then loses penaltyRate
     * points for every 1% over target. Can go negative.
     */
    private fun penalisedScore(consumed: Double, target: Double, penaltyRate: Double): Double {
        if (target <= 0) return 100.0
        val ratio = consumed / target
        return if (ratio <= 1.0)
            (ratio * 100.0).coerceIn(0.0, 100.0)
        else
            100.0 - (ratio - 1.0) * 100.0 * penaltyRate
    }
}
