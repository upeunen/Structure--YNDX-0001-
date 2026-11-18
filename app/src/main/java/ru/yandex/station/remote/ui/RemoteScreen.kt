package ru.yandex.station.remote.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OledBlack)
    ) {
        // Шапка модального пульта
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Пульт",
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Настройки",
                        tint = TextPrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть пульт",
                        tint = TextPrimary
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Блок кнопок навигации D-Pad
            DPadController(
                onUp = { viewModel.sendKeyEvent(19) },
                onDown = { viewModel.sendKeyEvent(20) },
                onLeft = { viewModel.sendKeyEvent(21) },
                onRight = { viewModel.sendKeyEvent(22) },
                onOk = { viewModel.sendKeyEvent(66) }
            )

            // Плеер воспроизведения ТВ (Play, Pause, Плавная перемотка, Громкость, Меню, Дорожка, Субтитры)
            MediaPlaybackCard(
                playbackSec = uiState.activePlaybackSec,
                onSeekRelative = { viewModel.seekRelativeSeconds(it) },
                onSeekExact = { viewModel.seekToExactTime(it) },
                onPlayPause = { viewModel.mediaPlayPause() },
                onRewind = { viewModel.mediaRewind() },
                onFastForward = { viewModel.mediaFastForward() },
                onPrevious = { viewModel.mediaPrevious() },
                onNext = { viewModel.mediaNext() },
                onVolumeDown = { viewModel.sendKeyEvent(25) },
                onVolumeUp = { viewModel.sendKeyEvent(24) },
                onVolumeMute = { viewModel.volumeMute() },
                onBack = { viewModel.sendKeyEvent(4) },
                onMenu = { viewModel.sendKeyEvent(82) },
                onAudioTrack = { viewModel.switchAudioTrack() },
                onSubtitles = { viewModel.toggleSubtitles() },
                onBookmark = { viewModel.bookmarkCurrentMedia() }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun MediaPlaybackCard(
    playbackSec: Long,
    onSeekRelative: (Long) -> Unit,
    onSeekExact: (String) -> Unit,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeMute: () -> Unit,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onAudioTrack: () -> Unit,
    onSubtitles: () -> Unit,
    onBookmark: () -> Unit
) {
    val mins = playbackSec / 60
    val secs = playbackSec % 60
    val timeLabel = String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
    var exactTimeText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ПЛЕЕР ВОСПРОИЗВЕДЕНИЯ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = timeLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentYandex
                )
            }

            // Плавные кнопки перемотки без перегрузки Станции
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    -300L to "−5 мин",
                    -60L to "−1 мин",
                    -10L to "−10 сек",
                    10L to "+10 сек",
                    60L to "+1 мин",
                    300L to "+5 мин"
                ).forEach { (delta, label) ->
                    OutlinedButton(
                        onClick = { onSeekRelative(delta) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, fontSize = 11.sp, color = TextPrimary, maxLines = 1)
                    }
                }
            }

            // Ввод времени вручную для мгновенного перехода
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = exactTimeText,
                    onValueChange = { exactTimeText = it },
                    placeholder = { Text("Время (напр. 15:30 или 45)", fontSize = 12.sp, color = TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Go),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentYandex,
                        unfocusedBorderColor = DarkSurfaceElevated,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentYandex
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        if (exactTimeText.isNotEmpty()) {
                            IconButton(onClick = { exactTimeText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Очистить", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )

                Button(
                    onClick = {
                        keyboardController?.hide()
                        if (exactTimeText.isNotBlank()) {
                            onSeekExact(exactTimeText)
                            exactTimeText = ""
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentYandex),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Перейти", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Основная строка управления плеером: Пред, Перемотка назад, Play/Pause, Перемотка вперед, След
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Предыдущее видео",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = onRewind) {
                    Icon(
                        Icons.Default.FastRewind,
                        contentDescription = "Назад 10 сек",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Большая центральная кнопка PLAY / PAUSE
                Surface(
                    onClick = onPlayPause,
                    shape = CircleShape,
                    color = AccentYandex,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Плей / Пауза",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                IconButton(onClick = onFastForward) {
                    Icon(
                        Icons.Default.FastForward,
                        contentDescription = "Вперед 10 сек",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Следующее видео",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Дополнительная строка: Меню, Назад, Громкость -, Mute, Громкость +
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(
                    icon = Icons.Default.Menu,
                    label = "Меню",
                    onClick = onMenu
                )
                CircleActionButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "Назад",
                    onClick = onBack
                )
                CircleActionButton(
                    icon = Icons.AutoMirrored.Filled.VolumeDown,
                    label = "Громкость −",
                    onClick = onVolumeDown
                )
                CircleActionButton(
                    icon = Icons.Default.VolumeOff,
                    label = "Без звука",
                    onClick = onVolumeMute
                )
                CircleActionButton(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    label = "Громкость +",
                    onClick = onVolumeUp
                )
            }

            // Быстрые кнопки: Звуковая дорожка, Субтитры и Закладка
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onAudioTrack,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔊 Дорожка", fontSize = 11.sp, color = TextPrimary, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onSubtitles,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("💬 Субтитры", fontSize = 11.sp, color = TextPrimary, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onBookmark,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("⭐ Закладка", fontSize = 11.sp, color = AccentYandex, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun DPadController(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(CircleShape)
            .background(DarkSurface)
            .border(2.dp, DarkSurfaceElevated, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Вверх
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(80.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onUp
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Вверх",
                tint = TextPrimary,
                modifier = Modifier.size(40.dp)
            )
        }

        // Вниз
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(80.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDown
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Вниз",
                tint = TextPrimary,
                modifier = Modifier.size(40.dp)
            )
        }

        // Влево
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(80.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLeft
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Влево",
                tint = TextPrimary,
                modifier = Modifier.size(40.dp)
            )
        }

        // Вправо
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(80.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRight
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Вправо",
                tint = TextPrimary,
                modifier = Modifier.size(40.dp)
            )
        }

        // Центральная кнопка ОК
        Surface(
            onClick = onOk,
            shape = CircleShape,
            color = AccentYandex,
            modifier = Modifier.size(76.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "OK",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun CircleActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceElevated),
            modifier = Modifier.size(56.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}
