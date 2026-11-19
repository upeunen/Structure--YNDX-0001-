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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.yandex.station.remote.adb.AdbClient
import ru.yandex.station.remote.adb.MediaPlaybackInfo
import ru.yandex.station.remote.adb.StationMediaManager
import ru.yandex.station.remote.data.*

enum class StationConnectionStatus {
    IDLE,
    CHECKING,
    CONNECTED,
    UNAVAILABLE
}

enum class AppScreen {
    CINEMA,
    SHOWCASE,
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
    val isPlaying: Boolean = false,
    val hasSeries: Boolean = false,
    val currentEpisodeTitle: String? = null,
    val hasNextEpisode: Boolean = false,
    val hasPrevEpisode: Boolean = false,
    val isCleaningMemory: Boolean = false,
    val pendingEpisodeSelection: PendingEpisodeSelection? = null,
    val pendingPeerRetry: PendingPeerRetry? = null,
    val torrServerLiveStats: TorrServerClient.ActiveTorrentStats? = null,
    val debugLogs: List<String> = emptyList(),
    // Витрина онлайн-кинотеатра и подборки
    val movieSections: List<MovieSection> = emptyList(),
    val isMovieCatalogLoading: Boolean = false,
    val selectedCatalogMovie: MovieCatalogItem? = null,
    val movieReleases: List<TorrentRelease> = emptyList(),
    val isLoadingReleases: Boolean = false,
    val releasesError: String? = null,
    val catalogSearchQuery: String = "",
    val catalogSearchResults: List<MovieCatalogItem> = emptyList(),
    val isSearchingCatalog: Boolean = false,
    val isShowcaseFilterOpen: Boolean = false,
    val enabledShowcaseCategories: Set<String> = emptySet(),
    val isRuTrackerCaptchaWaiting: Boolean = false
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = RemotePreferences(application)
    private val historyRepo = HistoryRepository(application)
    private val showcaseRepo = ShowcaseRepository(application)
    private val youtubeHistoryRepo = YouTubeHistoryRepository(application)

    private val _uiState = MutableStateFlow(
        RemoteUiState(
            ip = prefs.ip,
            port = prefs.port,
            rutrackerLogin = prefs.rutrackerLogin,
            rutrackerPass = prefs.rutrackerPassword,
            youtubeHistory = youtubeHistoryRepo.getHistory(),
            cinemaMovies = historyRepo.getHistory(),
            enabledShowcaseCategories = if (prefs.enabledRuTrackerCategories.size <= 8) {
                val full = RuTrackerScraper.CATEGORIES.map { it.id }.toSet()
                prefs.enabledRuTrackerCategories = full
                full
            } else {
                prefs.enabledRuTrackerCategories
            }
        )
    )
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()

    private var showcaseEnrichmentJob: kotlinx.coroutines.Job? = null

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

    private val fastCommandChannel = kotlinx.coroutines.channels.Channel<String>(capacity = 64)
    @Volatile
    private var lastUserActionTime: Long = 0L

    private var activeSeriesHash: String? = null
    private var activeSeriesMovie: MovieItem? = null
    private var activeSeriesFiles: List<TorrentVideoFile> = emptyList()
    private var activeSeriesIndex: Int = -1
    private var activePositionSec: Long = 0L
    private var isPlaybackPaused: Boolean = false
    private var playbackMonitorJob: kotlinx.coroutines.Job? = null
    private var heartbeatJob: kotlinx.coroutines.Job? = null
    private var manualCheckJob: kotlinx.coroutines.Job? = null
    private val refreshMutex = Mutex()
    @Volatile
    private var isAppInForeground: Boolean = true
    private var youtubeAutoHideJob: kotlinx.coroutines.Job? = null
    private var playerMissCount = 0
    private var seekJob: kotlinx.coroutines.Job? = null
    private var lastSeekTimestamp: Long = 0L

    init {
        resumeSession()
        startCommandQueueProcessor()
        viewModelScope.launch {
            ensureTorrServerRunning(notifyIfStarted = false)
        }
        resolveOldYouTubeHistoryTitles()
        viewModelScope.launch {
            loadMovieCatalog()
        }
    }

