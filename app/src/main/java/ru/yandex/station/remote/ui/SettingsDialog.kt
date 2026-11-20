package ru.yandex.station.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun SettingsDialog(
    currentIp: String,
    currentPort: Int,
    connectionStatus: StationConnectionStatus,
    voiceBeepEnabled: Boolean = true,
    onToggleVoiceBeep: ((Boolean) -> Unit)? = null,
    onTestVoiceBeep: (() -> Unit)? = null,
    debugLogs: List<String> = emptyList(),
    onTestConnection: () -> Unit,
    onCleanMemory: (() -> Unit)? = null,
    onClearLogs: (() -> Unit)? = null,
    onSave: (ip: String, port: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var ipText by remember { mutableStateOf(currentIp) }
    var portText by remember { mutableStateOf(currentPort.toString()) }
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val logScrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Настройки подключения",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "Укажите IP-адрес Яндекс.Станции в вашей локальной Wi-Fi сети.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = ipText,
                    onValueChange = { ipText = it },
                    label = { Text("IP Станции", color = TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentYandex,
                        unfocusedBorderColor = DarkSurfaceElevated,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentYandex
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Порт ADB", color = TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentYandex,
                        unfocusedBorderColor = DarkSurfaceElevated,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentYandex
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusText = when (connectionStatus) {
                        StationConnectionStatus.CONNECTED -> "Станция в сети ✓"
                        StationConnectionStatus.CHECKING -> "Проверка..."
                        StationConnectionStatus.UNAVAILABLE -> "Не отвечает ✕"
                        StationConnectionStatus.IDLE -> ""
                    }
                    val statusColor = when (connectionStatus) {
                        StationConnectionStatus.CONNECTED -> StatusGreen
                        StationConnectionStatus.UNAVAILABLE -> StatusRed
                        else -> AccentYandex
                    }

                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )

                    TextButton(
                        onClick = onTestConnection,
                        colors = ButtonDefaults.textButtonColors(contentColor = AccentBlue)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Проверить")
                    }
                }

                if (onCleanMemory != null) {
                    OutlinedButton(
                        onClick = onCleanMemory,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentYandex),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentYandex.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Очистить память Станции (кэш)", fontWeight = FontWeight.Bold)
                    }
                }

                // Настройка оповещения при передаче голоса на сервер
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Сигнал отправки на сервер",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Звуковой бип из колонки при отправке голоса в облако",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    lineHeight = 14.sp
                                )
                            }
                            if (onToggleVoiceBeep != null) {
                                Switch(
                                    checked = voiceBeepEnabled,
                                    onCheckedChange = onToggleVoiceBeep,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = AccentYandex,
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = DarkSurface
                                    )
                                )
                            }
                        }

                        if (voiceBeepEnabled && onTestVoiceBeep != null) {
                            OutlinedButton(
                                onClick = onTestVoiceBeep,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Проверить звук на Станции", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Секция подробных логов сети и подключения
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Журнал связи (Логи):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (debugLogs.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("Logs", debugLogs.joinToString("\n")))
                                        Toast.makeText(context, "Логи скопированы", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Скопировать",
                                        tint = AccentYandex,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            if (onClearLogs != null && debugLogs.isNotEmpty()) {
                                IconButton(
                                    onClick = onClearLogs,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Очистить",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp, max = 150.dp)
                            .background(OledBlack, RoundedCornerShape(8.dp))
                            .border(1.dp, DarkSurfaceElevated, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .verticalScroll(logScrollState)
                    ) {
                        if (debugLogs.isEmpty()) {
                            Text(
                                text = "Журнал пока пуст.\nНажмите «Проверить» для диагностики.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(
                                text = debugLogs.joinToString("\n"),
                                fontSize = 11.sp,
                                color = TextPrimary,
                                lineHeight = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("Отмена")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 5555
                            onSave(ipText.trim(), port)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentYandex,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Сохранить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
