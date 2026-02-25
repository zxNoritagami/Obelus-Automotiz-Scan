package com.obelus.util

import android.content.Context
import android.os.PowerManager
import android.util.Log

class BatteryOptimizer(context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        private const val TAG = "BatteryOpt"
        var isThrottled = false
            private set
    }

    /**
     * Adquiere un WakeLock para evitar que el CPU duerma.
     * Solo usar durante operaciones OBD criticas sostenidas.
     */
    fun acquireWakeLock(tag: String = "Obelus:DiagnosticLock") {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
                acquire(20 * 60 * 1000L /*20 minutes max timeout backup*/)
            }
            Log.i(TAG, "WakeLock [$tag] asegurado. CPU despierta.")
        }
    }

    /**
     * Libera sistematicamente cualquier lock al detener el scaner.
     */
    fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            wakeLock = null
            Log.i(TAG, "WakeLock liberado. Permitiendo Doze.")
        }
    }

    /**
     * Cambia la app a modo throttled globalmente.
     * ViewModels y Managers evaluaran esta bandera para bajar frecuencias.
     */
    fun applyThrottling(active: Boolean) {
        if (isThrottled != active) {
            isThrottled = active
            Log.i(TAG, "Battery Throttling estado cambiado a: $active")
            if(active) {
                // Al ir a BG liberamos wakelocks forzosamente si no estamos en una sesion critica grabada
                releaseWakeLock() 
            }
        }
    }
}
