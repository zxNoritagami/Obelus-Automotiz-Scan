package com.obelus.protocol.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import com.obelus.protocol.ConnectionState
import com.obelus.protocol.ElmConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * USB Host implementation of [ElmConnection] for ELM327 USB adapters.
 *
 * Supported chips:
 *  - FTDI FT232R  → VID 0x0403 / PID 0x6001
 *  - Prolific PL2303 → VID 0x067B / PID 0x2303
 *  - CH340/CH341  → VID 0x1A86 / PID 0x7523
 *
 * Baud rate: 38400 (ELM327 USB default).
 * Buffer: 64 bytes (standard USB bulk transfer size).
 */
@Singleton
class UsbElmConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbManager: UsbManager
) : ElmConnection {

    companion object {
        private const val TAG = "UsbElmConnection"
        private const val BAUD_RATE = 38400
        private const val BUFFER_SIZE = 64
        private const val WRITE_TIMEOUT_MS = 500
        private const val READ_TIMEOUT_MS = 200
        /** ATZ / reset commands need more time */
        private const val INIT_TIMEOUT_MS = 1000L
        private const val CMD_TIMEOUT_MS = 2500L

        /** Known ELM327 USB adapter VID/PID pairs */
        val SUPPORTED_DEVICES = listOf(
            0x0403 to 0x6001, // FTDI FT232R
            0x0403 to 0x6015, // FTDI FT230X
            0x067B to 0x2303, // Prolific PL2303
            0x067B to 0x23A3, // Prolific PL2303HXD
            0x1A86 to 0x7523, // QinHeng CH340
            0x1A86 to 0x5523, // QinHeng CH341
        )
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Optimizacion: Buffer re-utilizable (PROMPT 13)
    private val readBuffer = ByteArray(1024)
    // Limite reconexiones
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 3

    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private var currentDevice: UsbDevice? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * For USB connections [deviceAddress] is ignored — the ELM327 is found by VID/PID scan.
     * Pass empty string or any value; the connected USB device is auto-detected.
     */
    override suspend fun connect(deviceAddress: String): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.CONNECTING
        reconnectAttempts = 0

        val device = findElm327Device()
        if (device == null) {
            Log.e(TAG, "No ELM327 USB device found")
            _connectionState.value = ConnectionState.ERROR
            return@withContext false
        }

        if (!usbManager.hasPermission(device)) {
            Log.w(TAG, "USB permission not granted for ${device.deviceName}")
            _connectionState.value = ConnectionState.DISCONNECTED
            return@withContext false
        }

        return@withContext openDevice(device)
    }

    /** Connect directly using a device reference (called from UsbPermissionReceiver). */
    suspend fun connectDevice(device: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.CONNECTING
        openDevice(device)
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = ConnectionState.DISCONNECTING
            closeInternal()
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect: ${e.message}", e)
        } finally {
            // Ensure state is DISCONNECTED even if closeInternal() failed
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    override suspend fun send(command: String): String = withContext(Dispatchers.IO) {
        val conn = usbConnection ?: throw IOException("USB not connected")
        val epOut = endpointOut ?: throw IOException("USB OUT endpoint null")
        val epIn = endpointIn ?: throw IOException("USB IN endpoint null")

        try {
            val isInit = command.trim().uppercase().startsWith("AT")
            val timeoutMs = if (isInit) INIT_TIMEOUT_MS else CMD_TIMEOUT_MS

            val cmdBytes = (if (command.endsWith("\r")) command else "$command\r").toByteArray()
            val written = conn.bulkTransfer(epOut, cmdBytes, cmdBytes.size, WRITE_TIMEOUT_MS)
            if (written < 0) throw IOException("USB write failed (return=$written)")

            return@withContext readResponse(conn, epIn, timeoutMs)

        } catch (e: Exception) {
            Log.e(TAG, "send('$command') failed: ${e.message}", e)
            if (e is IOException && e.message?.contains("disconnected", ignoreCase = true) == true) {
                closeInternal()
            }
            _connectionState.value = ConnectionState.ERROR
            throw e
        }
    }

    override fun isConnected(): Boolean =
        usbConnection != null && _connectionState.value == ConnectionState.CONNECTED

    // ── Device discovery ─────────────────────────────────────────────────────

    fun findElm327Device(): UsbDevice? =
        usbManager.deviceList.values.firstOrNull { device ->
            SUPPORTED_DEVICES.any { (vid, pid) ->
                device.vendorId == vid && device.productId == pid
            }
        }

    /** Returns all connected USB devices recognized as ELM327 adapters. */
    fun listElm327Devices(): List<UsbDevice> =
        usbManager.deviceList.values.filter { device ->
            SUPPORTED_DEVICES.any { (vid, pid) ->
                device.vendorId == vid && device.productId == pid
            }
        }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun openDevice(device: UsbDevice): Boolean {
        val intf = findBulkInterface(device)
        if (intf == null) {
            Log.e(TAG, "No bulk interface found on ${device.deviceName}")
            _connectionState.value = ConnectionState.ERROR
            return false
        }

        val epOut = findEndpoint(intf, UsbConstants.USB_DIR_OUT)
        val epIn  = findEndpoint(intf, UsbConstants.USB_DIR_IN)
        if (epOut == null || epIn == null) {
            Log.e(TAG, "Missing bulk endpoints on ${device.deviceName}")
            _connectionState.value = ConnectionState.ERROR
            return false
        }

        val conn = usbManager.openDevice(device)
        if (conn == null) {
            Log.e(TAG, "Could not open USB device ${device.deviceName}")
            _connectionState.value = ConnectionState.ERROR
            return false
        }

        if (!conn.claimInterface(intf, true)) {
            Log.e(TAG, "Could not claim USB interface")
            conn.close()
            _connectionState.value = ConnectionState.ERROR
            return false
        }

        // Configure baud rate via control transfer (FTDI / CP210x style)
        configureBaudRate(conn, device)

        usbConnection = conn
        usbInterface  = intf
        endpointIn    = epIn
        endpointOut   = epOut
        currentDevice = device

        _connectionState.value = ConnectionState.CONNECTED
        Log.i(TAG, "USB connected: ${device.deviceName} (VID=${device.vendorId} PID=${device.productId})")
        return true
    }

    private fun findBulkInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA ||
                intf.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC ||
                intf.interfaceClass == 0xFF) {
                return intf
            }
        }
        // Fallback: first interface with bulk endpoints
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (hasBulkEndpoints(intf)) return intf
        }
        return null
    }

    private fun hasBulkEndpoints(intf: UsbInterface): Boolean {
        var hasIn = false; var hasOut = false
        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) hasIn = true
                else hasOut = true
            }
        }
        return hasIn && hasOut
    }

    private fun findEndpoint(intf: UsbInterface, direction: Int): UsbEndpoint? {
        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == direction) {
                return ep
            }
        }
        return null
    }

    /**
     * Sends a USB control transfer to set baud rate.
     * Works for FTDI chips; Prolific and CH340 have different control transfer sequences
     * but most ELM327 USB clones use 38400 as factory default so this is best-effort.
     */
    private fun configureBaudRate(conn: UsbDeviceConnection, device: UsbDevice) {
        when (device.vendorId) {
            0x0403 -> { // FTDI
                // FTDI set baud rate: value = 3000000 / baud
                val divisor = (3000000 / BAUD_RATE).toShort()
                conn.controlTransfer(
                    0x40,       // requestType: vendor | host-to-device | device
                    0x03,       // request: FTDI_SIO_SET_BAUD_RATE
                    divisor.toInt(),
                    0,
                    null, 0, 500
                )
                Log.d(TAG, "FTDI baud rate set to $BAUD_RATE (divisor=$divisor)")
            }
            0x067B -> { // Prolific PL2303
                // Most PL2303 ELM327 clones already default to 38400 — skip reconfiguration
                Log.d(TAG, "Prolific PL2303: using device default baud ($BAUD_RATE)")
            }
            0x1A86 -> { // CH340/CH341
                Log.d(TAG, "CH34x: using device default baud ($BAUD_RATE)")
            }
        }
    }

    private fun readResponse(
        conn: UsbDeviceConnection,
        epIn: UsbEndpoint,
        timeoutMs: Long
    ): String {
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val read = conn.bulkTransfer(epIn, readBuffer, readBuffer.size, READ_TIMEOUT_MS)
            if (read > 0) {
                val chunk = String(readBuffer, 0, read)
                sb.append(chunk)
                if (chunk.contains('>')) break // ELM327 prompt = end of response
            }
        }

        return sb.toString()
            .replace(">", "")
            .replace("\r", " ")
            .trim()
    }

    private fun closeInternal() {
        try {
            usbInterface?.let { usbConnection?.releaseInterface(it) }
            usbConnection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing USB: ${e.message}")
        } finally {
            usbConnection = null
            usbInterface  = null
            endpointIn    = null
            endpointOut   = null
            currentDevice = null
            _connectionState.value = ConnectionState.DISCONNECTED
            Log.i(TAG, "USB disconnected")
        }
    }
}
