package com.jnd.ngdroid.engine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jnd.ngdroid.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps long simulations alive on Android 8.0+
 * (background execution limits). Short sims skip it to avoid notification flicker.
 */
class SimulationService : Service() {

    companion object {
        const val ACTION_START = "com.jnd.ngdroid.action.SIM_START"
        const val ACTION_STOP = "com.jnd.ngdroid.action.SIM_STOP"
        const val EXTRA_TITLE = "extra_title"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context, title: String) {
            val intent = Intent(context, SimulationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
            }
            // minSdk is 26: always a foreground-service launch.
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SimulationService::class.java).apply {
                action = ACTION_STOP
            }
            // startService() from the background throws; fall
            // back to a direct stopService() which needs no launch.
            try {
                context.startForegroundService(intent)
            } catch (_: IllegalStateException) {
                try {
                    context.stopService(Intent(context, SimulationService::class.java))
                } catch (_: Exception) { }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var progressJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                NotificationHelper.ensureChannel(this)
                val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
                    .setContentTitle("Simulating${if (title.isNotBlank()) " — $title" else ""}")
                    .setContentText("ngspice running…")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()
                startForeground(NOTIFICATION_ID, notification)
                watchProgress(title)
                return START_NOT_STICKY
            }
            // OS restart (null intent) or unknown action: never linger as a
            // foreground service with a stale progress loop.
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun watchProgress(title: String) {
        progressJob?.cancel()
        // Repository is process-local; poll latest status via static hook set by ViewModel.
        progressJob = scope.launch {
            while (isActive) {
                delay(1_000)
                val snapshot = ProgressHook.current()
                if (snapshot != null) {
                    val notification = NotificationCompat.Builder(
                        this@SimulationService,
                        NotificationHelper.CHANNEL_ID,
                    )
                        .setContentTitle("Simulating${if (title.isNotBlank()) " — $title" else ""}")
                        .setContentText(snapshot)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .build()
                    postProgressNotification(notification)
                }
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission") // Guarded by hasPostNotifications(); best-effort.
    private fun postProgressNotification(notification: android.app.Notification) {
        try {
            // POST_NOTIFICATIONS (33+) governs app-posted notifications; the FGS
            // notification itself is posted via startForeground and stays exempt.
            // This progress re-notify is best-effort on 33+ without the permission.
            if ((Build.VERSION.SDK_INT < 33) || hasPostNotifications()) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            }
        } catch (_: SecurityException) { /* permission revoked — sim continues */ }
    }

    override fun onDestroy() {
        progressJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    private fun hasPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** Lightweight hook so the service can show status without binding the repository. */
    object ProgressHook {
        @Volatile
        var current: () -> String? = { null }
    }
}
