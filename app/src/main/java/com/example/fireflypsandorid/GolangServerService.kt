package com.example.fireflypsandorid

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import libandroid.Libandroid

class GolangServerService : Service() {

    companion object {
        const val CHANNEL_ID = "GolangServerChannel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "GolangServerService"
        var isRunning by mutableStateOf(false)
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) {
            Log.d(TAG, "❗ Server is already running")
            return START_STICKY
        }
        isRunning = true
        Log.d(TAG, "onStartCommand called")

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FireflyGO Server")
            .setContentText("FireflyGO is running...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GolangServer::WakeLock")
            wakeLock?.acquire()
            Log.d(TAG, "✅ WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "❌ WakeLock failed", e)
        }

        Thread {
            try {
                val appDataPath = intent?.getStringExtra("appDataPath")
                if (appDataPath != null) {
                    Libandroid.setPathDataLocal(appDataPath)
                    Log.d(TAG, "✅ Set path data: $appDataPath")
                } else {
                    isRunning = false
                    Log.e(TAG, "❌ appDataPath not received in intent")
                    stopSelf()
                    return@Thread
                }

                Libandroid.setServerRunning(true)
                isRunning = true
                Log.d(TAG, "✅ Server started")
            } catch (e: Exception) {
                isRunning = false
                Log.e(TAG, "❌ Error starting server", e)
                stopSelf()
            }
        }.start()


        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")

        // 1. Tắt server
        try {
            val result = Libandroid.setServerRunning(false)
            isRunning = false
            Log.d(TAG, "Server shutdown result: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down server", e)
        }

        // 2. Giải phóng WakeLock nếu còn giữ
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "✅ WakeLock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to release WakeLock", e)
        }
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Golang Server Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for running Golang backend in foreground"
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d(TAG, "✅ Notification channel created")
        }
    }
}
