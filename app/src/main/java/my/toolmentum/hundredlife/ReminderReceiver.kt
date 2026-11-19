package my.toolmentum.hundredlife

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Same encrypted store the settings screen writes. The old plaintext
            // file "100life_settings" is never read.
            val prefs = try {
                getSecurePrefs(context)
            } catch (e: Exception) {
                Log.e(TAG, "Reminder not rescheduled: encrypted settings unavailable", e)
                return
            }
            val isEnabled = prefs.getBoolean(SettingKeys.REMINDER_ENABLED, false)
            if (isEnabled) {
                val hour = prefs.getInt(SettingKeys.REMINDER_HOUR, 9)
                val minute = prefs.getInt(SettingKeys.REMINDER_MINUTE, 0)
                scheduleReminder(context, hour, minute)
            }
            return
        }
        showNotification(context)
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val CHANNEL_ID = "100life_reminder"
        private const val NOTIFICATION_ID = 1001
        private const val ALARM_REQUEST = 2001

        fun scheduleReminder(context: Context, hour: Int, minute: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            // Inexact repeating does not need SCHEDULE_EXACT_ALARM. Delivery can
            // drift by several minutes; that is the trade-off for not holding the
            // exact-alarm permission.
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
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Don't forget to record your sugar reading today!\n\nTap here to open 100Life.")
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
}
