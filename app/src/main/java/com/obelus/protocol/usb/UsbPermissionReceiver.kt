package com.obelus.protocol.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import android.widget.Toast
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles USB permission results and USB_DEVICE_ATTACHED events.
 *
 * Register in AndroidManifest and also dynamically with:
 * ```
 * val filter = IntentFilter(UsbPermissionReceiver.ACTION_USB_PERMISSION).apply {
 *     addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
 *     addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
 * }
 * registerReceiver(usbReceiver, filter)
 * ```
 */
class UsbPermissionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UsbReceiverEntryPoint {
        fun usbConnection(): UsbElmConnection
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.obelus.USB_PERMISSION"
        private const val TAG = "UsbPermissionReceiver"
    }

    @Inject
    lateinit var usbConnection: UsbElmConnection

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (!::usbConnection.isInitialized) {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                UsbReceiverEntryPoint::class.java
            )
            usbConnection = entryPoint.usbConnection()
        }

        when (intent.action) {
            ACTION_USB_PERMISSION -> handlePermissionResult(context, intent)
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> handleDeviceAttached(context, intent)
            UsbManager.ACTION_USB_DEVICE_DETACHED -> handleDeviceDetached(intent)
        }
    }

    private fun handlePermissionResult(context: Context, intent: Intent) {
        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

        if (device == null) {
            Log.e(TAG, "USB permission result: device is null")
            return
        }

        if (granted) {
            Log.i(TAG, "USB permission GRANTED for ${device.deviceName}")
            scope.launch {
                val ok = usbConnection.connectDevice(device)
                if (!ok) {
                    Log.e(TAG, "Failed to open USB device after permission granted")
                    showToast(context, "Error al conectar adaptador USB")
                }
            }
        } else {
            Log.w(TAG, "USB permission DENIED for ${device.deviceName}")
            showToast(context, "Permiso USB denegado")
        }
    }

    private fun handleDeviceAttached(context: Context, intent: Intent) {
        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        if (device == null) return

        // Check if it's a known ELM327 device
        val isElm327 = UsbElmConnection.SUPPORTED_DEVICES.any { (vid, pid) ->
            device.vendorId == vid && device.productId == pid
        }

        if (isElm327) {
            Log.i(TAG, "ELM327 USB device attached: ${device.deviceName}")
            showToast(context, "Adaptador ELM327 USB detectado")
        }
    }

    private fun handleDeviceDetached(intent: Intent) {
        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        Log.i(TAG, "USB device detached: ${device?.deviceName}")
        scope.launch {
            if (usbConnection.isConnected()) {
                usbConnection.disconnect()
                Log.w(TAG, "USB device disconnected during use — connection closed safely")
            }
        }
    }

    private fun showToast(context: Context, message: String) {
        // Must run on main thread
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
