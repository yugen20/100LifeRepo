package com.example.a100life

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("100life_settings", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("reminder_enabled", false)
            if (isEnabled) {
                val hour   = prefs.getInt("reminder_hour",   9)
                val minute = prefs.getInt("reminder_minute", 0)
                scheduleReminder(context, hour, minute)
            }
            return
        }
        showNotification(context)
    }

    companion object {

        private const val CHANNEL_ID      = "100life_reminder"
        private const val NOTIFICATION_ID = 1001
        private const val ALARM_REQUEST   = 2001

        fun scheduleReminder(context: Context, hour: Int, minute: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE,      minute)
                set(Calendar.SECOND,      0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }

        fun cancelReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        fun showNotification(context: Context) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Daily Sugar Reminder",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily reminder to record your sugar reading"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("100Life — Daily Reminder 💊")
                .setContentText("Don't forget to record your sugar reading today!")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Don't forget to record your sugar reading today!\n\nTap here to open 100Life."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
}