package com.obelus.domain.usecase

import android.content.Context
import android.util.Log
import com.obelus.data.repository.ObdRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class EcuBackupUseCase @Inject constructor(
    private val obdRepository: ObdRepository
) {
    suspend fun execute(context: Context, ecuName: String): File? {
        try {
            // 1. Read Coding (UDS Service 0x22 - ReadDataByIdentifier)
            // Common identifiers for coding: 0xF100, 0x0100, etc.
            // For now, we read a few common ones
            val codingData = StringBuilder()
            val ids = listOf("F190", "F188", "F191", "F197") // VIN, Software, HW, Coding
            
            for (id in ids) {
                val response = obdRepository.sendCommand("22 $id")
                if (response.isNotEmpty() && !response.contains("ERROR")) {
                    codingData.append("$id: $response\n")
                }
            }

            if (codingData.isEmpty()) return null

            // 2. Save to internal storage
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${ecuName}_$timestamp.bin"
            val backupDir = File(context.getExternalFilesDir(null), "backups")
            if (!backupDir.exists()) backupDir.mkdirs()
            
            val file = File(backupDir, fileName)
            file.writeText(codingData.toString())
            
            Log.d("EcuBackup", "Backup created: ${file.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e("EcuBackup", "Backup failed", e)
            return null
        }
    }
}
