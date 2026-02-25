package com.obelus.presentation.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.webserver.WebServerManager
import com.obelus.data.webserver.WebServerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class WebServerViewModel @Inject constructor(
    private val webServerManager: WebServerManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val serverState: StateFlow<WebServerState> = webServerManager.state

    fun toggleServer() {
        val intent = Intent(context, com.obelus.service.WebServerService::class.java)
        if (serverState.value is WebServerState.Stopped) {
            intent.action = com.obelus.service.WebServerService.ACTION_START
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            intent.action = com.obelus.service.WebServerService.ACTION_STOP
            context.startService(intent)
        }
    }

    fun copyUrlToClipboard(url: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Obelus Dashboard URL", url)
        clipboard.setPrimaryClip(clip)
    }

    fun shareUrl(url: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Obelus Web Dashboard")
            putExtra(Intent.EXTRA_TEXT, "Mira los datos de mi vehículo en tiempo real aqui: $url")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Compartir Dashboard")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun openLocally(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
