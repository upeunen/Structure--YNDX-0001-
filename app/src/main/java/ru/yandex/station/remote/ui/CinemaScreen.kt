package ru.yandex.station.remote.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.yandex.station.remote.data.MovieItem

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

    // Диалог подтверждения удаления из истории / закладок
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
                viewModel.showMessage("Ошибка чтения файла: ${e.message ?: "неизвестная ошибка"}")
                return@rememberLauncherForActivityResult
            }

            if (bytes == null || bytes.isEmpty()) {
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
                    IconButton(
                        onClick = { viewModel.cleanStationMemory() },
                        enabled = !uiState.isCleaningMemory
                    ) {
                        if (uiState.isCleaningMemory) {
                            CircularProgressIndicator(
                                color = AccentYandex,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = "Очистить память Станции",
                                tint = TextSecondary
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = onOpenCollections,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = DarkSurfaceElevated,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = AccentYandex
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "RuTracker",
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
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // Кнопки быстрой вставки Magnet и выбора .torrent файла
                item {
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
                                        viewModel.playMagnetUrl(text)
                                    } else {
                                        viewModel.showMessage("В буфере нет magnet-ссылки")
                                    }
                                } else {
                                    viewModel.showMessage("Буфер обмена пуст")
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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

                // Переключатели: История просмотров, Закладки, Смотреть позже
                item {
                    val categories = listOf("🕒 История", "⭐ Закладки", "📌 Смотреть позже")
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
                                onClick = { viewModel.selectCinemaCategory(cat) },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) AccentYandex else DarkSurface,
                                border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceElevated) else null,
                                modifier = Modifier.height(34.dp)
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

                // Поисковая строка по своим фильмам (если в списке есть фильмы)
                if (uiState.cinemaMovies.isNotEmpty() || uiState.cinemaSearchQuery.isNotEmpty()) {
                    item {
                        OutlinedTextField(
                            value = uiState.cinemaSearchQuery,
                            onValueChange = { viewModel.setCinemaSearchQuery(it) },
                            placeholder = {
                                Text("Поиск в разделе «${uiState.selectedCinemaCategory}»...", color = TextSecondary, fontSize = 13.sp)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (uiState.cinemaSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setCinemaSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Очистить", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
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
                    }
                }

                // Фильтрованный список сохраненных фильмов
                val filteredMovies = if (uiState.cinemaSearchQuery.isBlank()) {
                    uiState.cinemaMovies
                } else {
                    uiState.cinemaMovies.filter {
                        it.title.contains(uiState.cinemaSearchQuery, ignoreCase = true) ||
                        it.description.contains(uiState.cinemaSearchQuery, ignoreCase = true)
                    }
                }

                if (filteredMovies.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = TextSecondary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (uiState.cinemaSearchQuery.isNotBlank()) "Ничего не найдено по запросу" else "Список «${uiState.selectedCinemaCategory}» пуст",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                if (uiState.cinemaSearchQuery.isBlank()) {
                                    Text(
                                        text = "Сохраняйте находки из RuTracker и Витрины",
                                        color = TextSecondary.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(filteredMovies) { movie ->
                        CinemaMovieCard(
                            movie = movie,
                            onPlay = { viewModel.playMovieOnStation(movie) },
                            onDelete = { movieToDelete = movie }
                        )
                    }
                }

                // Отступ снизу для панели плеера
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }

            // Тонкая компактная полоска плеера внизу экрана во время воспроизведения
            val activeCinemaTitle = uiState.activeMoviePlaying?.title ?: uiState.torrServerLiveStats?.title
            if (activeCinemaTitle != null) {
                Surface(
                    color = Color(0xF018181A),
                    shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentYandex.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .clickable { viewModel.openRemoteModal() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = AccentYandex,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeCinemaTitle,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.stopCinemaAndReturnAlice(cleanMemory = true) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Остановить и очистить память",
                                tint = AccentRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Карточка фильма в истории просмотров / закладках Кинозала
@Composable
fun CinemaMovieCard(
    movie: MovieItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = movie.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Удалить",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (movie.description.isNotBlank()) {
                Text(
                    text = movie.description,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (movie.size.isNotBlank()) {
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
                    if (movie.year.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DarkSurfaceElevated
                        ) {
                            Text(
                                text = movie.year,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
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
