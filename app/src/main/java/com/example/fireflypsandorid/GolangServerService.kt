package com.example.fireflypsandorid

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import libandroid.Libandroid

class GolangServerService : Service() {
    companion object {
        const val CHANNEL_ID = "GolangServerChannel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "GolangServerService"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Golang Server")
            .setContentText("Server đang chạy")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // Chạy server trong thread riêng để tránh ANR
        Thread {
            try {
                val appDataPath = intent?.getStringExtra("appDataPath")

                if (appDataPath != null) {
                    Libandroid.setPathDataLocal(appDataPath)
                    Log.d(TAG, "✅ Set path data: $appDataPath")
                } else {
                    Log.e(TAG, "❌ appDataPath not received in intent")
                }

                Libandroid.setServerRunning(true)
                Log.d(TAG, "✅ Server started")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Lỗi khi chạy server:", e)
            }
        }.start()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            val result = Libandroid.setServerRunning(false)
            Log.d(TAG, "Server shutdown result: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down server", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Golang Server Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Thông báo khi server Golang đang chạy"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
