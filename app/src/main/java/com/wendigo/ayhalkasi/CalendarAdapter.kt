package com.wendigo.ayhalkasi

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate

class CalendarAdapter(private val onDayClick: (LocalDate) -> Unit) :
    RecyclerView.Adapter<CalendarAdapter.VH>() {

    data class Cell(
        val date: LocalDate?,
        val state: DayState,
        val isToday: Boolean,
        val hasLog: Boolean = false
    )

    private val cells = ArrayList<Cell>()

    fun submit(list: List<Cell>) {
        cells.clear()
        cells.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = cells.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(cells[position])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val num = itemView.findViewById<TextView>(R.id.dayNumber)
        private val dot = itemView.findViewById<View>(R.id.dayDot)

        fun bind(c: Cell) {
            val ctx = itemView.context
            val d = c.date
            if (d == null) {
                num.text = ""
                num.background = null
                num.visibility = View.INVISIBLE
                dot.visibility = View.INVISIBLE
                itemView.setOnClickListener(null)
                return
            }
            num.visibility = View.VISIBLE
            num.text = d.dayOfMonth.toString()
            dot.visibility = if (c.hasLog) View.VISIBLE else View.INVISIBLE

            val onPrimary = Palette.onPrimary(ctx)
            var bg = R.drawable.day_normal
            var color = ContextCompat.getColor(ctx, R.color.md_on_surface)
            var bold = false

            when (c.state) {
                DayState.PERIOD -> {
                    bg = R.drawable.day_period; color = onPrimary; bold = true
                }
                DayState.SPOTTING -> {
                    bg = R.drawable.day_spotting
                    color = ContextCompat.getColor(ctx, R.color.period)
                    bold = true
                }
                DayState.PRED_PERIOD -> {
                    bg = R.drawable.day_period_pred
                    color = ContextCompat.getColor(ctx, R.color.period)
                }
                DayState.OVULATION -> {
                    bg = R.drawable.day_ovulation; color = onPrimary; bold = true
                }
                DayState.FERTILE -> {
                    bg = R.drawable.day_fertile
                    color = Palette.onPrimaryContainer(ctx)
                }
                DayState.NORMAL -> {
                    color = ContextCompat.getColor(ctx, R.color.text_dim)
                }
            }

            if (c.isToday) {
                bold = true
                if (c.state == DayState.NORMAL) bg = R.drawable.day_today
            }

            num.background = ContextCompat.getDrawable(ctx, bg)
            num.setTextColor(color)
            num.setTypeface(null, if (bold) Typeface.BOLD else Typeface.NORMAL)
            itemView.setOnClickListener { onDayClick(d) }
        }
    }
}
