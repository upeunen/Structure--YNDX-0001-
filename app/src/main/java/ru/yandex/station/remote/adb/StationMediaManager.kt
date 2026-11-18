package ru.yandex.station.remote.adb

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Locale

data class MediaPlaybackInfo(
    val packageName: String,
    val state: Int, // 3 = PLAYING, 2 = PAUSED, 6 = BUFFERING
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float,
    val title: String,
    val artist: String
) {
    val isPlaying: Boolean get() = state == 3
    val positionSec: Long get() = (positionMs / 1000L).coerceAtLeast(0L)
    val durationSec: Long get() = (durationMs / 1000L).coerceAtLeast(0L)
}

object StationMediaManager {
    private const val REMOTE_DEX_PATH = "/data/local/tmp/station_media.dex"
    private const val REMOTE_CLASS = "ru.yandex.station.media.StationMediaTool"
    private const val SU_PREFIX = "su 0 /system/bin/sh -c 'CLASSPATH=/data/local/tmp/station_media.dex app_process /data/local/tmp ru.yandex.station.media.StationMediaTool"

    @Volatile
    private var isDeployed = false

    suspend fun ensureDeployed(context: Context, adbClient: AdbClient): Boolean = withContext(Dispatchers.IO) {
        if (isDeployed) return@withContext true
        try {
            val checkRes = adbClient.execute("test -f $REMOTE_DEX_PATH && echo EXISTS")
            if (checkRes.getOrNull()?.contains("EXISTS") == true) {
                isDeployed = true
                return@withContext true
            }

            // Читаем из assets и загружаем
            val assetStream: InputStream = context.assets.open("station_media.dex")
            val bytes = assetStream.use { it.readBytes() }
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

            val tmpB64 = "/data/local/tmp/sm.b64"
            adbClient.execute("rm -f $tmpB64 $REMOTE_DEX_PATH")

            val chunkSize = 1500
            var i = 0
            while (i < b64.length) {
                val end = minOf(i + chunkSize, b64.length)
                val part = b64.substring(i, end)
                adbClient.execute("printf '%s' '$part' >> $tmpB64")
                i += chunkSize
            }

            val deployRes = adbClient.execute("toybox base64 -d $tmpB64 > $REMOTE_DEX_PATH && rm -f $tmpB64 && chmod 777 $REMOTE_DEX_PATH")
            isDeployed = deployRes.isSuccess
            isDeployed
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getMediaInfo(adbClient: AdbClient): MediaPlaybackInfo? = withContext(Dispatchers.IO) {
        try {
            val cmd = "$SU_PREFIX info'"
            val res = adbClient.execute(cmd)
            val output = res.getOrNull() ?: return@withContext null
            parseMediaInfo(output)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun seekTo(adbClient: AdbClient, targetMs: Long): MediaPlaybackInfo? = withContext(Dispatchers.IO) {
        try {
            val cmd = "$SU_PREFIX seek $targetMs'"
            val res = adbClient.execute(cmd)
            val output = res.getOrNull() ?: return@withContext null
            parseMediaInfo(output)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun seekRelative(adbClient: AdbClient, currentPosSec: Long, deltaSec: Long): MediaPlaybackInfo? = withContext(Dispatchers.IO) {
        try {
            // Сначала пробуем получить актуальную позицию с ТВ
            val currentInfo = getMediaInfo(adbClient)
            val baseMs = currentInfo?.positionMs ?: (currentPosSec * 1000L)
            val targetMs = (baseMs + deltaSec * 1000L).coerceAtLeast(0L)
            seekTo(adbClient, targetMs)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun play(adbClient: AdbClient): Boolean = withContext(Dispatchers.IO) {
        try {
            val res = adbClient.execute("$SU_PREFIX play'")
            res.getOrNull()?.contains("PLAY_SUCCESS") == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun pause(adbClient: AdbClient): Boolean = withContext(Dispatchers.IO) {
        try {
            val res = adbClient.execute("$SU_PREFIX pause'")
            res.getOrNull()?.contains("PAUSE_SUCCESS") == true
        } catch (e: Exception) {
            false
        }
    }

    private fun parseMediaInfo(text: String): MediaPlaybackInfo? {
        val line = text.lines().firstOrNull { it.contains("MEDIA_INFO|") } ?: return null
        val parts = line.split("|")
        var pkg = ""
        var state = 0
        var pos = 0L
        var dur = 0L
        var speed = 1.0f
        var title = ""
        var artist = ""

        for (part in parts) {
            val kv = part.split("=", limit = 2)
            if (kv.size != 2) continue
            val k = kv[0].trim()
            val v = kv[1].trim()
            when (k) {
                "pkg" -> pkg = v
                "state" -> state = v.toIntOrNull() ?: 0
                "pos" -> pos = v.toLongOrNull() ?: 0L
                "dur" -> dur = v.toLongOrNull() ?: 0L
                "speed" -> speed = v.toFloatOrNull() ?: 1.0f
                "title" -> title = v
                "artist" -> artist = v
            }
        }

        if (pkg.isEmpty() && state == 0) return null
        return MediaPlaybackInfo(
            packageName = pkg,
            state = state,
            positionMs = pos,
            durationMs = dur,
            speed = speed,
            title = title,
            artist = artist
        )
    }
}
