package ru.yandex.station.remote

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
                            NavigationBarItem(
                                selected = uiState.activeAppScreen == AppScreen.REMOTE,
                                onClick = { viewModel.setAppScreen(AppScreen.REMOTE) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = "Пульт"
                                    )
                                },
                                label = { Text("Пульт") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentYandex,
                                    unselectedIconColor = TextSecondary,
                                    selectedTextColor = AccentYandex,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = DarkSurfaceElevated
                                )
                            )

                            NavigationBarItem(
                                selected = uiState.activeAppScreen == AppScreen.CINEMA,
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
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (uiState.activeAppScreen) {
                            AppScreen.REMOTE -> {
                                RemoteScreen(
                                    viewModel = viewModel,
                                    onOpenSettings = { showSettings = true }
                                )
                            }
                            AppScreen.CINEMA -> {
                                CinemaScreen(
                                    viewModel = viewModel,
                                    onOpenSettings = { showSettings = true },
                                    onBackToRemote = { viewModel.setAppScreen(AppScreen.REMOTE) }
                                )
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
                        viewModel.sendYouTubeUrl(sharedText) { success ->
                            runOnUiThread {
                                if (success) {
                                    Toast.makeText(
                                        applicationContext,
                                        "Видео отправлено на телевизор",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        applicationContext,
                                        "Не удалось отправить видео на Станцию",
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
