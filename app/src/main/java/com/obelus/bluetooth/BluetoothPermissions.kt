package com.obelus.bluetooth

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// ─────────────────────────────────────────────────────────────────────────────
// BluetoothPermissions.kt
// Gestión unificada de permisos Bluetooth para Android 6–14+.
// ─────────────────────────────────────────────────────────────────────────────

object BluetoothPermissions {

    /**
     * Código de solicitud para [requestPermissions] si se usa el flujo clásico
     * de Activity.requestPermissions (fallback no-Compose).
     */
    const val REQUEST_CODE = 1001

    // ── Listas de permisos según API level ────────────────────────────────────

    /**
     * Permisos requeridos en Android 12+ (API 31+).
     * - BLUETOOTH_SCAN: necesario para descubrir dispositivos.
     * - BLUETOOTH_CONNECT: necesario para conectar y leer nombres de dispositivos.
     */
    val PERMISSIONS_API31: List<String> = listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    /**
     * Permisos requeridos en Android 6–11 (API 23–30).
     * - BLUETOOTH / BLUETOOTH_ADMIN: control del adaptador y conexiones.
     * - ACCESS_FINE_LOCATION: requerido por el sistema para el descubrimiento BT clásico.
     */
    @Suppress("DEPRECATION")
    val PERMISSIONS_LEGACY: List<String> = listOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    /**
     * Devuelve la lista de permisos apropiada para el nivel de API del dispositivo.
     */
    val requiredPermissions: List<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PERMISSIONS_API31
        } else {
            PERMISSIONS_LEGACY
        }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Comprueba si todos los permisos Bluetooth requeridos están concedidos.
     *
     * @param context Contexto de la aplicación (no necesita ser Activity).
     * @return `true` si todos los permisos están en estado GRANTED.
     */
    fun hasPermissions(context: Context): Boolean {
        val result = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
        println("[BluetoothPermissions] hasPermissions → $result (API ${Build.VERSION.SDK_INT})")
        return result
    }

    /**
     * Lista de permisos que aún NO han sido concedidos.
     *
     * @param context Contexto de la aplicación.
     * @return Lista vacía si todos están concedidos.
     */
    fun missingPermissions(context: Context): List<String> =
        requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) !=
                    PackageManager.PERMISSION_GRANTED
        }

    /**
     * Solicita los permisos faltantes usando el flujo clásico de Activity
     * (compatible con Views y Fragments).
     *
     * Para Compose, utiliza [rememberLauncherForActivityResult] en el Composable
     * con [android.content.Intent] o [androidx.activity.compose.rememberLauncherForActivityResult]
     * pasando [requiredPermissions].toTypedArray() al launcher de múltiples permisos.
     *
     * @param activity Activity desde la que se lanza la solicitud.
     */
    fun requestPermissions(activity: Activity) {
        val missing = missingPermissions(activity)
        if (missing.isEmpty()) {
            println("[BluetoothPermissions] Todos los permisos ya concedidos.")
            return
        }
        println("[BluetoothPermissions] Solicitando permisos: $missing")
        ActivityCompat.requestPermissions(
            activity,
            missing.toTypedArray(),
            REQUEST_CODE
        )
    }

    /**
     * Verifica si el usuario negó permanentemente algún permiso
     * (seleccionó "No volver a preguntar").
     *
     * @param activity Activity activa.
     * @return `true` si hay al menos un permiso permanentemente denegado.
     */
    fun isPermanentlyDenied(activity: Activity): Boolean =
        missingPermissions(activity).any { permission ->
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }

    /**
     * Comprueba si el hardware Bluetooth está disponible en este dispositivo.
     *
     * @param context Contexto de la aplicación.
     */
    fun isBluetoothSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
}
