package ru.yandex.station.remote.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
            // Блок кнопок навигации D-Pad и кнопка Тап возле джойстика
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(54.dp))

                DPadController(
                    onUp = { viewModel.sendKeyEvent(19) },
                    onDown = { viewModel.sendKeyEvent(20) },
                    onLeft = { viewModel.sendKeyEvent(21) },
                    onRight = { viewModel.sendKeyEvent(22) },
                    onOk = { viewModel.sendKeyEvent(66) }
                )

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    onClick = { viewModel.tapCenter() },
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.5.dp, AccentYandex),
                    modifier = Modifier.size(width = 50.dp, height = 76.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Тап",
                            tint = AccentYandex,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Тап",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Плеер воспроизведения ТВ (Play, Pause, Плавная перемотка, Громкость, Меню, Дорожка, Субтитры)
            MediaPlaybackCard(
                playbackSec = uiState.activePlaybackSec,
                isPlaying = uiState.isPlaying,
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
    isPlaying: Boolean,
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
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Пауза" else "Воспроизведение",
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

enum class DPadSector {
    NONE, UP, DOWN, LEFT, RIGHT, OK
}

@Composable
fun DPadController(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit
) {
    var activeSector by remember { mutableStateOf(DPadSector.NONE) }

    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val centerPx = Offset(size.width / 2f, size.height / 2f)
                    val dx = down.position.x - centerPx.x
                    val dy = down.position.y - centerPx.y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    val centerRadiusPx = 40.dp.toPx()
                    val outerRadiusPx = size.width / 2f

                    val sector = if (dist <= centerRadiusPx) {
                        DPadSector.OK
                    } else if (dist <= outerRadiusPx) {
                        val angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble()))
                        when {
                            angle >= -135.0 && angle < -45.0 -> DPadSector.UP
                            angle >= -45.0 && angle < 45.0 -> DPadSector.RIGHT
                            angle >= 45.0 && angle < 135.0 -> DPadSector.DOWN
                            else -> DPadSector.LEFT
                        }
                    } else {
                        DPadSector.NONE
                    }

                    activeSector = sector
                    when (sector) {
                        DPadSector.UP -> onUp()
                        DPadSector.DOWN -> onDown()
                        DPadSector.LEFT -> onLeft()
                        DPadSector.RIGHT -> onRight()
                        DPadSector.OK -> onOk()
                        DPadSector.NONE -> {}
                    }

                    waitForUpOrCancellation()
                    activeSector = DPadSector.NONE
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Фоновая отрисовка круга, секторов и подсветки активного сектора
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f

            // Фон базы
            drawCircle(color = DarkSurface, radius = radius, center = center)

            // Подсветка нажатого сектора
            val highlightColor = AccentYandex.copy(alpha = 0.35f)
            when (activeSector) {
                DPadSector.UP -> {
                    drawArc(
                        color = highlightColor,
                        startAngle = -135f,
                        sweepAngle = 90f,
                        useCenter = true
                    )
                }
                DPadSector.RIGHT -> {
                    drawArc(
                        color = highlightColor,
                        startAngle = -45f,
                        sweepAngle = 90f,
                        useCenter = true
                    )
                }
                DPadSector.DOWN -> {
                    drawArc(
                        color = highlightColor,
                        startAngle = 45f,
                        sweepAngle = 90f,
                        useCenter = true
                    )
                }
                DPadSector.LEFT -> {
                    drawArc(
                        color = highlightColor,
                        startAngle = 135f,
                        sweepAngle = 90f,
                        useCenter = true
                    )
                }
                else -> {}
            }

            // Тонкие разделительные засечки между секторами (45°, 135°, 225°, 315°)
            val strokeColor = DarkSurfaceElevated.copy(alpha = 0.7f)
            val strokeWidth = 1.5.dp.toPx()
            val innerR = 40.dp.toPx()
            for (angleDeg in listOf(-135.0, -45.0, 45.0, 135.0)) {
                val rad = Math.toRadians(angleDeg)
                val cosA = kotlin.math.cos(rad).toFloat()
                val sinA = kotlin.math.sin(rad).toFloat()
                drawLine(
                    color = strokeColor,
                    start = Offset(center.x + innerR * cosA, center.y + innerR * sinA),
                    end = Offset(center.x + radius * cosA, center.y + radius * sinA),
                    strokeWidth = strokeWidth
                )
            }

            // Внешний контур круга
            drawCircle(
                color = DarkSurfaceElevated,
                radius = radius - 1.dp.toPx(),
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
        }

        // Иконка: Вверх
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = "Вверх",
            tint = if (activeSector == DPadSector.UP) AccentYandex else TextPrimary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
                .size(38.dp)
        )

        // Иконка: Вниз
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Вниз",
            tint = if (activeSector == DPadSector.DOWN) AccentYandex else TextPrimary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .size(38.dp)
        )

        // Иконка: Влево
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Влево",
            tint = if (activeSector == DPadSector.LEFT) AccentYandex else TextPrimary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 18.dp)
                .size(38.dp)
        )

        // Иконка: Вправо
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Вправо",
            tint = if (activeSector == DPadSector.RIGHT) AccentYandex else TextPrimary,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 18.dp)
                .size(38.dp)
        )

        // Центральная кнопка ОК
        Surface(
            shape = CircleShape,
            color = if (activeSector == DPadSector.OK) AccentYandex.copy(alpha = 0.8f) else AccentYandex,
            border = BorderStroke(2.dp, if (activeSector == DPadSector.OK) Color.White else DarkSurfaceElevated),
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
