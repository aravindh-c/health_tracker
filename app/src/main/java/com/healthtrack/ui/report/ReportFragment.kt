package com.healthtrack.ui.report

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.healthtrack.data.model.DailyNutrientPoint
import com.healthtrack.data.model.NutrientData
import com.healthtrack.data.model.NutrientTargets
import com.healthtrack.databinding.FragmentReportBinding
import com.healthtrack.ui.MainActivity
import com.healthtrack.utils.SecurePrefs
import com.healthtrack.utils.UserProfileManager
import java.time.YearMonth
import java.time.format.DateTimeFormatter

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
                    profileManager = UserProfileManager(requireContext()),
                    context = requireContext()
                ) as T
            }
        }
    }

    private val monthOptions: List<YearMonth> by lazy {
        val now = YearMonth.now()
        (0..5).map { now.minusMonths(it.toLong()) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupNutrientChart()
        setupMonthlyChart()
        setupMonthPicker()

        binding.btnRefresh.setOnClickListener { viewModel.load() }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.nutrients.observe(viewLifecycleOwner) { n ->
            val targets = viewModel.profile.value?.targets
            bindNutrients(n, targets)
            if (targets != null) updateNutrientChart(n, targets)
        }

        viewModel.score.observe(viewLifecycleOwner) { score ->
            binding.tvScore.text = "${score.overall.toInt()}%"
            binding.tvScoreLabel.text = "Daily Nutrition Score"
            binding.progressScore.progress = score.overall.toInt()
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) binding.tvError.apply { visibility = View.VISIBLE; text = error }
            else binding.tvError.visibility = View.GONE
        }

        viewModel.monthlyData.observe(viewLifecycleOwner) { data ->
            val targets = viewModel.profile.value?.targets
            if (targets != null) updateMonthlyChart(data, targets)
        }

        viewModel.monthlyLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressMonthly.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.load()
        viewModel.loadMonthlyData(YearMonth.now())
    }

    private fun setupMonthPicker() {
        val fmt = DateTimeFormatter.ofPattern("MMMM yyyy")
        val labels = monthOptions.map { it.format(fmt) }.toTypedArray()
        binding.spinnerMonth.setSimpleItems(labels)
        binding.spinnerMonth.setText(labels[0], false)
        binding.spinnerMonth.setOnItemClickListener { _, _, position, _ ->
            viewModel.loadMonthlyData(monthOptions[position])
        }
    }

    private fun setupNutrientChart() {
        binding.chartNutrients.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setDrawGridBackground(false)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(
                    arrayOf("Simple Carbs", "Fat", "Fiber", "Calories", "Protein")
                )
                textSize = 11f
            }
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 150f
                addLimitLine(LimitLine(100f, "Target").apply {
                    lineColor = Color.GRAY
                    lineWidth = 1f
                    enableDashedLine(10f, 5f, 0f)
                    labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                    textSize = 10f
                })
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "${value.toInt()}%"
                }
            }
            axisRight.isEnabled = false
        }
    }

    private fun updateNutrientChart(n: NutrientData, t: NutrientTargets) {
        fun pct(actual: Double, target: Double) =
            if (target > 0) ((actual / target) * 100f).toFloat().coerceAtMost(150f) else 0f

        val values = listOf(
            pct(n.carbs_simple_g, t.simple_carbs_max_g),
            pct(n.fat_g, t.fat_g),
            pct(n.fiber_g, t.fiber_g),
            pct(n.calories_kcal, t.calories_kcal),
            pct(n.protein_g, t.protein_g)
        )
        val entries = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
        val colors = values.mapIndexed { i, v ->
            when {
                i == 0 && v > 100f -> Color.parseColor("#F44336")
                v >= 80f -> Color.parseColor("#4CAF50")
                v >= 50f -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#F44336")
            }
        }
        val dataSet = BarDataSet(entries, "").apply {
            setColors(colors)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = "${value.toInt()}%"
            }
            valueTextSize = 11f
        }
        binding.chartNutrients.data = BarData(dataSet).apply { barWidth = 0.6f }
        binding.chartNutrients.animateX(500)
        binding.chartNutrients.invalidate()
    }

    private fun setupMonthlyChart() {
        binding.chartMonthly.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(false)
            setDrawGridBackground(false)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                textSize = 10f
                setDrawGridLines(false)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "${value.toInt() + 1}"
                }
            }
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 150f
                addLimitLine(LimitLine(100f, "100%").apply {
                    lineColor = Color.GRAY
                    lineWidth = 1f
                    enableDashedLine(10f, 5f, 0f)
                    labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                    textSize = 9f
                })
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "${value.toInt()}%"
                }
            }
            axisRight.isEnabled = false
        }
    }

    private fun updateMonthlyChart(points: List<DailyNutrientPoint>, t: NutrientTargets) {
        fun pct(actual: Double, target: Double) =
            if (target > 0) ((actual / target) * 100f).toFloat().coerceAtMost(150f) else 0f

        val calEntries = mutableListOf<Entry>()
        val proEntries = mutableListOf<Entry>()
        val fibEntries = mutableListOf<Entry>()

        points.forEachIndexed { i, point ->
            val n = point.nutrients
            if (n.calories_kcal > 0 || n.protein_g > 0) {
                calEntries.add(Entry(i.toFloat(), pct(n.calories_kcal, t.calories_kcal)))
                proEntries.add(Entry(i.toFloat(), pct(n.protein_g, t.protein_g)))
                fibEntries.add(Entry(i.toFloat(), pct(n.fiber_g, t.fiber_g)))
            }
        }

        if (calEntries.isEmpty()) {
            binding.chartMonthly.clear()
            binding.chartMonthly.invalidate()
            return
        }

        fun makeSet(entries: List<Entry>, color: Int) = LineDataSet(entries, "").apply {
            this.color = color
            setCircleColor(color)
            lineWidth = 2f
            circleRadius = 3f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        binding.chartMonthly.data = LineData(
            makeSet(calEntries, Color.parseColor("#4CAF50")),
            makeSet(proEntries, Color.parseColor("#2196F3")),
            makeSet(fibEntries, Color.parseColor("#FF9800"))
        )
        binding.chartMonthly.animateX(600)
        binding.chartMonthly.invalidate()
    }

    private fun bindNutrients(n: NutrientData, targets: NutrientTargets?) {
        binding.tvProtein.text = "Protein: ${n.protein_g.toInt()} g" + (targets?.let { " / ${it.protein_g.toInt()} g" } ?: "")
        binding.tvFat.text = "Fat: ${n.fat_g.toInt()} g" + (targets?.let { " / ${it.fat_g.toInt()} g" } ?: "")
        binding.tvFiber.text = "Fiber: ${n.fiber_g.toInt()} g" + (targets?.let { " / ${it.fiber_g.toInt()} g" } ?: "")
        binding.tvCalories.text = "Calories: ${n.calories_kcal.toInt()} kcal" + (targets?.let { " / ${it.calories_kcal.toInt()} kcal" } ?: "")
        binding.tvSimpleCarbs.text = "\uD83D\uDD34 Simple Carbs: ${n.carbs_simple_g.toInt()} g" + (targets?.let { " (max ${it.simple_carbs_max_g.toInt()} g)" } ?: "")
        binding.tvComplexCarbs.text = "\uD83D\uDFE2 Complex Carbs: ${n.carbs_complex_g.toInt()} g"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
