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
    var note: String = "",
    var bbt: Double? = null,
    var mucus: Int = 0,
    var lh: Int = 0
) {
    fun isEmpty(): Boolean =
        flow == 0 && pain == 0 && mood == 0 && symptoms.isEmpty() && note.isBlank() &&
            bbt == null && mucus == 0 && lh == 0

    fun hasFertility(): Boolean = bbt != null || mucus > 0 || lh > 0
}

object Store {
    private const val PREFS = "ay_halkasi"
    private const val KEY_DATA = "data"
    private const val DATA_VERSION = 3
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_NOTIF = "notif_enabled"
    private const val KEY_REMIND_DAYS = "remind_days"
    private const val KEY_DAILY = "daily_reminder"
    private const val KEY_DAILY_MIN = "daily_minute"
    private const val KEY_CYCLE_OVR = "cycle_override"
    private const val KEY_PERIOD_OVR = "period_override"
    private const val KEY_LOCK = "lock_enabled"
    private const val KEY_PIN = "pin_hash"
    private const val KEY_IRREGULAR = "irregular_mode"
    private const val KEY_MED_ENABLED = "med_enabled"
    private const val KEY_MED_MIN = "med_minute"
    private const val KEY_MED_LABEL = "med_label"
    private const val KEY_BEAR_SOUND = "bear_sound"
    private const val KEY_PET_ANIMAL = "pet_animal"
    private const val KEY_PET_NAME = "pet_name"

    private var prefs: SharedPreferences? = null
    private val days = HashMap<String, DayLog>()
    private val medTaken = HashSet<String>()

