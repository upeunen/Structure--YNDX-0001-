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
    CINEMA,
    COLLECTIONS,
    YOUTUBE
}

enum class RemoteTab {
    REMOTE,
    KEYBOARD
}

data class RemoteUiState(
    val ip: String = RemotePreferences.DEFAULT_IP,
    val port: Int = RemotePreferences.DEFAULT_PORT,
    val torrServerPort: Int = 8090,
    val connectionStatus: StationConnectionStatus = StationConnectionStatus.IDLE,
    val lastMessage: String? = null,
    val activeAppScreen: AppScreen = AppScreen.CINEMA,
    val isRemoteModalOpen: Boolean = false,
    val activeTab: RemoteTab = RemoteTab.REMOTE,
    val keyboardInput: String = "",
    val rutrackerLogin: String = "",
    val rutrackerPass: String = "",
    // Раздел «YouTube»
    val youtubeUrlInput: String = "",
    val youtubeHistory: List<YouTubeItem> = emptyList(),
    // Раздел «Кинозал»
    val cinemaSearchQuery: String = "",
    val selectedCinemaCategory: String = "🕒 История",
    val cinemaMovies: List<MovieItem> = emptyList(),
    val isCinemaLoading: Boolean = false,
    val cinemaStatusMessage: String? = null,
    val activeMoviePlaying: MovieItem? = null,
    val activePlaybackSec: Long = 0L,
    val pendingEpisodeSelection: PendingEpisodeSelection? = null,
    val pendingPeerRetry: PendingPeerRetry? = null,
    val debugLogs: List<String> = emptyList()
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = RemotePreferences(application)
    private val historyRepo = HistoryRepository(application)
    private val youtubeHistoryRepo = YouTubeHistoryRepository(application)

    private val _uiState = MutableStateFlow(
        RemoteUiState(
            ip = prefs.ip,
            port = prefs.port,
            rutrackerLogin = prefs.rutrackerLogin,
            rutrackerPass = prefs.rutrackerPassword,
            youtubeHistory = youtubeHistoryRepo.getHistory(),
            cinemaMovies = historyRepo.getHistory()
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

    @Volatile
    private var isStartingTorrServer: Boolean = false

    private var activeSeriesHash: String? = null
    private var activeSeriesMovie: MovieItem? = null
    private var activeSeriesFiles: List<TorrentVideoFile> = emptyList()
    private var activeSeriesIndex: Int = -1
    private var activePositionSec: Long = 0L
    private var isPlaybackPaused: Boolean = false
    private var playbackMonitorJob: kotlinx.coroutines.Job? = null
    private var youtubeAutoHideJob: kotlinx.coroutines.Job? = null
    private var seekJob: kotlinx.coroutines.Job? = null

    init {
        checkConnection()
        startConnectionHeartbeat()
        viewModelScope.launch {
            ensureTorrServerRunning(notifyIfStarted = false)
        }
        resolveOldYouTubeHistoryTitles()
    }

    private fun startConnectionHeartbeat() {
        viewModelScope.launch {
            while (true) {
                delay(30000L)
                refreshStationState()
            }
        }
    }

    private suspend fun refreshStationState() {
        val isOk = adbClient.testConnection()
        if (!isOk) {
            stopPlaybackMonitor()
            _uiState.value = _uiState.value.copy(
                connectionStatus = StationConnectionStatus.UNAVAILABLE,
                activeMoviePlaying = null
            )
            return
        }
        var stillPlaying = _uiState.value.activeMoviePlaying
        if (stillPlaying != null) {
            val psRes = adbClient.execute("ps | grep -iE \"fireplayer|vlc\"")
            val running = psRes.getOrNull().orEmpty()
            if (psRes.isSuccess && !running.contains("fireplayer") && !running.contains("vlc")) {
                stopPlaybackMonitor()
                stillPlaying = null
            }
        }
        _uiState.value = _uiState.value.copy(
            connectionStatus = StationConnectionStatus.CONNECTED,
            activeMoviePlaying = stillPlaying
        )
    }

    fun setAppScreen(screen: AppScreen) {
        vibrate(20)
        _uiState.value = _uiState.value.copy(activeAppScreen = screen)
        if (screen == AppScreen.CINEMA) {
            viewModelScope.launch {
                ensureTorrServerRunning(notifyIfStarted = true)
            }
        }
    }

    // Проверка и автозапуск TorrServer на Станции через ADB при входе в Кинозал
    suspend fun ensureTorrServerRunning(notifyIfStarted: Boolean = false): Boolean {
        val hostIp = _uiState.value.ip
        if (torrServerClient.isServerRunning()) {
            addLog("TorrServer $hostIp:8090 уже запущен")
            return true
        }
        addLog("TorrServer $hostIp:8090 не отвечает, пробуем запуск через ADB...")
        if (isStartingTorrServer) {
            repeat(6) {
                delay(500)
                if (torrServerClient.isServerRunning()) return true
            }
            return false
        }

        isStartingTorrServer = true
        return try {
            val startCmd =
                "/system/bin/busybox-smp pidof torrserver >/dev/null 2>&1 || " +
                    "(cd /data/torrserver && /system/bin/busybox-smp setsid /data/local/bin/torrserver " +
                    "-c /data/torrserver/config.yaml </dev/null >>/data/torrserver/torr.log 2>&1 &)"
            val adbResult = adbClient.execute(startCmd)
            if (adbResult.isFailure) {
                addLog("ADB запуск TorrServer ошибка: ${adbResult.exceptionOrNull()?.message}")
                return false
            }

            repeat(6) {
                delay(500)
                if (torrServerClient.isServerRunning()) {
                    addLog("TorrServer запущен успешно!")
                    if (notifyIfStarted) {
                        _uiState.value = _uiState.value.copy(
                            connectionStatus = StationConnectionStatus.CONNECTED,
                            lastMessage = "TorrServer запущен на Станции"
                        )
                    }
                    return true
                }
            }
            addLog("TorrServer не поднялся за 3 сек после ADB команды")
            false
        } finally {
            isStartingTorrServer = false
        }
    }

    fun openRemoteModal() {
        vibrate(25)
        _uiState.value = _uiState.value.copy(isRemoteModalOpen = true)
        checkConnection()
    }

    fun closeRemoteModal() {
        _uiState.value = _uiState.value.copy(isRemoteModalOpen = false)
    }

    fun showMessage(message: String) {
        addLog("MSG: $message")
        _uiState.value = _uiState.value.copy(
            lastMessage = message,
            cinemaStatusMessage = message
        )
    }

    fun addLog(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val line = "[$time] $message"
        val updated = (_uiState.value.debugLogs + line).takeLast(100)
        _uiState.value = _uiState.value.copy(debugLogs = updated)
    }

    fun clearLogs() {
        _uiState.value = _uiState.value.copy(debugLogs = emptyList())
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
            refreshStationState()
        }
    }

    private fun cancelYouTubeAutoHide() {
        youtubeAutoHideJob?.cancel()
        youtubeAutoHideJob = null
    }

    private fun scheduleYouTubeAutoHide() {
        cancelYouTubeAutoHide()
        youtubeAutoHideJob = viewModelScope.launch {
            delay(30000L)
            adbClient.execute("dumpsys window windows | grep -q \"mCurrentFocus=.*org.smarttube.stable\" && (input keyevent 19; sleep 0.3; input keyevent 19)")
        }
    }

    fun sendKeyEvent(keyCode: Int) {
        vibrate(30)
        cancelYouTubeAutoHide()
        executeCommand("input keyevent $keyCode")
    }

    // Ввод текста с клавиатуры телефона на ТВ
    fun sendText(rawText: String) {
        vibrate(30)
        cancelYouTubeAutoHide()
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

    // Авто-ввод логина и пароля для RuTracker
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
        cancelYouTubeAutoHide()
        isPlaybackPaused = !isPlaybackPaused
        executeCommand("input keyevent 85")
    }

    fun mediaRewind() {
        vibrate(30)
        cancelYouTubeAutoHide()
        activePositionSec = (activePositionSec - 10L).coerceAtLeast(0L)
        executeCommand("input keyevent 89")
    }

    fun mediaFastForward() {
        vibrate(30)
        cancelYouTubeAutoHide()
        activePositionSec += 10L
        _uiState.value = _uiState.value.copy(activePlaybackSec = activePositionSec)
        executeCommand("input keyevent 90")
    }

    fun seekRelativeSeconds(deltaSec: Long) {
        vibrate(30)
        cancelYouTubeAutoHide()
        activePositionSec = (activePositionSec + deltaSec).coerceAtLeast(0L)
        _uiState.value = _uiState.value.copy(activePlaybackSec = activePositionSec)
        val keyCode = if (deltaSec >= 0) 22 else 21
        val count = if (kotlin.math.abs(deltaSec) >= 60L) 3 else 1
        val cmd = (1..count).joinToString("; ") { "input keyevent $keyCode" }
        seekJob?.cancel()
        seekJob = viewModelScope.launch {
            adbClient.execute(cmd)
        }
    }

    fun mediaPrevious() {
        vibrate(40)
        cancelYouTubeAutoHide()
        if (_uiState.value.activeMoviePlaying != null && activeSeriesFiles.size > 1) {
            playPreviousEpisode()
        } else {
            executeCommand("input keyevent 88")
        }
    }

    fun mediaNext() {
        vibrate(40)
        cancelYouTubeAutoHide()
        if (_uiState.value.activeMoviePlaying != null && activeSeriesFiles.size > 1) {
            playNextEpisode()
        } else {
            executeCommand("input keyevent 87")
        }
    }

    fun volumeMute() {
        vibrate(30)
        cancelYouTubeAutoHide()
        executeCommand("input keyevent 164")
    }

    fun launchYouTube() {
        vibrate(50)
        stopPlaybackMonitor()
        executeCommand("am force-stop com.softwinner.fireplayer; am force-stop org.videolan.vlc; svc power stayon true; input keyevent 224; am start -n org.smarttube.stable/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity")
    }

    fun launchAlice() {
        vibrate(50)
        stopPlaybackMonitor()
        _uiState.value = _uiState.value.copy(activeMoviePlaying = null, lastMessage = "Остановлено: экран Алисы включен")
        viewModelScope.launch {
            val cmd = "am force-stop com.softwinner.fireplayer; am force-stop org.videolan.vlc; am force-stop org.smarttube.stable; am force-stop org.schabi.newpipe; am force-stop top.rootu.lampa; input keyevent 86; input keyevent 3; am start -a android.intent.action.MAIN -c android.intent.category.HOME"
            val result = adbClient.execute(cmd)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(connectionStatus = StationConnectionStatus.CONNECTED)
            }
        }
    }

    // ==========================================
    // ЛОГИКА РАЗДЕЛА «YOUTUBE»
    // ==========================================

    fun launchYouTubeInput(rawInput: String, onComplete: ((Boolean) -> Unit)? = null) {
        vibrate(50)
        val text = rawInput.trim()
        if (text.isEmpty()) {
            _uiState.value = _uiState.value.copy(lastMessage = "Введите запрос или ссылку")
            onComplete?.invoke(false)
            return
        }

        val isUrl = text.contains("http://") || text.contains("https://") || text.contains("youtu.be") || text.contains("youtube.com")
        val cmd: String
        val historyItem: YouTubeItem

        if (isUrl) {
            val extractedUrl = extractUrl(text).ifEmpty { text }
            // Если прислано «Поделиться» в формате "Название https://youtu.be/...", берём название из текста
            val textWithoutUrl = text.replace(extractedUrl, "").trim().trim('-', ':', '|', '—')
            val initialTitle = textWithoutUrl.takeIf { it.length >= 2 }
                ?: fallbackYouTubeTitle(extractedUrl)
            cmd = "am force-stop com.softwinner.fireplayer; svc power stayon true; input keyevent 224; am start -a android.intent.action.VIEW -d \"$extractedUrl\" -p org.smarttube.stable"
            historyItem = YouTubeItem(
                id = "yt_" + System.currentTimeMillis(),
                title = initialTitle,
                urlOrQuery = extractedUrl,
                isUrl = true
            )
        } else {
            val escaped = text.replace("\"", "\\\"")
            cmd = "am force-stop com.softwinner.fireplayer; svc power stayon true; input keyevent 224; am start -a android.intent.action.SEARCH -q \"$escaped\" -p org.smarttube.stable"
            historyItem = YouTubeItem(
                id = "yt_" + System.currentTimeMillis(),
                title = text,
                urlOrQuery = text,
                isUrl = false
            )
        }

        viewModelScope.launch {
            val result = adbClient.execute(cmd)
            if (result.isSuccess) {
                youtubeHistoryRepo.addToHistory(historyItem)
                _uiState.value = _uiState.value.copy(
                    connectionStatus = StationConnectionStatus.CONNECTED,
                    youtubeUrlInput = "",
                    youtubeHistory = youtubeHistoryRepo.getHistory(),
                    lastMessage = if (isUrl) "Видео отправлено на телевизор" else "Поиск «$text» запущен на ТВ"
                )
                onComplete?.invoke(true)
                scheduleYouTubeAutoHide()

                // Подтягиваем реальное название ролика с YouTube для истории
                if (isUrl) {
                    val resolvedTitle = fetchYouTubeTitle(historyItem.urlOrQuery)
                    if (!resolvedTitle.isNullOrBlank()) {
                        youtubeHistoryRepo.addToHistory(historyItem.copy(title = resolvedTitle))
                        _uiState.value = _uiState.value.copy(
                            youtubeHistory = youtubeHistoryRepo.getHistory()
                        )
                    }
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    connectionStatus = StationConnectionStatus.UNAVAILABLE,
                    lastMessage = "Ошибка отправки на Станцию: ${result.exceptionOrNull()?.message}"
                )
                onComplete?.invoke(false)
            }
        }
    }

    fun launchYouTubeHistoryItem(item: YouTubeItem) {
        launchYouTubeInput(item.urlOrQuery)
    }

    fun sendYouTubeUrl(rawUrl: String, onComplete: ((Boolean) -> Unit)? = null) {
        launchYouTubeInput(rawUrl, onComplete)
    }

    fun openSmartTubeSection(section: String) {
        vibrate(35)
        val cmd = when (section) {
            "menu" -> "input keyevent 21"
            "search" -> "input keyevent 84"
            else -> "am force-stop com.softwinner.fireplayer; svc power stayon true; input keyevent 224; am start -n org.smarttube.stable/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity"
        }
        val label = when (section) {
            "menu" -> "Боковое меню"
            "search" -> "Поиск"
            else -> "Главная"
        }
        _uiState.value = _uiState.value.copy(lastMessage = "SmartTube: $label")
        executeCommand(cmd)
    }

    fun bookmarkCurrentYouTubeVideo() {
        vibrate(45)
        viewModelScope.launch {
            val res = adbClient.execute("dumpsys media_session | grep -iE \"description=|metadata\" | head -n 5")
            val raw = res.getOrNull().orEmpty()
            val descMatch = Regex("description=([^,\\n\\r]+)").find(raw)
            val detectedTitle = descMatch?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

            if (detectedTitle != null) {
                val item = YouTubeItem(
                    id = "yt_bm_" + System.currentTimeMillis(),
                    title = "⭐ $detectedTitle",
                    urlOrQuery = detectedTitle,
                    isUrl = false
                )
                youtubeHistoryRepo.addToHistory(item)
                _uiState.value = _uiState.value.copy(
                    youtubeHistory = youtubeHistoryRepo.getHistory(),
                    lastMessage = "Сохранено в закладки: $detectedTitle"
                )
            } else {
                val lastItem = _uiState.value.youtubeHistory.firstOrNull()
                if (lastItem != null) {
                    val cleanTitle = lastItem.title.removePrefix("⭐ ")
                    val bookmarked = lastItem.copy(
                        title = "⭐ $cleanTitle",
                        timestamp = System.currentTimeMillis()
                    )
                    youtubeHistoryRepo.addToHistory(bookmarked)
                    _uiState.value = _uiState.value.copy(
                        youtubeHistory = youtubeHistoryRepo.getHistory(),
                        lastMessage = "Сохранено в закладки: $cleanTitle"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        lastMessage = "Сначала запустите видео, чтобы добавить его в закладки"
                    )
                }
            }
        }
    }

    private fun fallbackYouTubeTitle(url: String): String {
        val idMatch = Regex("(?:youtu\\.be/|v=|shorts/)([A-Za-z0-9_-]{6,})").find(url)
        val vid = idMatch?.groupValues?.get(1)
        return if (!vid.isNullOrBlank()) "Видео YouTube ($vid)" else "Видео YouTube"
    }

    private fun resolveOldYouTubeHistoryTitles() {
        viewModelScope.launch {
            val history = youtubeHistoryRepo.getHistory()
            val toUpdate = history.filter { it.isUrl && it.title.startsWith("http", ignoreCase = true) }
            if (toUpdate.isEmpty()) return@launch
            for (item in toUpdate) {
                val resolved = fetchYouTubeTitle(item.urlOrQuery) ?: fallbackYouTubeTitle(item.urlOrQuery)
                youtubeHistoryRepo.addToHistory(item.copy(title = resolved))
            }
            _uiState.value = _uiState.value.copy(youtubeHistory = youtubeHistoryRepo.getHistory())
        }
    }

    private suspend fun fetchYouTubeTitle(videoUrl: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val encoded = try {
            java.net.URLEncoder.encode(videoUrl, "UTF-8")
        } catch (_: Exception) {
            return@withContext null
        }
        val endpoints = listOf(
            "https://noembed.com/embed?url=$encoded",
            "https://www.youtube.com/oembed?url=$encoded&format=json"
        )
        for (endpoint in endpoints) {
            var conn: java.net.HttpURLConnection? = null
            try {
                val url = java.net.URL(endpoint)
                conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(body)
                    val title = json.optString("title").takeIf { it.isNotBlank() }
                    if (title != null) return@withContext title
                }
            } catch (_: Exception) {
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }
        null
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
            else -> {
                val hist = historyRepo.getHistory()
                _uiState.value = _uiState.value.copy(cinemaMovies = hist)
            }
        }
    }

    fun searchCinema(query: String) {
        vibrate(30)
        val q = query.trim()
        if (q.startsWith("magnet:?", ignoreCase = true)) {
            playMagnetUrl(q)
            return
        }
        val pool = (historyRepo.getHistory() + historyRepo.getFavorites()).distinctBy { it.id }
        val results = if (q.isBlank()) {
            historyRepo.getHistory()
        } else {
            val words = q.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
            pool.filter { m ->
                val text = "${m.title} ${m.description}".lowercase()
                words.all { text.contains(it) }
            }
        }
        _uiState.value = _uiState.value.copy(
            cinemaMovies = results,
            isCinemaLoading = false,
            cinemaStatusMessage = if (results.isEmpty() && q.isNotBlank()) {
                "В истории не найдено. Для поиска новых раздач откройте «Подборки»."
            } else null
        )
    }

    // Запуск фильма на Станции через TorrServer Silo и ADB
    fun playMovieOnStation(movie: MovieItem) {
        vibrate(60)
        setAppScreen(AppScreen.CINEMA)
        viewModelScope.launch {
            val enrichedMagnet = RuTrackerCollections.appendDefaultTrackers(movie.magnetUrl)
            val targetMovie = movie.copy(magnetUrl = enrichedMagnet)

            _uiState.value = _uiState.value.copy(
                isCinemaLoading = true,
                cinemaStatusMessage = "Подготовка «${targetMovie.title}»...",
                lastMessage = "Подготовка «${targetMovie.title}»..."
            )
            historyRepo.addToHistory(targetMovie)
            if (_uiState.value.selectedCinemaCategory == "🕒 История") {
                _uiState.value = _uiState.value.copy(cinemaMovies = historyRepo.getHistory())
            }

            // 0. Убеждаемся, что TorrServer запущен на Станции
            if (!ensureTorrServerRunning(notifyIfStarted = true)) {
                val errMsg = "TorrServer на Станции (${_uiState.value.ip}) не отвечает"
                _uiState.value = _uiState.value.copy(
                    isCinemaLoading = false,
                    cinemaStatusMessage = errMsg,
                    lastMessage = errMsg
                )
                return@launch
            }

            // 1. Отправляем в TorrServer Silo на Станции
            addLog("addTorrent: ${targetMovie.title}")
            val addResult = torrServerClient.addTorrent(targetMovie.magnetUrl, targetMovie.title)
            val torrentHash = addResult.getOrNull()

            if (torrentHash.isNullOrBlank()) {
                val errDetail = addResult.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "пустой ответ"
                addLog("addTorrent FAIL: $errDetail")
                val errMsg = "Ошибка TorrServer: $errDetail"
                _uiState.value = _uiState.value.copy(
                    isCinemaLoading = false,
                    cinemaStatusMessage = errMsg,
                    lastMessage = errMsg
                )
                return@launch
            }
            addLog("addTorrent OK: hash=$torrentHash")

            prepareAndPlayTorrent(torrentHash, targetMovie)
        }
    }

    fun playMagnetUrl(magnetUrl: String) {
        val enriched = RuTrackerCollections.appendDefaultTrackers(magnetUrl)
        val parsedName = RuTrackerCollections.extractDisplayNameFromMagnet(enriched)
            .ifBlank { "Фильм из Magnet ссылки" }
        val movie = MovieItem(
            id = "magnet_" + System.currentTimeMillis(),
            title = parsedName,
            quality = "HD",
            size = "Онлайн",
            seeds = 100,
            magnetUrl = enriched,
            category = "Вставка",
            description = "Запуск по ссылке"
        )
        playMovieOnStation(movie)
    }

    // Загрузка .torrent файла со смартфона на Станцию в TorrServer Silo
    fun uploadTorrentFileAndPlay(fileBytes: ByteArray, fileName: String) {
        vibrate(50)
        setAppScreen(AppScreen.CINEMA)
        viewModelScope.launch {
            if (fileBytes.isEmpty()) {
                showMessage("Выбранный файл пуст")
                return@launch
            }
            val cleanName = fileName.removeSuffix(".torrent").ifBlank { "Торрент-файл" }
            _uiState.value = _uiState.value.copy(
                isCinemaLoading = true,
                cinemaStatusMessage = "Запуск TorrServer и загрузка «$cleanName»...",
                lastMessage = "Загрузка торрент-файла на Станцию..."
            )
            if (!ensureTorrServerRunning(notifyIfStarted = true)) {
                val errMsg = "TorrServer на Станции (${_uiState.value.ip}) не отвечает"
                _uiState.value = _uiState.value.copy(
                    isCinemaLoading = false,
                    cinemaStatusMessage = errMsg,
                    lastMessage = errMsg
                )
                return@launch
            }
            addLog("uploadTorrentFile: name=$fileName, size=${fileBytes.size} байт, url=http://${_uiState.value.ip}:${_uiState.value.torrServerPort}/api/torrents")
            val uploadResult = torrServerClient.uploadTorrentFile(fileBytes, fileName)
            val uploadedInfo = uploadResult.getOrNull()
            val torrentHash = uploadedInfo?.hash

            if (torrentHash.isNullOrBlank()) {
                val errDetail = uploadResult.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "нет ответа"
                addLog("uploadTorrentFile FAIL: $errDetail")
                val errMsg = "Ошибка загрузки торрента: $errDetail"
                _uiState.value = _uiState.value.copy(
                    isCinemaLoading = false,
                    cinemaStatusMessage = errMsg,
                    lastMessage = errMsg
                )
                return@launch
            }
            addLog("uploadTorrentFile OK: hash=$torrentHash, name=${uploadedInfo.name}")

            val isForumPageTitle = cleanName.startsWith("rutracker_") ||
                cleanName.startsWith("movie_") ||
                cleanName.contains("[стр.", ignoreCase = true) ||
                cleanName.contains("форум", ignoreCase = true)
            val displayTitle = if (isForumPageTitle) {
                uploadedInfo.name.ifBlank { cleanName }
            } else {
                cleanName
            }
            val sizeText = when {
                uploadedInfo.sizeBytes >= 1024L * 1024L * 1024L ->
                    String.format(java.util.Locale.US, "%.2f ГБ", uploadedInfo.sizeBytes / (1024.0 * 1024.0 * 1024.0))
                uploadedInfo.sizeBytes >= 1024L * 1024L ->
                    String.format(java.util.Locale.US, "%.0f МБ", uploadedInfo.sizeBytes / (1024.0 * 1024.0))
                else -> "Файл"
            }

            val magnetLink = RuTrackerCollections.buildMagnetWithTrackers(torrentHash, displayTitle)
            val movie = MovieItem(
                id = "torrent_${torrentHash.lowercase()}",
                title = displayTitle,
                quality = "HD",
                size = sizeText,
                seeds = 100,
                magnetUrl = magnetLink,
                category = "Загруженные",
                description = "Загружено из файла $fileName"
            )
            historyRepo.addToHistory(movie)

            _uiState.value = _uiState.value.copy(
                selectedCinemaCategory = "🕒 История",
                cinemaMovies = historyRepo.getHistory()
            )

            prepareAndPlayTorrent(torrentHash, movie)
        }
    }

    // Единый умный пайплайн: ожидание списка файлов -> выбор серии (если их несколько) -> реальная буферизация -> запуск плеера
    private suspend fun prepareAndPlayTorrent(torrentHash: String, movie: MovieItem) {
        _uiState.value = _uiState.value.copy(
            isCinemaLoading = true,
            cinemaStatusMessage = "Ожидание ответа от пиров и списка файлов...",
            lastMessage = "Поиск файлов раздачи..."
        )
        torrServerClient.wakeTorrent(torrentHash)
        val videoFiles = torrServerClient.waitForVideoFiles(torrentHash, maxWaitSec = 15)
        addLog("waitForVideoFiles($torrentHash): найдено ${videoFiles.size} файлов")

        if (videoFiles.isEmpty()) {
            val errMsg = "Не удалось найти раздачу (нет пиров)"
            _uiState.value = _uiState.value.copy(
                isCinemaLoading = false,
                cinemaStatusMessage = errMsg,
                lastMessage = errMsg,
                pendingPeerRetry = PendingPeerRetry(torrentHash = torrentHash, movie = movie)
            )
            return
        }

        if (videoFiles.size > 1) {
            activeSeriesHash = torrentHash
            activeSeriesMovie = movie
            activeSeriesFiles = videoFiles
            val saved = historyRepo.getLastProgressForTorrent(torrentHash)
            _uiState.value = _uiState.value.copy(
                isCinemaLoading = false,
                cinemaStatusMessage = "Найдено серий/файлов: ${videoFiles.size}. Выберите нужный.",
                pendingEpisodeSelection = PendingEpisodeSelection(
                    torrentHash = torrentHash,
                    movie = movie,
                    files = videoFiles,
                    lastFileId = saved?.fileId ?: -1,
                    lastPositionSec = saved?.positionSec ?: 0L
                )
            )
            return
        }

        activeSeriesHash = torrentHash
        activeSeriesMovie = movie
        activeSeriesFiles = videoFiles
        activeSeriesIndex = 0
        bufferAndLaunchFile(torrentHash, videoFiles.first(), movie)
    }

    fun selectEpisodeAndPlay(file: TorrentVideoFile) {
        val pending = _uiState.value.pendingEpisodeSelection ?: return
        vibrate(40)
        activeSeriesHash = pending.torrentHash
        activeSeriesMovie = pending.movie
        activeSeriesFiles = pending.files
        activeSeriesIndex = pending.files.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(
            pendingEpisodeSelection = null,
            isCinemaLoading = true
        )
        viewModelScope.launch {
            bufferAndLaunchFile(pending.torrentHash, file, pending.movie)
        }
    }

    fun dismissEpisodeSelection() {
        _uiState.value = _uiState.value.copy(
            pendingEpisodeSelection = null,
            isCinemaLoading = false,
            cinemaStatusMessage = null
        )
    }

    fun retryPeerSearch() {
        val pending = _uiState.value.pendingPeerRetry ?: return
        vibrate(35)
        _uiState.value = _uiState.value.copy(pendingPeerRetry = null)
        viewModelScope.launch {
            prepareAndPlayTorrent(pending.torrentHash, pending.movie)
        }
    }

    fun dismissPeerRetry() {
        _uiState.value = _uiState.value.copy(
            pendingPeerRetry = null,
            isCinemaLoading = false,
            cinemaStatusMessage = "Запуск отменён"
        )
    }

    private fun playNextEpisode() {
        val hash = activeSeriesHash ?: return
        val movie = activeSeriesMovie ?: return
        if (activeSeriesIndex + 1 >= activeSeriesFiles.size) {
            showMessage("Это последняя серия в списке")
            return
        }
        activeSeriesIndex++
        val nextFile = activeSeriesFiles[activeSeriesIndex]
        addLog("Автопереключение на следующую серию: ${nextFile.name}")
        viewModelScope.launch {
            bufferAndLaunchFile(hash, nextFile, movie)
        }
    }

    private fun playPreviousEpisode() {
        val hash = activeSeriesHash ?: return
        val movie = activeSeriesMovie ?: return
        if (activeSeriesIndex - 1 < 0) {
            showMessage("Это первая серия в списке")
            return
        }
        activeSeriesIndex--
        val prevFile = activeSeriesFiles[activeSeriesIndex]
        addLog("Переключение на предыдущую серию: ${prevFile.name}")
        viewModelScope.launch {
            bufferAndLaunchFile(hash, prevFile, movie)
        }
    }

    private fun stopPlaybackMonitor() {
        playbackMonitorJob?.cancel()
        playbackMonitorJob = null
    }

    private fun startPlaybackMonitor(torrentHash: String, file: TorrentVideoFile, movie: MovieItem) {
        stopPlaybackMonitor()
        playbackMonitorJob = viewModelScope.launch {
            var checks = 0
            while (true) {
                delay(5000)
                if (!isPlaybackPaused) {
                    activePositionSec += 5L
                    _uiState.value = _uiState.value.copy(activePlaybackSec = activePositionSec)
                    historyRepo.savePlaybackProgress(torrentHash, file.id, file.name, activePositionSec)
                }
                checks++
                // Каждые 10 сек (после 30 сек просмотра) проверяем, не завершилась ли серия в плеере
                if (checks % 2 == 0 && checks >= 6 && activeSeriesFiles.size > 1 && !isPlaybackPaused) {
                    val psRes = adbClient.execute("ps | grep -iE \"fireplayer|vlc\"")
                    val running = psRes.getOrNull().orEmpty()
                    if (psRes.isSuccess && !running.contains("fireplayer") && !running.contains("vlc")) {
                        addLog("Плеер завершил серию ${file.name}")
                        if (activeSeriesIndex + 1 < activeSeriesFiles.size) {
                            playNextEpisode()
                        } else {
                            stopPlaybackMonitor()
                        }
                        break
                    }
                }
            }
        }
    }

    private fun formatSeconds(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }

    private suspend fun bufferAndLaunchFile(torrentHash: String, file: TorrentVideoFile, movie: MovieItem) {
        stopPlaybackMonitor()
        val minBufferBytes = 1536 * 1024L // 1.5 МБ
        _uiState.value = _uiState.value.copy(
            isCinemaLoading = true,
            cinemaStatusMessage = "Буферизация «${file.name}»: 0.0 / 1.5 МБ..."
        )
        torrServerClient.preloadFile(torrentHash, file.id)

        val filled = torrServerClient.waitForBuffer(
            torrentHash = torrentHash,
            minBytes = minBufferBytes,
            maxWaitMs = 22000L
        ) { currentBytes ->
            val mb = String.format(java.util.Locale.US, "%.1f", currentBytes / (1024.0 * 1024.0))
            _uiState.value = _uiState.value.copy(
                cinemaStatusMessage = "Буферизация «${file.name}»: $mb / 1.5 МБ..."
            )
        }

        if (filled < 512 * 1024L) {
            addLog("waitForBuffer FAIL: filled=$filled байт")
            val errMsg = "Не удалось набрать буфер (нет пиров)"
            _uiState.value = _uiState.value.copy(
                isCinemaLoading = false,
                cinemaStatusMessage = errMsg,
                lastMessage = errMsg
            )
            return
        }
        addLog("waitForBuffer OK: filled=$filled байт, запуск ADB...")

        val savedPosSec = historyRepo.getFilePositionSec(torrentHash, file.id)
        val posExtra = if (savedPosSec > 15L) "--ei position ${savedPosSec * 1000L} " else ""
        val streamUrl = torrServerClient.getStreamUrl(torrentHash, file.id)
        // Запускаем штатный аппаратный плеер Станции (VLC падает с 404 на HEAD-запрос TorrServer Silo.2)
        val adbCmd = "am force-stop org.smarttube.stable; am force-stop org.videolan.vlc; am force-stop com.softwinner.fireplayer; " +
            "(svc power stayon true; settings put secure screensaver_enabled 0; input keyevent 224) >/dev/null 2>&1 & " +
            "am start -n com.softwinner.fireplayer/.ui.VideoPlayerActivity -a android.intent.action.VIEW -d \"$streamUrl\" -t \"video/*\" $posExtra"
        var adbResult = adbClient.execute(adbCmd)

        if (!adbResult.isSuccess) {
            adbResult = adbClient.execute("am start -a android.intent.action.VIEW -d \"$streamUrl\" -t \"video/*\"")
        }

        if (adbResult.isSuccess) {
            val out = adbResult.getOrNull().orEmpty()
            addLog("ADB launch OK: ${file.name} (${out.take(80)})")
            activePositionSec = savedPosSec
            isPlaybackPaused = false
            historyRepo.savePlaybackProgress(torrentHash, file.id, file.name, savedPosSec)
            val updatedMovie = movie.copy(
                description = if (savedPosSec > 15L) "Продолжить с ${formatSeconds(savedPosSec)} • ${file.name}" else file.name
            )
            historyRepo.addToHistory(updatedMovie)

            val okMsg = if (savedPosSec > 15L) {
                "«${file.name}» запущено (с ${formatSeconds(savedPosSec)})!"
            } else {
                "«${file.name}» запущено на телевизоре!"
            }
            _uiState.value = _uiState.value.copy(
                isCinemaLoading = false,
                activeMoviePlaying = updatedMovie.copy(title = file.name, magnetUrl = streamUrl),
                cinemaMovies = if (_uiState.value.selectedCinemaCategory == "🕒 История") historyRepo.getHistory() else _uiState.value.cinemaMovies,
                cinemaStatusMessage = okMsg,
                lastMessage = okMsg
            )
            startPlaybackMonitor(torrentHash, file, movie)
        } else {
            val adbErr = adbResult.exceptionOrNull()?.message ?: "неизвестно"
            addLog("ADB launch FAIL: $adbErr")
            val errMsg = "Ошибка запуска плеера: $adbErr"
            _uiState.value = _uiState.value.copy(
                isCinemaLoading = false,
                cinemaStatusMessage = errMsg,
                lastMessage = errMsg
            )
        }
    }

    // Пауза, буферизация кэша торрента и возобновление / нормализация
    fun normalizeAndBuffering() {
        vibrate(45)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCinemaLoading = true,
                lastMessage = "Пауза: наполнение буфера торрента..."
            )
            // 1. Ставим плеер на паузу
            executeCommand("input keyevent 85")

            // 2. Будим торрент и запрашиваем наполнение кэша для точного fileId
            val activeMovie = _uiState.value.activeMoviePlaying
            var activeHash: String? = null
            if (activeMovie != null) {
                val hash = torrServerClient.extractHashFromMagnet(activeMovie.magnetUrl)
                    ?: if (activeMovie.magnetUrl.contains("/stream/")) {
                        activeMovie.magnetUrl.substringAfter("/stream/").substringBefore("/")
                    } else null
                val exactFileId = if (activeMovie.magnetUrl.contains("/stream/")) {
                    activeMovie.magnetUrl.substringAfter("/stream/").substringAfter("/", "0").toIntOrNull() ?: 0
                } else 0

                if (!hash.isNullOrBlank()) {
                    activeHash = hash
                    torrServerClient.wakeTorrent(hash)
                    torrServerClient.preloadFile(hash, exactFileId)
                }
            }

            // 3. Ожидаем реальное наполнение буфера
            if (activeHash != null) {
                torrServerClient.waitForBuffer(activeHash, minBytes = 1536 * 1024L, maxWaitMs = 8000L)
            } else {
                delay(3000)
            }

            // 4. Проверяем / перезапускаем плеер, если он вылетел
            if (activeMovie != null && activeMovie.magnetUrl.startsWith("http")) {
                val streamUrl = activeMovie.magnetUrl
                val adbCmd = "am start -n com.softwinner.fireplayer/.ui.VideoPlayerActivity -a android.intent.action.VIEW -d \"$streamUrl\" -t \"video/*\""
                adbClient.execute(adbCmd)
            } else {
                executeCommand("input keyevent 85")
            }

            _uiState.value = _uiState.value.copy(
                isCinemaLoading = false,
                lastMessage = "Буфер пополнен, воспроизведение продолжено"
            )
        }
    }

    fun toggleFavoriteMovie(movie: MovieItem) {
        vibrate(25)
        val isFav = historyRepo.toggleFavorite(movie)
        _uiState.value = _uiState.value.copy(
            lastMessage = if (isFav) "Добавлено в закладки" else "Удалено из закладок"
        )
    }

    fun isFavoriteMovie(movieId: String): Boolean {
        return historyRepo.isFavorite(movieId)
    }

    fun stopCinemaAndReturnAlice() {
        vibrate(50)
        _uiState.value = _uiState.value.copy(activeMoviePlaying = null)
        launchAlice()
    }

    fun clearLastMessage() {
        _uiState.value = _uiState.value.copy(lastMessage = null)
    }

    private fun executeCommand(command: String) {
        viewModelScope.launch {
            val result = adbClient.execute(command)
            if (result.isSuccess) {
                if (_uiState.value.connectionStatus != StationConnectionStatus.CONNECTED) {
                    _uiState.value = _uiState.value.copy(connectionStatus = StationConnectionStatus.CONNECTED)
                }
            }
        }
    }

    companion object {
        fun extractUrl(text: String): String {
            val regex = Regex("https?://[^\\s]+")
            val match = regex.find(text)
            return match?.value ?: ""
        }
    }
}
