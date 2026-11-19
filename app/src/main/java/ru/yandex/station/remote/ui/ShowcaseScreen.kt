package ru.yandex.station.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ru.yandex.station.remote.data.MovieCatalogItem
import ru.yandex.station.remote.data.MovieSection
import ru.yandex.station.remote.data.RuTrackerScraper
import ru.yandex.station.remote.data.TorrentRelease

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowcaseScreen(
    viewModel: RemoteViewModel,
    onOpenCollections: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    // Диалог подтверждения полной очистки витрины
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = {
                Text(
                    text = "Очистка витрины",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Полностью очистить витрину? Все текущие подборки и раздачи будут удалены до следующего обновления.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearEntireShowcase()
                    }
                ) {
                    Text("Очистить всё", color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Отмена", color = TextPrimary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Диалог выбора загружаемых разделов RuTracker
    if (uiState.isShowcaseFilterOpen) {
        ShowcaseFilterDialog(
            enabledCategories = uiState.enabledShowcaseCategories,
            onToggleCategory = { viewModel.toggleShowcaseCategory(it) },
            onSelectAll = { viewModel.selectAllShowcaseCategories() },
            onUnselectAll = { viewModel.unselectAllShowcaseCategories() },
            onApplyAndOpenBrowser = { viewModel.applyShowcaseCategoriesAndOpenBrowser() },
            onDismiss = { viewModel.closeShowcaseFilter() }
        )
    }

    // Просторная шторка подробностей выбранного фильма
    uiState.selectedCatalogMovie?.let { movie ->
        ShowcaseDetailsSheet(
            movie = movie,
            releases = uiState.movieReleases,
            isLoading = uiState.isLoadingReleases,
            errorMessage = uiState.releasesError,
            onDismiss = { viewModel.dismissMovieReleases() },
            onBookmark = { viewModel.bookmarkCatalogMovie(movie, asLater = false) },
            onWatchLater = { viewModel.bookmarkCatalogMovie(movie, asLater = true) },
            onSelectRelease = { release ->
                viewModel.playRelease(release, movie)
            },
            onPlayDirectTopic = {
                viewModel.playRuTrackerTopic(movie.id, movie.title)
            }
        )
    }

    Scaffold(
        containerColor = OledBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "🍿 Витрина",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Новинки, подборки и находки",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    // Кнопка очистки витрины
                    IconButton(
                        onClick = { showClearConfirm = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Очистить витрину",
                            tint = TextSecondary
                        )
                    }
                    // Кнопка настройки загружаемых полок
                    IconButton(
                        onClick = { viewModel.openShowcaseFilter() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Настройка полок",
                            tint = AccentYandex
                        )
                    }
                    // Случайная подборка
                    IconButton(
                        onClick = { viewModel.generateRandomSelection() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Случайная подборка",
                            tint = AccentYandex
                        )
                    }
                    // Обновление подборок
                    IconButton(
                        onClick = { viewModel.loadMovieCatalog(forceRefreshRuTracker = true) },
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
                    // Переход в RuTracker
                    FilledTonalButton(
                        onClick = onOpenCollections,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = DarkSurfaceElevated,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = AccentYandex
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "RuTracker",
                            fontSize = 11.sp,
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OledBlack)
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
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Баннер ожидания прохождения проверки/капчи RuTracker
                if (uiState.isRuTrackerCaptchaWaiting) {
                    item {
                        Card(
                            onClick = onOpenCollections,
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF382305)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚠️",
                                    fontSize = 24.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "RuTracker запросил проверку",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFB74D),
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Нажмите здесь, чтобы пройти капчу в браузере. После этого сбор раздач сразу продолжится.",
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = "Пройти капчу",
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Строка поиска по фильмам и сериалам
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
                            Text("Поиск фильмов, новинок, сериалов...", color = TextSecondary, fontSize = 13.sp)
                        },
                        trailingIcon = {
                            if (uiState.isSearchingCatalog) {
                                CircularProgressIndicator(
                                    color = AccentYandex,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else if (uiState.catalogSearchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.setCatalogSearchQuery("")
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Очистить", tint = TextSecondary)
                                }
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

                // Результаты поиска по каталогу
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

                // Карусели подборок фильмов и сериалов
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
                                Text("Загрузка подборок витрины...", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                } else if (uiState.movieSections.isEmpty()) {
                    // Пустое состояние (после очистки)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = TextSecondary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Витрина пуста",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Настройте разделы RuTracker или нажмите «Обновить» для загрузки свежих подборок",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { viewModel.openShowcaseFilter() },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentYandex,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Настроить полки", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.loadMovieCatalog(forceRefreshRuTracker = true) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Загрузить", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(uiState.movieSections) { section ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = section.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${section.movies.size}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
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

                // Нижний отступ для полосы плеера
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }

            // Компактная полоска плеера внизу
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

// Диалог выбора загружаемых разделов RuTracker
@Composable
fun ShowcaseFilterDialog(
    enabledCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onSelectAll: () -> Unit,
    onUnselectAll: () -> Unit,
    onApplyAndOpenBrowser: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "⚙️ Настройка полок RuTracker",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Отметьте разделы для загрузки на Витрину:",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onSelectAll) {
                        Text("Выбрать все", fontSize = 11.sp, color = AccentYandex)
                    }
                    TextButton(onClick = onUnselectAll) {
                        Text("Снять все", fontSize = 11.sp, color = TextSecondary)
                    }
                }
                HorizontalDivider(color = DarkSurfaceElevated, modifier = Modifier.padding(bottom = 6.dp))

                RuTrackerScraper.CATEGORIES.forEach { cat ->
                    val isChecked = enabledCategories.contains(cat.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleCategory(cat.id) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleCategory(cat.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentYandex,
                                checkmarkColor = Color.Black,
                                uncheckedColor = TextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = cat.title,
                            fontSize = 13.sp,
                            color = if (isChecked) TextPrimary else TextSecondary,
                            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApplyAndOpenBrowser,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentYandex,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Применить и в RuTracker", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = TextSecondary)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

// Просторная шторка с подробной информацией о фильме и раздачами
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowcaseDetailsSheet(
    movie: MovieCatalogItem,
    releases: List<TorrentRelease>,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onBookmark: () -> Unit,
    onWatchLater: () -> Unit,
    onSelectRelease: (TorrentRelease) -> Unit,
    onPlayDirectTopic: () -> Unit = {}
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
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Верхний блок: Постер + Название + Рейтинг + Жанр
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Постер крупно
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 160.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(40.dp)
                    )
                    if (movie.posterUrl.isNotBlank()) {
                        AsyncImage(
                            model = movie.posterUrl,
                            contentDescription = movie.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Информация о фильме
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = movie.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        lineHeight = 21.sp
                    )
                    if (movie.originalTitle.isNotBlank() && movie.originalTitle != movie.title) {
                        Text(
                            text = movie.originalTitle,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (movie.releaseYear.isNotBlank()) {
                            Surface(
                                color = DarkSurfaceElevated,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = movie.releaseYear,
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (movie.rating > 0.0) {
                            Surface(
                                color = Color(0xFF2A2400),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentYandex.copy(alpha = 0.5f))
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

                        if (movie.seeds > 0) {
                            Surface(
                                color = Color(0xFF1B431E),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("⚡", fontSize = 10.sp)
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${movie.seeds} сид.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF69F0AE)
                                    )
                                }
                            }
                        }
                    }

                    if (movie.genre.isNotBlank()) {
                        Text(
                            text = movie.genre,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Кнопки быстрого добавления в закладки Кинозала и Смотреть позже
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onBookmark,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = DarkSurfaceElevated,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AccentYandex
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("В закладки", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onWatchLater,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = DarkSurfaceElevated,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AccentBlue
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Смотреть позже", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Прямой запуск раздачи с RuTracker на ТВ
            if (movie.id > 100000) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onPlayDirectTopic,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentYandex,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "▶ Запустить раздачу с RuTracker на ТВ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Полный развернутый синопсис сюжета
            if (movie.overview.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "О фильме",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentYandex
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = movie.overview,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = DarkSurfaceElevated)
            Spacer(modifier = Modifier.height(12.dp))

            // Заголовок раздела раздач
            Text(
                text = "⚡ РАЗДАЧИ ДЛЯ СТАНЦИИ (VLC)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentYandex,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentYandex, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Поиск лучших раздач на трекерах...",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else if (!errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            } else if (releases.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Раздачи не найдены",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    releases.forEach { release ->
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

// Карточка фильма в каруселях витрины
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
                    .background(DarkSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(40.dp)
                )
                if (movie.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = movie.posterUrl,
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // В правом верхнем углу: число сидов ⚡ (скорость и популярность) или рейтинг ⭐
                if (movie.seeds > 0) {
                    val isHot = movie.seeds >= 30
                    Surface(
                        color = if (isHot) Color(0xEE1B431E) else Color(0xDD18181A),
                        shape = RoundedCornerShape(bottomStart = 8.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("⚡", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${movie.seeds}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isHot) Color(0xFF69F0AE) else Color(0xFFFFD54F)
                            )
                        }
                    }
                } else if (movie.rating > 0.0) {
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
                    lineHeight = 15.sp,
                    overflow = TextOverflow.Ellipsis
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
