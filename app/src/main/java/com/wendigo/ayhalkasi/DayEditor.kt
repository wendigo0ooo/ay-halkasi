package com.wendigo.ayhalkasi

import android.view.View
import android.widget.EditText
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.time.LocalDate

class DayEditor(root: View, private val onSaved: () -> Unit) {

    private val flowGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.flowGroup)
    private val painGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.painGroup)
    private val moodGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.moodGroup)
    private val symptomChips = root.findViewById<ChipGroup>(R.id.symptomChips)
    private val noteInput = root.findViewById<EditText>(R.id.noteInput)

    var date: LocalDate = LocalDate.now()
        private set

    init {
        root.findViewById<View>(R.id.btnSave).setOnClickListener { save() }
        root.findViewById<View>(R.id.btnDelete).setOnClickListener {
            Store.remove(date)
            onSaved()
        }
    }

    fun load(d: LocalDate) {
        date = d
        val log = Store.get(d) ?: DayLog()
        check(flowGroup, log.flow.coerceIn(0, 3))
        check(painGroup, log.pain.coerceIn(0, 3))
        check(moodGroup, (log.mood + 2).coerceIn(0, 4))
        val syms = log.symptoms
        for (i in 0 until symptomChips.childCount) {
            (symptomChips.getChildAt(i) as Chip).isChecked = i in syms
        }
        noteInput.setText(log.note)
    }

    private fun save() {
        val symptoms = mutableSetOf<Int>()
        for (i in 0 until symptomChips.childCount) {
            if ((symptomChips.getChildAt(i) as Chip).isChecked) symptoms.add(i)
        }
        val log = DayLog(
            flow = index(flowGroup),
            pain = index(painGroup),
            mood = index(moodGroup) - 2,
            symptoms = symptoms,
            note = noteInput.text.toString().trim()
        )
        Store.put(date, log)
        onSaved()
    }

    private fun index(g: MaterialButtonToggleGroup): Int {
        val id = g.checkedButtonId
        if (id == View.NO_ID) return 0
        return g.indexOfChild(g.findViewById(id)).coerceAtLeast(0)
    }

    private fun check(g: MaterialButtonToggleGroup, i: Int) {
        if (g.childCount == 0) return
        val child = g.getChildAt(i.coerceIn(0, g.childCount - 1)) as? MaterialButton ?: return
        g.check(child.id)
    }
}
