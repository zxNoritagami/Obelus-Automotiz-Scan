package com.obelus.util

import android.util.Log
import android.view.Choreographer
import kotlin.math.max

object PerformanceMonitor {
    private const val TAG = "PerfMon"
    private var lastFrameTimeNanos: Long = 0
    private var isMonitoring = false
    
    // Alarma al superar el limite visual de 16.6ms (60 FPS)
    private const val JANK_THRESHOLD_MS = 32.0 

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isMonitoring) return
            
            if (lastFrameTimeNanos != 0L) {
                val deltaMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000.0
                if (deltaMs > JANK_THRESHOLD_MS) {
                    val skippedFrames = (deltaMs / 16.6).toInt()
                    Log.w(TAG, "Jank Detectado! Saltados ~$skippedFrames frames. (Delta: ${deltaMs}ms)")
                }
            }
            lastFrameTimeNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (isMonitoring) return
        isMonitoring = true
        lastFrameTimeNanos = 0
        Choreographer.getInstance().postFrameCallback(frameCallback)
        Log.i(TAG, "Performance Monitor Iniciado")
    }

    fun stop() {
        isMonitoring = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        Log.i(TAG, "Performance Monitor Detenido")
    }

    fun checkMemoryLeaks() {
        val runtime = Runtime.getRuntime()
        val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
        val maxHeapSizeInMB = runtime.maxMemory() / 1048576L
        
        val usagePercentage = (usedMemInMB.toFloat() / maxHeapSizeInMB) * 100
        
        Log.d(TAG, "Memory: ${usedMemInMB}MB / ${maxHeapSizeInMB}MB (%.1f%%)".format(usagePercentage))
        
        if (usagePercentage > 85f) {
            Log.e(TAG, "ALERTA CRÍTICA: La memoria Heap de Dalvik/ART ha excedido el 85%. Posible Memory Leak.")
            // En un futuro se podría auto-limpiar caches internos
        }
    }
}
