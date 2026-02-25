package com.obelus.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.obelus.data.webserver.WebServerManager
import com.obelus.data.webserver.WebServerState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WebServerService : Service() {

    @Inject
    lateinit var webServerManager: WebServerManager

    private lateinit var notificationManager: WebServerNotificationManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val NOTIFICATION_ID = 8080
        const val ACTION_START = "com.obelus.service.ACTION_START"
        const val ACTION_STOP = "com.obelus.service.ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = WebServerNotificationManager(this)
        
        // Mantener viva la notificación reactivamente si cambia la IP o puerto del Manager
        serviceScope.launch {
            webServerManager.state.collectLatest { state ->
                when (state) {
                    is WebServerState.Running -> {
                        notificationManager.updateNotification(state.url, NOTIFICATION_ID)
                    }
                    is WebServerState.Stopped, is WebServerState.Error -> {
                        if (state is WebServerState.Error) {
                            stopSelf()
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = notificationManager.buildNotification("Iniciando en Puerto 8080...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                // Iniciar el enrutador NanoHTTPD real
                if (webServerManager.state.value !is WebServerState.Running) {
                    webServerManager.startServer(8080)
                }
            }
            ACTION_STOP -> {
                webServerManager.stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        webServerManager.stopServer()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // No se ata a Actividades, solo responde a Intents absolutos.
    }
}
