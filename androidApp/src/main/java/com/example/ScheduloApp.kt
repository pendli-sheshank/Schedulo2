package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

class ScheduloApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        try {
            val firestore = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            firestore.firestoreSettings = settings
        } catch (_: Exception) { }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                ShiftReminderReceiver.CHANNEL_ID,
                "Shift Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm-style reminders for upcoming shifts"
                setSound(alarmUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            }
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)
        }

        NotificationHelper.rescheduleAllReminders(this)
    }
}
