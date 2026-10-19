package com.wendigo.ayhalkasi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        when (intent.action) {
            ReminderScheduler.ACTION_PERIOD -> {
                val next = CycleEngine.analyze().nextStart
                val text = when {
                    next == null -> context.getString(R.string.no_data_short)
                    next == LocalDate.now() -> context.getString(R.string.notif_period_today)
                    else -> context.getString(R.string.notif_period_text, Dates.long(next))
                }
                NotificationHelper.show(
                    context, NotificationHelper.PERIOD_ID,
                    context.getString(R.string.notif_period_title), text
                )
            }
            ReminderScheduler.ACTION_DAILY -> {
                NotificationHelper.show(
                    context, NotificationHelper.DAILY_ID,
                    context.getString(R.string.notif_daily_title),
                    context.getString(R.string.notif_daily_text)
                )
            }
            ReminderScheduler.ACTION_MED -> {
                val label = Store.medLabel.ifBlank { context.getString(R.string.med_default_label) }
                NotificationHelper.show(
                    context, NotificationHelper.MED_ID,
                    context.getString(R.string.notif_med_title),
                    context.getString(R.string.notif_med_text, label)
                )
            }
        }
        ReminderScheduler.reschedule(context)
    }
}
