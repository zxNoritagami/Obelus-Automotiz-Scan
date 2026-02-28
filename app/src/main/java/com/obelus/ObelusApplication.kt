package com.obelus

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.StrictMode
import android.app.Activity
import android.os.Bundle
import com.obelus.data.cache.CacheManager
import com.obelus.data.cache.DbcCache
import com.obelus.data.crash.CrashReporter
import com.obelus.util.BatteryOptimizer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
// import com.obelus.BuildConfig

@HiltAndroidApp
class ObelusApplication : Application() {

    @Inject lateinit var crashReporter: CrashReporter
    @Inject lateinit var dbcCache: DbcCache
    @Inject lateinit var cacheManager: CacheManager

    private var activeActivities = 0
    private lateinit var batteryOptimizer: BatteryOptimizer
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        batteryOptimizer = BatteryOptimizer(this)
        setupLifecycleCallbacks()

        // Manejo de excepciones no capturadas
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            crashReporter.logCrash(throwable, "uncaught_exception")
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // ── Precarga en background (no bloquea el UI thread) ─────────────────────────────
        appScope.launch {
            // 1. Precargar los 3 DBCs más usados en RAM
            dbcCache.preloadCommonDatabases()

            // 2. Cargar último dispositivo BT cacheado
            cacheManager.getLastBluetoothDevice()?.let { (address, name) ->
                println("[App] Último dispositivo BT: $name ($address)")
            }

            // 3. Verificar estado del Bluetooth (sin conectar)
            val btManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter   = btManager?.adapter
            println("[App] Bluetooth disponible: ${adapter != null}, habilitado: ${adapter?.isEnabled}")

            // 4. Imprimir métricas iniciales
            cacheManager.printMetrics()
        }
    }

    private fun setupLifecycleCallbacks() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                if (activeActivities == 0) {
                    // App en Foreground -> Wakelocks permitidos, full speed
                    batteryOptimizer.applyThrottling(false)
                }
                activeActivities++
            }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                activeActivities--
                if (activeActivities == 0) {
                    // App en Background -> Activar ahorro de bateria estricto
                    batteryOptimizer.applyThrottling(true)
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
