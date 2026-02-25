package com.obelus

import android.app.Application
import android.os.StrictMode
import android.app.Activity
import android.os.Bundle
import com.obelus.data.crash.CrashReporter
import com.obelus.util.BatteryOptimizer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.obelus.BuildConfig

@HiltAndroidApp
class ObelusApplication : Application() {

    @Inject lateinit var crashReporter: CrashReporter
    
    private var activeActivities = 0
    private lateinit var batteryOptimizer: BatteryOptimizer

    override fun onCreate() {
        super.onCreate()
        crashReporter.init()
        batteryOptimizer = BatteryOptimizer(this)

        if (BuildConfig.DEBUG) {
            setupStrictMode()
        }
        
        setupLifecycleCallbacks()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            crashReporter.logCrash(throwable, "uncaught_exception")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun setupStrictMode() {
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
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
