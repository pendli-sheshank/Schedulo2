package com.example

import android.app.Application
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
                .setSizeBytes(100 * 1024 * 1024) // 100 MB local cache
                .build()
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(cacheSettings)
                .build()
            firestore.firestoreSettings = settings
        } catch (_: Exception) { }
    }
}
