package ru.yandex.station.remote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.yandex.station.remote.data.BufferingStatus
import java.util.Locale

@Composable
fun BufferingDialog(
    status: BufferingStatus,
    onContinueBuffering: () -> Unit,
    onLaunchNow: () -> Unit,
    onCancel: () -> Unit,
    onChangeTargetMb: (Int) -> Unit
) {
    if (!status.isVisible) return

    val currentMb = status.currentBytes / (1024.0 * 1024.0)
    val targetMb = status.targetBytes / (1024.0 * 1024.0)
    val progress = (status.currentBytes.toFloat() / status.targetBytes.toFloat()).coerceIn(0f, 1f)
    val percent = (progress * 100).toInt()

    val speedText = if (status.downloadSpeedBytes >= 1024L * 1024L) {
        String.format(Locale.US, "%.1f МБ/с", status.downloadSpeedBytes / (1024.0 * 1024.0))
    } else {
        "${status.downloadSpeedBytes / 1024L} КБ/с"
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, DarkSurfaceElevated),
                tonalElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Заголовок фильма
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = status.title.ifBlank { "Подготовка фильма" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        if (status.fileName.isNotBlank() && status.fileName != status.title) {
                            Text(
                                text = status.fileName,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Крупный счетчик буфера
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF18181D),
                        border = BorderStroke(1.dp, Color(0xFF282830)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1f", currentMb),
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Black,
                                    color = AccentYandex
                                )
                                Text(
                                    text = " / ${targetMb.toInt()} МБ",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (percent >= 100) StatusGreen else AccentYandex.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    Text(
                                        text = "$percent%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (percent >= 100) Color.Black else AccentYandex,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Прогресс-бар
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = AccentYandex,
                                trackColor = Color(0xFF2C2C34)
                            )

                            // Информация: скорость и сиды
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = StatusGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = speedText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusGreen
                                    )
                                }

                                Text(
                                    text = "👥 Сиды/Пиры: ${status.activePeers} / ${status.totalPeers}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // Выбор размера буфера
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Размер буфера:",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        listOf(5, 10, 20).forEach { mbOption ->
                            val isSelected = targetMb.toInt() == mbOption
                            Surface(
                                onClick = { onChangeTargetMb(mbOption) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) AccentYandex else Color.White.copy(alpha = 0.08f),
                                modifier = Modifier.padding(horizontal = 3.dp)
                            ) {
                                Text(
                                    text = "$mbOption МБ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else TextPrimary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Блок при нехватке сидов / медленном наборе
                    if (status.isSlowWarning) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF241C0A),
                            border = BorderStroke(1.dp, Color(0xFF6B5314)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HourglassBottom,
                                        contentDescription = null,
                                        tint = AccentYandex,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Мало сидов (раздающих)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentYandex
                                    )
                                }
                                Text(
                                    text = "Набрано ${String.format(Locale.US, "%.1f", currentMb)} из ${targetMb.toInt()} МБ. Скорость низкая. Продолжить набор буфера?",
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    lineHeight = 16.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onContinueBuffering,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentYandex,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        Text("Набирать дальше", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (status.currentBytes >= 300 * 1024L) {
                                        FilledTonalButton(
                                            onClick = onLaunchNow,
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = StatusGreen.copy(alpha = 0.2f),
                                                contentColor = StatusGreen
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Запустить", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Кнопка отмены
                    OutlinedButton(
                        onClick = onCancel,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Отмена",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
