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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinemaScreen(
    viewModel: RemoteViewModel,
    onOpenSettings: () -> Unit = {},
    onBackToRemote: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Лаунчер для выбора .torrent файла на смартфоне
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val fileName = "downloaded_" + System.currentTimeMillis() + ".torrent"
                    viewModel.uploadTorrentFileAndPlay(bytes, fileName)
                }
            } catch (e: Exception) {
                // Ошибка чтения файла
            }
        }
    }

    Scaffold(
        containerColor = OledBlack,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackToRemote) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "К пульту",
                            tint = TextPrimary
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "🎬 Кинозал (TorrServer)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
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
                                onClick = { filePickerLauncher.launch("*/*") },
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

            // Горизонтальные категории и подборки
            item {
                val categories = listOf("Новинки", "Топ фильмов", "Сериалы", "Классика", "⭐ Закладки", "🕒 История")
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
                        onToggleFavorite = { viewModel.toggleFavoriteMovie(movie) }
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
    }
}
}

@Composable
fun MovieCard(
    movie: MovieItem,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
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
