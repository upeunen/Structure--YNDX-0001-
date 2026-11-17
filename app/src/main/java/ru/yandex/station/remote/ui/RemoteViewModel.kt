package ru.yandex.station.remote.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
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

enum class RemoteTab {
    REMOTE,
    TOUCHPAD,
    KEYBOARD
}

data class RemoteUiState(
    val ip: String = RemotePreferences.DEFAULT_IP,
    val port: Int = RemotePreferences.DEFAULT_PORT,
    val connectionStatus: StationConnectionStatus = StationConnectionStatus.IDLE,
    val lastMessage: String? = null,
    val youtubeUrlInput: String = "",
    val activeTab: RemoteTab = RemoteTab.REMOTE,
    val cursorX: Float = 960f,
    val cursorY: Float = 540f,
    val keyboardInput: String = "",
    val rutrackerLogin: String = "",
    val rutrackerPass: String = ""
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = RemotePreferences(application)
    private val _uiState = MutableStateFlow(
        RemoteUiState(
            ip = prefs.ip,
            port = prefs.port,
            rutrackerLogin = prefs.rutrackerLogin,
            rutrackerPass = prefs.rutrackerPassword
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

    fun setActiveTab(tab: RemoteTab) {
        vibrate(20)
        _uiState.value = _uiState.value.copy(activeTab = tab)
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

    fun setKeyboardInput(text: String) {
        _uiState.value = _uiState.value.copy(keyboardInput = text)
    }

    fun setRuTrackerCredentials(login: String, pass: String) {
        _uiState.value = _uiState.value.copy(rutrackerLogin = login, rutrackerPass = pass)
        prefs.rutrackerLogin = login
        prefs.rutrackerPassword = pass
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

    // Ввод текста с клавиатуры телефона на ТВ
    fun sendText(rawText: String) {
        vibrate(30)
        if (rawText.isEmpty()) return
        val escaped = rawText
            .replace(" ", "%s")
            .replace("&", "\\&")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("`", "\\`")
            .replace("$", "\\$")
            .replace(";", "\\;")
            .replace("|", "\\|")
            .replace("<", "\\<")
            .replace(">", "\\>")

        executeCommand("input text \"$escaped\"")
    }

    // Авто-ввод логина и пароля для RuTracker:
    // Ввод логина -> Tab -> Пароль -> Enter
    fun autoFillRuTracker(login: String, pass: String) {
        vibrate(50)
        viewModelScope.launch {
            if (login.isNotEmpty()) {
                sendText(login)
                delay(300)
            }
            // Переход на поле пароля (Tab)
            sendKeyEvent(61)
            delay(300)
            if (pass.isNotEmpty()) {
                sendText(pass)
                delay(300)
            }
            // Нажатие Enter / Войти
            sendKeyEvent(66)
            _uiState.value = _uiState.value.copy(lastMessage = "Данные RuTracker отправлены на ТВ")
        }
    }

    // Управление медиаплеером YouTube / Кино
    fun mediaPlayPause() {
        vibrate(40)
        executeCommand("input keyevent 85")
    }

    fun mediaRewind() {
        vibrate(30)
        executeCommand("input keyevent 89")
    }

    fun mediaFastForward() {
        vibrate(30)
        executeCommand("input keyevent 90")
    }

    fun mediaPrevious() {
        vibrate(40)
        executeCommand("input keyevent 88")
    }

    fun mediaNext() {
        vibrate(40)
        executeCommand("input keyevent 87")
    }

    fun volumeMute() {
        vibrate(30)
        executeCommand("input keyevent 164")
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
        // Гарантированный возврат часов и Алисы:
        // 1) Закрываем сторонние плееры (SmartTube и Kodi)
        // 2) Посылаем системную кнопку HOME (keyevent 3) для возврата лаунчера с часами
        // 3) Запускаем родное приложение Яндекс.Станции Quasar
        executeCommand("am force-stop org.smarttube.stable; am force-stop org.xbmc.kodi; input keyevent 3; monkey -p ru.yandex.quasar.app -c android.intent.category.LAUNCHER 1; am start -a android.intent.action.MAIN -c android.intent.category.HOME")
    }

    // Управление мышью / сенсорной площадкой
    fun moveCursor(deltaX: Float, deltaY: Float, sensitivity: Float = 2.4f) {
        val current = _uiState.value
        val newX = (current.cursorX + deltaX * sensitivity).coerceIn(0f, 1920f)
        val newY = (current.cursorY + deltaY * sensitivity).coerceIn(0f, 1080f)
        _uiState.value = current.copy(cursorX = newX, cursorY = newY)
    }

    // Двойное нажатие = клик в текущей позиции курсора
    fun clickAtCursor() {
        vibrate(55)
        val x = _uiState.value.cursorX.toInt()
        val y = _uiState.value.cursorY.toInt()
        executeCommand("input tap $x $y")
    }

    // Прокрутка (свайп)
    fun scroll(scrollUp: Boolean) {
        vibrate(35)
        val x = _uiState.value.cursorX.toInt()
        if (scrollUp) {
            executeCommand("input swipe $x 750 $x 300 200")
        } else {
            executeCommand("input swipe $x 300 $x 750 200")
        }
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
