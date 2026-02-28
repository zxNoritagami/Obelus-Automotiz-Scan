package com.obelus.data.crash

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashReporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val crashDir by lazy {
        File(context.getExternalFilesDir(null), "crashes").apply {
            if (!exists()) mkdirs()
        }
    }

    fun logCrash(throwable: Throwable, component: String = "unknown") {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "crash_${timestamp}_$component.txt"
            val file = File(crashDir, fileName)

            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            val deviceContext = """
                Timestamp: ${Date()}
                Context: $component
                App Version: ${getAppVersion()}
                Device: ${Build.MANUFACTURER} ${Build.MODEL}
                Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
                --- STACKTRACE ---
                $sw
            """.trimIndent()

            FileOutputStream(file).use { it.write(deviceContext.toByteArray()) }
            cleanupOldLogs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCrashFiles(): List<File> {
        return crashDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun deleteAllLogs() {
        crashDir.listFiles()?.forEach { it.delete() }
    }

    private fun cleanupOldLogs() {
        val files = crashDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        if (files.size > 10) {
            files.take(files.size - 10).forEach { it.delete() }
        }
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pInfo.versionName} (${pInfo.versionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
