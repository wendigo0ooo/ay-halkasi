package com.wendigo.ayhalkasi

import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.time.LocalDate
import java.util.Locale

class DayEditor(root: View, private val onSaved: () -> Unit) {

    private val rootView = root
    private val flowGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.flowGroup)
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
        rootView.findViewById<View>(R.id.btnQuickStart).setOnClickListener {
            check(flowGroup, 2)
            save()
        }
        rootView.findViewById<View>(R.id.btnQuickEnd).setOnClickListener {
            check(flowGroup, 0)
            save()
        }
    }

    fun load(d: LocalDate) {
        date = d
        val log = Store.get(d) ?: DayLog()
        check(flowGroup, log.flow.coerceIn(0, 3))
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
    }

    private fun save() {
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
            Toast.makeText(rootView.context, R.string.ttc_bbt_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        val log = DayLog(
            flow = index(flowGroup),
            pain = index(painGroup),
            mood = mood,
            symptoms = symptoms,
            note = noteInput.text.toString().trim(),
            bbt = bbt,
            mucus = chipIndex(mucusChips),
            lh = index(lhGroup)
        )
        Store.put(date, log)
        onSaved()
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
        return g.indexOfChild(g.findViewById(id)).coerceAtLeast(0)
    }

    private fun fmtTemp(v: Double): String =
        String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    private fun check(g: MaterialButtonToggleGroup, i: Int) {
        if (g.childCount == 0) return
        val child = g.getChildAt(i.coerceIn(0, g.childCount - 1)) as? MaterialButton ?: return
        g.check(child.id)
    }
}
