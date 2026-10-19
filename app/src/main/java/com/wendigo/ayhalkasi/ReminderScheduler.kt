package com.wendigo.ayhalkasi

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderScheduler {
    const val ACTION_PERIOD = "com.wendigo.ayhalkasi.PERIOD_REMINDER"
    const val ACTION_DAILY = "com.wendigo.ayhalkasi.DAILY_REMINDER"
    const val ACTION_MED = "com.wendigo.ayhalkasi.MED_REMINDER"
    private const val RC_PERIOD = 7001
    private const val RC_DAILY = 7002
    private const val RC_MED = 7003

    fun reschedule(ctx: Context) {
        cancel(ctx, ACTION_PERIOD, RC_PERIOD)
        cancel(ctx, ACTION_DAILY, RC_DAILY)
        cancel(ctx, ACTION_MED, RC_MED)
        if (!Store.notifEnabled) return

        val result = CycleEngine.analyze()
        val next = result.nextStart
        if (next != null) {
            val triggerDate = next.minusDays(Store.remindDaysBefore.coerceIn(1, 7).toLong())
            val millis = ZonedDateTime.of(triggerDate, LocalTime.of(9, 0), ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            if (millis > System.currentTimeMillis()) {
                set(ctx, ACTION_PERIOD, RC_PERIOD, millis)
            }
        }

        if (Store.dailyReminder) {
            val millis = nextDaily(Store.dailyMinute)
            set(ctx, ACTION_DAILY, RC_DAILY, millis)
        }

        if (Store.medEnabled) {
            val millis = nextDaily(Store.medMinute)
            set(ctx, ACTION_MED, RC_MED, millis)
        }
    }

    private fun nextDaily(minuteOfDay: Int): Long {
        val now = LocalDateTime.now()
        val minutes = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        var at = now.toLocalDate().atTime(minutes / 60, minutes % 60)
        if (!at.isAfter(now)) at = at.plusDays(1)
        return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun set(ctx: Context, action: String, rc: Int, millis: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx, action, rc)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun cancel(ctx: Context, action: String, rc: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, action, rc))
    }

    private fun pending(ctx: Context, action: String, rc: Int): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            ctx, rc, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
