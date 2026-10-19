package com.wendigo.ayhalkasi

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object Fertility {

    data class Point(val date: LocalDate, val value: Double)

    data class Shift(val date: LocalDate, val rise: Double)

    data class Summary(
        val hasData: Boolean,
        val bbtSeries: List<Point>,
        val latestBbt: Point?,
        val latestMucus: Pair<LocalDate, Int>?,
        val latestLh: Pair<LocalDate, Int>?
    ) {
        val shift: Shift? get() = thermalShift(bbtSeries)

        fun recentPositiveLh(today: LocalDate): Pair<LocalDate, Int>? =
            latestLh?.takeIf { it.second == 2 && ChronoUnit.DAYS.between(it.first, today) <= 10 }

        fun recentMucusPeak(today: LocalDate): Pair<LocalDate, Int>? =
            latestMucus?.takeIf { it.second >= 4 && ChronoUnit.DAYS.between(it.first, today) <= 7 }
    }

    fun analyze(today: LocalDate, days: Int = 14): Summary {
        val entries = Store.allEntries()
        val series = entries.filter { it.second.bbt != null }
            .mapNotNull { e -> e.second.bbt?.let { Point(e.first, it) } }
            .filter { !it.date.isBefore(today.minusDays(days.toLong())) }
            .sortedBy { it.date }
        val latestBbt = entries.lastOrNull { it.second.bbt != null }
            ?.let { Point(it.first, it.second.bbt!!) }
        val latestMucus = entries.lastOrNull { it.second.mucus > 0 }
            ?.let { it.first to it.second.mucus }
        val latestLh = entries.lastOrNull { it.second.lh > 0 }
            ?.let { it.first to it.second.lh }
        return Summary(
            hasData = entries.any { it.second.hasFertility() },
            bbtSeries = series,
            latestBbt = latestBbt,
            latestMucus = latestMucus,
            latestLh = latestLh
        )
    }

    /**
     * Son 3 ölçüm, önceki en az 3 ölçümün ortalamasından en az 0,2 °C yüksekse
     * sıcaklık artışı (muhtemel yumurtlama sonrası) kabul edilir. Ölçümlerin
     * birbirine yakın günlerde olması gerekir; aksi halde yorum yapılmaz.
     */
    fun thermalShift(series: List<Point>): Shift? {
        if (series.size < 6) return null
        val last3 = series.takeLast(3)
        val span = ChronoUnit.DAYS.between(last3.first().date, last3.last().date)
        if (span > 6) return null
        val prior = series.dropLast(3).takeLast(6)
        if (prior.size < 3) return null
        val base = prior.map { it.value }.average()
        val high = last3.map { it.value }.average()
        if (high - base >= 0.2 && last3.all { it.value >= base + 0.15 }) {
            return Shift(last3.last().date, high - base)
        }
        return null
    }
}
