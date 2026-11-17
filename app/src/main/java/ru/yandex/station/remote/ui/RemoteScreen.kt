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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = OledBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Яндекс.Станция",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Переключатель режима: Кнопки / Мышь-тачпад / Клавиатура & RuTracker
            TabRow(
                selectedTabIndex = when (uiState.activeTab) {
                    RemoteTab.REMOTE -> 0
                    RemoteTab.TOUCHPAD -> 1
                    RemoteTab.KEYBOARD -> 2
                },
                containerColor = DarkSurface,
                contentColor = AccentYandex,
                indicator = {},
                divider = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Tab(
                    selected = uiState.activeTab == RemoteTab.REMOTE,
                    onClick = { viewModel.setActiveTab(RemoteTab.REMOTE) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (uiState.activeTab == RemoteTab.REMOTE) DarkSurfaceElevated else Color.Transparent
                        )
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        text = "Пульт",
                        fontWeight = if (uiState.activeTab == RemoteTab.REMOTE) FontWeight.Bold else FontWeight.Normal,
                        color = if (uiState.activeTab == RemoteTab.REMOTE) TextPrimary else TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Tab(
                    selected = uiState.activeTab == RemoteTab.TOUCHPAD,
                    onClick = { viewModel.setActiveTab(RemoteTab.TOUCHPAD) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (uiState.activeTab == RemoteTab.TOUCHPAD) DarkSurfaceElevated else Color.Transparent
                        )
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        text = "Мышь-пад",
                        fontWeight = if (uiState.activeTab == RemoteTab.TOUCHPAD) FontWeight.Bold else FontWeight.Normal,
                        color = if (uiState.activeTab == RemoteTab.TOUCHPAD) TextPrimary else TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Tab(
                    selected = uiState.activeTab == RemoteTab.KEYBOARD,
                    onClick = { viewModel.setActiveTab(RemoteTab.KEYBOARD) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (uiState.activeTab == RemoteTab.KEYBOARD) DarkSurfaceElevated else Color.Transparent
                        )
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        text = "Ввод & RuTracker",
                        fontWeight = if (uiState.activeTab == RemoteTab.KEYBOARD) FontWeight.Bold else FontWeight.Normal,
                        color = if (uiState.activeTab == RemoteTab.KEYBOARD) TextPrimary else TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            // Быстрые режимы в 1 касание
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "БЫСТРЫЕ РЕЖИМЫ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickModeButton(
                            title = "YouTube",
                            subtitle = "SmartTube",
                            icon = Icons.Default.PlayCircle,
                            accentColor = AccentRed,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.launchYouTube() }
                        )
                        QuickModeButton(
                            title = "🎬 Кино",
                            subtitle = "Kodi",
                            icon = Icons.Default.Movie,
                            accentColor = AccentBlue,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.launchKodi() }
                        )
                        QuickModeButton(
                            title = "🏠 Алиса",
                            subtitle = "Часы / Главная",
                            icon = Icons.Default.Home,
                            accentColor = AccentYandex,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.launchAlice() }
                        )
                    }
                }
            }

            // Основная зона управления по выбранной вкладке
            when (uiState.activeTab) {
                RemoteTab.REMOTE -> {
                    DPadController(
                        onUp = { viewModel.sendKeyEvent(19) },
                        onDown = { viewModel.sendKeyEvent(20) },
                        onLeft = { viewModel.sendKeyEvent(21) },
                        onRight = { viewModel.sendKeyEvent(22) },
                        onOk = { viewModel.sendKeyEvent(66) }
                    )
                }
                RemoteTab.TOUCHPAD -> {
                    TouchpadPad(
                        cursorX = uiState.cursorX,
                        cursorY = uiState.cursorY,
                        onMove = { dx, dy -> viewModel.moveCursor(dx, dy) },
                        onDoubleClick = { viewModel.clickAtCursor() },
                        onScrollUp = { viewModel.scroll(true) },
                        onScrollDown = { viewModel.scroll(false) }
                    )
                }
                RemoteTab.KEYBOARD -> {
                    RuTrackerAndKeyboardCard(
                        savedLogin = uiState.rutrackerLogin,
                        savedPass = uiState.rutrackerPass,
                        keyboardInput = uiState.keyboardInput,
                        onKeyboardInputChange = { viewModel.setKeyboardInput(it) },
                        onSendText = { viewModel.sendText(it) },
                        onSaveCredentials = { login, pass ->
                            viewModel.setRuTrackerCredentials(login, pass)
                        },
                        onAutoFillRuTracker = { login, pass ->
                            viewModel.autoFillRuTracker(login, pass)
                        },
                        onSendKeyEvent = { viewModel.sendKeyEvent(it) }
                    )
                }
            }

            // Плеер воспроизведения YouTube / ТВ (Play, Pause, Перемотка, Громкость)
            MediaPlaybackCard(
                onPlayPause = { viewModel.mediaPlayPause() },
                onRewind = { viewModel.mediaRewind() },
                onFastForward = { viewModel.mediaFastForward() },
                onPrevious = { viewModel.mediaPrevious() },
                onNext = { viewModel.mediaNext() },
                onVolumeDown = { viewModel.sendKeyEvent(25) },
                onVolumeUp = { viewModel.sendKeyEvent(24) },
                onVolumeMute = { viewModel.volumeMute() },
                onBack = { viewModel.sendKeyEvent(4) }
            )

            // Блок отправки YouTube ссылок
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ЗАПУСК YOUTUBE ВИДЕО НА ТВ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )

                    OutlinedTextField(
                        value = uiState.youtubeUrlInput,
                        onValueChange = { viewModel.setYoutubeUrlInput(it) },
                        placeholder = {
                            Text(
                                "Вставьте ссылку на YouTube видео...",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        },
                        trailingIcon = {
                            if (uiState.youtubeUrlInput.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setYoutubeUrlInput("") }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Очистить",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentYandex,
                            unfocusedBorderColor = DarkSurfaceElevated,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentYandex
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text?.toString() ?: ""
                                    viewModel.setYoutubeUrlInput(text)
                                    viewModel.vibrate(20)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Из буфера", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.sendYouTubeUrl(uiState.youtubeUrlInput)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentRed,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(
                                Icons.Default.Tv,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Смотреть на ТВ",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun RuTrackerAndKeyboardCard(
    savedLogin: String,
    savedPass: String,
    keyboardInput: String,
    onKeyboardInputChange: (String) -> Unit,
    onSendText: (String) -> Unit,
    onSaveCredentials: (String, String) -> Unit,
    onAutoFillRuTracker: (String, String) -> Unit,
    onSendKeyEvent: (Int) -> Unit
) {
    var login by remember { mutableStateOf(savedLogin) }
    var password by remember { mutableStateOf(savedPass) }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Блок RuTracker
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ВХОД RUTRACKER & KODI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )

                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text("Логин / Никнейм", color = TextSecondary) },
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

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль", color = TextSecondary) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        }
                    },
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

                // Большая кнопка авто-ввода в форму
                Button(
                    onClick = {
                        onSaveCredentials(login, password)
                        onAutoFillRuTracker(login, password)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentYandex,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Авто-ввод (Логин ➔ Tab ➔ Пароль ➔ Вход)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSendText(login) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Только логин", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { onSendKeyEvent(61) }, // TAB
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Text("Tab ⇥", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { onSendText(password) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Только пароль", fontSize = 11.sp)
                    }
                }
            }
        }

        // Блок свободной клавиатуры смартфона для ТВ
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "БЫСТРЫЙ ВВОД ТЕКСТА НА ТЕЛЕВИЗОР",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )

                OutlinedTextField(
                    value = keyboardInput,
                    onValueChange = onKeyboardInputChange,
                    placeholder = {
                        Text("Введите любой текст или поисковый запрос...", color = TextSecondary, fontSize = 13.sp)
                    },
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
                    Button(
                        onClick = {
                            onSendText(keyboardInput)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Отправить", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { onSendKeyEvent(67) }, // BACKSPACE
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Text("Стереть ⌫", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { onSendKeyEvent(66) }, // ENTER
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Text("Enter ↵", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MediaPlaybackCard(
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeMute: () -> Unit,
    onBack: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "ПЛЕЕР ВОСПРОИЗВЕДЕНИЯ YOUTUBE / ТВ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

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

            // Дополнительная строка: Назад, Громкость -, Mute, Громкость +
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
        }
    }
}

@Composable
fun TouchpadPad(
    cursorX: Float,
    cursorY: Float,
    onMove: (dx: Float, dy: Float) -> Unit,
    onDoubleClick: () -> Unit,
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Сенсорная область тачпада
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(2.dp, DarkSurfaceElevated, RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            onDoubleClick()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onMove(dragAmount.x, dragAmount.y)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val padWidth = size.width
                val padHeight = size.height

                val gridColor = Color(0xFF232328)
                drawLine(gridColor, Offset(padWidth / 2, 0f), Offset(padWidth / 2, padHeight), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, padHeight / 2), Offset(padWidth, padHeight / 2), strokeWidth = 1f)

                val pointX = (cursorX / 1920f) * padWidth
                val pointY = (cursorY / 1080f) * padHeight

                drawCircle(
                    color = AccentYandex.copy(alpha = 0.35f),
                    radius = 18f,
                    center = Offset(pointX, pointY)
                )
                drawCircle(
                    color = AccentYandex,
                    radius = 7f,
                    center = Offset(pointX, pointY)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ДВОЙНОЕ НАЖАТИЕ — КЛИК",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary.copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                )
            }
        }

        // Вспомогательные быстрые кнопки мыши
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDoubleClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentYandex,
                    contentColor = Color.Black
                ),
                modifier = Modifier.weight(1.5f)
            ) {
                Icon(Icons.Default.Mouse, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Клик мыши", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = onScrollUp,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Вверх")
                Text("Скролл", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onScrollDown,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Вниз")
                Text("Скролл", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun QuickModeButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceElevated,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = TextSecondary
            )
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
