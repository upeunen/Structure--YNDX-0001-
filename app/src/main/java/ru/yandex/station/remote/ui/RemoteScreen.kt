package ru.yandex.station.remote.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Режимы в 1 касание
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

            // Сенсорный D-Pad
            DPadController(
                onUp = { viewModel.sendKeyEvent(19) },
                onDown = { viewModel.sendKeyEvent(20) },
                onLeft = { viewModel.sendKeyEvent(21) },
                onRight = { viewModel.sendKeyEvent(22) },
                onOk = { viewModel.sendKeyEvent(66) }
            )

            // Быстрые действия: Назад, Плей/Пауза, Громкость
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(
                    icon = Icons.Default.ArrowBack,
                    label = "Назад",
                    onClick = { viewModel.sendKeyEvent(4) }
                )
                CircleActionButton(
                    icon = Icons.Default.PlayArrow,
                    label = "Пауза/Плей",
                    onClick = { viewModel.sendKeyEvent(85) }
                )
                CircleActionButton(
                    icon = Icons.Default.VolumeDown,
                    label = "Громкость −",
                    onClick = { viewModel.sendKeyEvent(25) }
                )
                CircleActionButton(
                    icon = Icons.Default.VolumeUp,
                    label = "Громкость +",
                    onClick = { viewModel.sendKeyEvent(24) }
                )
            }

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
                imageVector = Icons.Default.KeyboardArrowLeft,
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
                imageVector = Icons.Default.KeyboardArrowRight,
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
