package com.jnd.ngdroid.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationHelper {
    const val CHANNEL_ID = "simulation"
    const val CHANNEL_NAME = "Simulations"

    fun ensureChannel(context: Context) {
        // minSdk is 26: NotificationChannel always exists.
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing SPICE simulation status"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
