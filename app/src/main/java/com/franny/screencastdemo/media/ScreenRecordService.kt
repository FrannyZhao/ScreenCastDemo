package com.franny.screencastdemo.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import com.franny.screencastdemo.MainActivity
import com.franny.screencastdemo.R
import timber.log.Timber

class ScreenRecordService : Service() {
    private var mediaProjection: MediaProjection? = null


    override fun onCreate() {
        super.onCreate()
        val notification: Notification =
            Notification.Builder(this, "CHANNEL_DEFAULT_IMPORTANCE")
            .build()
        // Notification ID cannot be 0.
        val channel = NotificationChannel(
            "CHANNEL_DEFAULT_IMPORTANCE",
            "NOTIFICATION_CHANNEL_NAME",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "NOTIFICATION_CHANNEL_DESC"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.extras?.let {
            val type = it["type"] as String
            Timber.d("onStartCommand, type is $type")
            when (type) {
                COMMAND_INIT -> {
                    val code = it["code"] as Int
                    val data = it["data"] as? Intent
                    data?.let {
                        val mediaProjectionManager =
                            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjection = mediaProjectionManager.getMediaProjection(code, data)
                    }
                }
                COMMAND_RECORD -> {
                    mediaProjection?.let {
                        ScreenRecorder.INSTANCE.startRecord(this, it)
                    }
                }
                else -> {}
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    companion object {
        const val COMMAND_INIT = "init"
        const val COMMAND_RECORD = "record"
    }
}