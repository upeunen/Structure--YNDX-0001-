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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ru.yandex.station.remote.data.MovieCatalogItem
import ru.yandex.station.remote.data.MovieItem
import ru.yandex.station.remote.data.TorrentRelease

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

    // Шторка выбора раздачи для выбранного фильма из каталога
    uiState.selectedCatalogMovie?.let { selectedMovie ->
        ReleasesSheet(
            movie = selectedMovie,
            releases = uiState.movieReleases,
            isLoading = uiState.isLoadingReleases,
            errorMessage = uiState.releasesError,
            onDismiss = { viewModel.dismissMovieReleases() },
            onSelectRelease = { release ->
                viewModel.playRelease(release, selectedMovie)
            }
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
                        onClick = { viewModel.loadMovieCatalog() },
                        enabled = !uiState.isMovieCatalogLoading
                    ) {
                        if (uiState.isMovieCatalogLoading) {
                            CircularProgressIndicator(
                                color = AccentYandex,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Обновить подборки",
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Поисковая строка фильмов и раздач
                item {
                    OutlinedTextField(
                        value = uiState.catalogSearchQuery,
                        onValueChange = {
                            viewModel.setCatalogSearchQuery(it)
                            if (it.length >= 3) {
                                viewModel.searchCatalog(it)
                            }
                        },
                        placeholder = {
                            Text("Поиск фильмов, новинок, сериалов...", color = TextSecondary, fontSize = 14.sp)
                        },
                        trailingIcon = {
                            if (uiState.isSearchingCatalog) {
                                CircularProgressIndicator(
                                    color = AccentYandex,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                IconButton(onClick = { viewModel.searchCatalog(uiState.catalogSearchQuery) }) {
                                    Icon(Icons.Default.Search, contentDescription = "Поиск", tint = AccentYandex)
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

                // Результаты поиска по каталогу (если идет поиск)
                if (uiState.catalogSearchQuery.isNotBlank() && uiState.catalogSearchResults.isNotEmpty()) {
                    item {
                        Text(
                            text = "🔍 НАЙДЕНО В КАТАЛОГЕ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentYandex,
                            letterSpacing = 1.sp
                        )
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.catalogSearchResults) { movie ->
                                MovieCard(
                                    movie = movie,
                                    onClick = { viewModel.selectMovieForReleases(movie) }
                                )
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

                // Карусели витрины онлайн-кинотеатра
                if (uiState.isMovieCatalogLoading && uiState.movieSections.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AccentYandex, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Загрузка подборок кино...", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    items(uiState.movieSections) { section ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = section.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 4.dp)
                            ) {
                                items(section.movies) { movie ->
                                    MovieCard(
                                        movie = movie,
                                        onClick = { viewModel.selectMovieForReleases(movie) }
                                    )
                                }
                            }
                        }
                    }
                }

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
                                        viewModel.setCatalogSearchQuery(text)
                                        viewModel.searchCatalog(text)
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

                // История просмотров и Закладки
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

                // Список сохраненных фильмов
                if (uiState.cinemaMovies.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Список «${uiState.selectedCinemaCategory}» пуст",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(uiState.cinemaMovies) { movie ->
                        CinemaMovieCard(
                            movie = movie,
                            onPlay = { viewModel.playMovieOnStation(movie) },
                            onDelete = { movieToDelete = movie }
                        )
                    }
                }

                // Отступ снизу для панели плеера
                item {
                    Spacer(modifier = Modifier.height(70.dp))
                }
            }

            // Тонкая компактная полоска плеера в одну строку во время воспроизведения
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
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.stopCinemaAndReturnAlice(cleanMemory = true) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть и очистить память",
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

// Карточка фильма в витрине онлайн-кинотеатра
@Composable
fun MovieCard(
    movie: MovieCatalogItem,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(DarkSurfaceElevated)
            ) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (movie.rating > 0.0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(bottomStart = 8.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("⭐", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "%.1f".format(movie.rating),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentYandex
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = movie.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    lineHeight = 15.sp
                )
                if (movie.releaseYear.isNotBlank()) {
                    Text(
                        text = movie.releaseYear,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

// Шторка со списком раздач для выбранного фильма
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleasesSheet(
    movie: MovieCatalogItem,
    releases: List<TorrentRelease>,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSelectRelease: (TorrentRelease) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Заголовок фильма
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 54.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = movie.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (movie.releaseYear.isNotBlank() || movie.rating > 0.0) {
                        Text(
                            text = "${movie.releaseYear} • ⭐ ${movie.rating}",
                            fontSize = 12.sp,
                            color = AccentYandex
                        )
                    }
                    Text(
                        text = "Выберите раздачу для воспроизведения в VLC:",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = DarkSurfaceElevated, modifier = Modifier.padding(bottom = 8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentYandex, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Поиск лучших раздач на трекерах...",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else if (!errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(releases) { release ->
                        Card(
                            onClick = { onSelectRelease(release) },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (release.seeds > 0) Color(0xFF1E3A1E) else Color(0xFF2A2A2A),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "⚡ ${release.seeds} сид.",
                                            color = if (release.seeds > 0) Color(0xFF4CAF50) else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (release.size.isNotBlank()) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = release.size,
                                                color = TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = release.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Карточка фильма в истории просмотров / закладках
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