    var onboarded = false
    var notifEnabled = true
    var remindDaysBefore = 2
    var dailyReminder = false
    var dailyMinute = 20 * 60
    var cycleOverride = 0
    var periodOverride = 0
    var lockEnabled = false
    var pinHash = ""
    var irregularMode = false
    var medEnabled = false
    var medMinute = 21 * 60
    var medLabel = ""
    var bearSound = true
    var petAnimal = "BEAR"
    var petName = ""

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        onboarded = p.getBoolean(KEY_ONBOARDED, false)
        notifEnabled = p.getBoolean(KEY_NOTIF, true)
        remindDaysBefore = p.getInt(KEY_REMIND_DAYS, 2)
        dailyReminder = p.getBoolean(KEY_DAILY, false)
        dailyMinute = p.getInt(KEY_DAILY_MIN, 20 * 60)
        cycleOverride = p.getInt(KEY_CYCLE_OVR, 0)
        periodOverride = p.getInt(KEY_PERIOD_OVR, 0)
        lockEnabled = p.getBoolean(KEY_LOCK, false)
        pinHash = p.getString(KEY_PIN, "") ?: ""
        irregularMode = p.getBoolean(KEY_IRREGULAR, false)
        medEnabled = p.getBoolean(KEY_MED_ENABLED, false)
        medMinute = p.getInt(KEY_MED_MIN, 21 * 60)
        medLabel = p.getString(KEY_MED_LABEL, "") ?: ""
        bearSound = p.getBoolean(KEY_BEAR_SOUND, true)
        petAnimal = p.getString(KEY_PET_ANIMAL, "BEAR") ?: "BEAR"
        petName = p.getString(KEY_PET_NAME, "") ?: ""
        p.getString(KEY_DATA, null)?.takeIf { it.isNotBlank() }?.let { parseInto(it, days, medTaken) }
    }

    fun saveSettings() {
        prefs?.edit()
            ?.putBoolean(KEY_ONBOARDED, onboarded)
            ?.putBoolean(KEY_NOTIF, notifEnabled)
            ?.putInt(KEY_REMIND_DAYS, remindDaysBefore)
            ?.putBoolean(KEY_DAILY, dailyReminder)
            ?.putInt(KEY_DAILY_MIN, dailyMinute)
            ?.putInt(KEY_CYCLE_OVR, cycleOverride)
            ?.putInt(KEY_PERIOD_OVR, periodOverride)
            ?.putBoolean(KEY_LOCK, lockEnabled)
            ?.putString(KEY_PIN, pinHash)
            ?.putBoolean(KEY_IRREGULAR, irregularMode)
            ?.putBoolean(KEY_MED_ENABLED, medEnabled)
            ?.putInt(KEY_MED_MIN, medMinute)
            ?.putString(KEY_MED_LABEL, medLabel)
            ?.putBoolean(KEY_BEAR_SOUND, bearSound)
            ?.putString(KEY_PET_ANIMAL, petAnimal)
            ?.putString(KEY_PET_NAME, petName)
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
        medTaken.clear()
        persist()
    }

    fun markPeriodStart(date: LocalDate) {
        val cur = days[date.toString()] ?: DayLog()
        if (cur.flow < 2) cur.flow = 2
        put(date, cur)
    }

    fun markPeriodEnd(date: LocalDate) {
        val cur = days[date.toString()] ?: DayLog()
        cur.flow = 0
        put(date, cur)
    }

    /** Gerçek adet günleri (leke hariç). Döngü tespiti yalnız bunları kullanır. */
    fun periodDates(): List<LocalDate> =
        days.entries.filter { it.value.flow >= 2 }
            .mapNotNull { runCatching { LocalDate.parse(it.key) }.getOrNull() }
            .sorted()

    /** Yalnız lekelenme günleri (flow == 1). Döngü başlangıcı sayılmaz. */
    fun spottingDates(): List<LocalDate> =
        days.entries.filter { it.value.flow == 1 }
            .mapNotNull { runCatching { LocalDate.parse(it.key) }.getOrNull() }
            .sorted()

    fun hasAny(): Boolean = days.isNotEmpty()
    fun allDays(): List<DayLog> = days.values.toList()

    /** Tarihe göre sıralı tüm günlük kayıtlar (rapor/dışa aktarma için). */
    fun allEntries(): List<Pair<LocalDate, DayLog>> =
        days.entries.mapNotNull { e ->
            runCatching { LocalDate.parse(e.key) }.getOrNull()?.let { it to e.value }
        }.sortedBy { it.first }

    fun allMedTaken(): List<LocalDate> =
        medTaken.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
    fun rangeStart(): LocalDate? = periodDates().firstOrNull()

    fun isMedTaken(date: LocalDate): Boolean = medTaken.contains(date.toString())

    fun setMedTaken(date: LocalDate, taken: Boolean) {
        if (taken) medTaken.add(date.toString()) else medTaken.remove(date.toString())
        persist()
    }

    fun toJsonString(): String {
        val root = JSONObject()
        root.put("app", "ay-halkasi")
        root.put("version", DATA_VERSION)
        val arr = JSONArray()
        for ((k, v) in days) {
            val o = JSONObject()
            o.put("d", k)
            o.put("flow", v.flow)
            o.put("pain", v.pain)
            o.put("mood", v.mood)
            o.put("note", v.note)
            v.bbt?.let { o.put("bbt", it) }
            o.put("mucus", v.mucus)
            o.put("lh", v.lh)
            val s = JSONArray()
            v.symptoms.sorted().forEach { s.put(it) }
            o.put("sym", s)
            arr.put(o)
        }
        root.put("days", arr)
        val med = JSONArray()
        medTaken.sorted().forEach { med.put(it) }
        root.put("medTaken", med)
        return root.toString()
    }

    fun persist() {
        prefs?.edit()?.putString(KEY_DATA, toJsonString())?.apply()
    }

    fun importFrom(raw: String): Boolean {
        val target = HashMap<String, DayLog>()
        val targetMed = HashSet<String>()
        val ok = parseInto(raw, target, targetMed)
        if (!ok || (target.isEmpty() && targetMed.isEmpty())) return false
        days.clear()
        days.putAll(target)
        medTaken.clear()
        medTaken.addAll(targetMed)
        persist()
        return true
    }

    private fun parseInto(raw: String, target: HashMap<String, DayLog>, targetMed: HashSet<String>): Boolean {
        return runCatching {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("days") ?: return false
            val ver = root.optInt("version", 1)
            target.clear()
            targetMed.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                // v1: ruh hali -2..2 aralığında; v2: 1..5 (0 = kayıtsız).
                var mood = o.optInt("mood", 0)
                if (ver < 2) mood += 3
                val log = DayLog(
                    flow = o.optInt("flow", 0),
                    pain = o.optInt("pain", 0),
                    mood = mood,
                    note = o.optString("note", ""),
                    bbt = if (o.has("bbt") && !o.isNull("bbt")) o.optDouble("bbt") else null,
                    mucus = o.optInt("mucus", 0).coerceIn(0, 5),
                    lh = o.optInt("lh", 0).coerceIn(0, 2)
                )
                o.optJSONArray("sym")?.let { s ->
                    for (j in 0 until s.length()) log.symptoms.add(s.getInt(j))
                }
                if (!log.isEmpty()) target[o.getString("d")] = log
            }
            root.optJSONArray("medTaken")?.let { m ->
                for (j in 0 until m.length()) targetMed.add(m.getString(j))
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
