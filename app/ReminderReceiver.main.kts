#!/usr/bin/env kotlin

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

        // If phone rebooted — reschedule the alarm
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

        // Show the notification
        showNotification(context)
    }

    companion object {

        private const val CHANNEL_ID      = "100life_reminder"
        private const val NOTIFICATION_ID = 1001
        private const val ALARM_REQUEST   = 2001

        // ── Schedule daily alarm ──
        fun scheduleReminder(context: Context, hour: Int, minute: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Set alarm for today at chosen time
            // If time already passed today — set for tomorrow
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE,      minute)
                set(Calendar.SECOND,      0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            // Use setInexactRepeating for battery efficiency
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }

        // ── Cancel alarm ──
        fun cancelReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        // ── Show notification ──
        fun showNotification(context: Context) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create notification channel (required for Android 8+)
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

            // Build the notification
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