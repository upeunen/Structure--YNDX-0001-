package ru.yandex.station.remote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

data class TorrentRelease(
    val title: String,
    val size: String,
    val seeds: Int,
    val peers: Int = 0,
    val magnetUrl: String
)

object TorrentSearchService {

    private val MIRRORS = listOf(
        "http://rutor.info",
        "https://rutor.is"
    )

    suspend fun searchReleases(movieTitle: String, originalTitle: String = "", year: String = ""): List<TorrentRelease> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TorrentRelease>()

        // 1. Поиск по русскому названию (с годом или без)
        val query1 = if (year.length == 4 && movieTitle.isNotBlank()) "$movieTitle $year" else movieTitle
        val found1 = queryMirror(query1)
        results.addAll(found1)

        // 2. Если по точному названию с годом мало результатов, пробуем без года
        if (results.size < 5 && year.isNotBlank() && movieTitle.isNotBlank()) {
            val foundWithoutYear = queryMirror(movieTitle)
            for (item in foundWithoutYear) {
                if (results.none { it.magnetUrl.substringBefore("&") == item.magnetUrl.substringBefore("&") }) {
                    results.add(item)
                }
            }
        }

        // 3. Если мало результатов и есть оригинальное название, ищем по оригинальному
        if (results.size < 3 && originalTitle.isNotBlank() && !originalTitle.equals(movieTitle, ignoreCase = true)) {
            val queryOrig = if (year.length == 4) "$originalTitle $year" else originalTitle
            val foundOrig = queryMirror(queryOrig)
            for (item in foundOrig) {
                if (results.none { it.magnetUrl.substringBefore("&") == item.magnetUrl.substringBefore("&") }) {
                    results.add(item)
                }
            }
        }

        // Сортировка: от максимального количества сидов (скорости) к меньшему
        results.sortedByDescending { it.seeds }
    }

    private fun queryMirror(query: String): List<TorrentRelease> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        for (mirror in MIRRORS) {
            try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val searchUrl = "$mirror/search/0/0/0/0/$encoded"
                val conn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 7000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }
                if (conn.responseCode in 200..299) {
                    val html = conn.inputStream.bufferedReader().use { it.readText() }
                    val parsed = parseRutorHtml(html)
                    if (parsed.isNotEmpty()) {
                        return parsed
                    }
                }
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    private fun parseRutorHtml(html: String): List<TorrentRelease> {
        val items = mutableListOf<TorrentRelease>()
        val rowPattern = Pattern.compile("<tr class=\"[gt]um\">(.*?)</tr>", Pattern.DOTALL)
        val matcher = rowPattern.matcher(html)

        val magPattern = Pattern.compile("href=\"(magnet:\\?xt=[^\"]+)\"")
        val titlePattern = Pattern.compile("<a href=\"/torrent/\\d+/[^\"]*\">([^<]+)</a>")
        val sizePattern = Pattern.compile("<td align=\"right\">([^<]+)</td>")
        val seedsPattern = Pattern.compile("<span class=\"green\">.*?(\\d+)</span>", Pattern.DOTALL)

        while (matcher.find()) {
            val row = matcher.group(1) ?: continue
            val mMag = magPattern.matcher(row)
            val mTitle = titlePattern.matcher(row)

            if (mMag.find() && mTitle.find()) {
                val rawMag = mMag.group(1) ?: ""
                val fullTitle = mTitle.group(1)
                    ?.replace("&nbsp;", " ")
                    ?.replace("&#039;", "'")
                    ?.replace("&quot;", "\"")
                    ?.trim() ?: ""

                val mSize = sizePattern.matcher(row)
                val sizeStr = if (mSize.find()) {
                    mSize.group(1)?.replace("&nbsp;", " ")?.trim() ?: ""
                } else ""

                val mSeeds = seedsPattern.matcher(row)
                val seedsCount = if (mSeeds.find()) {
                    mSeeds.group(1)?.toIntOrNull() ?: 0
                } else 0

                val enrichedMagnet = RuTrackerCollections.appendDefaultTrackers(rawMag)

                if (fullTitle.isNotBlank() && enrichedMagnet.startsWith("magnet:?")) {
                    items.add(
                        TorrentRelease(
                            title = fullTitle,
                            size = sizeStr,
                            seeds = seedsCount,
                            magnetUrl = enrichedMagnet
                        )
                    )
                }
            }
        }
        return items
    }
}
