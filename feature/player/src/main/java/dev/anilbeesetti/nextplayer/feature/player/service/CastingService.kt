package dev.anilbeesetti.nextplayer.feature.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.anilbeesetti.nextplayer.core.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CastingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_START = "dev.anilbeesetti.nextplayer.action.START_CASTING"
        const val ACTION_STOP = "dev.anilbeesetti.nextplayer.action.STOP_CASTING"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_SUBTITLE_PATH = "extra_subtitle_path"
        const val NOTIFICATION_ID = 2001

        fun startIntent(context: Context, filePath: String, subtitlePath: String?) =
            Intent(context, CastingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FILE_PATH, filePath)
                putExtra(EXTRA_SUBTITLE_PATH, subtitlePath)
            }

        fun stopIntent(context: Context) =
            Intent(context, CastingService::class.java).apply {
                action = ACTION_STOP
            }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {}
            ACTION_STOP -> {
                serviceScope.launch {
                    DlnaManager.stopCasting(this@CastingService)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0, stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channel = NotificationChannel(
            "casting_channel", "DLNA Casting",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        return NotificationCompat.Builder(this, "casting_channel")
            .setContentTitle("Casting to device")
            .setContentText("Playing in background")
            .setSmallIcon(R.drawable.ic_cast)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
