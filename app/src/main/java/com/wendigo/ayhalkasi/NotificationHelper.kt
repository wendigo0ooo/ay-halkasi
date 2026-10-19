package com.wendigo.ayhalkasi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {
    const val CHANNEL = "ay_halkasi_reminders"
    const val PERIOD_ID = 1001
    const val DAILY_ID = 1002
    const val MED_ID = 1003

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(
                    CHANNEL,
                    ctx.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                ch.description = ctx.getString(R.string.notif_channel_desc)
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun show(ctx: Context, id: Int, title: String, text: String) {
        ensureChannel(ctx)
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(Palette.primaryColor(ctx))
            .setAutoCancel(true)
            .setContentIntent(pi)

        runCatching {
            NotificationManagerCompat.from(ctx).notify(id, builder.build())
        }
    }
}
