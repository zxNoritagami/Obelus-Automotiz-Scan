package com.obelus.data.export

import com.obelus.data.local.entity.DtcCode
import com.obelus.data.local.entity.ScanSession
import com.obelus.data.local.entity.SignalReading
import com.obelus.protocol.OBD2Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// JsonExporter.kt
// Exporta datos de sesión en JSON estructurado para integración técnica.
// Incluye metadatos: sesión, protocolo, DBC, señales, lecturas, DTCs.
// ─────────────────────────────────────────────────────────────────────────────

private val ISO8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Singleton
class JsonExporter @Inject constructor() {

    /**
     * Genera un archivo JSON completo con todos los datos de la sesión.
     *
     * @param session       Sesión de diagnóstico.
     * @param readings      Lecturas de señales.
     * @param dtcs          Códigos de falla.
     * @param protocol      Protocolo OBD2 detectado.
     * @param dbcFileName   Nombre del DBC cargado (null = genérico).
     * @param outputFile    Archivo destino.
     * @return El archivo escrito.
     */
    suspend fun exportSession(
        session: ScanSession,
        readings: List<SignalReading>,
        dtcs: List<DtcCode>,
        protocol: OBD2Protocol?,
        dbcFileName: String?,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {

            // ── Metadatos ─────────────────────────────────────────────────────
            put("schema_version", "1.0")
            put("generator", "Obelus Scan")
            put("generated_at", ISO8601.format(Date()))

            // ── Sesión ────────────────────────────────────────────────────────
            put("session", JSONObject().apply {
                put("id",         session.id)
                put("start_time", ISO8601.format(Date(session.startTime)))
                session.endTime?.let { put("end_time", ISO8601.format(Date(it))) }
                session.notes?.let { if (it.isNotBlank()) put("notes", it) }
                session.averageSpeed?.let { put("avg_speed_kmh", it) }
                put("duration_seconds",
                    ((session.endTime ?: System.currentTimeMillis()) - session.startTime) / 1000)
            })

            // ── Conexión ──────────────────────────────────────────────────────
            put("connection", JSONObject().apply {
                protocol?.let {
                    put("protocol_name",      it.protocolName)
                    put("protocol_at_command", it.atCommand)
                } ?: put("protocol", "unknown")
                dbcFileName?.let { put("dbc_file", it) } ?: put("dbc_file", "generic_obd2")
            })

            // ── Estadísticas por señal ────────────────────────────────────────
            val statsArray = JSONArray()
            readings
                .groupBy { it.pid }
                .forEach { (pid, list) ->
                    val values = list.map { it.value }
                    statsArray.put(JSONObject().apply {
                        put("pid",    pid)
                        put("name",   list.first().name)
                        put("unit",   list.first().unit)
                        put("min",    values.min())
                        put("avg",    values.average())
                        put("max",    values.max())
                        put("count",  values.size)
                    })
                }
            put("signal_stats", statsArray)

            // ── Lecturas brutas (primeras 5000 para tamaño razonable) ──────────
            val readingsArray = JSONArray()
            readings
                .sortedBy { it.timestamp }
                .take(5_000)
                .forEach { r ->
                    readingsArray.put(JSONObject().apply {
                        put("ts",    r.timestamp)
                        put("pid",   r.pid)
                        put("name",  r.name)
                        put("value", r.value)
                        put("unit",  r.unit)
                    })
                }
            put("readings",       readingsArray)
            put("readings_total", readings.size)
            if (readings.size > 5_000) {
                put("readings_truncated", true)
                put("readings_truncated_note", "Only first 5000 readings exported for size constraints.")
            }

            // ── DTCs ──────────────────────────────────────────────────────────
            val dtcArray = JSONArray()
            dtcs.sortedBy { it.code }.forEach { d ->
                dtcArray.put(JSONObject().apply {
                    put("code",         d.code)
                    put("description",  d.description ?: "")
                    put("category",     d.category.toString())
                    put("is_active",    d.isActive)
                    put("is_pending",   d.isPending)
                    put("is_permanent", d.isPermanent)
                    d.sessionId?.let { put("session_id", it) }
                })
            }
            put("dtcs", dtcArray)
        }

        outputFile.writeText(root.toString(2), Charsets.UTF_8)
        println(
            "[JsonExporter] JSON → ${outputFile.name} " +
            "(${readings.size} readings, ${dtcs.size} DTCs, ~${outputFile.length() / 1024}KB)"
        )
        outputFile
    }
}
