package ru.yandex.station.remote.data

import java.net.URLDecoder
import java.net.URLEncoder

object RuTrackerCollections {

    private val defaultTrackers = listOf(
        "http://bt.t-ru.org/ann?magnet",
        "http://bt2.t-ru.org/ann?magnet",
        "http://bt3.t-ru.org/ann?magnet",
        "http://bt4.t-ru.org/ann?magnet",
        "http://retracker.local/announce",
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce"
    )

    fun appendDefaultTrackers(magnetUrl: String): String {
        val trimmed = magnetUrl.trim()
        if (!trimmed.startsWith("magnet:", ignoreCase = true)) return trimmed
        if (trimmed.contains("&tr=", ignoreCase = true) || trimmed.contains("?tr=", ignoreCase = true)) {
            return trimmed
        }
        val trackersSuffix = defaultTrackers.joinToString("") { tracker ->
            val encoded = try {
                URLEncoder.encode(tracker, "UTF-8")
            } catch (_: Exception) {
                tracker
            }
            "&tr=$encoded"
        }
        return trimmed + trackersSuffix
    }

    fun buildMagnetWithTrackers(hash: String, title: String): String {
        val encodedName = try {
            URLEncoder.encode(title.ifBlank { "movie" }, "UTF-8")
        } catch (_: Exception) {
            "movie"
        }
        return appendDefaultTrackers("magnet:?xt=urn:btih:${hash.lowercase()}&dn=$encodedName")
    }

    fun extractDisplayNameFromMagnet(magnet: String): String {
        val regex = Regex("dn=([^&]+)")
        val match = regex.find(magnet)
        val raw = match?.groupValues?.get(1) ?: return ""
        return try {
            URLDecoder.decode(raw, "UTF-8").replace(".", " ")
        } catch (_: Exception) {
            raw.replace(".", " ")
        }
    }
}
