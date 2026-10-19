package com.wendigo.ayhalkasi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class CycleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Store.init(context)
        for (id in appWidgetIds) render(context, appWidgetManager, id)
        scheduleNext(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refresh(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.wendigo.ayhalkasi.WIDGET_REFRESH"

        fun refresh(context: Context) {
            Store.init(context)
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, CycleWidgetProvider::class.java))
            for (id in ids) render(context, mgr, id)
        }

        private fun render(context: Context, mgr: AppWidgetManager, id: Int) {
            val r = CycleEngine.analyze()
            val today = LocalDate.now()
            val views = RemoteViews(context.packageName, R.layout.widget_cycle)
            views.setTextColor(R.id.widgetTitle, Palette.primaryColor(context))

            val last = r.lastStart
            val next = r.nextStart
            if (last == null || next == null) {
                views.setTextViewText(R.id.widgetBig, context.getString(R.string.widget_no_data))
                views.setTextViewText(R.id.widgetSub, "")
            } else {
                val dayNo = (ChronoUnit.DAYS.between(last, today) + 1).toInt()
                views.setTextViewText(R.id.widgetBig, context.getString(R.string.widget_day, dayNo))
                val diff = ChronoUnit.DAYS.between(today, next)
                val sub = when {
                    diff > 0 -> context.getString(R.string.widget_next_in, diff.toInt())
                    diff == 0L -> context.getString(R.string.widget_next_today)
                    else -> context.getString(R.string.widget_next_late, (-diff).toInt())
                }
                views.setTextViewText(R.id.widgetSub, sub)
            }

            val open = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pi)

            mgr.updateAppWidget(id, views)
        }

        fun scheduleNext(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 5)
            cal.set(java.util.Calendar.SECOND, 0)
            val at = cal.timeInMillis
            val intent = Intent(context, CycleWidgetProvider::class.java).setAction(ACTION_REFRESH)
            val pi = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.set(android.app.AlarmManager.RTC, at, pi)
                } else {
                    am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC, at, pi)
                }
            }.onFailure { am.set(android.app.AlarmManager.RTC, at, pi) }
        }
    }
}
