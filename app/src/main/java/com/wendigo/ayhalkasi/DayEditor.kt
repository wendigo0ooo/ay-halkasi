package com.wendigo.ayhalkasi

import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

class DayEditor(root: View, private val onSaved: () -> Unit) {

    private val rootView = root
    private val periodStatus = root.findViewById<TextView>(R.id.periodStatus)
    private val painGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.painGroup)
    private val moodGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.moodGroup)
    private val symptomChips = root.findViewById<ChipGroup>(R.id.symptomChips)
    private val noteInput = root.findViewById<EditText>(R.id.noteInput)
    private val bbtInput = root.findViewById<EditText>(R.id.bbtInput)
    private val mucusChips = root.findViewById<ChipGroup>(R.id.mucusChips)
    private val lhGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.lhGroup)

    var date: LocalDate = LocalDate.now()
        private set

    init {
        root.findViewById<View>(R.id.btnSave).setOnClickListener { save() }
        root.findViewById<View>(R.id.btnDelete).setOnClickListener { confirmDelete() }
        rootView.findViewById<View>(R.id.btnQuickStart).setOnClickListener { confirmStart() }
        rootView.findViewById<View>(R.id.btnQuickEnd).setOnClickListener {
            if (date.isAfter(LocalDate.now())) {
                toast(R.string.period_future)
                return@setOnClickListener
            }
            if (!writeLog()) return@setOnClickListener
            if (Store.markPeriodEnd(date)) {
                load(date)
                onSaved()
            } else {
                toast(R.string.period_end_no_start)
            }
        }
        rootView.findViewById<View>(R.id.btnEditRange).setOnClickListener {
            if (date.isAfter(LocalDate.now())) {
                toast(R.string.period_future)
                return@setOnClickListener
            }
            if (!writeLog()) return@setOnClickListener
            PeriodRangeDialog.show(rootView.context, date) {
                load(date)
                onSaved()
            }
        }
    }

    fun load(d: LocalDate) {
        date = d
        val log = Store.get(d) ?: DayLog()
        check(painGroup, log.pain.coerceIn(0, 3))
        if (log.mood in 1..5) {
            check(moodGroup, log.mood - 1)
        } else {
            for (i in 0 until moodGroup.childCount) {
                (moodGroup.getChildAt(i) as? MaterialButton)?.isChecked = false
            }
        }
        val syms = log.symptoms
        for (i in 0 until symptomChips.childCount) {
            (symptomChips.getChildAt(i) as Chip).isChecked = i in syms
        }
        noteInput.setText(log.note)
        bbtInput.setText(log.bbt?.let { fmtTemp(it) } ?: "")
        val mucus = log.mucus.coerceIn(0, mucusChips.childCount - 1)
        for (i in 0 until mucusChips.childCount) {
            (mucusChips.getChildAt(i) as Chip).isChecked = i == mucus
        }
        check(lhGroup, log.lh.coerceIn(0, lhGroup.childCount - 1))
        renderPeriodStatus(log.flow)
    }

    private fun renderPeriodStatus(flow: Int) {
        val group = Store.periodGroupFor(date)
        periodStatus.text = when {
            flow >= 2 && group == null -> rootView.context.getString(R.string.day_period_period, 1)
            flow >= 2 && group!!.size == 1 -> rootView.context.getString(R.string.day_period_single)
            flow >= 2 && group!!.first() == date ->
                rootView.context.getString(R.string.day_period_start, group.size)
            flow >= 2 && group!!.last() == date ->
                rootView.context.getString(R.string.day_period_end, group.size)
            flow >= 2 -> rootView.context.getString(
                R.string.day_period_period,
                ChronoUnit.DAYS.between(group!!.first(), date).toInt() + 1
            )
            flow == 1 -> rootView.context.getString(R.string.day_period_spot)
            else -> rootView.context.getString(R.string.day_period_none)
        }
    }

    private fun confirmStart() {
        if (date.isAfter(LocalDate.now())) {
            toast(R.string.period_future)
            return
        }
        if (Store.periodGroupFor(date) != null) {
            markStart()
            return
        }
        val prev = CycleEngine.analyze().lastStart?.takeIf { it.isBefore(date) }
        val gap = prev?.let { ChronoUnit.DAYS.between(it, date) }
        if (gap != null && gap in 1..20) {
            AlertDialog.Builder(rootView.context)
                .setTitle(R.string.period_short_cycle_title)
                .setMessage(rootView.context.getString(R.string.period_short_cycle_msg, gap))
                .setPositiveButton(R.string.period_confirm_yes) { _, _ -> markStart() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            markStart()
        }
    }

    private fun markStart() {
        if (!writeLog()) return
        Store.markPeriodStart(date)
        load(date)
        onSaved()
    }

    private fun save() {
        if (!writeLog()) return
        onSaved()
    }

    private fun writeLog(): Boolean {
        if (date.isAfter(LocalDate.now())) {
            toast(R.string.future_no_log)
            return false
        }
        val symptoms = mutableSetOf<Int>()
        for (i in 0 until symptomChips.childCount) {
            if ((symptomChips.getChildAt(i) as Chip).isChecked) symptoms.add(i)
        }
        val moodId = moodGroup.checkedButtonId
        val mood = if (moodId == View.NO_ID) 0
        else moodGroup.indexOfChild(moodGroup.findViewById<View>(moodId)) + 1
        val rawTemp = bbtInput.text.toString().trim().replace(',', '.')
        val bbt = if (rawTemp.isEmpty()) null else rawTemp.toDoubleOrNull()?.takeIf { it in 30.0..45.0 }
        if (rawTemp.isNotEmpty() && bbt == null) {
            toast(R.string.ttc_bbt_invalid)
            return false
        }
        val log = DayLog(
            flow = Store.get(date)?.flow ?: 0,
            pain = index(painGroup),
            mood = mood,
            symptoms = symptoms,
            note = noteInput.text.toString().trim(),
            bbt = bbt,
            mucus = chipIndex(mucusChips),
            lh = index(lhGroup)
        )
        Store.put(date, log)
        return true
    }

    private fun confirmDelete() {
        AlertDialog.Builder(rootView.context)
            .setTitle(R.string.day_delete_title)
            .setMessage(R.string.day_delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                Store.remove(date)
                onSaved()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chipIndex(g: ChipGroup): Int {
        val id = g.checkedChipId
        if (id == View.NO_ID) return 0
        return g.indexOfChild(g.findViewById(id)).coerceAtLeast(0)
    }

    private fun index(g: MaterialButtonToggleGroup): Int {
        val id = g.checkedButtonId
        if (id == View.NO_ID) return 0
        return g.indexOfChild(g.findViewById<View>(id)).coerceAtLeast(0)
    }

    private fun fmtTemp(v: Double): String =
        String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    private fun check(g: MaterialButtonToggleGroup, i: Int) {
        if (g.childCount == 0) return
        val child = g.getChildAt(i.coerceIn(0, g.childCount - 1)) as? MaterialButton ?: return
        g.check(child.id)
    }

    private fun toast(res: Int) {
        Toast.makeText(rootView.context, res, Toast.LENGTH_SHORT).show()
    }
}
