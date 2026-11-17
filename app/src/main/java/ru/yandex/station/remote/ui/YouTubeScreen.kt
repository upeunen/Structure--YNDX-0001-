package ru.yandex.station.remote.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import ru.yandex.station.remote.data.YouTubeItem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeScreen(
    viewModel: RemoteViewModel,
    onOpenSettings: () -> Unit = {},
    onBackToRemote: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var inputText by remember { mutableStateOf(uiState.youtubeUrlInput) }

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
                            text = "▶ YouTube (SmartTube)",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Блок ввода и вставки ролика или поиска
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
                            text = "ЗАПУСК ВИДЕО И ПОИСК НА YOUTUBE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                                viewModel.setYoutubeUrlInput(it)
                            },
                            placeholder = {
                                Text(
                                    "Ссылка на ролик или поисковый запрос...",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            },
                            trailingIcon = {
                                if (inputText.isNotEmpty()) {
                                    IconButton(onClick = {
                                        inputText = ""
                                        viewModel.setYoutubeUrlInput("")
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Очистить", tint = TextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentRed,
                                unfocusedBorderColor = DarkSurfaceElevated,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = AccentRed
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
                                        inputText = text
                                        viewModel.setYoutubeUrlInput(text)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Из буфера", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        viewModel.launchYouTubeInput(inputText)
                                        inputText = ""
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentRed,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("▶ На Станцию", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. Панель управления воспроизведением SmartTube
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "УПРАВЛЕНИЕ ВОСПРОИЗВЕДЕНИЕМ (SMARTTUBE)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )

                        // Ряд основных кнопок плеера
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // -10с
                            IconButton(
                                onClick = { viewModel.mediaRewind() },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.Replay10, contentDescription = "-10 сек", tint = TextPrimary, modifier = Modifier.size(28.dp))
                            }

                            // Пауза / Возобновить
                            Surface(
                                onClick = { viewModel.mediaPlayPause() },
                                shape = CircleShape,
                                color = AccentRed,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Пауза/Плей", tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                            }

                            // +30с
                            IconButton(
                                onClick = { viewModel.mediaFastForward() },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.Forward30, contentDescription = "+30 сек", tint = TextPrimary, modifier = Modifier.size(28.dp))
                            }

                            // Следующий ролик
                            IconButton(
                                onClick = { viewModel.mediaNext() },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Следующий ролик", tint = TextPrimary, modifier = Modifier.size(28.dp))
                            }
                        }

                        // Ряд громкости и возврат к Алисе
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.sendKeyEvent(25) },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceElevated)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Громкость -", tint = TextPrimary)
                            }

                            IconButton(
                                onClick = { viewModel.sendKeyEvent(24) },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceElevated)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Громкость +", tint = TextPrimary)
                            }

                            IconButton(
                                onClick = { viewModel.volumeMute() },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceElevated)
                            ) {
                                Icon(Icons.Default.VolumeOff, contentDescription = "Без звука", tint = TextSecondary)
                            }

                            Button(
                                onClick = { viewModel.launchAlice() },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(2f)
                            ) {
                                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Стоп / Алиса", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. Заголовок истории
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ИСТОРИЯ ПРОСМОТРОВ (ПОСЛЕДНИЕ 20)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${uiState.youtubeHistory.size} шт.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            // Список последних 20 роликов
            if (uiState.youtubeHistory.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "История пуста. Запустите ролик или введите поиск выше.",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(uiState.youtubeHistory, key = { it.id + it.urlOrQuery }) { item ->
                    YouTubeHistoryCard(
                        item = item,
                        onClick = { viewModel.launchYouTubeHistoryItem(item) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun YouTubeHistoryCard(
    item: YouTubeItem,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    val dateStr = remember(item.timestamp) { dateFormat.format(Date(item.timestamp)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (item.isUrl) Color(0xFF331515) else DarkSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isUrl) Icons.Default.PlayCircle else Icons.Default.Search,
                    contentDescription = null,
                    tint = if (item.isUrl) AccentRed else AccentYandex,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.isUrl) "Видеоролик" else "Поисковый запрос",
                        fontSize = 11.sp,
                        color = if (item.isUrl) AccentRed else AccentYandex
                    )
                    Text(
                        text = "• $dateStr",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Запустить повторно",
                    tint = AccentYandex
                )
            }
        }
    }
}
