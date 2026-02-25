package com.obelus

import android.app.Application
import com.obelus.data.crash.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ObelusApplication : Application() {

    @Inject
    lateinit var crashReporter: CrashReporter

    override fun onCreate() {
        super.onCreate()
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            crashReporter.logCrash(throwable, "uncaught_exception")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
