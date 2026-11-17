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
import ru.yandex.station.remote.data.*

enum class StationConnectionStatus {
    IDLE,
    CHECKING,
    CONNECTED,
    UNAVAILABLE
}

enum class AppScreen {
    REMOTE,
    CINEMA
}

enum class RemoteTab {
    REMOTE,
    TOUCHPAD,
    KEYBOARD
}

data class RemoteUiState(
    val ip: String = RemotePreferences.DEFAULT_IP,
    val port: Int = RemotePreferences.DEFAULT_PORT,
    val torrServerPort: Int = 8090,
    val connectionStatus: StationConnectionStatus = StationConnectionStatus.IDLE,
    val lastMessage: String? = null,
    val youtubeUrlInput: String = "",
    val activeAppScreen: AppScreen = AppScreen.REMOTE,
    val activeTab: RemoteTab = RemoteTab.REMOTE,
    val cursorX: Float = 960f,
    val cursorY: Float = 540f,
    val keyboardInput: String = "",
    val rutrackerLogin: String = "",
    val rutrackerPass: String = "",
    // Раздел «Кинозал»
    val cinemaSearchQuery: String = "",
    val selectedCinemaCategory: String = "Новинки",
    val cinemaMovies: List<MovieItem> = RuTrackerCollections.curatedMovies.filter { it.category == "Новинки" },
    val isCinemaLoading: Boolean = false,
    val activeMoviePlaying: MovieItem? = null
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = RemotePreferences(application)
    private val historyRepo = HistoryRepository(application)

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
    private var torrServerClient = TorrServerClient(_uiState.value.ip, _uiState.value.torrServerPort)

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

    fun setAppScreen(screen: AppScreen) {
        vibrate(20)
        _uiState.value = _uiState.value.copy(activeAppScreen = screen)
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
        torrServerClient = TorrServerClient(newIp, _uiState.value.torrServerPort)
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
    fun autoFillRuTracker(login: String, pass: String) {
        vibrate(50)
        viewModelScope.launch {
            if (login.isNotEmpty()) {
                sendText(login)
                delay(300)
            }
            sendKeyEvent(61) // Tab
            delay(300)
            if (pass.isNotEmpty()) {
                sendText(pass)
                delay(300)
            }
            sendKeyEvent(66) // Enter
            _uiState.value = _uiState.value.copy(lastMessage = "Данные RuTracker отправлены на ТВ")
        }
    }

    // Управление медиаплеером YouTube / ТВ
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

    fun launchAlice() {
        vibrate(50)
        // 1) Закрываем сторонние плееры
        // 2) Посылаем системную кнопку HOME (keyevent 3) для возврата лаунчера с часами
        // 3) Запускаем родное приложение Яндекс.Станции Quasar
        executeCommand("am force-stop org.smarttube.stable; am force-stop com.softwinner.fireplayer; am force-stop org.xbmc.kodi; input keyevent 3; monkey -p ru.yandex.quasar.app -c android.intent.category.LAUNCHER 1; am start -a android.intent.action.MAIN -c android.intent.category.HOME")
    }

    // Управление мышью / сенсорной площадкой
    private var accumulatedDragX = 0f
    private var accumulatedDragY = 0f
    private val DRAG_THRESHOLD = 28f

    fun onTouchpadDrag(deltaX: Float, deltaY: Float) {
        accumulatedDragX += deltaX
        accumulatedDragY += deltaY

        val current = _uiState.value
        val newX = (current.cursorX + deltaX * 2.5f).coerceIn(0f, 1920f)
        val newY = (current.cursorY + deltaY * 2.5f).coerceIn(0f, 1080f)
        _uiState.value = current.copy(cursorX = newX, cursorY = newY)

        if (accumulatedDragX > DRAG_THRESHOLD) {
            accumulatedDragX = 0f
            vibrate(15)
            executeCommand("input keyevent 22") // Вправо
        } else if (accumulatedDragX < -DRAG_THRESHOLD) {
            accumulatedDragX = 0f
            vibrate(15)
            executeCommand("input keyevent 21") // Влево
        }

        if (accumulatedDragY > DRAG_THRESHOLD) {
            accumulatedDragY = 0f
            vibrate(15)
            executeCommand("input keyevent 20") // Вниз
        } else if (accumulatedDragY < -DRAG_THRESHOLD) {
            accumulatedDragY = 0f
            vibrate(15)
            executeCommand("input keyevent 19") // Вверх
        }
    }

    fun onTouchpadDragEnd() {
        accumulatedDragX = 0f
        accumulatedDragY = 0f
    }

    // Двойное нажатие = клик
    fun clickAtCursor() {
        vibrate(55)
        val x = _uiState.value.cursorX.toInt()
        val y = _uiState.value.cursorY.toInt()
        executeCommand("input keyevent 66; input tap $x $y")
    }

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

    // ==========================================
    // ЛОГИКА РАЗДЕЛА «КИНОЗАЛ И ТОРРЕНТЫ»
    // ==========================================

    fun setCinemaSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(cinemaSearchQuery = query)
    }

    fun selectCinemaCategory(category: String) {
        vibrate(18)
        _uiState.value = _uiState.value.copy(selectedCinemaCategory = category)
        when (category) {
            "⭐ Закладки" -> {
                val favs = historyRepo.getFavorites()
                _uiState.value = _uiState.value.copy(cinemaMovies = favs)
            }
            "🕒 История" -> {
                val hist = historyRepo.getHistory()
                _uiState.value = _uiState.value.copy(cinemaMovies = hist)
            }
            else -> {
                val filtered = RuTrackerCollections.curatedMovies.filter { it.category == category }
                _uiState.value = _uiState.value.copy(cinemaMovies = filtered)
            }
        }
    }

    fun searchCinema(query: String) {
        vibrate(30)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCinemaLoading = true)
            val results = RuTrackerCollections.searchMovies(query)
            _uiState.value = _uiState.value.copy(
                cinemaMovies = results,
                isCinemaLoading = false,
                selectedCinemaCategory = if (query.isBlank()) "Новинки" else "Поиск"
            )
        }
    }

    // Запуск фильма на Станции через TorrServer и ADB
    fun playMovieOnStation(movie: MovieItem) {
        vibrate(60)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCinemaLoading = true,
                lastMessage = "Запуск «${movie.title}» на Станции..."
            )
            historyRepo.addToHistory(movie)

            // 1. Отправляем в TorrServer на Станции
            val addResult = torrServerClient.addTorrent(movie.magnetUrl, movie.title)
            val torrentHash = addResult.getOrNull()

            if (torrentHash.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isCinemaLoading = false,
                    lastMessage = "Ошибка добавления в TorrServer: ${addResult.exceptionOrNull()?.message}"
                )
                return@launch
            }

            // 2. Формируем локальный поток для плеера Станции
            val streamUrl = "http://127.0.0.1:8090/stream?link=$torrentHash"

            // 3. Останавливаем YouTube и разворачиваем встроенный плеер на ТВ
            val adbCmd = "am force-stop org.smarttube.stable; am start -a android.intent.action.VIEW -d \"$streamUrl\" -t \"video/*\""
            val adbResult = adbClient.execute(adbCmd)

            if (adbResult.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isCinemaLoading = false,
                    activeMoviePlaying = movie,
                    lastMessage = "«${movie.title}» запущено на телевизоре!"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isCinemaLoading = false,
                    lastMessage = "Ошибка запуска плеера через ADB: ${adbResult.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun playMagnetUrl(magnetUrl: String) {
        val movie = MovieItem(
            id = "magnet_" + System.currentTimeMillis(),
            title = "Фильм из Magnet ссылки",
            quality = "HD",
            size = "Онлайн",
            seeds = 100,
            magnetUrl = magnetUrl,
            category = "Вставка",
            description = "Запуск по ссылке"
        )
        setAppScreen(AppScreen.CINEMA)
        playMovieOnStation(movie)
    }

    // Загрузка .torrent файла со смартфона на Станцию
    fun uploadTorrentFileAndPlay(fileBytes: ByteArray, fileName: String) {
        vibrate(50)
        setAppScreen(AppScreen.CINEMA)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCinemaLoading = true,
                lastMessage = "Загрузка торрент-файла на Станцию..."
            )
            val uploadResult = torrServerClient.uploadTorrentFile(fileBytes, fileName)
            val torrentHash = uploadResult.getOrNull()

            if (torrentHash.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isCinemaLoading = false,
                    lastMessage = "Ошибка загрузки торрента: ${uploadResult.exceptionOrNull()?.message}"
                )
                return@launch
            }

            val cleanName = fileName.removeSuffix(".torrent")
            val movie = MovieItem(
                id = "torrent_upload_" + System.currentTimeMillis(),
                title = cleanName,
                quality = "HD",
                size = "Файл",
                seeds = 100,
                magnetUrl = "http://127.0.0.1:8090/stream?link=$torrentHash",
                category = "Загруженные",
                description = "Загружено из файла $fileName"
            )
            historyRepo.addToHistory(movie)

            val streamUrl = "http://127.0.0.1:8090/stream?link=$torrentHash"
            val adbCmd = "am force-stop org.smarttube.stable; am start -a android.intent.action.VIEW -d \"$streamUrl\" -t \"video/*\""
            val adbResult = adbClient.execute(adbCmd)

            if (adbResult.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isCinemaLoading = false,
                    activeMoviePlaying = movie,
                    lastMessage = "«$cleanName» запущено на телевизоре!"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isCinemaLoading = false,
                    lastMessage = "Ошибка запуска плеера через ADB: ${adbResult.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun toggleFavoriteMovie(movie: MovieItem) {
        vibrate(25)
        val isFav = historyRepo.toggleFavorite(movie)
        _uiState.value = _uiState.value.copy(
            lastMessage = if (isFav) "Добавлено в закладки" else "Удалено из закладок"
        )
        // Обновляем список, если открыта вкладка закладок
        if (_uiState.value.selectedCinemaCategory == "⭐ Закладки") {
            _uiState.value = _uiState.value.copy(cinemaMovies = historyRepo.getFavorites())
        }
    }

    fun isFavoriteMovie(movieId: String): Boolean {
        return historyRepo.isFavorite(movieId)
    }

    // Остановка кино и возврат часов/Алисы
    fun stopCinemaAndReturnAlice() {
        vibrate(50)
        _uiState.value = _uiState.value.copy(activeMoviePlaying = null)
        executeCommand("am force-stop org.smarttube.stable; am force-stop com.softwinner.fireplayer; am force-stop org.xbmc.kodi; input keyevent 3; monkey -p ru.yandex.quasar.app -c android.intent.category.LAUNCHER 1; am start -a android.intent.action.MAIN -c android.intent.category.HOME")
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
