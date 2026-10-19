package com.wendigo.ayhalkasi

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate

data class DayLog(
    var flow: Int = 0,
    var pain: Int = 0,
    var mood: Int = 0,
    val symptoms: MutableSet<Int> = mutableSetOf(),
    var note: String = ""
) {
    fun isEmpty(): Boolean =
        flow == 0 && pain == 0 && mood == 0 && symptoms.isEmpty() && note.isBlank()
}

object Store {
    private const val PREFS = "ay_halkasi"
    private const val KEY_DATA = "data"
    private const val KEY_NOTIF = "notif_enabled"
    private const val KEY_REMIND_DAYS = "remind_days"
    private const val KEY_DAILY = "daily_reminder"
    private const val KEY_DAILY_MIN = "daily_minute"
    private const val KEY_CYCLE_OVR = "cycle_override"
    private const val KEY_PERIOD_OVR = "period_override"
    private const val KEY_LOCK = "lock_enabled"
    private const val KEY_PIN = "pin_hash"
    private const val KEY_IRREGULAR = "irregular_mode"

    private var prefs: SharedPreferences? = null
    private val days = HashMap<String, DayLog>()

    var notifEnabled = true
    var remindDaysBefore = 2
    var dailyReminder = false
    var dailyMinute = 20 * 60
    var cycleOverride = 0
    var periodOverride = 0
    var lockEnabled = false
    var pinHash = ""
    var irregularMode = false

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        notifEnabled = p.getBoolean(KEY_NOTIF, true)
        remindDaysBefore = p.getInt(KEY_REMIND_DAYS, 2)
        dailyReminder = p.getBoolean(KEY_DAILY, false)
        dailyMinute = p.getInt(KEY_DAILY_MIN, 20 * 60)
        cycleOverride = p.getInt(KEY_CYCLE_OVR, 0)
        periodOverride = p.getInt(KEY_PERIOD_OVR, 0)
        lockEnabled = p.getBoolean(KEY_LOCK, false)
        pinHash = p.getString(KEY_PIN, "") ?: ""
        irregularMode = p.getBoolean(KEY_IRREGULAR, false)
        p.getString(KEY_DATA, null)?.takeIf { it.isNotBlank() }?.let { parseInto(it, days) }
    }

    fun saveSettings() {
        prefs?.edit()
            ?.putBoolean(KEY_NOTIF, notifEnabled)
            ?.putInt(KEY_REMIND_DAYS, remindDaysBefore)
            ?.putBoolean(KEY_DAILY, dailyReminder)
            ?.putInt(KEY_DAILY_MIN, dailyMinute)
            ?.putInt(KEY_CYCLE_OVR, cycleOverride)
            ?.putInt(KEY_PERIOD_OVR, periodOverride)
            ?.putBoolean(KEY_LOCK, lockEnabled)
            ?.putString(KEY_PIN, pinHash)
            ?.putBoolean(KEY_IRREGULAR, irregularMode)
            ?.apply()
    }

    fun get(date: LocalDate): DayLog? = days[date.toString()]

    fun put(date: LocalDate, log: DayLog) {
        val k = date.toString()
        if (log.isEmpty()) days.remove(k) else days[k] = log
        persist()
    }

    fun remove(date: LocalDate) {
        days.remove(date.toString())
        persist()
    }

    fun clearAll() {
        days.clear()
        persist()
    }

    fun markPeriodStart(date: LocalDate) {
        val cur = days[date.toString()] ?: DayLog()
        if (cur.flow == 0) cur.flow = 2
        put(date, cur)
    }

    fun markPeriodEnd(date: LocalDate) {
        val cur = days[date.toString()] ?: DayLog()
        cur.flow = 0
        put(date, cur)
    }

    fun periodDates(): List<LocalDate> =
        days.entries.filter { it.value.flow > 0 }
            .mapNotNull { runCatching { LocalDate.parse(it.key) }.getOrNull() }
            .sorted()

    fun hasAny(): Boolean = days.isNotEmpty()
    fun rangeStart(): LocalDate? = periodDates().firstOrNull()

    fun toJsonString(): String {
        val root = JSONObject()
        root.put("app", "ay-halkasi")
        root.put("version", 1)
        val arr = JSONArray()
        for ((k, v) in days) {
            val o = JSONObject()
            o.put("d", k)
            o.put("flow", v.flow)
            o.put("pain", v.pain)
            o.put("mood", v.mood)
            o.put("note", v.note)
            val s = JSONArray()
            v.symptoms.sorted().forEach { s.put(it) }
            o.put("sym", s)
            arr.put(o)
        }
        root.put("days", arr)
        return root.toString()
    }

    fun persist() {
        prefs?.edit()?.putString(KEY_DATA, toJsonString())?.apply()
    }

    fun importFrom(raw: String): Boolean {
        val target = HashMap<String, DayLog>()
        val ok = parseInto(raw, target)
        if (!ok || target.isEmpty()) return false
        days.clear()
        days.putAll(target)
        persist()
        return true
    }

    private fun parseInto(raw: String, target: HashMap<String, DayLog>): Boolean {
        return runCatching {
            val arr = JSONObject(raw).optJSONArray("days") ?: return false
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val log = DayLog(
                    flow = o.optInt("flow", 0),
                    pain = o.optInt("pain", 0),
                    mood = o.optInt("mood", 0),
                    note = o.optString("note", "")
                )
                o.optJSONArray("sym")?.let { s ->
                    for (j in 0 until s.length()) log.symptoms.add(s.getInt(j))
                }
                if (!log.isEmpty()) target[o.getString("d")] = log
            }
            true
        }.getOrDefault(false)
    }

    fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String): Boolean = pinHash.isNotEmpty() && hashPin(pin) == pinHash
}
