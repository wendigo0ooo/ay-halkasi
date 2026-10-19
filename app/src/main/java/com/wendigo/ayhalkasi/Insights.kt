package com.wendigo.ayhalkasi

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object Insights {

    enum class Phase { MENSTRUAL, FOLLICULAR, FERTILE, LUTEAL, UNKNOWN }

    data class PhaseInfo(val phase: Phase, val cycleDay: Int)

    fun phaseNow(r: CycleEngine.Result, today: LocalDate): PhaseInfo {
        val last = r.lastStart ?: return PhaseInfo(Phase.UNKNOWN, 0)
        val cycleDay = (ChronoUnit.DAYS.between(last, today) + 1).toInt()
        if (cycleDay < 1) return PhaseInfo(Phase.UNKNOWN, 0)

        val ovDay = (r.cycleCenter - 14).coerceAtLeast(1)
        val fertileStart = (ovDay - 5).coerceAtLeast(1)
        val fertileEnd = ovDay + 1

        val loggedPeriod = (Store.get(today)?.flow ?: 0) > 0
        val phase = when {
            loggedPeriod -> Phase.MENSTRUAL
            cycleDay <= r.avgPeriod -> Phase.MENSTRUAL
            cycleDay in fertileStart..fertileEnd -> Phase.FERTILE
            cycleDay < ovDay -> Phase.FOLLICULAR
            else -> Phase.LUTEAL
        }
        return PhaseInfo(phase, cycleDay)
    }

    fun symptomCounts(): IntArray {
        val counts = IntArray(SYMPTOM_COUNT)
        for (log in Store.allDays()) {
            for (s in log.symptoms) {
                if (s in 0 until SYMPTOM_COUNT) counts[s]++
            }
        }
        return counts
    }

    fun topSymptoms(limit: Int = 5): List<Pair<Int, Int>> {
        val counts = symptomCounts()
        return counts.withIndex()
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.index to it.value }
    }

    const val SYMPTOM_COUNT = 12
}
