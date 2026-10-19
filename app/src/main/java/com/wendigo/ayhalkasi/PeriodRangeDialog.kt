package com.wendigo.ayhalkasi

import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object PeriodRangeDialog {

    fun show(
        context: Context,
        anchor: LocalDate,
        replaceAnchorGroup: Boolean = true,
        prefillExisting: Boolean = true,
        onSaved: () -> Unit
    ) {
        if (anchor.isAfter(LocalDate.now())) {
            Toast.makeText(context, R.string.period_future, Toast.LENGTH_SHORT).show()
            return
        }
        val existing = if (prefillExisting) Store.periodGroupFor(anchor) else null
        var start = existing?.first() ?: anchor
        var end: LocalDate? = existing?.last() ?: anchor
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_period_range, null)
        val startBtn = view.findViewById<MaterialButton>(R.id.rangeStartBtn)
        val endBtn = view.findViewById<MaterialButton>(R.id.rangeEndBtn)
        val ongoingBtn = view.findViewById<MaterialButton>(R.id.rangeOngoingBtn)
        val saveBtn = view.findViewById<MaterialButton>(R.id.rangeSaveBtn)
        val cancelBtn = view.findViewById<MaterialButton>(R.id.rangeCancelBtn)

        fun render() {
            startBtn.text = context.getString(R.string.period_range_start, Dates.long(start))
            endBtn.text =
                if (end == null) context.getString(R.string.period_range_end_none)
                else context.getString(R.string.period_range_end, Dates.long(end!!))
            ongoingBtn.text = context.getString(
                if (end == null) R.string.period_range_ongoing_on
                else R.string.period_range_ongoing
            )
        }
        render()

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.period_range_title)
            .setView(view)
            .create()

        startBtn.setOnClickListener {
            pickDate(context, start) { picked ->
                start = picked
                if (end != null && end!!.isBefore(start)) end = start
                render()
            }
        }
        endBtn.setOnClickListener {
            pickDate(context, end ?: start) { picked ->
                end = picked
                if (end!!.isBefore(start)) start = end!!
                render()
            }
        }
        ongoingBtn.setOnClickListener {
            end = null
            render()
        }
        cancelBtn.setOnClickListener { dialog.dismiss() }
        saveBtn.setOnClickListener {
            if (Store.savePeriodRange(anchor, start, end, replaceAnchorGroup)) {
                dialog.dismiss()
                onSaved()
            } else {
                Toast.makeText(
                    context,
                    if (end != null && ChronoUnit.DAYS.between(start, end!!).toInt() + 1 > 15)
                        R.string.period_range_too_long
                    else R.string.period_range_invalid,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        dialog.show()
    }

    private fun pickDate(context: Context, initial: LocalDate, onPick: (LocalDate) -> Unit) {
        val picker = DatePickerDialog(
            context,
            { _, year, month, day -> onPick(LocalDate.of(year, month + 1, day)) },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        )
        picker.datePicker.maxDate = System.currentTimeMillis()
        picker.show()
    }
}