    private fun startCommandQueueProcessor() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (isActive) {
                try {
                    for (cmd in fastCommandChannel) {
                        val res = adbClient.executeFast(cmd)
                        if (res.isSuccess) {
                            if (_uiState.value.connectionStatus != StationConnectionStatus.CONNECTED) {
                                _uiState.value = _uiState.value.copy(connectionStatus = StationConnectionStatus.CONNECTED)
                            }
                        }
                    }
                } catch (_: Exception) {
                    kotlinx.coroutines.delay(500)
                }
            }
        }
    }

    fun resumeSession() {
        isAppInForeground = true
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            // Первая проверка при включении экрана (только если прямо сейчас не идет ручная проверка)
            if (refreshMutex.tryLock()) {
                try {
                    refreshStationStateInternal(isManualCheck = false)
                } finally {
                    refreshMutex.unlock()
                }
            }

            // Фоновый опрос только пока приложение на переднем плане:
            // Если связи нет (или Wi-Fi телефона восстанавливается) — интервал 3.5 сек.
            // При стабильном подключении — спокойный интервал (4 сек для пульта, 8 сек для витрины).
            while (isAppInForeground) {
                val isConnected = _uiState.value.connectionStatus == StationConnectionStatus.CONNECTED
                val delayMs = when {
                    !isConnected -> 3500L
                    _uiState.value.isRemoteModalOpen -> 4000L
                    else -> 8000L
                }
                delay(delayMs)
                if (isAppInForeground) {
                    if (refreshMutex.tryLock()) {
                        try {
                            refreshStationStateInternal(isManualCheck = false)
                        } finally {
                            refreshMutex.unlock()
                        }
                    }
                }
            }
        }
    }

    fun pauseSession() {
        isAppInForeground = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        manualCheckJob?.cancel()
        manualCheckJob = null
    }

    private suspend fun refreshStationStateInternal(isManualCheck: Boolean = false) {
        // Если пользователь недавно нажимал кнопки (меньше 3 сек) и это не ручная проверка, не перегружаем Станцию
        if (!isManualCheck && System.currentTimeMillis() - lastUserActionTime < 3000L) {
            return
        }

        val prevStatus = _uiState.value.connectionStatus
        val res = adbClient.testConnectionDetailed()

        if (!res.isSuccess) {
            stopPlaybackMonitor()
            _uiState.value = _uiState.value.copy(
                connectionStatus = StationConnectionStatus.UNAVAILABLE
            )
            if (isManualCheck || prevStatus != StationConnectionStatus.UNAVAILABLE) {
                addLog("Связь FAIL (${res.durationMs}мс): ${res.errorMessage ?: "Нет ответа"}")
            }
            return
        }

        if (isManualCheck || prevStatus != StationConnectionStatus.CONNECTED) {
            addLog("Связь OK: пинг ${res.tcpConnectMs}мс, ответ ADB ${res.handshakeMs}мс (всего ${res.durationMs}мс)")
        }

        // Синхронизация реального времени воспроизведения с ТВ (SmartTube или VLC)
        if (_uiState.value.isRemoteModalOpen) {
            val mediaInfo = StationMediaManager.getMediaInfo(adbClient)
            if (mediaInfo != null && (mediaInfo.isPlaying || mediaInfo.state == 2 || mediaInfo.state == 6)) {
                activePositionSec = mediaInfo.positionSec
                isPlaybackPaused = mediaInfo.state == 2
                _uiState.value = _uiState.value.copy(
                    activePlaybackSec = activePositionSec,
                    isPlaying = mediaInfo.isPlaying
                )
            }
        }

        val liveStats = torrServerClient.getActiveTorrentStats()
        var stillPlaying = _uiState.value.activeMoviePlaying
        if (stillPlaying != null && liveStats == null) {
            val psRes = adbClient.execute("ps | grep -i vlc")
            val running = psRes.getOrNull().orEmpty()
            if (psRes.isSuccess && !running.contains("vlc")) {
                playerMissCount++
                if (playerMissCount >= 3) {
                    stopPlaybackMonitor()
                    stillPlaying = null
                    playerMissCount = 0
                }
            } else {
                playerMissCount = 0
            }
        }
        _uiState.value = _uiState.value.copy(
            connectionStatus = StationConnectionStatus.CONNECTED,
            activeMoviePlaying = stillPlaying,
            torrServerLiveStats = liveStats
        )
    }

    fun setAppScreen(screen: AppScreen) {
        vibrate(20)
        _uiState.value = _uiState.value.copy(activeAppScreen = screen)
        if (screen == AppScreen.CINEMA || screen == AppScreen.SHOWCASE) {
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
        viewModelScope.launch {
            StationMediaManager.ensureDeployed(getApplication(), adbClient)
            val info = StationMediaManager.getMediaInfo(adbClient)
            if (info != null && (info.isPlaying || info.state == 2 || info.state == 6)) {
                activePositionSec = info.positionSec
                isPlaybackPaused = info.state == 2
                _uiState.value = _uiState.value.copy(
                    activePlaybackSec = activePositionSec,
                    isPlaying = info.isPlaying
                )
            }
        }
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
        manualCheckJob?.cancel()
        manualCheckJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(connectionStatus = StationConnectionStatus.CHECKING)
            addLog("Проверка связи со Станцией (${_uiState.value.ip}:${_uiState.value.port})...")
            refreshMutex.withLock {
                refreshStationStateInternal(isManualCheck = true)
            }
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
        lastUserActionTime = System.currentTimeMillis()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = adbClient.execute("input keyevent $keyCode")
            if (result.isSuccess) {
                if (_uiState.value.connectionStatus != StationConnectionStatus.CONNECTED) {
                    _uiState.value = _uiState.value.copy(connectionStatus = StationConnectionStatus.CONNECTED)
                }
            }
        }
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

    fun tapCenter() {
        vibrate(40)
        lastUserActionTime = System.currentTimeMillis()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            adbClient.execute("input tap 960 540")
        }
        _uiState.value = _uiState.value.copy(lastMessage = "👆 Тап по центру экрана")
    }

    // Управление медиаплеером YouTube / ТВ (мгновенное переключение с первого тапа)
    fun mediaPlayPause() {
        vibrate(40)
        cancelYouTubeAutoHide()
        lastUserActionTime = System.currentTimeMillis()
        val nextPlaying = !_uiState.value.isPlaying
        _uiState.value = _uiState.value.copy(isPlaying = nextPlaying)
        isPlaybackPaused = !nextPlaying
        fastCommandChannel.trySend("input keyevent 85")
    }

    fun mediaRewind() {
        seekRelativeSeconds(-10L)
    }

    fun mediaFastForward() {
        seekRelativeSeconds(10L)
    }

    fun parseTimeToSeconds(input: String): Long? {
        val clean = input.trim().lowercase().replace(" ", "").replace("мин", "").replace("m", "").replace("м", "")
        if (clean.isEmpty()) return null
        val parts = clean.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            1 -> {
                val num = parts[0]
                if (num <= 180) num * 60L else num
            }
            2 -> parts[0] * 60L + parts[1]
            3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
            else -> null
        }
    }

    fun seekToExactTime(timeStr: String) {
        val targetSec = parseTimeToSeconds(timeStr) ?: run {
            _uiState.value = _uiState.value.copy(lastMessage = "Неверный формат времени (пример: 15:30 или 45)")
            return
        }
        vibrate(40)
        cancelYouTubeAutoHide()

        viewModelScope.launch {
            StationMediaManager.ensureDeployed(getApplication(), adbClient)
            val info = StationMediaManager.seekTo(adbClient, targetSec * 1000L)
            if (info != null) {
                activePositionSec = info.positionSec
                _uiState.value = _uiState.value.copy(
                    activePlaybackSec = info.positionSec,
                    lastMessage = "Переход на ${formatSeconds(info.positionSec)}"
                )
            } else {
                val activeMovie = _uiState.value.activeMoviePlaying
                if (activeMovie != null && activeMovie.magnetUrl.startsWith("http")) {
                    val streamUrl = activeMovie.magnetUrl
                    val cmd = "am start -n org.videolan.vlc/.StartActivity -a android.intent.action.VIEW -d \"$streamUrl\" -t \"video/*\" --ei position ${targetSec * 1000L}"
                    adbClient.execute(cmd)
                } else if (_uiState.value.activeAppScreen == AppScreen.YOUTUBE && _uiState.value.youtubeUrlInput.isNotBlank()) {
                    val url = extractUrl(_uiState.value.youtubeUrlInput).ifEmpty { _uiState.value.youtubeUrlInput }
                    val targetUrl = if (url.contains("?")) "$url&t=${targetSec}s" else "$url?t=${targetSec}s"
                    val cmd = "am start -a android.intent.action.VIEW -d \"$targetUrl\" -p org.smarttube.stable"
                    adbClient.execute(cmd)
                }
                activePositionSec = targetSec
                _uiState.value = _uiState.value.copy(
                    activePlaybackSec = targetSec,
                    lastMessage = "Переход на ${formatSeconds(targetSec)}"
                )
            }
        }
    }

    fun seekRelativeSeconds(deltaSec: Long) {
        val now = System.currentTimeMillis()
        if (now - lastSeekTimestamp < 350L) {
            return // Защита от дребезга и повторных нажатий
        }
        lastSeekTimestamp = now

        vibrate(30)
        cancelYouTubeAutoHide()

        seekJob?.cancel()
        seekJob = viewModelScope.launch {
            StationMediaManager.ensureDeployed(getApplication(), adbClient)
            val info = StationMediaManager.seekRelative(adbClient, activePositionSec, deltaSec)
            if (info != null) {
                activePositionSec = info.positionSec
                _uiState.value = _uiState.value.copy(
                    activePlaybackSec = info.positionSec,
                    lastMessage = "${if (deltaSec > 0) "+" else ""}${deltaSec / 60} мин (${formatSeconds(info.positionSec)})"
                )
            } else {
                activePositionSec = (activePositionSec + deltaSec).coerceAtLeast(0L)
                _uiState.value = _uiState.value.copy(activePlaybackSec = activePositionSec)
                val action = if (deltaSec >= 0) "fast-forward" else "rewind"
                adbClient.execute("media dispatch $action")
            }
        }
    }

    fun mediaPrevious() {
        vibrate(40)
        cancelYouTubeAutoHide()
        if (_uiState.value.activeMoviePlaying != null && activeSeriesFiles.size > 1) {
            playPreviousEpisode()
        } else {
            executeCommand("media dispatch previous")
        }
    }

    fun mediaNext() {
        vibrate(40)
        cancelYouTubeAutoHide()
        if (_uiState.value.activeMoviePlaying != null && activeSeriesFiles.size > 1) {
            playNextEpisode()
        } else {
            executeCommand("media dispatch next")
        }
    }

    fun volumeMute() {
        vibrate(30)
        cancelYouTubeAutoHide()
        executeCommand("input keyevent 164")
    }

    fun doubleTapOnTvScreen(normX: Float, normY: Float) {
        vibrate(50)
        cancelYouTubeAutoHide()
        val x = (normX.coerceIn(0f, 1f) * 1920f).toInt()
        val y = (normY.coerceIn(0f, 1f) * 1080f).toInt()
        executeCommand("input tap $x $y; input tap $x $y")
    }

    fun tapOnTvScreen(normX: Float, normY: Float) {
        vibrate(35)
        cancelYouTubeAutoHide()
        val x = (normX.coerceIn(0f, 1f) * 1920f).toInt()
        val y = (normY.coerceIn(0f, 1f) * 1080f).toInt()
        executeCommand("input tap $x $y")
    }

    fun launchYouTube() {
        vibrate(50)
        stopPlaybackMonitor()
        executeCommand("am force-stop org.videolan.vlc; svc power stayon true; input keyevent 224; am start -n org.smarttube.stable/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity")
    }

    fun launchAlice() {
        vibrate(50)
        stopPlaybackMonitor()
        _uiState.value = _uiState.value.copy(
            activeMoviePlaying = null,
            isPlaying = false,
            lastMessage = "Остановлено: экран Алисы включен"
        )
        viewModelScope.launch {
            val cmd = "am force-stop org.videolan.vlc; am force-stop org.smarttube.stable; am force-stop org.schabi.newpipe; am force-stop top.rootu.lampa; input keyevent 86; input keyevent 3; am start -a android.intent.action.MAIN -c android.intent.category.HOME"
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
            cmd = "am force-stop org.videolan.vlc; svc power stayon true; input keyevent 224; am start -a android.intent.action.VIEW -d \"$extractedUrl\" -p org.smarttube.stable"
            historyItem = YouTubeItem(
                id = "yt_" + System.currentTimeMillis(),
                title = initialTitle,
                urlOrQuery = extractedUrl,
                isUrl = true
            )
        } else {
            val escaped = text.replace("\"", "\\\"")
            cmd = "am force-stop org.videolan.vlc; svc power stayon true; input keyevent 224; am start -a android.intent.action.SEARCH -q \"$escaped\" -p org.smarttube.stable"
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
                    isPlaying = true,
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
            else -> "am force-stop org.videolan.vlc; svc power stayon true; input keyevent 224; am start -n org.smarttube.stable/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity"
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
            "📌 Смотреть позже" -> {
                val later = historyRepo.getWatchLater()
                _uiState.value = _uiState.value.copy(cinemaMovies = later)
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

    fun playNextEpisode() {
        vibrate(35)
        val hash = activeSeriesHash ?: return
        val movie = activeSeriesMovie ?: return
        if (activeSeriesIndex + 1 >= activeSeriesFiles.size) {
            showMessage("Это последняя серия в списке")
            return
        }
        activeSeriesIndex++
        val nextFile = activeSeriesFiles[activeSeriesIndex]
        addLog("Переключение на следующую серию: ${nextFile.name}")
        viewModelScope.launch {
            bufferAndLaunchFile(hash, nextFile, movie)
        }
    }

    fun playPreviousEpisode() {
        vibrate(35)
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

    // Читает реальное состояние VLC на ТВ: (state, positionMs) или null, если сессия не найдена
    private suspend fun readVlcSession(): Pair<Int, Long>? {
        val res = adbClient.execute("dumpsys media_session")
        if (!res.isSuccess) return null
        val out = res.getOrNull().orEmpty()
        val idx = out.indexOf("org.videolan.vlc")
        if (idx < 0) return null
        val chunk = out.substring(idx, minOf(out.length, idx + 3000))
        val m = Regex("state=PlaybackState \\{state=(\\d+), position=(\\d+)").find(chunk) ?: return null
        return m.groupValues[1].toInt() to m.groupValues[2].toLong()
    }

    private fun startPlaybackMonitor(torrentHash: String, file: TorrentVideoFile, movie: MovieItem) {
        stopPlaybackMonitor()
        playbackMonitorJob = viewModelScope.launch {
            var checks = 0
            var endHits = 0
            while (true) {
                delay(5000)
                checks++
                val session = readVlcSession()
                if (session != null && (session.first == 3 || session.first == 2 || session.first == 6)) {
                    // Реальная позиция из VLC: запоминаем точно
                    endHits = 0
                    isPlaybackPaused = session.first == 2
                    activePositionSec = session.second / 1000L
                    val liveStats = torrServerClient.getActiveTorrentStats()
                    val isRecentAction = (System.currentTimeMillis() - lastUserActionTime < 3500L)
                    _uiState.value = _uiState.value.copy(
                        activePlaybackSec = activePositionSec,
                        isPlaying = if (isRecentAction) _uiState.value.isPlaying else (session.first == 3),
                        torrServerLiveStats = liveStats
                    )
                    historyRepo.savePlaybackProgress(torrentHash, file.id, file.name, activePositionSec)
                } else {
                    if (session == null && !isPlaybackPaused) {
                        activePositionSec += 5L
                        val liveStats = torrServerClient.getActiveTorrentStats()
                        _uiState.value = _uiState.value.copy(
                            activePlaybackSec = activePositionSec,
                            torrServerLiveStats = liveStats
                        )
                        historyRepo.savePlaybackProgress(torrentHash, file.id, file.name, activePositionSec)
                    }
                    if (checks >= 6 && activeSeriesFiles.size > 1) {
                        val stopped = session != null && (session.first == 0 || session.first == 1)
                        var gone = false
                        if (session == null) {
                            val psRes = adbClient.execute("ps | grep -i vlc")
                            val running = psRes.getOrNull().orEmpty()
                            gone = psRes.isSuccess && !running.contains("vlc")
                        }
                        if (stopped || gone) endHits++ else endHits = 0
                        if (endHits >= 2) {
                            addLog("Плеер завершил серию ${file.name}")
                            if (activeSeriesIndex + 1 < activeSeriesFiles.size) {
                                historyRepo.savePlaybackProgress(torrentHash, file.id, file.name, 0L)
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
        // Запускаем VLC плеер напрямую
        val adbCmd = "am force-stop org.smarttube.stable; am force-stop org.videolan.vlc; " +
            "(svc power stayon true; settings put secure screensaver_enabled 0; settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; input keyevent 224) >/dev/null 2>&1 & " +
            "am start -n org.videolan.vlc/.StartActivity -a android.intent.action.VIEW -d \"$streamUrl\" -t \"video/*\" $posExtra"
        var adbResult = adbClient.execute(adbCmd)

        if (!adbResult.isSuccess) {
            adbResult = adbClient.execute("am start -a android.intent.action.VIEW -d \"$streamUrl\" -t \"video/*\" -p org.videolan.vlc $posExtra")
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
            val hasMultipleEpisodes = activeSeriesFiles.size > 1
            val epName = if (hasMultipleEpisodes) file.name else null
            val canNext = hasMultipleEpisodes && (activeSeriesIndex + 1 < activeSeriesFiles.size)
            val canPrev = hasMultipleEpisodes && (activeSeriesIndex - 1 >= 0)

            _uiState.value = _uiState.value.copy(
                isCinemaLoading = false,
                activeMoviePlaying = updatedMovie.copy(title = file.name, magnetUrl = streamUrl),
                activePlaybackSec = savedPosSec,
                isPlaying = true,
                hasSeries = hasMultipleEpisodes,
                currentEpisodeTitle = epName,
                hasNextEpisode = canNext,
                hasPrevEpisode = canPrev,
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
            executeCommand("media dispatch play-pause")

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
                val adbCmd = "am start -n org.videolan.vlc/.StartActivity -a android.intent.action.VIEW -d \"$streamUrl\" -t \"video/*\""
                adbClient.execute(adbCmd)
            } else {
                executeCommand("media dispatch play-pause")
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

    fun stopCinemaAndReturnAlice(cleanMemory: Boolean = true) {
        vibrate(50)
        _uiState.value = _uiState.value.copy(
            activeMoviePlaying = null,
            hasSeries = false,
            currentEpisodeTitle = null,
            hasNextEpisode = false,
            hasPrevEpisode = false,
            isPlaying = false
        )
        launchAlice()
        if (cleanMemory) {
            cleanStationMemory()
        }
    }

    // Быстрая очистка кэша и оперативной памяти Станции (1 клик)
    fun cleanStationMemory() {
        vibrate(40)
        _uiState.value = _uiState.value.copy(
            isCleaningMemory = true,
            lastMessage = "🧹 Очистка памяти Станции..."
        )
        viewModelScope.launch {
            try {
                // Сброс дискового кэша ядра Linux и освобождение неиспользуемых фоновых процессов
                val cleanCmd = "su 0 sh -c 'sync; echo 3 > /proc/sys/vm/drop_caches; am kill-all'; cat /proc/meminfo"
                val res = adbClient.execute(cleanCmd)
                var freeMb = 0
                if (res.isSuccess) {
                    val output = res.getOrNull().orEmpty()
                    val memFreeLine = output.lines().firstOrNull { it.startsWith("MemFree:") }
                    if (memFreeLine != null) {
                        val kb = memFreeLine.replace("MemFree:", "").replace("kB", "").trim().toIntOrNull() ?: 0
                        freeMb = kb / 1024
                    }
                }
                val msg = if (freeMb > 0) {
                    "🧹 Память Станции очищена: свободно $freeMb МБ"
                } else {
                    "🧹 Память Станции успешно очищена!"
                }
                _uiState.value = _uiState.value.copy(
                    isCleaningMemory = false,
                    lastMessage = msg
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCleaningMemory = false,
                    lastMessage = "Ошибка очистки памяти: ${e.message}"
                )
            }
        }
    }

    fun clearLastMessage() {
        _uiState.value = _uiState.value.copy(lastMessage = null)
    }

    fun saveLastRutrackerUrl(url: String) {
        if (url.startsWith("http") && !url.contains("dl.php")) {
            prefs.lastRutrackerUrl = url
        }
    }

    fun getLastRutrackerUrl(): String {
        return prefs.lastRutrackerUrl
    }

    fun bookmarkFromBrowser(targetUrl: String, pageTitle: String) {
        vibrate(55)
        viewModelScope.launch {
            val cleanTitle = if (pageTitle.isNotBlank() && !pageTitle.contains("RuTracker", ignoreCase = true)) {
                pageTitle.substringBefore(" :: ").substringBefore(" [").trim()
            } else {
                "Раздача RuTracker"
            }
            val movie = MovieItem(
                id = "bm_rt_" + (targetUrl.hashCode().toLong() and 0x7FFFFFFF),
                title = cleanTitle,
                quality = "HD",
                size = "Торрент",
                seeds = 100,
                magnetUrl = targetUrl,
                category = "⭐ Закладки",
                description = "Добавлено из RuTracker: $pageTitle",
                isFavorite = true
            )
            historyRepo.toggleFavorite(movie)
            _uiState.value = _uiState.value.copy(
                lastMessage = "⭐ Добавлено в закладки: $cleanTitle"
            )
        }
    }

    fun addToWatchLaterFromBrowser(targetUrl: String, pageTitle: String) {
        vibrate(55)
        viewModelScope.launch {
            val cleanTitle = if (pageTitle.isNotBlank() && !pageTitle.contains("RuTracker", ignoreCase = true)) {
                pageTitle.substringBefore(" :: ").substringBefore(" [").trim()
            } else {
                "Раздача RuTracker"
            }
            val movie = MovieItem(
                id = "wl_rt_" + (targetUrl.hashCode().toLong() and 0x7FFFFFFF),
                title = cleanTitle,
                quality = "HD",
                size = "Торрент",
                seeds = 100,
                magnetUrl = targetUrl,
                category = "Смотреть позже",
                description = "Сохранено из RuTracker: $pageTitle",
                isFavorite = false
            )
            historyRepo.addToWatchLater(movie)
            _uiState.value = _uiState.value.copy(
                lastMessage = "📌 Добавлено в «Смотреть позже»: $cleanTitle"
            )
        }
    }

    fun removeFromWatchLater(movieId: String) {
        historyRepo.removeFromWatchLater(movieId)
        if (_uiState.value.selectedCinemaCategory == "📌 Смотреть позже") {
            _uiState.value = _uiState.value.copy(cinemaMovies = historyRepo.getWatchLater())
        }
    }

    fun removeMovie(movie: MovieItem) {
        vibrate(25)
        when (_uiState.value.selectedCinemaCategory) {
            "⭐ Закладки" -> {
                historyRepo.removeFromFavorites(movie.id)
                _uiState.value = _uiState.value.copy(
                    cinemaMovies = historyRepo.getFavorites(),
                    lastMessage = "Удалено из закладок: ${movie.title}"
                )
            }
            "📌 Смотреть позже" -> {
                historyRepo.removeFromWatchLater(movie.id)
                _uiState.value = _uiState.value.copy(
                    cinemaMovies = historyRepo.getWatchLater(),
                    lastMessage = "Удалено из «Смотреть позже»: ${movie.title}"
                )
            }
            else -> {
                historyRepo.removeFromHistory(movie.id)
                _uiState.value = _uiState.value.copy(
                    cinemaMovies = historyRepo.getHistory(),
                    lastMessage = "Удалено из истории: ${movie.title}"
                )
            }
        }
    }

    fun removeYouTubeHistoryItem(id: String) {
        vibrate(25)
        youtubeHistoryRepo.removeFromHistory(id)
        _uiState.value = _uiState.value.copy(
            youtubeHistory = youtubeHistoryRepo.getHistory(),
            lastMessage = "Запись удалена из списка"
        )
    }

    fun switchAudioTrack() {
        vibrate(30)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            adbClient.execute("input keyevent 222")
            _uiState.value = _uiState.value.copy(lastMessage = "Меню дорожек на ТВ")
        }
    }

    fun nextAudioTrack() {
        vibrate(35)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            adbClient.execute("input keyevent 222; sleep 0.25; input keyevent 20; sleep 0.15; input keyevent 66")
            _uiState.value = _uiState.value.copy(lastMessage = "Включена следующая дорожка")
        }
    }

    fun previousAudioTrack() {
        vibrate(35)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            adbClient.execute("input keyevent 222; sleep 0.25; input keyevent 19; sleep 0.15; input keyevent 66")
            _uiState.value = _uiState.value.copy(lastMessage = "Включена предыдущая дорожка")
        }
    }

    fun toggleSubtitles() {
        vibrate(30)
        viewModelScope.launch {
            // KEYCODE_CAPTIONS = 175, для SmartTube клавиша 'C' = 31
            adbClient.execute("input keyevent 175; input keyevent 31")
            _uiState.value = _uiState.value.copy(lastMessage = "Переключение субтитров")
        }
    }

    fun bookmarkCurrentMedia() {
        vibrate(50)
        viewModelScope.launch {
            val active = _uiState.value.activeMoviePlaying
            if (active != null) {
                historyRepo.toggleFavorite(active)
                _uiState.value = _uiState.value.copy(
                    lastMessage = "⭐ Фильм сохранен в закладки: ${active.title}"
                )
                return@launch
            }
            // Если открыт SmartTube, посылаем горячую клавишу закладки 30 (B) и фиксируем в истории
            adbClient.execute("input keyevent 30")
            bookmarkCurrentYouTubeVideo()
        }
    }

    // === Управление онлайн-витриной и раздачами ===

    fun openShowcaseFilter() {
        _uiState.value = _uiState.value.copy(isShowcaseFilterOpen = true)
    }

    fun closeShowcaseFilter() {
        _uiState.value = _uiState.value.copy(isShowcaseFilterOpen = false)
    }

    fun toggleShowcaseCategory(categoryId: String) {
        val current = _uiState.value.enabledShowcaseCategories.toMutableSet()
        if (current.contains(categoryId)) {
            current.remove(categoryId)
        } else {
            current.add(categoryId)
        }
        _uiState.value = _uiState.value.copy(enabledShowcaseCategories = current)
    }

    fun selectAllShowcaseCategories() {
        val all = RuTrackerScraper.CATEGORIES.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(enabledShowcaseCategories = all)
    }

    fun unselectAllShowcaseCategories() {
        _uiState.value = _uiState.value.copy(enabledShowcaseCategories = emptySet())
    }

    fun applyShowcaseCategoriesAndOpenBrowser() {
        prefs.enabledRuTrackerCategories = _uiState.value.enabledShowcaseCategories
        closeShowcaseFilter()
        loadMovieCatalog(forceRefreshRuTracker = false)
        setAppScreen(AppScreen.COLLECTIONS)
        showMessage("Разделы сохранены. Пройдите проверку на RuTracker")
    }

    fun clearEntireShowcase() {
        vibrate(30)
        showcaseRepo.clearAllShowcase()
        _uiState.value = _uiState.value.copy(movieSections = emptyList())
        showMessage("Витрина полностью очищена")
        addLog("Витрина: пользователь полностью очистил все подборки")
    }

    fun loadMovieCatalog(forceRefreshRuTracker: Boolean = false) {
        if (_uiState.value.isMovieCatalogLoading) return
        _uiState.value = _uiState.value.copy(isMovieCatalogLoading = true)
        viewModelScope.launch {
            val customSections = mutableListOf<MovieSection>()

            // 1. Личные находки пользователя из открытых тем
            val ruSaved = showcaseRepo.getRuTrackerMovies()
            if (ruSaved.isNotEmpty()) {
                customSections.add(MovieSection("rutracker_saved", "💎 Находки из RuTracker", ruSaved))
            }

            // 2. Распарсенные разделы RuTracker по сессии (только включенные галочками)
            val enabled = _uiState.value.enabledShowcaseCategories.ifEmpty { prefs.enabledRuTrackerCategories }
            for (cat in RuTrackerScraper.CATEGORIES) {
                if (!enabled.contains(cat.id)) continue
                val catMovies = showcaseRepo.getCategoryMovies(cat.id)
                if (catMovies.isNotEmpty()) {
                    customSections.add(MovieSection(cat.id, cat.title, catMovies))
                }
            }

            _uiState.value = _uiState.value.copy(
                movieSections = customSections,
                isMovieCatalogLoading = false
            )
            addLog("Витрина: подборки обновлены (${customSections.size} категорий)")

            // Фоновая синхронизация с RuTracker при наличии сессии
            if (hasRuTrackerAuthCookies()) {
                val now = System.currentTimeMillis()
                if (forceRefreshRuTracker || (now - prefs.rutrackerLastSyncTimestamp > 15 * 60 * 1000L)) {
                    syncRuTrackerCategoriesInBackground(force = forceRefreshRuTracker)
                }
            }
        }
    }

    fun hasRuTrackerAuthCookies(cookies: String = prefs.rutrackerCookies): Boolean {
        return cookies.contains("bb_data") || cookies.contains("bb_session") || cookies.contains("bb_ssl")
    }

    fun onRuTrackerPageVisited(url: String, cookies: String, userAgent: String) {
        if (url.isBlank()) return
        prefs.lastRutrackerUrl = url
        if (cookies.isNotBlank()) {
            prefs.rutrackerCookies = cookies
        }
        if (userAgent.isNotBlank()) {
            prefs.rutrackerUserAgent = userAgent
        }
        val wasCaptchaWaiting = _uiState.value.isRuTrackerCaptchaWaiting
        if (wasCaptchaWaiting) {
            _uiState.value = _uiState.value.copy(isRuTrackerCaptchaWaiting = false)
            addLog("RuTracker: открыта страница трекера, капча пройдена. Возобновляем сбор подборок...")
            syncRuTrackerCategoriesInBackground(force = true)
            return
        }
        val now = System.currentTimeMillis()
        if (hasRuTrackerAuthCookies(cookies) && (now - prefs.rutrackerLastSyncTimestamp > 20 * 60 * 1000L)) {
            syncRuTrackerCategoriesInBackground(force = false)
        }
    }

    fun syncRuTrackerCategoriesInBackground(force: Boolean = false) {
        val cookies = prefs.rutrackerCookies
        if (cookies.isBlank()) return
        val ua = prefs.rutrackerUserAgent
        val baseUrl = prefs.lastRutrackerUrl.ifBlank { RuTrackerScraper.DEFAULT_BASE_URL }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            addLog("RuTracker: фоновый сбор подборок (до 100 раздач на полку)...")
            var totalNew = 0
            val enabled = _uiState.value.enabledShowcaseCategories.ifEmpty { prefs.enabledRuTrackerCategories }

            try {
                for (cat in RuTrackerScraper.CATEGORIES) {
                    if (!enabled.contains(cat.id)) continue
                    try {
                        delay(1000) // Бережная пауза между запросами к разделам
                        val items = RuTrackerScraper.fetchCategoryMovies(baseUrl, cookies, ua, cat, limit = 100)
                        if (items.isNotEmpty()) {
                            showcaseRepo.saveCategoryMovies(cat.id, items)
                            totalNew += items.size
                        }
                    } catch (e: RuTrackerCaptchaException) {
                        throw e
                    } catch (e: Exception) {
                        addLog("RuTracker: ошибка сбора ${cat.title}: ${e.message}")
                    }
                }
                prefs.rutrackerLastSyncTimestamp = System.currentTimeMillis()
                _uiState.value = _uiState.value.copy(isRuTrackerCaptchaWaiting = false)

                if (totalNew > 0) {
                    addLog("RuTracker: витрина успешно обновлена ($totalNew раздач)")
                    refreshShowcaseSectionsUi(enabled)
                    // Запускаем фоновую плавную докачку остальных обложек и рейтингов с 20 по 100
                    startBackgroundShowcaseEnricher(baseUrl, cookies, ua)
                }
            } catch (e: RuTrackerCaptchaException) {
                addLog("RuTracker: требуется подтверждение/капча. Фоновый сбор приостановлен.")
                _uiState.value = _uiState.value.copy(
                    isRuTrackerCaptchaWaiting = true,
                    isMovieCatalogLoading = false
                )
            }
        }
    }

    private fun startBackgroundShowcaseEnricher(baseUrl: String, cookies: String, ua: String) {
        showcaseEnrichmentJob?.cancel()
        val cleanBase = RuTrackerScraper.getCleanBaseUrl(baseUrl)

        showcaseEnrichmentJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val enabled = _uiState.value.enabledShowcaseCategories.ifEmpty { prefs.enabledRuTrackerCategories }

            for (cat in RuTrackerScraper.CATEGORIES) {
                if (!enabled.contains(cat.id)) continue
                val movies = showcaseRepo.getCategoryMovies(cat.id).toMutableList()
                if (movies.isEmpty()) continue

                var changedInCat = false

                // Плавно обходим раздачи с 20 по 99, загружая постеры и оценки КП/IMDb
                for (i in 20 until movies.size) {
                    if (!isActive) return@launch
                    val item = movies[i]

                    if (item.posterUrl.isBlank() || item.rating == 0.0) {
                        try {
                            delay(900) // Бережная пауза, чтобы RuTracker не запрашивал капчу
                            val enriched = RuTrackerScraper.enrichTopicDetails(cleanBase, cookies, ua, item)
                            if (enriched.posterUrl != item.posterUrl || enriched.rating != item.rating || enriched.overview != item.overview) {
                                movies[i] = enriched
                                changedInCat = true
                            }
                        } catch (e: RuTrackerCaptchaException) {
                            addLog("RuTracker: фоновая догрузка прервана (требуется капча)")
                            _uiState.value = _uiState.value.copy(isRuTrackerCaptchaWaiting = true)
                            return@launch
                        } catch (_: Exception) {}
                    }

                    // Каждые 4 обновленные карточки сохраняем и мгновенно показываем пользователю
                    if (changedInCat && (i % 4 == 0 || i == movies.size - 1)) {
                        showcaseRepo.saveCategoryMovies(cat.id, movies)
                        refreshShowcaseSectionsUi(enabled)
                    }
                }

                if (changedInCat) {
                    showcaseRepo.saveCategoryMovies(cat.id, movies)
                    refreshShowcaseSectionsUi(enabled)
                }
            }
        }
    }

    private fun refreshShowcaseSectionsUi(enabled: Set<String>) {
        val updatedCustom = mutableListOf<MovieSection>()
        val ruSaved = showcaseRepo.getRuTrackerMovies()
        if (ruSaved.isNotEmpty()) {
            updatedCustom.add(MovieSection("rutracker_saved", "💎 Находки из RuTracker", ruSaved))
        }
        for (cat in RuTrackerScraper.CATEGORIES) {
            if (!enabled.contains(cat.id)) continue
            val catMovies = showcaseRepo.getCategoryMovies(cat.id)
            if (catMovies.isNotEmpty()) {
                updatedCustom.add(MovieSection(cat.id, cat.title, catMovies))
            }
        }
        _uiState.value = _uiState.value.copy(movieSections = updatedCustom)
    }

    fun playRuTrackerTopic(topicId: Long, title: String) {
        val baseUrl = prefs.lastRutrackerUrl.ifBlank { RuTrackerScraper.DEFAULT_BASE_URL }
        val cleanBase = RuTrackerScraper.getCleanBaseUrl(baseUrl)
        val dlUrl = "$cleanBase/dl.php?t=$topicId"
        val cookies = prefs.rutrackerCookies
        val ua = prefs.rutrackerUserAgent

        viewModelScope.launch {
            showMessage("Загрузка раздачи «$title» с RuTracker...")
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    var currentUrl = dlUrl
                    var currentReferer = "$cleanBase/viewtopic.php?t=$topicId"
                    var responseCode = 0
                    var bytes = ByteArray(0)

                    for (step in 0..5) {
                        val conn = (java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 10000
                            readTimeout = 15000
                            instanceFollowRedirects = false
                            if (cookies.isNotBlank()) setRequestProperty("Cookie", cookies)
                            if (currentReferer.isNotBlank()) setRequestProperty("Referer", currentReferer)
                            if (ua.isNotBlank()) setRequestProperty("User-Agent", ua)
                        }
                        responseCode = conn.responseCode
                        if (responseCode in 300..399) {
                            val location = conn.getHeaderField("Location")
                            conn.disconnect()
                            if (location.isNullOrBlank()) break
                            currentReferer = currentUrl
                            currentUrl = java.net.URL(java.net.URL(currentUrl), location).toString()
                            continue
                        }
                        bytes = conn.inputStream.use { it.readBytes() }
                        conn.disconnect()
                        break
                    }

                    if (responseCode in 200..299 && bytes.isNotEmpty() && bytes.firstOrNull() == 'd'.code.toByte()) {
                        Result.success(bytes)
                    } else {
                        Result.failure(Exception("Не удалось скачать торрент (код $responseCode)"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            result.onSuccess { bytes ->
                val cleanName = title.take(50).ifBlank { "RuTracker_$topicId" }
                uploadTorrentFileAndPlay(bytes, "$cleanName.torrent")
            }.onFailure { err ->
                showMessage("Ошибка: ${err.message}. Попробуйте открыть тему в браузере")
            }
        }
    }

    fun saveRuTrackerTopicToShowcase(
        topicId: String,
        rawTitle: String,
        posterUrl: String,
        overview: String,
        year: String,
        category: String
    ) {
        if (rawTitle.isBlank() || !RuTrackerScraper.isValidMovieTopic(rawTitle)) return
        val parsed = RuTrackerScraper.parseRuTrackerTitle(rawTitle)
        val cleanTitle = parsed.title.ifBlank { rawTitle.take(50) }
        var poster = if (posterUrl.isNotBlank()) posterUrl else MovieCatalogService.findPoster(cleanTitle, parsed.originalTitle)

        val movie = MovieCatalogItem(
            id = topicId.toLongOrNull() ?: System.currentTimeMillis(),
            title = cleanTitle,
            originalTitle = parsed.originalTitle,
            overview = if (overview.isNotBlank()) overview.trim().take(500) else parsed.overview,
            posterUrl = poster,
            releaseYear = if (year.isNotBlank()) year.trim() else parsed.year.ifBlank { "2024" },
            rating = 8.5,
            genre = category.ifBlank { "RuTracker" }
        )
        showcaseRepo.addRuTrackerMovie(movie)
        loadMovieCatalog()
        addLog("RuTracker: в витрину добавлен фильм «$cleanTitle»")

        // Если постера нет, пробуем подтянуть его в фоне через открытый кино-сервер
        if (poster.isBlank()) {
            viewModelScope.launch {
                val onlinePoster = MovieCatalogService.fetchOnlinePoster(cleanTitle, parsed.originalTitle, movie.releaseYear)
                if (onlinePoster.isNotBlank()) {
                    showcaseRepo.addRuTrackerMovie(movie.copy(posterUrl = onlinePoster))
                    loadMovieCatalog()
                }
            }
        }
    }

    fun bookmarkCatalogMovie(movie: MovieCatalogItem, asLater: Boolean = false) {
        vibrate(30)
        val item = MovieItem(
            id = "catalog_" + movie.id,
            title = movie.title,
            year = movie.releaseYear,
            quality = "HD",
            size = "",
            seeds = 10,
            category = if (asLater) "Смотреть позже" else "Закладки",
            magnetUrl = if (movie.id > 1000L) "rutracker_topic_${movie.id}" else "",
            description = movie.overview,
            posterUrl = movie.posterUrl
        )
        if (asLater) {
            historyRepo.addToWatchLater(item)
            showMessage("«${movie.title}» добавлено в «Смотреть позже»")
        } else {
            historyRepo.addToFavorites(item)
            showMessage("«${movie.title}» добавлено в закладки Кинозала")
        }
        _uiState.value = _uiState.value.copy(
            cinemaMovies = when (_uiState.value.selectedCinemaCategory) {
                "⭐ Закладки" -> historyRepo.getFavorites()
                "📌 Смотреть позже" -> historyRepo.getWatchLater()
                else -> historyRepo.getHistory()
            }
        )
    }

    fun generateRandomSelection() {
        vibrate(25)
        val randomSec = MovieCatalogService.getRandomSection()
        val existing = _uiState.value.movieSections.filter { !it.id.startsWith("random") }
        _uiState.value = _uiState.value.copy(
            movieSections = listOf(randomSec) + existing
        )
        addLog("Витрина: сгенерирована случайная подборка (${randomSec.movies.size} фильмов)")
    }

    fun selectMovieForReleases(movie: MovieCatalogItem) {
        vibrate(30)
        _uiState.value = _uiState.value.copy(
            selectedCatalogMovie = movie,
            isLoadingReleases = true,
            movieReleases = emptyList(),
            releasesError = null
        )
        viewModelScope.launch {
            val list = TorrentSearchService.searchReleases(
                movieTitle = movie.title,
                originalTitle = movie.originalTitle,
                year = movie.releaseYear
            ).toMutableList()

            // Если фильм получен из RuTracker и есть куки, добавляем прямую раздачу на первое место
            if (movie.id > 1000L && hasRuTrackerAuthCookies()) {
                val ruRelease = TorrentRelease(
                    title = "⚡ Оригинальная раздача с RuTracker [${movie.genre}]",
                    size = "RuTracker",
                    seeds = 100,
                    magnetUrl = "rutracker_topic_${movie.id}"
                )
                list.add(0, ruRelease)
            }

            _uiState.value = _uiState.value.copy(
                movieReleases = list,
                isLoadingReleases = false,
                releasesError = if (list.isEmpty()) "Раздач для «${movie.title}» не найдено" else null
            )
        }
    }

    fun dismissMovieReleases() {
        _uiState.value = _uiState.value.copy(
            selectedCatalogMovie = null,
            movieReleases = emptyList(),
            isLoadingReleases = false,
            releasesError = null
        )
    }

    fun playRelease(release: TorrentRelease, movie: MovieCatalogItem) {
        vibrate(40)
        dismissMovieReleases()
        if (release.magnetUrl.startsWith("rutracker_topic_")) {
            val topicId = release.magnetUrl.removePrefix("rutracker_topic_").toLongOrNull() ?: movie.id
            playRuTrackerTopic(topicId, movie.title)
            return
        }
        val movieItem = MovieItem(
            id = "movie_" + System.currentTimeMillis(),
            title = movie.title,
            year = movie.releaseYear,
            quality = release.size,
            size = release.size,
            seeds = release.seeds,
            category = movie.genre.ifBlank { "Кинозал" },
            magnetUrl = release.magnetUrl,
            description = release.title,
            posterUrl = movie.posterUrl
        )
        playMovieOnStation(movieItem)
    }

    fun setCatalogSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(catalogSearchQuery = query)
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(catalogSearchResults = emptyList(), isSearchingCatalog = false)
        }
    }

    fun searchCatalog(query: String) {
        vibrate(25)
        val q = query.trim()
        if (q.isBlank()) {
            _uiState.value = _uiState.value.copy(catalogSearchResults = emptyList(), isSearchingCatalog = false)
            return
        }
        _uiState.value = _uiState.value.copy(isSearchingCatalog = true)
        viewModelScope.launch {
            val results = MovieCatalogService.searchMovies(q)
            _uiState.value = _uiState.value.copy(
                catalogSearchResults = results,
                isSearchingCatalog = false
            )
        }
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
