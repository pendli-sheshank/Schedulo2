package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

class ScheduloApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        try {
            val firestore = FirebaseFirestore.getInstance()
            val cacheSettings = PersistentCacheSettings.newBuilder()
                .setSizeBytes(100 * 1024 * 1024)
                .build()
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(cacheSettings)
                .build()
            firestore.firestoreSettings = settings
        } catch (_: Exception) { }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ShiftReminderReceiver.CHANNEL_ID,
                "Shift Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Reminders for upcoming shifts" }
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)
        }
    }
}
