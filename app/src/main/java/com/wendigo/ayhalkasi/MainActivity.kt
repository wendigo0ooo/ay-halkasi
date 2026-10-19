package com.wendigo.ayhalkasi

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

    private val weekLabels = arrayOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContentView(R.layout.activity_main)

        pages = listOf(
            findViewById(R.id.pageCalendar),
            findViewById(R.id.pageToday),
            findViewById(R.id.pageSummary),
            findViewById(R.id.pageSettings)
        )

        setupNavigation()
        setupCalendarPage()
        setupTodayPage()
        setupSummaryPage()
        setupSettingsPage()

        selectTab(0)
        maybeRequestNotificationPermission()
        ReminderScheduler.reschedule(this)
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
        ReminderScheduler.reschedule(this)
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
                    else -> 3
                }
            )
            true
        }
    }

    private fun selectTab(index: Int) {
        pages.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        if (index == 0) renderCalendar()
        if (index == 1) loadTodayEditor()
        if (index == 2) renderSummary()
    }

    private fun refreshAll() {
        renderCalendar()
        loadTodayEditor()
        renderSummary()
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
            cells.add(CalendarAdapter.Cell(date, CycleEngine.stateFor(date, result), date == today))
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
            hint.text = "${getString(R.string.summary_next_period)}: ${Dates.short(next)} · $whenText"
        }
        findViewById<TextView>(R.id.calendarEmpty).visibility =
            if (Store.hasAny()) View.GONE else View.VISIBLE
    }

    // ---------------- Today ----------------

    private fun setupTodayPage() {
        todayEditor = DayEditor(findViewById(R.id.dayEditorRoot)) { onDaySaved() }
        findViewById<ImageButton>(R.id.btnPrevDay).setOnClickListener {
            currentDay = currentDay.minusDays(1)
            loadTodayEditor()
        }
        findViewById<ImageButton>(R.id.btnNextDay).setOnClickListener {
            currentDay = currentDay.plusDays(1)
            loadTodayEditor()
        }
        findViewById<MaterialButton>(R.id.btnGoToday).setOnClickListener {
            currentDay = LocalDate.now()
            loadTodayEditor()
        }
    }

    private fun loadTodayEditor() {
        findViewById<TextView>(R.id.todayDate).text = Dates.longDay(currentDay)
        todayEditor.load(currentDay)
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

    private fun renderSummary() {
        val r = CycleEngine.analyze()
        val today = LocalDate.now()

        val next = r.nextStart
        findViewById<TextView>(R.id.nextDate).text =
            if (next == null) "—" else Dates.long(next)
        val countdown = findViewById<TextView>(R.id.nextCountdown)
        if (next == null) {
            countdown.text = getString(R.string.no_data_short)
        } else {
            val days = ChronoUnit.DAYS.between(today, next)
            countdown.text = when {
                days > 0 -> getString(R.string.days_left, days)
                days == 0L -> getString(R.string.today_mark)
                else -> getString(R.string.days_late, -days)
            }
        }
        findViewById<TextView>(R.id.ovDate).text =
            if (r.ovulation == null) "—" else Dates.short(r.ovulation)
        findViewById<TextView>(R.id.fertileDate).text =
            if (r.fertileStart == null || r.fertileEnd == null) "—"
            else "${Dates.short(r.fertileStart)} – ${Dates.short(r.fertileEnd)}"
        findViewById<TextView>(R.id.avgCycle).text = "${r.avgCycle} ${getString(R.string.days)}"
        findViewById<TextView>(R.id.avgPeriod).text = "${r.avgPeriod} ${getString(R.string.days)}"
        findViewById<TextView>(R.id.cycleCount).text = r.starts.size.toString()

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
                .append(Dates.long(r.nextStart)).append("\n")
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

        findViewById<MaterialButton>(R.id.btnClear).setOnClickListener { confirmClear() }

        val about = findViewById<TextView>(R.id.aboutText)
        val ver = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "?"
        about.text = getString(R.string.settings_about_body, getString(R.string.app_name), ver)
    }

    private fun timeLabel(minutes: Int): String =
        String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

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
    }
}
