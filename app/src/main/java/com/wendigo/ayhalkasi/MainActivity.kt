package com.wendigo.ayhalkasi

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var nav: BottomNavigationView
    private lateinit var pages: List<View>
    private lateinit var calendarAdapter: CalendarAdapter

    private var calMonth: YearMonth = YearMonth.now()
    private var currentDay: LocalDate = LocalDate.now()
    private lateinit var todayEditor: DayEditor

    private lateinit var bearView: BearView
    private lateinit var lockOverlay: View
    private lateinit var etPin: EditText
    private lateinit var swLock: MaterialSwitch
    private lateinit var swMedTaken: MaterialSwitch
    private var biometricAvailable = false
    private var keepUnlocked = false
    private var lockToggleGuard = false
    private var medGuard = false
    private var editorUserToggled = false
    private var onboardDate: LocalDate = LocalDate.now()
    private var currentTab = 0
    private var welcomeShown = false

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            keepUnlocked = false
            if (uri != null) writeBackup(uri)
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            keepUnlocked = false
            if (uri != null) confirmRestore(uri)
        }

    private val pdfLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            if (uri != null) writeReport(uri)
        }

    private val csvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) writeCsv(uri)
        }

    private val weekLabels = arrayOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        applyPalette()
        setContentView(R.layout.activity_main)

        pages = listOf(
            findViewById(R.id.pageCalendar),
            findViewById(R.id.pageToday),
            findViewById(R.id.pageSummary),
            findViewById(R.id.pageBear),
            findViewById(R.id.pageSettings)
        )

        setupNavigation()
        setupCalendarPage()
        setupTodayPage()
        setupSummaryPage()
        setupBearPage()
        setupSettingsPage()
        setupLock()

        selectTab(savedInstanceState?.getInt(KEY_TAB, 0) ?: 0)
        ReminderScheduler.reschedule(this)
        maybeShowOnboarding()
        maybeShowSpecialWelcome()
    }

    private fun applyPalette() {
        val overlay = Palette.overlayFor(Store.palette)
        if (overlay != 0) theme.applyStyle(overlay, true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab)
    }

    override fun onStart() {
        super.onStart()
        if (Store.lockEnabled && !sessionUnlocked && !keepUnlocked) showLock()
    }

    override fun onStop() {
        super.onStop()
        if (Store.lockEnabled && !keepUnlocked) sessionUnlocked = false
    }

    override fun onResume() {
        super.onResume()
        applySecureFlag()
        refreshAll()
        ReminderScheduler.reschedule(this)
    }

    private fun applySecureFlag() {
        if (Store.lockEnabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // ---------------- Navigation ----------------

    private fun setupNavigation() {
        nav = findViewById(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            selectTab(
                when (item.itemId) {
                    R.id.nav_calendar -> 0
                    R.id.nav_today -> 1
                    R.id.nav_summary -> 2
                    R.id.nav_bear -> 3
                    else -> 4
                }
            )
            true
        }
    }

    private fun selectTab(index: Int) {
        currentTab = index
        pages.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        if (index == 0) renderCalendar()
        if (index == 1) loadTodayEditor()
        if (index == 2) renderSummary()
    }

    private fun refreshAll() {
        renderCalendar()
        loadTodayEditor()
        renderSummary()
        CycleWidgetProvider.refresh(this)
    }

    // ---------------- Calendar ----------------

    private fun setupCalendarPage() {
        findViewById<ImageButton>(R.id.btnPrevMonth).setOnClickListener {
            calMonth = calMonth.minusMonths(1)
            renderCalendar()
        }
        findViewById<ImageButton>(R.id.btnNextMonth).setOnClickListener {
            calMonth = calMonth.plusMonths(1)
            renderCalendar()
        }
        val week = findViewById<LinearLayout>(R.id.weekHeader)
        week.removeAllViews()
        for (label in weekLabels) {
            val tv = TextView(this)
            tv.text = label
            tv.gravity = android.view.Gravity.CENTER
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_dim))
            tv.textSize = 12f
            tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            week.addView(tv)
        }
        val grid = findViewById<RecyclerView>(R.id.calendarGrid)
        grid.layoutManager = GridLayoutManager(this, 7)
        calendarAdapter = CalendarAdapter { date -> showDayDialog(date) }
        grid.adapter = calendarAdapter

        findViewById<MaterialButton>(R.id.btnMarkToday).setOnClickListener {
            val today = LocalDate.now()
            val cur = Store.get(today)
            if (cur != null && cur.flow >= 2) {
                Toast.makeText(this, getString(R.string.already_started), Toast.LENGTH_SHORT).show()
            } else {
                confirmPeriodStart(today)
            }
        }
        findViewById<MaterialButton>(R.id.btnAddRange).setOnClickListener {
            PeriodRangeDialog.show(
                this,
                LocalDate.now(),
                replaceAnchorGroup = false,
                prefillExisting = false
            ) { refreshAll() }
        }
    }

    private fun confirmPeriodStart(date: LocalDate) {
        val prev = CycleEngine.analyze().lastStart?.takeIf { it.isBefore(date) }
        val gap = prev?.let { ChronoUnit.DAYS.between(it, date) }
        if (gap != null && gap in 1..20) {
            AlertDialog.Builder(this)
                .setTitle(R.string.period_short_cycle_title)
                .setMessage(getString(R.string.period_short_cycle_msg, gap))
                .setPositiveButton(R.string.period_confirm_yes) { _, _ ->
                    Store.markPeriodStart(date)
                    Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
                    refreshAll()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            Store.markPeriodStart(date)
            Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
            refreshAll()
        }
    }

    private fun renderCalendar() {
        findViewById<TextView>(R.id.monthLabel).text = Dates.month(calMonth)
        val result = CycleEngine.analyze()
        val today = LocalDate.now()

        val cells = ArrayList<CalendarAdapter.Cell>()
        val first = calMonth.atDay(1)
        val offset = first.dayOfWeek.value - 1
        repeat(offset) { cells.add(CalendarAdapter.Cell(null, DayState.NORMAL, false)) }
        for (d in 1..calMonth.lengthOfMonth()) {
            val date = calMonth.atDay(d)
            cells.add(
                CalendarAdapter.Cell(
                    date,
                    CycleEngine.stateFor(date, result),
                    date == today,
                    Store.get(date) != null
                )
            )
        }
        while (cells.size % 7 != 0) cells.add(CalendarAdapter.Cell(null, DayState.NORMAL, false))
        calendarAdapter.submit(cells)

        val hint = findViewById<TextView>(R.id.calendarHint)
        val next = result.nextStart
        if (next == null) {
            hint.text = getString(R.string.no_data_short)
        } else {
            val days = ChronoUnit.DAYS.between(today, next)
            val whenText = if (days > 0) getString(R.string.days_left, days)
            else if (days == 0L) getString(R.string.today_mark)
            else getString(R.string.days_late, -days)
            hint.text = "${getString(R.string.summary_next_period)}: ${result.nextRangeText()} · $whenText"
        }
        findViewById<TextView>(R.id.calendarEmpty).visibility =
            if (Store.hasAny()) View.GONE else View.VISIBLE
    }

    // ---------------- Today ----------------

    private fun setupTodayPage() {
        todayEditor = DayEditor(findViewById(R.id.dayEditorRoot)) { onDaySaved() }
        findViewById<ImageButton>(R.id.btnPrevDay).setOnClickListener {
            currentDay = currentDay.minusDays(1)
            editorUserToggled = false
            loadTodayEditor()
        }
        findViewById<ImageButton>(R.id.btnNextDay).setOnClickListener {
            currentDay = currentDay.plusDays(1)
            editorUserToggled = false
            loadTodayEditor()
        }
        findViewById<MaterialButton>(R.id.btnGoToday).setOnClickListener {
            currentDay = LocalDate.now()
            editorUserToggled = false
            loadTodayEditor()
        }
        findViewById<MaterialButton>(R.id.btnToggleEditor).setOnClickListener { toggleTodayEditor() }
        swMedTaken = findViewById(R.id.swMedTaken)
        swMedTaken.setOnCheckedChangeListener { _, checked ->
            if (medGuard) return@setOnCheckedChangeListener
            Store.setMedTaken(currentDay, checked)
            updateMedStreak()
        }
    }

    private fun loadTodayEditor() {
        findViewById<TextView>(R.id.todayDate).text = Dates.longDay(currentDay)
        todayEditor.load(currentDay)
        renderTodayDashboard()
        if (!editorUserToggled) setEditorExpanded(Store.get(currentDay) == null)

        val medCard = findViewById<View>(R.id.medCard)
        if (Store.medEnabled) {
            medCard.visibility = View.VISIBLE
            medGuard = true
            swMedTaken.isChecked = Store.isMedTaken(currentDay)
            medGuard = false
            updateMedStreak()
        } else {
            medCard.visibility = View.GONE
        }
    }

    private fun updateMedStreak() {
        val ym = YearMonth.from(currentDay)
        var count = 0
        for (d in 1..ym.lengthOfMonth()) {
            if (Store.isMedTaken(ym.atDay(d))) count++
        }
        findViewById<TextView>(R.id.medStreak).text =
            getString(R.string.today_med_streak, count)
    }

    private fun renderTodayDashboard() {
        val r = CycleEngine.analyze()
        val info = Insights.phaseNow(r, currentDay)
        findViewById<TextView>(R.id.todayPhase).text = getString(phaseTitleRes(info.phase))
        findViewById<TextView>(R.id.todayPhaseDay).text =
            if (info.phase != Insights.Phase.UNKNOWN && info.cycleDay > 0)
                getString(R.string.phase_day, info.cycleDay) else ""
        findViewById<TextView>(R.id.todayNext).text =
            if (r.nextStart == null) getString(R.string.no_data_short) else r.nextRangeText()

        val log = Store.get(currentDay)
        val ttc = ArrayList<String>()
        log?.bbt?.let { ttc.add(getString(R.string.today_ttc_bbt, fmtTemp(it))) }
        if (log != null) {
            if (log.mucus > 0) ttc.add(getString(R.string.today_ttc_mucus, mucusName(log.mucus)))
            if (log.lh > 0) ttc.add(getString(R.string.today_ttc_lh, lhName(log.lh)))
        }
        val ttcView = findViewById<TextView>(R.id.todayTtc)
        ttcView.text = ttc.joinToString(" · ")
        ttcView.visibility = if (ttc.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setEditorExpanded(expanded: Boolean) {
        findViewById<View>(R.id.todayEditorWrap).visibility =
            if (expanded) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.btnToggleEditor).text =
            getString(if (expanded) R.string.today_edit_hide else R.string.today_edit_show)
    }

    private fun toggleTodayEditor() {
        editorUserToggled = true
        val expanded = findViewById<View>(R.id.todayEditorWrap).visibility != View.VISIBLE
        setEditorExpanded(expanded)
    }

    private fun phaseTitleRes(p: Insights.Phase): Int = when (p) {
        Insights.Phase.MENSTRUAL -> R.string.phase_menstrual
        Insights.Phase.FOLLICULAR -> R.string.phase_follicular
        Insights.Phase.FERTILE -> R.string.phase_fertile
        Insights.Phase.LUTEAL -> R.string.phase_luteal
        Insights.Phase.UNKNOWN -> R.string.phase_unknown
    }

    private fun onDaySaved() {
        Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
        refreshAll()
    }

    private fun showDayDialog(date: LocalDate) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_day, null)
        view.findViewById<TextView>(R.id.dialogDate).text = Dates.longDay(date)
        val editor = DayEditor(view.findViewById(R.id.dayEditorRoot)) { onDaySaved() }
        editor.load(date)
        AlertDialog.Builder(this)
            .setView(view)
            .setNegativeButton(R.string.close, null)
            .create()
            .show()
    }

    // ---------------- Summary ----------------

    private fun setupSummaryPage() {
        findViewById<MaterialButton>(R.id.btnShare).setOnClickListener { shareSummary() }
        findViewById<MaterialButton>(R.id.btnExport).setOnClickListener { shareBackup() }
    }

    private fun setupBearPage() {
        bearView = findViewById(R.id.bearView)
        bearView.messages = resources.getStringArray(
            if (resources.getBoolean(R.bool.special_edition)) R.array.special_sweet_words
            else R.array.sweet_words
        ).toList()
        bearView.animal = parsePetAnimal(Store.petAnimal)
        applyPetName()

        val btnSound = findViewById<ImageButton>(R.id.btnBearSound)
        bearView.soundEnabled = Store.bearSound
        updateBearSoundIcon(btnSound)
        btnSound.setOnClickListener {
            bearView.soundEnabled = !bearView.soundEnabled
            Store.bearSound = bearView.soundEnabled
            Store.saveSettings()
            updateBearSoundIcon(btnSound)
        }

        findViewById<com.google.android.material.chip.Chip>(R.id.animalBear).isChecked = Store.petAnimal == "BEAR"
        findViewById<com.google.android.material.chip.Chip>(R.id.animalCat).isChecked = Store.petAnimal == "CAT"
        findViewById<com.google.android.material.chip.Chip>(R.id.animalRabbit).isChecked = Store.petAnimal == "RABBIT"
        findViewById<com.google.android.material.chip.Chip>(R.id.animalPanda).isChecked = Store.petAnimal == "PANDA"

        findViewById<com.google.android.material.chip.ChipGroup>(R.id.animalGroup)
            .setOnCheckedStateChangeListener { _, ids ->
                if (ids.isEmpty()) return@setOnCheckedStateChangeListener
                val a = when (ids[0]) {
                    R.id.animalCat -> "CAT"
                    R.id.animalRabbit -> "RABBIT"
                    R.id.animalPanda -> "PANDA"
                    else -> "BEAR"
                }
                if (a != Store.petAnimal) {
                    Store.petAnimal = a
                    Store.saveSettings()
                    bearView.animal = parsePetAnimal(a)
                    applyPetName()
                }
            }

        findViewById<MaterialButton>(R.id.btnHug).setOnClickListener { bearView.hug() }
        findViewById<MaterialButton>(R.id.btnSleep).setOnClickListener { bearView.sleep() }
        findViewById<MaterialButton>(R.id.btnDance).setOnClickListener { bearView.dance() }
        findViewById<MaterialButton>(R.id.btnJump).setOnClickListener { bearView.jump() }
        findViewById<MaterialButton>(R.id.btnKiss).setOnClickListener { bearView.kiss() }
        findViewById<MaterialButton>(R.id.btnSulk).setOnClickListener { bearView.sulk() }
        findViewById<MaterialButton>(R.id.btnTickle).setOnClickListener { bearView.tickle() }
        findViewById<MaterialButton>(R.id.btnFeed).setOnClickListener { bearView.eat() }
        findViewById<MaterialButton>(R.id.btnBall).setOnClickListener { bearView.ball() }
        findViewById<MaterialButton>(R.id.btnPeek).setOnClickListener { bearView.peek() }
        findViewById<MaterialButton>(R.id.btnBlowKiss).setOnClickListener { bearView.blowKiss() }
        findViewById<MaterialButton>(R.id.btnWave).setOnClickListener { bearView.wave() }

        findViewById<TextView>(R.id.bearName).setOnClickListener { showRenameDialog() }
    }

    private fun parsePetAnimal(a: String): BearView.PetAnimal = when (a) {
        "CAT" -> BearView.PetAnimal.CAT
        "RABBIT" -> BearView.PetAnimal.RABBIT
        "PANDA" -> BearView.PetAnimal.PANDA
        else -> BearView.PetAnimal.BEAR
    }

    private fun defaultPetName(a: String): String = getString(
        when (a) {
            "CAT" -> R.string.name_cat
            "RABBIT" -> R.string.name_rabbit
            "PANDA" -> R.string.name_panda
            else -> R.string.name_bear
        }
    )

    private fun applyPetName() {
        val name = if (Store.petName.isNotBlank()) Store.petName else defaultPetName(Store.petAnimal)
        bearView.petName = name
        findViewById<TextView>(R.id.bearName).text = name
    }

    private fun showRenameDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_rename, null)
        val input = view.findViewById<EditText>(R.id.nameInput)
        input.setText(if (Store.petName.isNotBlank()) Store.petName else defaultPetName(Store.petAnimal))
        input.setSelection(input.text.length)
        val dlg = AlertDialog.Builder(this)
            .setView(view)
            .create()
        view.findViewById<MaterialButton>(R.id.btnCancelName).setOnClickListener { dlg.dismiss() }
        view.findViewById<MaterialButton>(R.id.btnSaveName).setOnClickListener {
            Store.petName = input.text.toString().trim()
            Store.saveSettings()
            applyPetName()
            dlg.dismiss()
        }
        dlg.show()
    }

    private fun updateBearSoundIcon(btn: ImageButton) {
        btn.setImageResource(if (bearView.soundEnabled) R.drawable.ic_volume_on else R.drawable.ic_volume_off)
    }

    private fun renderSummary() {
        val r = CycleEngine.analyze()
        val today = LocalDate.now()
        renderInsights(r, today)

        val next = r.nextStart
        findViewById<TextView>(R.id.nextDate).text =
            if (next == null) "—" else r.nextRangeText()
        val confText = findViewById<TextView>(R.id.confText)
        val lastStartText = findViewById<TextView>(R.id.lastStartText)
        lastStartText.text = if (r.lastStart == null) ""
        else getString(R.string.summary_last_start, Dates.long(r.lastStart))
        lastStartText.visibility = if (r.lastStart == null) View.GONE else View.VISIBLE
        val irregularNote = findViewById<TextView>(R.id.irregularNote)
        confText.text = if (next == null) "" else
            "${getString(R.string.summary_confidence)}: ${getString(confLabelRes(r.confidence))}"
        irregularNote.visibility = if (r.irregular && r.hasData) View.VISIBLE else View.GONE
        val countdown = findViewById<TextView>(R.id.nextCountdown)
        val lateNote = findViewById<TextView>(R.id.lateNote)
        if (next == null) {
            countdown.text = getString(R.string.no_data_short)
            lateNote.visibility = View.GONE
        } else {
            val days = ChronoUnit.DAYS.between(today, next)
            countdown.text = when {
                days > 0 -> getString(R.string.days_left, days)
                days == 0L -> getString(R.string.today_mark)
                else -> getString(R.string.days_late, -days)
            }
            lateNote.visibility = if (days < 0) View.VISIBLE else View.GONE
        }
        findViewById<TextView>(R.id.ovDate).text =
            if (r.ovulation == null) "—" else Dates.short(r.ovulation)
        findViewById<TextView>(R.id.fertileDate).text =
            if (r.fertileStart == null || r.fertileEnd == null) "—"
            else "${Dates.short(r.fertileStart)} – ${Dates.short(r.fertileEnd)}"
        renderFertility(r, today)
        findViewById<TextView>(R.id.avgCycle).text = "${r.avgCycle} ${getString(R.string.days)}"
        findViewById<TextView>(R.id.avgPeriod).text = "${r.avgPeriod} ${getString(R.string.days)}"
        findViewById<TextView>(R.id.cycleCount).text = r.starts.size.toString()

        val acc = r.accuracy
        findViewById<TextView>(R.id.accuracyRate).text =
            if (acc.evaluated == 0) "—"
            else getString(R.string.summary_accuracy_value, acc.percent)
        findViewById<TextView>(R.id.accuracyDetail).text =
            if (acc.evaluated == 0) getString(R.string.summary_accuracy_none)
            else getString(
                R.string.summary_accuracy_detail,
                acc.evaluated,
                String.format(Locale.getDefault(), "%.1f", acc.meanAbs),
                acc.maxAbs
            )

        val list = findViewById<LinearLayout>(R.id.cycleList)
        list.removeAllViews()
        val groups = CycleEngine.groupConsecutive(Store.periodDates())
        if (groups.isEmpty()) {
            val tv = TextView(this)
            tv.text = getString(R.string.no_data_short)
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_dim))
            list.addView(tv)
            return
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        for (g in groups.reversed()) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_cycle, list, false)
            row.layoutParams = lp
            row.findViewById<TextView>(R.id.cycleRange).text =
                "${Dates.short(g.first())} – ${Dates.short(g.last())}"
            row.findViewById<TextView>(R.id.cycleLen).text =
                "${g.size} ${getString(R.string.days)}"
            list.addView(row)
        }
    }

    private fun renderInsights(r: CycleEngine.Result, today: LocalDate) {
        val info = Insights.phaseNow(r, today)
        val phaseTitle = phaseTitleRes(info.phase)
        val phaseDesc = when (info.phase) {
            Insights.Phase.MENSTRUAL -> R.string.phase_desc_menstrual
            Insights.Phase.FOLLICULAR -> R.string.phase_desc_follicular
            Insights.Phase.FERTILE -> R.string.phase_desc_fertile
            Insights.Phase.LUTEAL -> R.string.phase_desc_luteal
            Insights.Phase.UNKNOWN -> R.string.phase_desc_unknown
        }
        findViewById<TextView>(R.id.phaseText).text = getString(phaseTitle)
        findViewById<TextView>(R.id.phaseDay).text =
            if (info.cycleDay > 0) getString(R.string.phase_day, info.cycleDay) else ""
        findViewById<TextView>(R.id.phaseDesc).text = getString(phaseDesc)

        findViewById<CycleChartView>(R.id.cycleChart).setData(r.cycleLens, r.avgCycle)

        val list = findViewById<LinearLayout>(R.id.symptomList)
        list.removeAllViews()
        val names = resources.getStringArray(R.array.symptom_names)
        val top = Insights.topSymptoms(5)
        if (top.isEmpty()) {
            val tv = TextView(this)
            tv.text = getString(R.string.insights_no_symptoms)
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_dim))
            list.addView(tv)
        } else {
            val accent = Palette.accent(this)
            val track = Palette.accentTint(this)
            val maxCount = top.first().second
            for ((idx, count) in top) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = (10 * resources.displayMetrics.density).toInt() }
                }
                val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                head.addView(TextView(this).apply {
                    text = names.getOrElse(idx) { "" }
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.md_on_surface))
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                head.addView(TextView(this).apply {
                    text = getString(R.string.symptom_count, count)
                    setTextColor(accent)
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                row.addView(head)

                val bar = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundColor(track)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (6 * resources.displayMetrics.density).toInt()
                    ).apply { topMargin = (4 * resources.displayMetrics.density).toInt() }
                }
                bar.addView(View(this).apply {
                    setBackgroundColor(accent)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, count.toFloat())
                })
                bar.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (maxCount - count).toFloat())
                })
                row.addView(bar)
                list.addView(row)
            }
        }
    }

    private fun renderFertility(r: CycleEngine.Result, today: LocalDate) {
        val f = Fertility.analyze(today)
        val status = findViewById<TextView>(R.id.ttcStatus)
        val detail = findViewById<TextView>(R.id.ttcDetail)
        val chanceRow = findViewById<View>(R.id.ttcChanceRow)
        findViewById<BbtChartView>(R.id.bbtChart).setData(f.bbtSeries)
        if (!f.hasData) {
            status.text = getString(R.string.ttc_no_data)
            detail.visibility = View.GONE
            chanceRow.visibility = View.GONE
            return
        }
        detail.visibility = View.VISIBLE
        chanceRow.visibility = View.VISIBLE
        val chance = Fertility.conceptionChance(r, f, today)
        findViewById<TextView>(R.id.ttcChance).text = getString(
            when (chance) {
                Fertility.Chance.HIGH -> R.string.ttc_chance_high
                Fertility.Chance.MEDIUM -> R.string.ttc_chance_medium
                Fertility.Chance.LOW -> R.string.ttc_chance_low
            }
        )
        val shift = f.shift
        val lhPos = f.positiveLh
        val mucusPeak = f.peakMucus
        status.text = when {
            shift != null -> getString(R.string.ttc_status_shift, Dates.short(shift.date))
            lhPos != null -> getString(R.string.ttc_status_lh, Dates.short(lhPos.first))
            mucusPeak != null -> getString(R.string.ttc_status_mucus, Dates.short(mucusPeak.first))
            else -> getString(R.string.ttc_status_waiting)
        }
        val lines = ArrayList<String>()
        f.latestBbt?.let {
            lines.add(getString(R.string.ttc_latest_bbt, fmtTemp(it.value), Dates.short(it.date)))
        }
        f.latestMucus?.let {
            lines.add(getString(R.string.ttc_latest_mucus, mucusName(it.second), Dates.short(it.first)))
        }
        f.latestLh?.let {
            lines.add(getString(R.string.ttc_latest_lh, lhName(it.second), Dates.short(it.first)))
        }
        shift?.let { lines.add(getString(R.string.ttc_shift_detail, it.rise)) }
        detail.text = lines.joinToString("\n")
    }

    private fun fmtTemp(v: Double): String =
        String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    private fun mucusName(i: Int): String =
        resources.getStringArray(R.array.mucus_names).getOrElse(i) { "" }

    private fun lhName(i: Int): String =
        resources.getStringArray(R.array.lh_names).getOrElse(i) { "" }

    private fun summaryText(): String {
        val r = CycleEngine.analyze()
        val sb = StringBuilder()
        sb.append(getString(R.string.app_name)).append(" · ").append(getString(R.string.nav_summary)).append("\n\n")
        sb.append(getString(R.string.summary_cycle_len)).append(": ${r.avgCycle} ")
            .append(getString(R.string.days)).append("\n")
        sb.append(getString(R.string.summary_period_len)).append(": ${r.avgPeriod} ")
            .append(getString(R.string.days)).append("\n")
        if (r.lastStart != null) {
            sb.append("Son adet: ").append(Dates.long(r.lastStart)).append("\n")
        }
        if (r.nextStart != null) {
            sb.append(getString(R.string.summary_next_period)).append(": ")
                .append(r.nextRangeText()).append("\n")
            sb.append(getString(R.string.summary_confidence)).append(": ")
                .append(getString(confLabelRes(r.confidence))).append("\n")
        }
        if (r.accuracy.evaluated > 0) {
            sb.append(getString(R.string.summary_accuracy)).append(": ")
                .append(getString(R.string.summary_accuracy_value, r.accuracy.percent))
                .append(" (").append(r.accuracy.evaluated).append(" döngü, ort. ±")
                .append(String.format(Locale.getDefault(), "%.1f", r.accuracy.meanAbs))
                .append(" gün)\n")
        }
        if (r.ovulation != null) {
            sb.append(getString(R.string.summary_ovulation)).append(": ")
                .append(Dates.long(r.ovulation)).append("\n")
        }
        if (r.fertileStart != null && r.fertileEnd != null) {
            sb.append(getString(R.string.summary_fertile)).append(": ")
                .append(Dates.long(r.fertileStart)).append(" – ")
                .append(Dates.long(r.fertileEnd)).append("\n")
        }
        return sb.toString()
    }

    private fun shareSummary() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
            putExtra(Intent.EXTRA_TEXT, summaryText())
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun shareBackup() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.backup_subject))
            putExtra(Intent.EXTRA_TEXT, Store.toJsonString())
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    // ---------------- Settings ----------------

    private fun setupSettingsPage() {
        val swReminder = findViewById<MaterialSwitch>(R.id.swReminder)
        swReminder.isChecked = Store.notifEnabled
        swReminder.setOnCheckedChangeListener { _, checked ->
            Store.notifEnabled = checked
            Store.saveSettings()
            if (checked) maybeRequestNotificationPermission()
            ReminderScheduler.reschedule(this)
        }

        val remindGroup = findViewById<MaterialButtonToggleGroup>(R.id.remindGroup)
        checkRemind(remindGroup, Store.remindDaysBefore)
        remindGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            Store.remindDaysBefore = when (checkedId) {
                R.id.remind1 -> 1
                R.id.remind2 -> 2
                R.id.remind3 -> 3
                R.id.remind4 -> 4
                else -> 5
            }
            Store.saveSettings()
            ReminderScheduler.reschedule(this)
        }

        val swDaily = findViewById<MaterialSwitch>(R.id.swDaily)
        swDaily.isChecked = Store.dailyReminder
        swDaily.setOnCheckedChangeListener { _, checked ->
            Store.dailyReminder = checked
            Store.saveSettings()
            if (checked) maybeRequestNotificationPermission()
            ReminderScheduler.reschedule(this)
        }

        val timeBtn = findViewById<MaterialButton>(R.id.btnDailyTime)
        timeBtn.text = timeLabel(Store.dailyMinute)
        timeBtn.setOnClickListener { pickDailyTime(timeBtn) }

        val swMed = findViewById<MaterialSwitch>(R.id.swMed)
        swMed.isChecked = Store.medEnabled
        swMed.setOnCheckedChangeListener { _, checked ->
            Store.medEnabled = checked
            Store.saveSettings()
            if (checked) maybeRequestNotificationPermission()
            ReminderScheduler.reschedule(this)
        }
        val medTime = findViewById<MaterialButton>(R.id.btnMedTime)
        medTime.text = timeLabel(Store.medMinute)
        medTime.setOnClickListener { pickMedTime(medTime) }
        val etMedLabel = findViewById<EditText>(R.id.etMedLabel)
        etMedLabel.setText(Store.medLabel)
        etMedLabel.doAfterTextChanged {
            Store.medLabel = it?.toString()?.trim() ?: ""
            Store.saveSettings()
        }

        val etCycle = findViewById<EditText>(R.id.etCycle)
        val etPeriod = findViewById<EditText>(R.id.etPeriod)
        if (Store.cycleOverride > 0) etCycle.setText(Store.cycleOverride.toString())
        if (Store.periodOverride > 0) etPeriod.setText(Store.periodOverride.toString())
        findViewById<MaterialButton>(R.id.btnSaveOverrides).setOnClickListener {
            Store.cycleOverride = etCycle.text.toString().trim().toIntOrNull() ?: 0
            Store.periodOverride = etPeriod.text.toString().trim().toIntOrNull() ?: 0
            Store.saveSettings()
            Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
            refreshAll()
        }

        findViewById<MaterialButton>(R.id.btnBackup).setOnClickListener {
            keepUnlocked = true
            exportLauncher.launch(getString(R.string.backup_default_name))
        }
        findViewById<MaterialButton>(R.id.btnRestore).setOnClickListener {
            keepUnlocked = true
            importLauncher.launch(arrayOf("*/*"))
        }

        findViewById<MaterialButton>(R.id.btnClear).setOnClickListener { confirmClear() }

        findViewById<MaterialButton>(R.id.btnReport).setOnClickListener {
            if (!Store.hasAny()) {
                toast(getString(R.string.no_records))
            } else {
                pdfLauncher.launch(getString(R.string.report_default_name))
            }
        }
        findViewById<MaterialButton>(R.id.btnCsv).setOnClickListener {
            if (!Store.hasAny()) {
                toast(getString(R.string.no_records))
            } else {
                csvLauncher.launch(getString(R.string.csv_default_name))
            }
        }

        val swIrr = findViewById<MaterialSwitch>(R.id.swIrregular)
        swIrr.isChecked = Store.irregularMode
        swIrr.setOnCheckedChangeListener { _, checked ->
            Store.irregularMode = checked
            Store.saveSettings()
            refreshAll()
        }

        setupLockSwitch()
        setupPaletteCard()

        val about = findViewById<TextView>(R.id.aboutText)
        val ver = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "?"
        about.text = getString(R.string.settings_about_body, getString(R.string.app_name), ver)
    }

    private fun timeLabel(minutes: Int): String =
        String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

    private fun confLabelRes(c: CycleEngine.Confidence): Int = when (c) {
        CycleEngine.Confidence.HIGH -> R.string.conf_high
        CycleEngine.Confidence.MEDIUM -> R.string.conf_medium
        CycleEngine.Confidence.LOW -> R.string.conf_low
    }

    private fun pickDailyTime(button: MaterialButton) {
        val m = Store.dailyMinute
        TimePickerDialog(
            this,
            { _, h, min ->
                Store.dailyMinute = h * 60 + min
                Store.saveSettings()
                button.text = timeLabel(Store.dailyMinute)
                ReminderScheduler.reschedule(this)
            },
            m / 60, m % 60, true
        ).show()
    }

    private fun pickMedTime(button: MaterialButton) {
        val m = Store.medMinute
        TimePickerDialog(
            this,
            { _, h, min ->
                Store.medMinute = h * 60 + min
                Store.saveSettings()
                button.text = timeLabel(Store.medMinute)
                ReminderScheduler.reschedule(this)
            },
            m / 60, m % 60, true
        ).show()
    }

    private fun checkRemind(group: MaterialButtonToggleGroup, days: Int) {
        val id = when (days.coerceIn(1, 5)) {
            1 -> R.id.remind1
            2 -> R.id.remind2
            3 -> R.id.remind3
            4 -> R.id.remind4
            else -> R.id.remind5
        }
        group.check(id)
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_clear)
            .setMessage(R.string.settings_clear_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                Store.clearAll()
                refreshAll()
                Toast.makeText(this, getString(R.string.deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------- Backup / Restore ----------------

    private fun writeBackup(uri: Uri) {
        val ok = runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(Store.toJsonString().toByteArray()) }
            true
        }.getOrDefault(false)
        Toast.makeText(
            this,
            getString(if (ok) R.string.backup_done else R.string.backup_failed),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun writeReport(uri: Uri) {
        val ok = runCatching {
            contentResolver.openOutputStream(uri)?.use { ReportExporter.writePdf(this, it) }
            true
        }.getOrDefault(false)
        Toast.makeText(
            this,
            getString(if (ok) R.string.report_done else R.string.report_failed),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun writeCsv(uri: Uri) {
        val ok = runCatching {
            contentResolver.openOutputStream(uri)?.use { ReportExporter.writeCsv(this, it) }
            true
        }.getOrDefault(false)
        Toast.makeText(
            this,
            getString(if (ok) R.string.report_done else R.string.report_failed),
            Toast.LENGTH_SHORT
        ).show()
    }

    // ---------------- Onboarding ----------------

    private fun maybeShowOnboarding() {
        if (Store.onboarded || Store.hasAny()) return
        val view = layoutInflater.inflate(R.layout.dialog_onboarding, null)
        val dateBtn = view.findViewById<MaterialButton>(R.id.onboardDate)
        val cycleInput = view.findViewById<EditText>(R.id.onboardCycle)

        fun refreshDate() {
            dateBtn.text = Dates.long(onboardDate)
        }
        refreshDate()
        dateBtn.setOnClickListener {
            DatePickerDialog(
                this,
                { _, yy, mm, dd ->
                    onboardDate = LocalDate.of(yy, mm + 1, dd)
                    refreshDate()
                },
                onboardDate.year, onboardDate.monthValue - 1, onboardDate.dayOfMonth
            ).show()
        }
        view.findViewById<MaterialButton>(R.id.onboardToday).setOnClickListener {
            onboardDate = LocalDate.now()
            refreshDate()
        }

        AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .setPositiveButton(R.string.onboard_start) { _, _ ->
                Store.markPeriodStart(onboardDate)
                val ovr = cycleInput.text.toString().trim().toIntOrNull() ?: 0
                if (ovr in 15..90) Store.cycleOverride = ovr
                Store.onboarded = true
                Store.saveSettings()
                maybeRequestNotificationPermission()
                refreshAll()
                ReminderScheduler.reschedule(this)
                toast(getString(R.string.onboard_done))
            }
            .setNegativeButton(R.string.onboard_skip) { _, _ ->
                Store.onboarded = true
                Store.saveSettings()
            }
            .show()
    }

    private fun confirmRestore(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_title)
            .setMessage(R.string.restore_confirm)
            .setPositiveButton(R.string.restore_action) { _, _ -> doRestore(uri) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun doRestore(uri: Uri) {
        val text = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        val ok = text != null && Store.importFrom(text)
        Toast.makeText(
            this,
            getString(if (ok) R.string.restore_done else R.string.restore_failed),
            Toast.LENGTH_SHORT
        ).show()
        if (ok) {
            refreshAll()
            ReminderScheduler.reschedule(this)
        }
    }

    // ---------------- Lock ----------------

    private fun setupLock() {
        lockOverlay = findViewById(R.id.lockOverlay)
        etPin = findViewById(R.id.etPin)
        val bioBtn = findViewById<MaterialButton>(R.id.btnBiometric)
        biometricAvailable = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
        bioBtn.visibility = if (biometricAvailable) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.btnUnlock).setOnClickListener { tryUnlock() }
        bioBtn.setOnClickListener { promptBiometric() }
    }

    private fun showLock() {
        etPin.setText("")
        lockOverlay.visibility = View.VISIBLE
        if (biometricAvailable) promptBiometric()
    }

    private fun tryUnlock() {
        if (Store.verifyPin(etPin.text.toString())) {
            unlock()
        } else {
            etPin.setText("")
            Toast.makeText(this, getString(R.string.lock_wrong_pin), Toast.LENGTH_SHORT).show()
        }
    }

    private fun unlock() {
        sessionUnlocked = true
        lockOverlay.visibility = View.GONE
        maybeShowSpecialWelcome()
    }

    private fun promptBiometric() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlock()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_title))
            .setNegativeButtonText(getString(R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        runCatching { prompt.authenticate(info) }
    }

    private fun setupLockSwitch() {
        swLock = findViewById(R.id.swLock)
        swLock.isChecked = Store.lockEnabled
        swLock.setOnCheckedChangeListener { _, checked ->
            if (lockToggleGuard) return@setOnCheckedChangeListener
            if (checked) {
                if (Store.pinHash.isEmpty()) {
                    setLockSwitch(false)
                    promptSetPin()
                } else {
                    Store.lockEnabled = true
                    Store.saveSettings()
                    applySecureFlag()
                    toast(getString(R.string.lock_enabled))
                }
            } else {
                setLockSwitch(true)
                promptCurrentPin()
            }
        }
    }

    private fun setLockSwitch(value: Boolean) {
        lockToggleGuard = true
        swLock.isChecked = value
        lockToggleGuard = false
    }

    private fun promptSetPin() {
        val view = layoutInflater.inflate(R.layout.dialog_set_pin, null)
        val dlg = AlertDialog.Builder(this)
            .setTitle(R.string.lock_set_title)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p1 = view.findViewById<EditText>(R.id.etPinNew).text.toString()
                val p2 = view.findViewById<EditText>(R.id.etPinConfirm).text.toString()
                when {
                    p1.length < 4 -> toast(getString(R.string.lock_short))
                    p1 != p2 -> toast(getString(R.string.lock_mismatch))
                    else -> {
                        Store.pinHash = Store.hashPin(p1)
                        Store.lockEnabled = true
                        Store.saveSettings()
                        setLockSwitch(true)
                        applySecureFlag()
                        toast(getString(R.string.lock_enabled))
                        dlg.dismiss()
                    }
                }
            }
        }
        dlg.show()
    }

    private fun promptCurrentPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.lock_enter_current)
            setPadding(48, 32, 48, 32)
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle(R.string.lock_set_title)
            .setView(input)
            .setPositiveButton(R.string.lock_unlock, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (Store.verifyPin(input.text.toString())) {
                    Store.lockEnabled = false
                    Store.saveSettings()
                    setLockSwitch(false)
                    applySecureFlag()
                    toast(getString(R.string.lock_disabled))
                    dlg.dismiss()
                } else {
                    input.setText("")
                    toast(getString(R.string.lock_wrong_pin))
                }
            }
        }
        dlg.show()
    }

    private fun setupPaletteCard() {
        val card = findViewById<View>(R.id.paletteCard)
        if (!resources.getBoolean(R.bool.special_edition)) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        val row = findViewById<LinearLayout>(R.id.paletteRow)
        val name = findViewById<TextView>(R.id.paletteName)
        row.removeAllViews()
        val density = resources.displayMetrics.density
        Palette.entries.forEach { entry ->
            val dot = View(this)
            val size = (34 * density).toInt()
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginEnd = (8 * density).toInt()
            dot.layoutParams = lp
            dot.background = swatchDrawable(entry.swatch, entry.key == Store.palette)
            dot.contentDescription = getString(entry.label)
            dot.setOnClickListener {
                if (Store.palette == entry.key) return@setOnClickListener
                Store.palette = entry.key
                Store.saveSettings()
                keepUnlocked = true
                recreate()
            }
            row.addView(dot)
        }
        name.text = getString(Palette.current().label)
    }

    private fun swatchDrawable(colorRes: Int, selected: Boolean): GradientDrawable {
        val density = resources.displayMetrics.density
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(ContextCompat.getColor(this, colorRes))
        if (selected) {
            drawable.setStroke((3 * density).toInt(), ContextCompat.getColor(this, R.color.md_on_surface))
        } else {
            drawable.setStroke((1 * density).toInt(), 0x33000000)
        }
        return drawable
    }

    private fun maybeShowSpecialWelcome() {
        if (welcomeShown) return
        if (!resources.getBoolean(R.bool.special_edition)) return
        if (Store.lockEnabled && !sessionUnlocked && !keepUnlocked) return
        val lines = resources.getStringArray(R.array.special_welcomes)
        if (lines.isEmpty()) return
        welcomeShown = true
        val view = findViewById<TextView>(R.id.specialWelcome)
        view.text = lines[Store.nextWelcomeIndex(lines.size)]
        view.alpha = 0f
        view.translationY = -24f * resources.displayMetrics.density
        view.visibility = View.VISIBLE
        view.setOnClickListener { hideSpecialWelcome() }
        view.animate().alpha(1f).translationY(0f).setDuration(420).start()
        view.postDelayed({ hideSpecialWelcome() }, 5200)
    }

    private fun hideSpecialWelcome() {
        val view = findViewById<TextView>(R.id.specialWelcome)
        if (view.visibility != View.VISIBLE) return
        view.animate().alpha(0f).translationY(-18f * resources.displayMetrics.density)
            .setDuration(300)
            .withEndAction { view.visibility = View.GONE }
            .start()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ---------------- Permissions ----------------

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF
                )
            }
        }
    }

    companion object {
        private const val REQ_NOTIF = 2001
        private const val KEY_TAB = "tab_index"
        var sessionUnlocked = false
    }
}
