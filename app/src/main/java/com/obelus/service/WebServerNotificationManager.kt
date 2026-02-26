package com.obelus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.obelus.MainActivity
import com.obelus.R

class WebServerNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "web_server_channel"
        private const val CHANNEL_NAME = "Obelus Web Service"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW 
            ).apply {
                description = "Notificación persistente para el Dashboard Web en tiempo real"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(url: String): Notification {
        // Al tocar la notificacion completa se vuelve a MainActivity (abriendo el WebServerScreen por bundle extra param)
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "web_server")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Al tocar el Botón "Detener Servidor"
        val stopIntent = Intent(context, WebServerService::class.java).apply {
            action = WebServerService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round) // Replace with a neon silhouette ic_neon_web or similar 
            .setContentTitle("Obelus Web - Servidor activo")
            .setContentText("Red: $url")
            .setOngoing(true) // Persistent
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(mainPendingIntent)
            .addAction(0, "DETENER", stopPendingIntent) // Solo accion extra (parar servicio)
            .build()
    }

    fun updateNotification(url: String, notificationId: Int) {
        notificationManager.notify(notificationId, buildNotification(url))
    }
}
