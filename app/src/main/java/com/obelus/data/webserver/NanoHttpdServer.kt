package com.obelus.data.webserver

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.Scanner
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Servidor NanoHTTPD ligero para el Dashboard Web.
 */
class NanoHttpdServer(
    private val context: Context,
    port: Int = 8080,
    private val dataProvider: WebDataProvider
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        
        return when {
            uri == "/" || uri == "/index.html" -> serveHtml("web/dashboard.html")
            uri == "/dashboard.css" -> serveStatic("web/dashboard.css", "text/css")
            uri == "/dashboard.js"  -> serveStatic("web/dashboard.js", "application/javascript")
            uri == "/api/events"    -> serveSseEvents()
            uri.startsWith("/api/") -> serveApi(uri)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
        }
    }

    private fun serveHtml(path: String): Response {
        return serveStatic(path, "text/html")
    }

    private fun serveStatic(path: String, mime: String): Response {
        return try {
            val inputStream = context.assets.open(path)
            newChunkedResponse(Response.Status.OK, mime, inputStream)
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error loading asset: ${e.message}")
        }
    }

    private fun serveSseEvents(): Response {
        val inputStream = dataProvider.subscribeToEvents()
        val response = newChunkedResponse(Response.Status.OK, "text/event-stream", inputStream)
        response.addHeader("Cache-Control", "no-cache")
        response.addHeader("Connection", "keep-alive")
        // CORS opcional si el usuario abriese el dashboard en su PC via puerto local en modo dev
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }

    private fun serveApi(uri: String): Response {
        val json = when (uri) {
            "/api/status" -> dataProvider.getSystemStatusJson()
            "/api/pids"   -> dataProvider.getSensorsJson()
            "/api/dtc"    -> dataProvider.getDtcJson()
            "/api/race"   -> dataProvider.getRaceDataJson()
            else -> return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"error\":\"Not Found\"}")
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    interface WebDataProvider {
        fun getSystemStatusJson(): String
        fun getSensorsJson(): String
        fun getDtcJson(): String
        fun getRaceDataJson(): String
        fun subscribeToEvents(): InputStream
    }
}
