package ru.yandex.station.remote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class TorrServerClient(
    val serverHost: String,
    val serverPort: Int = 8090,
    private val timeoutMs: Int = 6000
) {
    private val baseUrl: String get() = "http://$serverHost:$serverPort"

    // Извлечение BTIH хэша напрямую из magnet-ссылки
    fun extractHashFromMagnet(magnetUrl: String): String? {
        val regex = Regex("xt=urn:btih:([a-fA-F0-9]{40}|[a-zA-Z2-7]{32})", RegexOption.IGNORE_CASE)
        val match = regex.find(magnetUrl)
        return match?.groupValues?.get(1)?.lowercase()
    }

    // Формирование ссылки на видеопоток для локального плеера Станции
    fun getStreamUrl(torrentHash: String, fileIdx: Int = 0): String {
        return "http://127.0.0.1:$serverPort/api/stream/$torrentHash/$fileIdx"
    }

    // Пробуждение торрента в движке TorrServer
    suspend fun wakeTorrent(torrentHash: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/api/torrents/$torrentHash/wake")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 3000
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    // Запуск предварительной буферизации файла
    suspend fun preloadFile(torrentHash: String, fileId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/api/torrents/$torrentHash/files/$fileId/preload")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 3000
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    // Проверка заполненности кэша (в байтах)
    suspend fun getCacheFilled(torrentHash: String): Long = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/api/torrents/$torrentHash/cache")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2000
                readTimeout = 2000
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                json.optLong("filled", 0L)
            } else {
                0L
            }
        } catch (_: Exception) {
            0L
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    // Добавление magnet-ссылки в TorrServer Silo
    suspend fun addTorrent(magnetUrl: String, title: String = ""): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/api/torrents")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val jsonBody = JSONObject().apply {
                put("link", magnetUrl)
                if (title.isNotEmpty()) {
                    put("title", title)
                }
                put("save_to_db", true)
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(StandardCharsets.UTF_8))
                os.flush()
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""

            val hashFromJson = try {
                val json = JSONObject(responseText)
                json.optString("hash").ifEmpty { json.optString("Hash") }
            } catch (_: Exception) {
                null
            }

            val finalHash = hashFromJson?.ifBlank { null } ?: extractHashFromMagnet(magnetUrl)
            if (!finalHash.isNullOrBlank()) {
                Result.success(finalHash)
            } else {
                Result.failure(Exception("Сервер вернул код $responseCode: $responseText"))
            }
        } catch (e: Exception) {
            val fallbackHash = extractHashFromMagnet(magnetUrl)
            if (!fallbackHash.isNullOrBlank()) {
                Result.success(fallbackHash)
            } else {
                Result.failure(e)
            }
        }
    }

    data class UploadedTorrentInfo(
        val hash: String,
        val name: String,
        val sizeBytes: Long
    )

    // Загрузка локального .torrent файла в TorrServer Silo
    suspend fun uploadTorrentFile(fileBytes: ByteArray, fileName: String = "movie.torrent"): Result<UploadedTorrentInfo> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            // Важно: boundary только из букв/цифр без '=', иначе строгий HTTP-парсер сбрасывает соединение
            val boundary = "TorrBoundary" + System.currentTimeMillis()
            val cleanTitle = fileName.removeSuffix(".torrent").ifBlank { "Фильм" }
            val safeFileName = "upload.torrent"
            val byteStream = ByteArrayOutputStream()

            // 1. Поле file (.torrent)
            byteStream.write(("--$boundary\r\n").toByteArray(StandardCharsets.UTF_8))
            byteStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"$safeFileName\"\r\n").toByteArray(StandardCharsets.UTF_8))
            byteStream.write(("Content-Type: application/x-bittorrent\r\n\r\n").toByteArray(StandardCharsets.UTF_8))
            byteStream.write(fileBytes)
            byteStream.write(("\r\n").toByteArray(StandardCharsets.UTF_8))

            // 2. Поле title
            byteStream.write(("--$boundary\r\n").toByteArray(StandardCharsets.UTF_8))
            byteStream.write(("Content-Disposition: form-data; name=\"title\"\r\n\r\n").toByteArray(StandardCharsets.UTF_8))
            byteStream.write((cleanTitle + "\r\n").toByteArray(StandardCharsets.UTF_8))

            // 3. Поле save_to_db
            byteStream.write(("--$boundary\r\n").toByteArray(StandardCharsets.UTF_8))
            byteStream.write(("Content-Disposition: form-data; name=\"save_to_db\"\r\n\r\n").toByteArray(StandardCharsets.UTF_8))
            byteStream.write(("true\r\n").toByteArray(StandardCharsets.UTF_8))

            byteStream.write(("--$boundary--\r\n").toByteArray(StandardCharsets.UTF_8))
            val payload = byteStream.toByteArray()

            val url = URL("$baseUrl/api/torrents")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = 15000
                doOutput = true
                setFixedLengthStreamingMode(payload.size)
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            conn.outputStream.use { os ->
                os.write(payload)
                os.flush()
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()

            val json = try {
                JSONObject(responseText)
            } catch (_: Exception) {
                null
            }
            val hash = json?.optString("hash")?.ifEmpty { json.optString("Hash") } ?: ""
            if (hash.isNotEmpty()) {
                val torrentName = json?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: json?.optString("title")?.takeIf { it.isNotBlank() }
                    ?: cleanTitle
                val sizeBytes = json?.optLong("torrent_size", 0L) ?: 0L
                Result.success(UploadedTorrentInfo(hash = hash, name = torrentName, sizeBytes = sizeBytes))
            } else {
                val apiError = json?.optString("error")?.takeIf { it.isNotBlank() }
                Result.failure(Exception(apiError ?: "Сервер вернул код $responseCode: $responseText"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    // Ожидание метаданных и получение списка медиафайлов (видео и аудио) в раздаче
    suspend fun waitForVideoFiles(torrentHash: String, maxWaitSec: Int = 20): List<TorrentVideoFile> = withContext(Dispatchers.IO) {
        val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".ts", ".mov", ".m2ts", ".webm", ".flv", ".vob", ".wmv", ".iso")
        val audioExtensions = listOf(".flac", ".mp3", ".m4a", ".aac", ".ogg", ".oga", ".wav", ".wma", ".ape", ".wv", ".opus", ".alac", ".ac3", ".dts", ".mka")
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < maxWaitSec * 1000L) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("$baseUrl/api/torrents")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }

                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val torrentsArray = json.optJSONArray("torrents")

                    if (torrentsArray != null) {
                        for (i in 0 until torrentsArray.length()) {
                            val tObj = torrentsArray.getJSONObject(i)
                            val h = tObj.optString("hash")
                            if (h.equals(torrentHash, ignoreCase = true)) {
                                val fileStats = tObj.optJSONArray("file_stats")
                                if (fileStats != null && fileStats.length() > 0) {
                                    val videoFiles = mutableListOf<TorrentVideoFile>()
                                    val audioFiles = mutableListOf<TorrentVideoFile>()
                                    val largeFallback = mutableListOf<TorrentVideoFile>()

                                    for (j in 0 until fileStats.length()) {
                                        val fObj = fileStats.getJSONObject(j)
                                        val id = fObj.optInt("id", j)
                                        val path = fObj.optString("path").ifBlank { fObj.optString("name") }
                                        val name = fObj.optString("name").ifBlank { path.substringAfterLast('/') }
                                        val length = fObj.optLong("length", 0L)

                                        val lower = "$path $name".lowercase()
                                        val isVideo = videoExtensions.any { ext -> lower.contains(ext) }
                                        val isAudio = audioExtensions.any { ext -> lower.contains(ext) }
                                        val item = TorrentVideoFile(id = id, name = name, path = path, sizeBytes = length)
                                        if (isVideo) {
                                            videoFiles.add(item)
                                        } else if (isAudio) {
                                            // Музыкальные файлы: отсекаем только пустые/поврежденные < 50 КБ
                                            if (length > 50 * 1024L) {
                                                audioFiles.add(item)
                                            }
                                        } else if (length > 30 * 1024 * 1024L) {
                                            largeFallback.add(item)
                                        }
                                    }

                                    val resultList = when {
                                        videoFiles.isNotEmpty() -> videoFiles
                                        audioFiles.isNotEmpty() -> audioFiles
                                        else -> largeFallback
                                    }.sortedBy { it.path.lowercase() }

                                    if (resultList.isNotEmpty()) {
                                        return@withContext resultList
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }

            delay(1200)
        }

        emptyList()
    }

    // Проверка доступности TorrServer на порту 8090
    suspend fun isServerRunning(): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/api/torrents")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1500
                readTimeout = 1500
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {}
        }
    }

    data class ActiveTorrentStats(
        val title: String,
        val fileName: String,
        val statString: String,
        val downloadSpeedBytes: Long,
        val activePeers: Int,
        val totalPeers: Int,
        val preloadedBytes: Long
    )

    suspend fun getActiveTorrentStats(): ActiveTorrentStats? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/api/torrents")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2000
                readTimeout = 2000
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val torrents = JSONObject(body).optJSONArray("torrents") ?: return@withContext null

            var workingObj: JSONObject? = null
            for (i in 0 until torrents.length()) {
                val t = torrents.getJSONObject(i)
                val statStr = t.optString("stat_string")
                if (statStr.equals("Torrent working", ignoreCase = true) || t.optInt("active_peers", 0) > 0) {
                    workingObj = t
                    break
                }
            }
            val obj = workingObj ?: return@withContext null
            val title = obj.optString("name").ifBlank { obj.optString("title") }
            val viewedArr = obj.optJSONArray("viewed_files")
            val activeFileIdx = if (viewedArr != null && viewedArr.length() > 0) viewedArr.optInt(0, -1) else -1
            var fileName = title
            val fileStats = obj.optJSONArray("file_stats")
            if (fileStats != null && activeFileIdx >= 0) {
                for (j in 0 until fileStats.length()) {
                    val f = fileStats.getJSONObject(j)
                    if (f.optInt("id", j) == activeFileIdx) {
                        fileName = f.optString("name").ifBlank { f.optString("path").substringAfterLast('/') }
                        break
                    }
                }
            }
            ActiveTorrentStats(
                title = title,
                fileName = fileName,
                statString = obj.optString("stat_string"),
                downloadSpeedBytes = obj.optLong("download_speed", 0L),
                activePeers = obj.optInt("active_peers", 0),
                totalPeers = obj.optInt("total_peers", 0),
                preloadedBytes = obj.optLong("preloaded_bytes", 0L)
            )
        } catch (_: Exception) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    // Ожидание реального наполнения стартового буфера перед запуском видеоплеера
    suspend fun waitForBuffer(
        torrentHash: String,
        minBytes: Long = 1536 * 1024L,
        maxWaitMs: Long = 22000L,
        onProgress: (Long) -> Unit = {}
    ): Long {
        val startTime = System.currentTimeMillis()
        var filled = 0L
        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            filled = getCacheFilled(torrentHash)
            onProgress(filled)
            if (filled >= minBytes) {
                return filled
            }
            delay(600)
        }
        return filled
    }
}
