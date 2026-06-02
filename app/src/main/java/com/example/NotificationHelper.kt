package com.example

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

class ShiftReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val company = intent.getStringExtra("company") ?: "Work"
        val timeStr = intent.getStringExtra("timeStr") ?: ""
        val shiftId = intent.getStringExtra("shiftId") ?: ""

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                CHANNEL_ID, "Shift Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm-style reminders for upcoming shifts"
                setSound(alarmUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("company", company)
            putExtra("timeStr", timeStr)
            putExtra("shiftId", shiftId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, shiftId.hashCode(), fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, DismissAlarmReceiver::class.java).apply {
            putExtra("shiftId", shiftId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, shiftId.hashCode() + 1, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, SnoozeAlarmReceiver::class.java).apply {
            putExtra("company", company)
            putExtra("timeStr", timeStr)
            putExtra("shiftId", shiftId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, shiftId.hashCode() + 2, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Upcoming Shift: $company")
            .setContentText("Your shift starts $timeStr")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)
            .addAction(android.R.drawable.ic_popup_reminder, "Snooze 5 min", snoozePendingIntent)
            .setOngoing(true)
            .build()

        notificationManager.notify(shiftId.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "shift_reminders"
    }
}

class DismissAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val shiftId = intent.getStringExtra("shiftId") ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(shiftId.hashCode())
    }
}

class SnoozeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val company = intent.getStringExtra("company") ?: "Work"
        val timeStr = intent.getStringExtra("timeStr") ?: ""
        val shiftId = intent.getStringExtra("shiftId") ?: return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(shiftId.hashCode())

        NotificationHelper.snoozeReminder(context, shiftId, company, timeStr)
    }
}

object NotificationHelper {
    fun scheduleReminder(context: Context, shift: Shift) {
        if (shift.reminderBeforeMinutes <= 0) return
        val triggerTime = shift.startTime - shift.reminderBeforeMinutes * 60 * 1000L
        if (triggerTime <= System.currentTimeMillis()) return

        val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
        val intent = Intent(context, ShiftReminderReceiver::class.java).apply {
            putExtra("company", shift.company)
            putExtra("timeStr", "at ${timeFormat.format(java.util.Date(shift.startTime))}")
            putExtra("shiftId", shift.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, shift.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun snoozeReminder(context: Context, shiftId: String, company: String, timeStr: String) {
        val triggerTime = System.currentTimeMillis() + 5 * 60 * 1000L
        val intent = Intent(context, ShiftReminderReceiver::class.java).apply {
            putExtra("company", company)
            putExtra("timeStr", timeStr)
            putExtra("shiftId", shiftId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, shiftId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelReminder(context: Context, shiftId: String) {
        val intent = Intent(context, ShiftReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, shiftId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }
}
