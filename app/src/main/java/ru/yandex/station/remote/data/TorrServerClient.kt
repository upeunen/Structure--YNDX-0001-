package ru.yandex.station.remote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class TorrServerClient(
    val serverHost: String,
    val serverPort: Int = 8090,
    private val timeoutMs: Int = 4000
) {
    private val baseUrl: String get() = "http://$serverHost:$serverPort"

    // Извлечение BTIH хэша напрямую из magnet-ссылки
    fun extractHashFromMagnet(magnetUrl: String): String? {
        val regex = Regex("xt=urn:btih:([a-fA-F0-9]{40}|[a-zA-Z2-7]{32})", RegexOption.IGNORE_CASE)
        val match = regex.find(magnetUrl)
        return match?.groupValues?.get(1)?.lowercase()
    }

    // Добавление magnet-ссылки в TorrServer
    suspend fun addTorrent(magnetUrl: String, title: String = ""): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/torrents")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val jsonBody = JSONObject().apply {
                put("action", "add")
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

            // Пытаемся распарсить hash из ответа сервера
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
            // Если запрос не прошел по сети, но это валидная magnet-ссылка, используем прямой хэш
            val fallbackHash = extractHashFromMagnet(magnetUrl)
            if (!fallbackHash.isNullOrBlank()) {
                Result.success(fallbackHash)
            } else {
                Result.failure(e)
            }
        }
    }

    // Загрузка локального .torrent файла в TorrServer
    suspend fun uploadTorrentFile(fileBytes: ByteArray, fileName: String = "movie.torrent"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val boundary = "====" + System.currentTimeMillis() + "===="
            val url = URL("$baseUrl/torrents/upload")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            val byteStream = ByteArrayOutputStream()
            byteStream.write(("--$boundary\r\n").toByteArray(StandardCharsets.UTF_8))
            byteStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n").toByteArray(StandardCharsets.UTF_8))
            byteStream.write(("Content-Type: application/x-bittorrent\r\n\r\n").toByteArray(StandardCharsets.UTF_8))
            byteStream.write(fileBytes)
            byteStream.write(("\r\n--$boundary--\r\n").toByteArray(StandardCharsets.UTF_8))

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
                Result.failure(Exception("Не удалось получить хэш торрент-файла"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
