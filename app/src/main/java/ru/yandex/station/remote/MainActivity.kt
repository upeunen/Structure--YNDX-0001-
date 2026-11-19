package ru.yandex.station.remote

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
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
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.Coil
import coil.ImageLoader
import okhttp3.OkHttpClient
import ru.yandex.station.remote.ui.*

class MainActivity : ComponentActivity() {

    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Настройка загрузчика изображений с поддержкой фотохостингов RuTracker
        try {
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val urlStr = original.url.toString()
                    val builder = original.newBuilder()
                    if (urlStr.contains("fastpic", ignoreCase = true) ||
                        urlStr.contains("imageban", ignoreCase = true) ||
                        urlStr.contains("rutracker", ignoreCase = true) ||
                        urlStr.contains("lostpic", ignoreCase = true) ||
                        urlStr.contains("radikal", ignoreCase = true) ||
                        urlStr.contains("vfl.ru", ignoreCase = true)
                    ) {
                        builder.header("Referer", "https://rutracker.org/")
                        builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    }
                    chain.proceed(builder.build())
                }
                .build()

            val imageLoader = ImageLoader.Builder(this)
                .okHttpClient(okHttpClient)
                .crossfade(true)
                .build()
            Coil.setImageLoader(imageLoader)
        } catch (_: Exception) {}

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
                            // 1. Кинозал (свои фильмы, закладки, плеер)
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

                            // 2. Витрина (новинки, подборки, находки)
                            NavigationBarItem(
                                selected = uiState.activeAppScreen == AppScreen.SHOWCASE,
                                onClick = { viewModel.setAppScreen(AppScreen.SHOWCASE) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.VideoLibrary,
                                        contentDescription = "Витрина"
                                    )
                                },
                                label = { Text("Витрина") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentYandex,
                                    unselectedIconColor = TextSecondary,
                                    selectedTextColor = AccentYandex,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = DarkSurfaceElevated
                                )
                            )

                            // 3. Алиса (быстрый возврат к часам в 1 касание)
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

                            // 4. YouTube
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
                            AppScreen.SHOWCASE -> {
                                ShowcaseScreen(
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
                                debugLogs = uiState.debugLogs,
                                onTestConnection = { viewModel.checkConnection() },
                                onCleanMemory = { viewModel.cleanStationMemory() },
                                onClearLogs = { viewModel.clearLogs() },
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

    override fun onResume() {
        super.onResume()
        viewModel.resumeSession()
    }

    override fun onPause() {
        super.onPause()
        viewModel.pauseSession()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                viewModel.sendKeyEvent(24) // KEYCODE_VOLUME_UP на Станции
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.sendKeyEvent(25) // KEYCODE_VOLUME_DOWN на Станции
                true
            }
            else -> super.onKeyDown(keyCode, event)
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
