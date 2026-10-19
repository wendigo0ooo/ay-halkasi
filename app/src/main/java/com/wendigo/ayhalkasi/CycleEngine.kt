package com.wendigo.ayhalkasi

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class DayState { NORMAL, PERIOD, PRED_PERIOD, FERTILE, OVULATION }

object CycleEngine {

    enum class Confidence { HIGH, MEDIUM, LOW }

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
        val cycleCenter: Int,
        val sdDays: Double,
        val halfRange: Int,
        val confidence: Confidence,
        val irregular: Boolean,
        val irregularAuto: Boolean,
        val lastStart: LocalDate?,
        val nextStart: LocalDate?,
        val nextLow: LocalDate?,
        val nextHigh: LocalDate?,
        val nextEnd: LocalDate?,
        val ovulation: LocalDate?,
        val fertileStart: LocalDate?,
        val fertileEnd: LocalDate?,
        val cycleLens: List<Int>,
        val periodLens: List<Int>,
        val upcoming: List<Upcoming>
    ) {
        val hasData: Boolean get() = lastStart != null

        fun nextRangeText(): String {
            val low = nextLow ?: return "—"
            val high = nextHigh ?: return "—"
            return if (low == high) Dates.short(low) else "${Dates.short(low)} – ${Dates.short(high)}"
        }
    }

    fun analyze(count: Int = 3): Result {
        val groups = groupConsecutive(Store.periodDates())
        val starts = groups.map { it.first() }
        val periodLens = groups.map { it.size }
        val cycleLens = starts.zipWithNext { a, b ->
            java.time.temporal.ChronoUnit.DAYS.between(a, b).toInt()
        }

        val recentCycles = cycleLens.takeLast(8).filter { it in 15..90 }
        val sd = stdDev(recentCycles)
        val autoIrregular = isIrregular(recentCycles)
        val irregular = Store.irregularMode || autoIrregular

        val usable = recentCycles.filter { if (irregular) it in 15..90 else it in 20..45 }
        val meanCycle = usable.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 28
        val medianCycle = median(usable) ?: 28

        val overrideActive = Store.cycleOverride in 15..90
        val avgCycle = if (overrideActive) Store.cycleOverride else meanCycle
        val cycleCenter = when {
            overrideActive -> Store.cycleOverride
            irregular -> medianCycle
            else -> meanCycle
        }

        val halfRange = when {
            usable.size < 2 -> 2
            irregular -> sd.roundToInt().coerceIn(2, 14)
            else -> sd.roundToInt().coerceIn(1, 7)
        }
        var confidence = when {
            usable.size < 3 -> Confidence.MEDIUM
            sd <= 1.5 -> Confidence.HIGH
            sd <= 3.0 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        if (irregular && confidence == Confidence.HIGH) confidence = Confidence.MEDIUM

        val recentPeriods = periodLens.takeLast(6).filter { it in 1..15 }
        val avgPeriod = if (Store.periodOverride in 1..15) Store.periodOverride
        else recentPeriods.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 5

        val lastStart = starts.lastOrNull()
        val upcoming = ArrayList<Upcoming>()
        var s: LocalDate? = lastStart?.plusDays(cycleCenter.toLong())
        for (i in 0 until count) {
            val start = s ?: break
            val end = start.plusDays((avgPeriod - 1).toLong())
            val ov = start.plusDays((cycleCenter - 14).toLong())
            upcoming.add(Upcoming(start, end, ov, ov.minusDays(5), ov.plusDays(1)))
            s = start.plusDays(cycleCenter.toLong())
        }
        val first = upcoming.firstOrNull()
        return Result(
            starts = starts,
            avgCycle = avgCycle,
            avgPeriod = avgPeriod,
            cycleCenter = cycleCenter,
            sdDays = sd,
            halfRange = halfRange,
            confidence = confidence,
            irregular = irregular,
            irregularAuto = autoIrregular,
            lastStart = lastStart,
            nextStart = first?.start,
            nextLow = first?.start?.minusDays(halfRange.toLong()),
            nextHigh = first?.start?.plusDays(halfRange.toLong()),
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

    private fun stdDev(xs: List<Int>): Double {
        if (xs.size < 2) return 0.0
        val m = xs.average()
        val v = xs.sumOf { val d = it - m; d * d } / xs.size
        return sqrt(v)
    }

    private fun median(xs: List<Int>): Int? {
        if (xs.isEmpty()) return null
        val s = xs.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else ((s[n / 2 - 1] + s[n / 2]) / 2.0).roundToInt()
    }

    private fun isIrregular(cycles: List<Int>): Boolean {
        if (cycles.size < 3) return false
        val recent = cycles.takeLast(6)
        val spread = recent.max() - recent.min()
        val outOfRange = recent.any { it < 21 || it > 35 }
        return spread >= 8 || outOfRange
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
