package com.wendigo.ayhalkasi

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class DayState { NORMAL, PERIOD, PRED_PERIOD, FERTILE, OVULATION }

object CycleEngine {

    enum class Confidence { HIGH, MEDIUM, LOW }

    // Geçerli döngü uzunluğu aralığı (aşırı uçlar yok sayılır)
    private const val MIN_CYCLE = 15
    private const val MAX_CYCLE = 90
    // "Normal" adet döngüsü aralığı (düzensizlik tespiti için)
    private const val NORMAL_MIN = 21
    private const val NORMAL_MAX = 35
    // Bir adet bloğunu bölerken tolere edilen en fazla gün boşluğu (unutulan kayıt için)
    private const val MERGE_GAP = 2
    private const val DEFAULT_CYCLE = 28
    private const val DEFAULT_PERIOD = 5
    // Luteal faz uzunluğu (yumurtlamadan sonraki sabit ~14 gün)
    private const val LUTEAL = 14

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

        /** Bugün ya da sonrasına denk gelen ilk tahmini adet başlangıcı (hatırlatıcı için). */
        fun nextFutureStart(today: LocalDate): LocalDate? =
            upcoming.firstOrNull { !it.start.isBefore(today) }?.start ?: nextStart
    }

    fun analyze(count: Int = 3): Result {
        val today = LocalDate.now()
        val groups = groupConsecutive(Store.periodDates())
        val starts = groups.map { it.first() }
        val periodLens = groups.map { it.size }
        val cycleLens = starts.zipWithNext { a, b ->
            ChronoUnit.DAYS.between(a, b).toInt()
        }

        // ---- Döngü uzunluğu: sağlam (robust) tahmin ----
        val valid = cycleLens.takeLast(15).filter { it in MIN_CYCLE..MAX_CYCLE }
        val medVal = median(valid)
        // Aykırı değerleri ele: medyanın çok altı/üstü (ör. kaçırılan bir döngü → ~2x değer)
        val kept = if (medVal == null) emptyList()
        else valid.filter { it >= medVal * 0.55 && it <= medVal * 1.75 }
        val sd = sampleSd(kept)

        val autoIrregular = isIrregular(valid)
        val irregular = Store.irregularMode || autoIrregular

        val overrideActive = Store.cycleOverride in MIN_CYCLE..MAX_CYCLE
        val medD = (median(kept) ?: medVal ?: DEFAULT_CYCLE).toDouble()
        val center = if (overrideActive) Store.cycleOverride
        else estimateCycle(kept, medD, irregular)

        val halfRange = when {
            kept.size < 2 -> 2
            irregular -> (sd * 1.5).roundToInt().coerceIn(2, 16)
            else -> (sd * 1.5).roundToInt().coerceIn(1, 7)
        }

        var confidence = when {
            kept.isEmpty() -> Confidence.LOW
            kept.size < 3 -> Confidence.MEDIUM
            sd <= 1.5 -> Confidence.HIGH
            sd <= 3.0 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        if (irregular && confidence == Confidence.HIGH) confidence = Confidence.MEDIUM

        // ---- Adet (kanama) uzunluğu: sağlam medyan ----
        val recentPeriods = periodLens.takeLast(6).filter { it in 1..15 }
        val avgPeriod = if (Store.periodOverride in 1..15) Store.periodOverride
        else median(recentPeriods) ?: DEFAULT_PERIOD

        val lastStart = starts.lastOrNull()

        // Veri eskiyse güveni düşür (tahmin zamanla belirsizleşir)
        if (lastStart != null && ChronoUnit.DAYS.between(lastStart, today) > center * 3L / 2L) {
            confidence = downgrade(confidence)
        }

        // ---- Sıradaki (imminent) tahmin ----
        val imminentStart = lastStart?.plusDays(center.toLong())
        val nextEnd = imminentStart?.plusDays((avgPeriod - 1).toLong())
        val imminentOv = imminentStart?.plusDays((center - LUTEAL).toLong())

        // ---- Takvim/doğurganlık için kaydırılmış tahminler ----
        val upcoming = ArrayList<Upcoming>()
        if (imminentStart != null) {
            var s = imminentStart
            // En fazla bir tahmini bloğu geçmişte bırakacak şekilde ilerlet
            while (s.plusDays(center.toLong()).isBefore(today)) s = s.plusDays(center.toLong())
            val horizon = today.plusDays((count.toLong() + 1) * center)
            var guard = 0
            while (!s.isAfter(horizon) && guard < 48) {
                upcoming.add(makeUpcoming(s, center, avgPeriod))
                s = s.plusDays(center.toLong())
                guard++
            }
        }
        val fu = upcoming.firstOrNull()

        val ov = fu?.ovulation ?: imminentOv
        val fs = fu?.fertileStart ?: ov?.minusDays(5)
        val fe = fu?.fertileEnd ?: ov?.plusDays(1)

        return Result(
            starts = starts,
            avgCycle = center,
            avgPeriod = avgPeriod,
            cycleCenter = center,
            sdDays = sd,
            halfRange = halfRange,
            confidence = confidence,
            irregular = irregular,
            irregularAuto = autoIrregular,
            lastStart = lastStart,
            nextStart = imminentStart,
            nextLow = imminentStart?.minusDays(halfRange.toLong()),
            nextHigh = imminentStart?.plusDays(halfRange.toLong()),
            nextEnd = nextEnd,
            ovulation = ov,
            fertileStart = fs,
            fertileEnd = fe,
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
            val gap = ChronoUnit.DAYS.between(sorted[i - 1], sorted[i])
            if (gap <= MERGE_GAP.toLong()) cur.add(sorted[i])
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

    // ---------------- yardımcılar ----------------

    private fun makeUpcoming(start: LocalDate, cl: Int, period: Int): Upcoming {
        val ov = start.plusDays((cl - LUTEAL).toLong())
        return Upcoming(
            start = start,
            end = start.plusDays((period - 1).toLong()),
            ovulation = ov,
            fertileStart = ov.minusDays(5),
            fertileEnd = ov.plusDays(1)
        )
    }

    /**
     * Sağlam döngü uzunluğu tahmini: medyan + yakınlık ağırlıklı ortalama (+ belirgin
     * eğilim varsa hafif trend katkısı). Düzensizde sadece medyan kullanılır.
     */
    private fun estimateCycle(kept: List<Int>, med: Double, irregular: Boolean): Int {
        if (kept.isEmpty()) return med.roundToInt().coerceIn(MIN_CYCLE, MAX_CYCLE)
        if (kept.size == 1) return kept[0]
        if (irregular) return med.roundToInt().coerceIn(MIN_CYCLE, MAX_CYCLE)
        val wm = weightedMean(kept)
        val base = 0.5 * med + 0.5 * wm
        val tr = trendNext(kept)
        val est = if (tr == null) base else {
            val w = 0.3 + 0.3 * tr.second // uyum (R²) iyiyse trend daha etkili
            (1 - w) * base + w * tr.first
        }
        return est.roundToInt().coerceIn(MIN_CYCLE, MAX_CYCLE)
    }

    /** Son döngüye daha çok ağırlık veren ortalama (lineer artan ağırlık). */
    private fun weightedMean(xs: List<Int>): Double {
        if (xs.isEmpty()) return 0.0
        var sw = 0.0
        var sv = 0.0
        for ((i, x) in xs.withIndex()) {
            val w = (i + 1).toDouble()
            sw += w
            sv += w * x
        }
        return sv / sw
    }

    /**
     * Lineer regresyonla bir SONRAKİ döngü uzunluğu tahmini. Yalnızca veri >= 4,
     * eğim makul (|slope| <= 3) ve uyum iyi (R² >= 0.5) ise döner; aksi halde null.
     * Dönen değer: (tahminiSonrakiUzunluk, R²).
     */
    private fun trendNext(xs: List<Int>): Pair<Double, Double>? {
        val n = xs.size
        if (n < 4) return null
        val meanX = (n - 1) / 2.0
        val meanY = xs.average()
        var sxx = 0.0
        var sxy = 0.0
        for ((i, y) in xs.withIndex()) {
            val dx = i - meanX
            sxx += dx * dx
            sxy += dx * (y - meanY)
        }
        if (sxx == 0.0) return null
        val slope = sxy / sxx
        if (abs(slope) > 3.0) return null
        val intercept = meanY - slope * meanX
        var ssTot = 0.0
        var ssRes = 0.0
        for ((i, y) in xs.withIndex()) {
            val pred = intercept + slope * i
            ssTot += (y - meanY) * (y - meanY)
            ssRes += (y - pred) * (y - pred)
        }
        val r2 = if (ssTot == 0.0) 0.0 else 1.0 - ssRes / ssTot
        if (r2 < 0.5) return null
        return (intercept + slope * n) to r2
    }

    private fun downgrade(c: Confidence): Confidence = when (c) {
        Confidence.HIGH -> Confidence.MEDIUM
        Confidence.MEDIUM -> Confidence.LOW
        Confidence.LOW -> Confidence.LOW
    }

    /** Örnek standart sapması (n-1) — küçük örneklerde daha doğru değişkenlik tahmini. */
    private fun sampleSd(xs: List<Int>): Double {
        val n = xs.size
        if (n < 2) return 0.0
        val m = xs.average()
        val v = xs.sumOf { val d = it - m; d * d } / (n - 1)
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
        val outOfRange = recent.any { it < NORMAL_MIN || it > NORMAL_MAX }
        val dev = sampleSd(recent)
        return spread >= 8 || outOfRange || dev >= 4.0
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
