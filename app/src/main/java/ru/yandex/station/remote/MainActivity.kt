package ru.yandex.station.remote

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.yandex.station.remote.ui.*

class MainActivity : ComponentActivity() {

    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleShareIntent(intent)

        setContent {
            YandexRemoteTheme {
                var showSettings by remember { mutableStateOf(false) }
                val uiState by viewModel.uiState.collectAsState()

                // Показ Toast при появлении сообщений
                LaunchedEffect(uiState.lastMessage) {
                    uiState.lastMessage?.let { message ->
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        viewModel.clearLastMessage()
                    }
                }

                Scaffold(
                    containerColor = OledBlack,
                    bottomBar = {
                        NavigationBar(
                            containerColor = DarkSurface,
                            contentColor = AccentYandex
                        ) {
                            // 1. Кинозал (главный экран)
                            NavigationBarItem(
                                selected = uiState.activeAppScreen == AppScreen.CINEMA ||
                                    uiState.activeAppScreen == AppScreen.COLLECTIONS,
                                onClick = { viewModel.setAppScreen(AppScreen.CINEMA) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = "Кинозал"
                                    )
                                },
                                label = { Text("Кинозал") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentYandex,
                                    unselectedIconColor = TextSecondary,
                                    selectedTextColor = AccentYandex,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = DarkSurfaceElevated
                                )
                            )

                            // 2. Алиса (быстрый возврат к часам в 1 касание)
                            NavigationBarItem(
                                selected = false,
                                onClick = { viewModel.launchAlice() },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = "Алиса",
                                        tint = AccentYandex
                                    )
                                },
                                label = { Text("Алиса", color = AccentYandex) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentYandex,
                                    unselectedIconColor = AccentYandex,
                                    selectedTextColor = AccentYandex,
                                    unselectedTextColor = AccentYandex,
                                    indicatorColor = DarkSurfaceElevated
                                )
                            )

                            // 3. YouTube
                            NavigationBarItem(
                                selected = uiState.activeAppScreen == AppScreen.YOUTUBE,
                                onClick = { viewModel.setAppScreen(AppScreen.YOUTUBE) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = "YouTube"
                                    )
                                },
                                label = { Text("YouTube") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentRed,
                                    unselectedIconColor = TextSecondary,
                                    selectedTextColor = AccentRed,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = DarkSurfaceElevated
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (uiState.activeAppScreen) {
                            AppScreen.CINEMA -> {
                                CinemaScreen(
                                    viewModel = viewModel,
                                    onOpenSettings = { showSettings = true },
                                    onOpenCollections = { viewModel.setAppScreen(AppScreen.COLLECTIONS) }
                                )
                            }
                            AppScreen.COLLECTIONS -> {
                                CollectionsScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.setAppScreen(AppScreen.CINEMA) }
                                )
                            }
                            AppScreen.YOUTUBE -> {
                                YouTubeScreen(
                                    viewModel = viewModel,
                                    onOpenSettings = { showSettings = true },
                                    onBackToRemote = { viewModel.openRemoteModal() }
                                )
                            }
                        }

                        // Кнопка «Пульт» по центру в верхней шапке на всех экранах
                        Surface(
                            onClick = { viewModel.openRemoteModal() },
                            shape = RoundedCornerShape(20.dp),
                            color = AccentYandex,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = "Пульт",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Пульт",
                                    color = Color.Black,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        // Модальное окно Пульта поверх всех окон
                        if (uiState.isRemoteModalOpen) {
                            Dialog(
                                onDismissRequest = { viewModel.closeRemoteModal() },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.72f))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { viewModel.closeRemoteModal() }
                                        )
                                        .padding(horizontal = 12.dp, vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = OledBlack,
                                        border = BorderStroke(1.dp, DarkSurfaceElevated),
                                        tonalElevation = 16.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 760.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {}
                                            )
                                    ) {
                                        RemoteScreen(
                                            viewModel = viewModel,
                                            onOpenSettings = { showSettings = true },
                                            onDismiss = { viewModel.closeRemoteModal() }
                                        )
                                    }
                                }
                            }
                        }

                        if (showSettings) {
                            SettingsDialog(
                                currentIp = uiState.ip,
                                currentPort = uiState.port,
                                connectionStatus = uiState.connectionStatus,
                                onTestConnection = { viewModel.checkConnection() },
                                onSave = { newIp, newPort ->
                                    viewModel.saveSettings(newIp, newPort)
                                },
                                onDismiss = { showSettings = false }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type || type.startsWith("text/")) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                if (sharedText.isNotBlank()) {
                    if (sharedText.contains("magnet:?")) {
                        val magnet = if (sharedText.startsWith("magnet:?")) {
                            sharedText.trim()
                        } else {
                            val start = sharedText.indexOf("magnet:?")
                            val sub = sharedText.substring(start)
                            sub.split("\\s+".toRegex())[0].trim()
                        }
                        viewModel.playMagnetUrl(magnet)
                    } else {
                        viewModel.setAppScreen(AppScreen.YOUTUBE)
                        viewModel.launchYouTubeInput(sharedText) { success ->
                            runOnUiThread {
                                if (success) {
                                    Toast.makeText(
                                        applicationContext,
                                        "Запущено на Станции",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        applicationContext,
                                        "Не удалось отправить на Станцию",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                }
            }
        } else if (Intent.ACTION_VIEW == action) {
            val dataUri = intent.data
            if (dataUri != null) {
                val uriStr = dataUri.toString()
                if (uriStr.startsWith("magnet:?")) {
                    viewModel.playMagnetUrl(uriStr)
                } else {
                    try {
                        contentResolver.openInputStream(dataUri)?.use { inputStream ->
                            val bytes = inputStream.readBytes()
                            val fileName = dataUri.lastPathSegment ?: ("torrent_" + System.currentTimeMillis() + ".torrent")
                            viewModel.uploadTorrentFileAndPlay(bytes, fileName)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            applicationContext,
                            "Ошибка чтения торрента: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}
