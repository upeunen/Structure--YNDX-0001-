package ru.yandex.station.remote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.yandex.station.remote.data.PendingEpisodeSelection
import ru.yandex.station.remote.data.TorrentVideoFile
import java.util.Locale

@Composable
fun SeriesSelectionDialog(
    pending: PendingEpisodeSelection?,
    onSelectFile: (TorrentVideoFile) -> Unit,
    onDismiss: () -> Unit
) {
    if (pending == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, DarkSurfaceElevated),
                tonalElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Выберите серию / файл",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = pending.movie.title,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = TextSecondary)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pending.files) { file ->
                            val isLast = file.id == pending.lastFileId
                            val sizeMb = file.sizeBytes / (1024.0 * 1024.0)
                            val sizeStr = if (sizeMb >= 1024.0) {
                                String.format(Locale.US, "%.1f ГБ", sizeMb / 1024.0)
                            } else {
                                "${sizeMb.toInt()} МБ"
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isLast) AccentYandex.copy(alpha = 0.12f) else DarkSurfaceElevated,
                                border = if (isLast) BorderStroke(1.dp, AccentYandex.copy(alpha = 0.6f)) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectFile(file) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = if (isLast) AccentYandex else TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            fontSize = 13.sp,
                                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isLast) AccentYandex else TextPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = sizeStr,
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                            if (isLast) {
                                                Text(
                                                    text = "• Последняя просмотренная",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = StatusGreen
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
        }
    }
}
