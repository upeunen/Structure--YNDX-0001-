package ru.yandex.station.remote.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.yandex.station.remote.adb.AdbClient
import ru.yandex.station.remote.data.RemotePreferences

enum class StationConnectionStatus {
    IDLE,
    CHECKING,
    CONNECTED,
    UNAVAILABLE
}

data class RemoteUiState(
    val ip: String = RemotePreferences.DEFAULT_IP,
    val port: Int = RemotePreferences.DEFAULT_PORT,
    val connectionStatus: StationConnectionStatus = StationConnectionStatus.IDLE,
    val lastMessage: String? = null,
    val youtubeUrlInput: String = ""
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = RemotePreferences(application)
    private val _uiState = MutableStateFlow(
        RemoteUiState(
            ip = prefs.ip,
            port = prefs.port
        )
    )
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()

    private var adbClient = AdbClient(_uiState.value.ip, _uiState.value.port)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        checkConnection()
    }

    fun vibrate(durationMs: Long = 35) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    fun setYoutubeUrlInput(text: String) {
        _uiState.value = _uiState.value.copy(youtubeUrlInput = text)
    }

    fun saveSettings(newIp: String, newPort: Int) {
        prefs.ip = newIp
        prefs.port = newPort
        _uiState.value = _uiState.value.copy(ip = newIp, port = newPort)
        adbClient = AdbClient(newIp, newPort)
        checkConnection()
    }

    fun checkConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(connectionStatus = StationConnectionStatus.CHECKING)
            val isOk = adbClient.testConnection()
            _uiState.value = _uiState.value.copy(
                connectionStatus = if (isOk) StationConnectionStatus.CONNECTED else StationConnectionStatus.UNAVAILABLE
            )
        }
    }

    fun sendKeyEvent(keyCode: Int) {
        vibrate(30)
        executeCommand("input keyevent $keyCode")
    }

    fun launchYouTube() {
        vibrate(50)
        executeCommand("am start -n org.smarttube.stable/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity")
    }

    fun launchKodi() {
        vibrate(50)
        executeCommand("am start -n org.xbmc.kodi/.Splash")
    }

    fun launchAlice() {
        vibrate(50)
        executeCommand("am force-stop org.smarttube.stable; am start -n ru.yandex.quasar.app/.MainActivity")
    }

    fun sendYouTubeUrl(rawUrl: String, onComplete: ((Boolean) -> Unit)? = null) {
        vibrate(50)
        val extractedUrl = extractUrl(rawUrl)
        if (extractedUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(lastMessage = "Ссылка не найдена")
            onComplete?.invoke(false)
            return
        }

        val cmd = "am start -a android.intent.action.VIEW -d \"$extractedUrl\" -p org.smarttube.stable"
        viewModelScope.launch {
            val result = adbClient.execute(cmd)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    connectionStatus = StationConnectionStatus.CONNECTED,
                    lastMessage = "Видео отправлено на телевизор"
                )
                onComplete?.invoke(true)
            } else {
                _uiState.value = _uiState.value.copy(
                    connectionStatus = StationConnectionStatus.UNAVAILABLE,
                    lastMessage = "Ошибка подключения: ${result.exceptionOrNull()?.message}"
                )
                onComplete?.invoke(false)
            }
        }
    }

    fun clearLastMessage() {
        _uiState.value = _uiState.value.copy(lastMessage = null)
    }

    private fun executeCommand(command: String) {
        viewModelScope.launch {
            val result = adbClient.execute(command)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(connectionStatus = StationConnectionStatus.CONNECTED)
            } else {
                _uiState.value = _uiState.value.copy(connectionStatus = StationConnectionStatus.UNAVAILABLE)
            }
        }
    }

    companion object {
        fun extractUrl(text: String): String {
            val regex = Regex("https?://[^\\s]+")
            val match = regex.find(text)
            return match?.value ?: text.trim()
        }
    }
}
