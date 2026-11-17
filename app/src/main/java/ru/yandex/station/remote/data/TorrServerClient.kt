package ru.yandex.station.remote.data

import kotlinx.coroutines.Dispatchers
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

            // Извлекаем хэш торрента из JSON
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
            // Если сеть дала сбой, но у нас есть прямой хэш из magnet-ссылки, используем его
            val fallbackHash = extractHashFromMagnet(magnetUrl)
            if (!fallbackHash.isNullOrBlank()) {
                Result.success(fallbackHash)
            } else {
                Result.failure(e)
            }
        }
    }

    // Загрузка локального .torrent файла в TorrServer Silo
    suspend fun uploadTorrentFile(fileBytes: ByteArray, fileName: String = "movie.torrent"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val boundary = "====" + System.currentTimeMillis() + "===="
            val url = URL("$baseUrl/api/torrents")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            val cleanTitle = fileName.removeSuffix(".torrent")
            val byteStream = ByteArrayOutputStream()

            // 1. Поле file (.torrent)
            byteStream.write(("--$boundary\r\n").toByteArray(StandardCharsets.UTF_8))
            byteStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n").toByteArray(StandardCharsets.UTF_8))
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

            conn.outputStream.use { os ->
                os.write(byteStream.toByteArray())
                os.flush()
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""

            val json = JSONObject(responseText)
            val hash = json.optString("hash").ifEmpty { json.optString("Hash") }
            if (hash.isNotEmpty()) {
                Result.success(hash)
            } else {
                Result.failure(Exception("Сервер вернул код $responseCode: $responseText"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
