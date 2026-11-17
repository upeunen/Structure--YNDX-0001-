package ru.yandex.station.remote

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = OledBlack
                ) {
                    RemoteScreen(
                        viewModel = viewModel,
                        onOpenSettings = { showSettings = true }
                    )

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
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
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
    }
}
