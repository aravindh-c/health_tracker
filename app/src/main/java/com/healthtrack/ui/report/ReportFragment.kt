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
import com.github.mikephil.charting.charts.LineChart
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
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

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

    private val IST = ZoneId.of("Asia/Kolkata")
    private val chipDateFormatter = DateTimeFormatter.ofPattern("d MMM")
    private var selectedWeightDate: LocalDate = LocalDate.now(ZoneId.of("Asia/Kolkata"))

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
        setupWeightDateChips()

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
            binding.tvScore.text = "${score.overall.roundToInt()}%"
            binding.tvScoreLabel.text = "Daily Nutrition Score"
            binding.progressScore.progress = score.overall.coerceIn(0.0, 100.0).toInt()
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

        viewModel.weightHistory.observe(viewLifecycleOwner) { entries ->
            val profile = viewModel.profile.value ?: return@observe
            setupAndUpdateWeightChart(entries, profile)
        }

        viewModel.weightLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressWeight.visibility = if (loading) View.VISIBLE else View.GONE
        }

        binding.btnLogWeight.setOnClickListener {
            val w = binding.etWeight.text?.toString()?.toDoubleOrNull()
            if (w == null || w <= 0) {
                binding.etWeight.error = "Enter a valid weight"
                return@setOnClickListener
            }
            binding.etWeight.error = null
            viewModel.logWeight(w, selectedWeightDate.toString())
            binding.etWeight.setText("")
        }

        viewModel.loadWeightHistory()

        viewModel.load()
        viewModel.loadMonthlyData(YearMonth.now())
    }

    private fun setupWeightDateChips() {
        binding.chipWeightToday.setOnClickListener {
            selectedWeightDate = LocalDate.now(IST)
            binding.chipWeightPickDate.text = "Pick date…"
        }
        binding.chipWeightYesterday.setOnClickListener {
            selectedWeightDate = LocalDate.now(IST).minusDays(1)
            binding.chipWeightPickDate.text = "Pick date…"
        }
        binding.chipWeightPickDate.setOnClickListener {
            val constraints = CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())
                .build()
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select weight log date")
                .setSelection(selectedWeightDate.toEpochDay() * 86_400_000L)
                .setCalendarConstraints(constraints)
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                val picked = LocalDate.ofEpochDay(millis / 86_400_000L)
                selectedWeightDate = picked
                when (picked) {
                    LocalDate.now(IST) -> {
                        binding.chipGroupWeightDate.check(binding.chipWeightToday.id)
                        binding.chipWeightPickDate.text = "Pick date…"
                    }
                    LocalDate.now(IST).minusDays(1) -> {
                        binding.chipGroupWeightDate.check(binding.chipWeightYesterday.id)
                        binding.chipWeightPickDate.text = "Pick date…"
                    }
                    else -> {
                        binding.chipWeightPickDate.text = picked.format(chipDateFormatter)
                        binding.chipGroupWeightDate.check(binding.chipWeightPickDate.id)
                    }
                }
            }
            picker.addOnNegativeButtonClickListener {
                if (binding.chipGroupWeightDate.checkedChipId == binding.chipWeightPickDate.id &&
                    binding.chipWeightPickDate.text == "Pick date…") {
                    binding.chipGroupWeightDate.check(binding.chipWeightToday.id)
                    selectedWeightDate = LocalDate.now(IST)
                }
            }
            picker.show(parentFragmentManager, "weight_date_picker")
        }
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
                    arrayOf("Cplx Carbs", "Sim Carbs", "Fat", "Fiber", "Calories", "Protein")
                )
                textSize = 11f
            }
            axisLeft.apply {
                axisMinimum = -100f
                axisMaximum = 200f
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
            if (target > 0) ((actual / target) * 100f).toFloat().coerceAtMost(200f) else 0f
        fun pctUnbounded(actual: Double, target: Double) =
            if (target > 0) ((actual / target) * 100f).toFloat() else 0f

        // Order matches X-axis labels: Cplx Carbs, Sim Carbs, Fat, Fiber, Calories, Protein
        val values = listOf(
            pct(n.carbs_complex_g, t.carbs_complex_g),
            pctUnbounded(n.carbs_simple_g, t.simple_carbs_max_g),
            pctUnbounded(n.fat_g, t.fat_g),
            pct(n.fiber_g, t.fiber_g),
            pctUnbounded(n.calories_kcal, t.calories_kcal),
            pct(n.protein_g, t.protein_g)
        )
        val entries = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
        val colors = values.mapIndexed { i, v ->
            when {
                i in listOf(1, 2, 4) && v > 110f -> Color.parseColor("#F44336") // punishment nutrients over
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
            valueTextSize = 10f
        }
        binding.chartNutrients.xAxis.valueFormatter = IndexAxisValueFormatter(
            arrayOf("Cplx Carbs", "Sim Carbs", "Fat", "Fiber", "Calories", "Protein")
        )
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
        val carbEntries = mutableListOf<Entry>()
        val fatEntries = mutableListOf<Entry>()

        points.forEachIndexed { i, point ->
            val n = point.nutrients
            if (n.calories_kcal > 0 || n.protein_g > 0) {
                calEntries.add(Entry(i.toFloat(), pct(n.calories_kcal, t.calories_kcal)))
                proEntries.add(Entry(i.toFloat(), pct(n.protein_g, t.protein_g)))
                fibEntries.add(Entry(i.toFloat(), pct(n.fiber_g, t.fiber_g)))
                val combinedCarbs = n.carbs_simple_g + n.carbs_complex_g
                val combinedTarget = t.simple_carbs_max_g + t.carbs_complex_g
                carbEntries.add(Entry(i.toFloat(), pct(combinedCarbs, combinedTarget)))
                fatEntries.add(Entry(i.toFloat(), pct(n.fat_g, t.fat_g)))
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
            makeSet(calEntries,  Color.parseColor("#4CAF50")),
            makeSet(proEntries,  Color.parseColor("#2196F3")),
            makeSet(fibEntries,  Color.parseColor("#FF9800")),
            makeSet(carbEntries, Color.parseColor("#9C27B0")),
            makeSet(fatEntries,  Color.parseColor("#F44336"))
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

    private fun setupAndUpdateWeightChart(
        entries: List<com.healthtrack.data.model.WeightEntry>,
        profile: com.healthtrack.data.model.UserProfile
    ) {
        val chart = binding.chartWeight
        val minY = profile.weight_min_chart_kg.toFloat()
        val maxY = profile.weight_max_chart_kg.toFloat()
        val targetKg = profile.weight_target_kg

        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            textSize = 10f
            setDrawGridLines(false)
        }
        chart.axisLeft.apply {
            axisMinimum = minY
            axisMaximum = maxY
            granularity = 0.1f
            // 100g grid lines
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = "${value}kg"
            }
            if (targetKg != null) {
                addLimitLine(LimitLine(targetKg.toFloat(), "Target ${targetKg}kg").apply {
                    lineColor = Color.parseColor("#FF9800")
                    lineWidth = 1.5f
                    enableDashedLine(10f, 5f, 0f)
                    labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                    textSize = 10f
                })
            }
        }
        chart.axisRight.isEnabled = false

        if (entries.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        val weightEntries = entries.mapIndexed { i, e ->
            Entry(i.toFloat(), e.weight_kg.toFloat())
        }
        val dates = entries.map { it.date.takeLast(5) } // "MM-DD"
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(dates.toTypedArray())

        val dataSet = LineDataSet(weightEntries, "Weight").apply {
            color = Color.parseColor("#4CAF50")
            setCircleColor(Color.parseColor("#4CAF50"))
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(true)
            valueTextSize = 9f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = "${value}kg"
            }
        }
        chart.data = LineData(dataSet)
        chart.animateX(600)
        chart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
