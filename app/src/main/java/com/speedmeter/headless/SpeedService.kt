package com.speedmeter.headless

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import java.util.Locale

class SpeedService : Service() {

    companion object {
        private const val CHANNEL_ID = "speed_meter_status_channel"
        private const val NOTIFICATION_ID = 7701
        private const val UPDATE_INTERVAL_MS = 2000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager

    private var isTracking = false
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastTimeMs = 0L
    private var initialRxBytes = 0L
    private var initialTxBytes = 0L
    private var isReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    pauseTracking()
                }
                Intent.ACTION_SCREEN_ON -> {
                    resumeTracking()
                }
            }
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isTracking) return

            val currentRx = TrafficStats.getTotalRxBytes()
            val currentTx = TrafficStats.getTotalTxBytes()
            val currentTimeMs = SystemClock.elapsedRealtime()

            var rxSpeed = 0.0
            var txSpeed = 0.0

            if (lastRxBytes > 0 && currentRx >= lastRxBytes && currentTimeMs > lastTimeMs) {
                val timeDiffSec = (currentTimeMs - lastTimeMs) / 1000.0
                if (timeDiffSec > 0.0) {
                    rxSpeed = (currentRx - lastRxBytes) / timeDiffSec
                    if (lastTxBytes > 0 && currentTx >= lastTxBytes) {
                        txSpeed = (currentTx - lastTxBytes) / timeDiffSec
                    }
                }
            }

            updateNotification(rxSpeed, txSpeed, currentRx, currentTx)

            lastRxBytes = currentRx
            lastTxBytes = currentTx
            lastTimeMs = currentTimeMs

            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
            isReceiverRegistered = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        resumeTracking()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        pauseTracking()
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isReceiverRegistered = false
        }
    }

    private fun startAsForeground() {
        val currentRx = TrafficStats.getTotalRxBytes()
        val currentTx = TrafficStats.getTotalTxBytes()
        if (initialRxBytes == 0L && currentRx > 0) initialRxBytes = currentRx
        if (initialTxBytes == 0L && currentTx > 0) initialTxBytes = currentTx

        val initialNotification = buildNotification(0.0, 0.0, currentRx, currentTx)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                0
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
    }

    private fun resumeTracking() {
        if (isTracking) return
        isTracking = true
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        if (initialRxBytes == 0L && lastRxBytes > 0) initialRxBytes = lastRxBytes
        if (initialTxBytes == 0L && lastTxBytes > 0) initialTxBytes = lastTxBytes
        lastTimeMs = SystemClock.elapsedRealtime()

        handler.removeCallbacks(updateRunnable)
        handler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS)
    }

    private fun pauseTracking() {
        isTracking = false
        handler.removeCallbacks(updateRunnable)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Internet Speed Monitor",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Real-time internet speed indicator in the status bar"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        rxBytesPerSec: Double,
        txBytesPerSec: Double,
        totalRx: Long,
        totalTx: Long
    ): Notification {
        val bitmap = SpeedBitmapGenerator.createSpeedBitmap(rxBytesPerSec)
        val icon = Icon.createWithBitmap(bitmap)
        val formattedRx = SpeedBitmapGenerator.formatFullSpeed(rxBytesPerSec)
        val formattedTx = SpeedBitmapGenerator.formatFullSpeed(txBytesPerSec)

        val sessionRxMB = if (initialRxBytes > 0 && totalRx >= initialRxBytes) {
            (totalRx - initialRxBytes) / (1024.0 * 1024.0)
        } else 0.0

        val sessionTxMB = if (initialTxBytes > 0 && totalTx >= initialTxBytes) {
            (totalTx - initialTxBytes) / (1024.0 * 1024.0)
        } else 0.0

        val contentTitle = "↓ $formattedRx   •   ↑ $formattedTx"
        val contentText = String.format(Locale.US, "%.1f MB down  •  %.1f MB up", sessionRxMB, sessionTxMB)

        val bigText = StringBuilder()
            .append("↓ Download: ").append(formattedRx).append(String.format(Locale.US, " (Total: %.1f MB)\n", sessionRxMB))
            .append("↑ Upload:   ").append(formattedTx).append(String.format(Locale.US, " (Total: %.1f MB)", sessionTxMB))
            .toString()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder.setSmallIcon(icon)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setStyle(Notification.BigTextStyle().bigText(bigText))
            .setColor(android.graphics.Color.parseColor("#38BDF8"))
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    private fun updateNotification(
        rxBytesPerSec: Double,
        txBytesPerSec: Double,
        totalRx: Long,
        totalTx: Long
    ) {
        val notification = buildNotification(rxBytesPerSec, txBytesPerSec, totalRx, totalTx)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
