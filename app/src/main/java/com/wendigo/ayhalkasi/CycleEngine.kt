package com.wendigo.ayhalkasi

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class DayState { NORMAL, PERIOD, PRED_PERIOD, FERTILE, OVULATION }

object CycleEngine {

    data class Upcoming(
        val start: LocalDate,
        val end: LocalDate,
        val ovulation: LocalDate,
        val fertileStart: LocalDate,
        val fertileEnd: LocalDate
    )

    data class Result(
        val starts: List<LocalDate>,
        val avgCycle: Int,
        val avgPeriod: Int,
        val lastStart: LocalDate?,
        val nextStart: LocalDate?,
        val nextEnd: LocalDate?,
        val ovulation: LocalDate?,
        val fertileStart: LocalDate?,
        val fertileEnd: LocalDate?,
        val cycleLens: List<Int>,
        val periodLens: List<Int>,
        val upcoming: List<Upcoming>
    ) {
        val hasData: Boolean get() = lastStart != null
    }

    fun analyze(count: Int = 3): Result {
        val groups = groupConsecutive(Store.periodDates())
        val starts = groups.map { it.first() }
        val periodLens = groups.map { it.size }
        val cycleLens = starts.zipWithNext { a, b -> java.time.temporal.ChronoUnit.DAYS.between(a, b).toInt() }

        val recentCycles = cycleLens.takeLast(6).filter { it in 15..60 }
        val avgCycle = if (Store.cycleOverride in 15..60) Store.cycleOverride
        else recentCycles.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 28

        val recentPeriods = periodLens.takeLast(6).filter { it in 1..15 }
        val avgPeriod = if (Store.periodOverride in 1..15) Store.periodOverride
        else recentPeriods.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 5

        val lastStart = starts.lastOrNull()
        val upcoming = ArrayList<Upcoming>()
        var s: LocalDate? = lastStart?.plusDays(avgCycle.toLong())
        for (i in 0 until count) {
            val start = s ?: break
            val end = start.plusDays((avgPeriod - 1).toLong())
            val ov = start.plusDays((avgCycle - 14).toLong())
            upcoming.add(Upcoming(start, end, ov, ov.minusDays(5), ov.plusDays(1)))
            s = start.plusDays(avgCycle.toLong())
        }
        val first = upcoming.firstOrNull()
        return Result(
            starts = starts,
            avgCycle = avgCycle,
            avgPeriod = avgPeriod,
            lastStart = lastStart,
            nextStart = first?.start,
            nextEnd = first?.end,
            ovulation = first?.ovulation,
            fertileStart = first?.fertileStart,
            fertileEnd = first?.fertileEnd,
            cycleLens = cycleLens,
            periodLens = periodLens,
            upcoming = upcoming
        )
    }

    fun groupConsecutive(sorted: List<LocalDate>): List<List<LocalDate>> {
        if (sorted.isEmpty()) return emptyList()
        val out = ArrayList<List<LocalDate>>()
        var cur = ArrayList<LocalDate>()
        cur.add(sorted.first())
        for (i in 1 until sorted.size) {
            val gap = java.time.temporal.ChronoUnit.DAYS.between(sorted[i - 1], sorted[i])
            if (gap <= 1) cur.add(sorted[i])
            else {
                out.add(cur)
                cur = ArrayList()
                cur.add(sorted[i])
            }
        }
        out.add(cur)
        return out
    }

    fun stateFor(date: LocalDate, r: Result): DayState {
        Store.get(date)?.let { if (it.flow > 0) return DayState.PERIOD }
        for (u in r.upcoming) {
            if (!date.isBefore(u.start) && !date.isAfter(u.end)) return DayState.PRED_PERIOD
        }
        for (u in r.upcoming) {
            if (date == u.ovulation) return DayState.OVULATION
            if (!date.isBefore(u.fertileStart) && !date.isAfter(u.fertileEnd)) return DayState.FERTILE
        }
        return DayState.NORMAL
    }
}

object Dates {
    private val tr = Locale("tr", "TR")
    private val longFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", tr)
    private val longDayFmt = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", tr)
    private val shortFmt = DateTimeFormatter.ofPattern("d MMM", tr)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", tr)

    fun long(d: LocalDate): String = d.format(longFmt)
    fun longDay(d: LocalDate): String = d.format(longDayFmt)
    fun short(d: LocalDate): String = d.format(shortFmt)
    fun month(m: YearMonth): String = m.format(monthFmt)
}
