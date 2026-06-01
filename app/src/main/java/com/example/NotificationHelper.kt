package com.example

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ShiftReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val company = intent.getStringExtra("company") ?: "Work"
        val timeStr = intent.getStringExtra("timeStr") ?: ""
        val shiftId = intent.getStringExtra("shiftId") ?: ""

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Shift Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Reminders for upcoming shifts" }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, shiftId.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Upcoming Shift: $company")
            .setContentText("Your shift starts $timeStr")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(shiftId.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "shift_reminders"
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
