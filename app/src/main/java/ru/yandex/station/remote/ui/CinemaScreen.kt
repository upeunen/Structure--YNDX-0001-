package ru.yandex.station.remote.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.yandex.station.remote.data.MovieItem

import android.provider.OpenableColumns

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinemaScreen(
    viewModel: RemoteViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenCollections: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var movieToDelete by remember { mutableStateOf<MovieItem?>(null) }

    if (movieToDelete != null) {
        val target = movieToDelete!!
        val listName = when (uiState.selectedCinemaCategory) {
            "⭐ Закладки" -> "закладок"
            "📌 Смотреть позже" -> "«Смотреть позже»"
            else -> "истории"
        }
        AlertDialog(
            onDismissRequest = { movieToDelete = null },
            title = {
                Text(
                    text = "Удаление",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Удалить «${target.title}» из $listName?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeMovie(target)
                        movieToDelete = null
                    }
                ) {
                    Text("Удалить", color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { movieToDelete = null }) {
                    Text("Отмена", color = TextPrimary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Лаунчер для выбора .torrent файла на смартфоне через OpenDocument (SAF)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val fileName = try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else null
                }
            } catch (_: Exception) { null } ?: ("movie_" + System.currentTimeMillis() + ".torrent")

            val bytes = try {
                val stream = context.contentResolver.openInputStream(uri)
                if (stream != null) {
                    stream.use { it.readBytes() }
                } else {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
                    }
                }
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("pending", ignoreCase = true) || msg.contains("trashed", ignoreCase = true)) {
                    viewModel.showMessage("Файл ещё не завершил скачивание в системе (pending) или в корзине. Подождите окончания загрузки или выберите файл из папки «Загрузки».")
                } else {
                    viewModel.showMessage("Ошибка чтения файла: ${e.message ?: "неизвестная ошибка"}")
                }
                return@rememberLauncherForActivityResult
            }

            if (bytes == null) {
                viewModel.showMessage("Не удалось открыть выбранный файл")
                return@rememberLauncherForActivityResult
            }
            if (bytes.isEmpty()) {
                viewModel.showMessage("Выбранный торрент-файл пуст")
                return@rememberLauncherForActivityResult
            }
            viewModel.uploadTorrentFileAndPlay(bytes, fileName)
        } catch (e: Exception) {
            viewModel.showMessage("Ошибка чтения файла: ${e.message ?: "неизвестная ошибка"}")
        }
    }

    Scaffold(
        containerColor = OledBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "🎬 Кинозал",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { viewModel.checkConnection() }
                                .padding(top = 2.dp, end = 8.dp, bottom = 2.dp)
                        ) {
                            val (dotColor, statusText) = when (uiState.connectionStatus) {
                                StationConnectionStatus.CONNECTED -> StatusGreen to "На связи (${uiState.ip})"
                                StationConnectionStatus.CHECKING -> AccentYandex to "Проверка связи..."
                                StationConnectionStatus.UNAVAILABLE -> StatusRed to "Не в сети (${uiState.ip})"
                                StationConnectionStatus.IDLE -> TextSecondary to uiState.ip
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusText,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = onOpenCollections,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AccentYandex,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Подборки",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OledBlack
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Поисковая панель торрентов
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "ПОИСК И ЗАГРУЗКА ФИЛЬМОВ (TORRSERVER)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )

                        OutlinedTextField(
                            value = uiState.cinemaSearchQuery,
                            onValueChange = { viewModel.setCinemaSearchQuery(it) },
                            placeholder = {
                                Text("Название фильма или сериала...", color = TextSecondary, fontSize = 14.sp)
                            },
                            trailingIcon = {
                                IconButton(onClick = { viewModel.searchCinema(uiState.cinemaSearchQuery) }) {
                                    Icon(Icons.Default.Search, contentDescription = "Поиск", tint = AccentYandex)
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentYandex,
                                unfocusedBorderColor = DarkSurfaceElevated,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = AccentYandex
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val text = clip.getItemAt(0).text?.toString() ?: ""
                                        if (text.startsWith("magnet:?")) {
                                            val movie = MovieItem(
                                                id = "magnet_" + System.currentTimeMillis(),
                                                title = "Фильм из Magnet ссылки",
                                                quality = "HD",
                                                size = "Онлайн",
                                                seeds = 100,
                                                magnetUrl = text,
                                                category = "Вставка",
                                                description = "Запуск по ссылке из буфера"
                                            )
                                            viewModel.playMovieOnStation(movie)
                                        } else {
                                            viewModel.setCinemaSearchQuery(text)
                                            viewModel.searchCinema(text)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Вставить Magnet", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkSurfaceElevated,
                                    contentColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(".torrent файл", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Горизонтальные категории: История, Закладки, Сборки, Смотреть позже
            item {
                val categories = listOf("🕒 История", "⭐ Закладки", "🎬 Сборки", "📌 Смотреть позже")
                val scrollState = rememberScrollState()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = uiState.selectedCinemaCategory == cat
                        Surface(
                            onClick = {
                                if (cat == "🎬 Сборки") {
                                    onOpenCollections()
                                } else {
                                    viewModel.selectCinemaCategory(cat)
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) AccentYandex else DarkSurface,
                            border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceElevated) else null,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.Black else TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Статус загрузки / запуска файла
            uiState.cinemaStatusMessage?.let { statusMsg ->
                item {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMsg,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Живая строка активного файла с TorrServer на Станции
            uiState.torrServerLiveStats?.let { stats ->
                item {
                    val speedText = if (stats.downloadSpeedBytes >= 1024L * 1024L) {
                        String.format(java.util.Locale.US, "%.1f МБ/с", stats.downloadSpeedBytes / (1024.0 * 1024.0))
                    } else {
                        "${stats.downloadSpeedBytes / 1024L} КБ/с"
                    }
                    val bufMb = String.format(java.util.Locale.US, "%.1f МБ", stats.preloadedBytes / (1024.0 * 1024.0))
                    Surface(
                        color = Color(0xFF151C16),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusGreen.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "⚡ В работе на Станции: ${stats.fileName}",
                                color = StatusGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Пиры: ${stats.activePeers}/${stats.totalPeers} • Скорость: $speedText • Буфер: $bufMb",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Панель логов работы с кнопкой копирования
            item {
                var logsExpanded by remember { mutableStateOf(true) }
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceElevated),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { logsExpanded = !logsExpanded }
                            ) {
                                Text(
                                    text = if (logsExpanded) "▼ Логи (${uiState.debugLogs.size})" else "▶ Логи (${uiState.debugLogs.size})",
                                    color = AccentYandex,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val allLogs = uiState.debugLogs.joinToString("\n").ifBlank { "Логов пока нет" }
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("StationLogs", allLogs))
                                        viewModel.showMessage("Логи скопированы в буфер")
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Копировать", fontSize = 11.sp)
                                }
                                if (uiState.debugLogs.isNotEmpty()) {
                                    TextButton(
                                        onClick = { viewModel.clearLogs() },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Очистить", fontSize = 11.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }

                        if (logsExpanded) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val logText = if (uiState.debugLogs.isEmpty()) {
                                "Пока нет событий..."
                            } else {
                                uiState.debugLogs.takeLast(30).joinToString("\n")
                            }
                            Text(
                                text = logText,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }

            // Список фильмов
            if (uiState.isCinemaLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentYandex)
                    }
                }
            } else if (uiState.cinemaMovies.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Фильмы не найдены. Попробуйте другой запрос или вставьте magnet-ссылку.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(uiState.cinemaMovies, key = { it.id + it.magnetUrl }) { movie ->
                    MovieCard(
                        movie = movie,
                        isFavorite = viewModel.isFavoriteMovie(movie.id),
                        onPlay = { viewModel.playMovieOnStation(movie) },
                        onToggleFavorite = { viewModel.toggleFavoriteMovie(movie) },
                        onDelete = { movieToDelete = movie }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Всплывающий мини-пульт воспроизведения фильма
        uiState.activeMoviePlaying?.let { playingMovie ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF18181C),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentYandex),
                    tonalElevation = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "СЕЙЧАС ИГРАЕТ НА ТВ",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusGreen,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = playingMovie.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                            }

                            IconButton(
                                onClick = { viewModel.stopCinemaAndReturnAlice() }
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Остановить и закрыть",
                                    tint = TextSecondary
                                )
                            }
                        }

                        // Кнопки управления плеером фильма
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.mediaRewind() }) {
                                Icon(Icons.Default.Replay10, contentDescription = "-10с", tint = TextPrimary)
                            }

                            // Пауза / Плей
                            Surface(
                                onClick = { viewModel.mediaPlayPause() },
                                shape = CircleShape,
                                color = AccentYandex,
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Пауза/Плей", tint = Color.Black)
                                }
                            }

                            IconButton(onClick = { viewModel.mediaFastForward() }) {
                                Icon(Icons.Default.Forward30, contentDescription = "+30с", tint = TextPrimary)
                            }

                            // Кнопка принудительной подкачки буфера / нормализации
                            IconButton(
                                onClick = { viewModel.normalizeAndBuffering() },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceElevated)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Буферизация и нормализация", tint = AccentYandex)
                            }

                            IconButton(onClick = { viewModel.sendKeyEvent(25) }) {
                                Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Громкость -", tint = TextPrimary)
                            }

                            IconButton(onClick = { viewModel.sendKeyEvent(24) }) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Громкость +", tint = TextPrimary)
                            }

                            Button(
                                onClick = { viewModel.stopCinemaAndReturnAlice() },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Стоп", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Если идет буферизация — крутим индикатор прелоадера
                        if (uiState.isCinemaLoading) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceElevated)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentYandex
                                )
                                Text(
                                    text = uiState.lastMessage ?: "Буферизация и наполнение кэша...",
                                    fontSize = 11.sp,
                                    color = AccentYandex,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Диалог выбора серии / файла для многофайловых раздач
        uiState.pendingEpisodeSelection?.let { pending ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissEpisodeSelection() },
                containerColor = DarkSurface,
                title = {
                    Column {
                        Text(
                            text = "Выберите серию / файл",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = pending.movie.title,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pending.files, key = { it.id }) { videoFile ->
                            val sizeText = if (videoFile.sizeBytes >= 1024L * 1024L * 1024L) {
                                String.format(java.util.Locale.US, "%.2f ГБ", videoFile.sizeBytes / (1024.0 * 1024.0 * 1024.0))
                            } else {
                                String.format(java.util.Locale.US, "%.0f МБ", videoFile.sizeBytes / (1024.0 * 1024.0))
                            }
                            val isLastWatched = pending.lastFileId == videoFile.id
                            val subtitleText = if (isLastWatched && pending.lastPositionSec > 15L) {
                                val m = pending.lastPositionSec / 60
                                val s = pending.lastPositionSec % 60
                                "$sizeText • Продолжить (${String.format(java.util.Locale.US, "%02d:%02d", m, s)})"
                            } else if (isLastWatched) {
                                "$sizeText • Последняя"
                            } else {
                                sizeText
                            }
                            Surface(
                                onClick = { viewModel.selectEpisodeAndPlay(videoFile) },
                                shape = RoundedCornerShape(12.dp),
                                color = DarkSurfaceElevated,
                                border = if (isLastWatched) androidx.compose.foundation.BorderStroke(1.5.dp, AccentYandex) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = videoFile.name,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isLastWatched) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 2
                                        )
                                        Text(
                                            text = subtitleText,
                                            color = if (isLastWatched) AccentYandex else TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Смотреть",
                                        tint = AccentYandex
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissEpisodeSelection() }) {
                        Text("Отмена", color = TextSecondary)
                    }
                }
            )
        }

        // Диалог повторного поиска пиров, если раздача не ответила за 15 секунд
        uiState.pendingPeerRetry?.let { pending ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissPeerRetry() },
                containerColor = DarkSurface,
                title = {
                    Text(
                        text = "Раздача не отвечает",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Не удалось найти раздачу «${pending.movie.title}» (нет пиров). Ищем дальше?",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.retryPeerSearch() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentYandex,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Искать дальше", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissPeerRetry() }) {
                        Text("Отмена", color = TextSecondary)
                    }
                }
            )
        }
    }
}
}

@Composable
fun MovieCard(
    movie: MovieItem,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = movie.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (movie.year.isNotEmpty()) {
                        Text(
                            text = movie.year + if (movie.category.isNotEmpty()) " • ${movie.category}" else "",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "В закладки",
                            tint = if (isFavorite) AccentYandex else TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Удалить",
                            tint = TextSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            if (movie.description.isNotEmpty()) {
                Text(
                    text = movie.description,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 2
                )
            }

            // Бэйджи: качество, размер, сиды
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E3A24)
                    ) {
                        Text(
                            text = movie.quality,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (movie.size.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DarkSurfaceElevated
                        ) {
                            Text(
                                text = movie.size,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Сиды",
                            tint = StatusGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${movie.seeds}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusGreen
                        )
                    }
                }

                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentYandex,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("На Станцию", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
