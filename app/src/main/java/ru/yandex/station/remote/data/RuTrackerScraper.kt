package ru.yandex.station.remote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

data class RuTrackerCategory(
    val id: String,
    val title: String,
    val forumId: Int
)

object RuTrackerScraper {

    const val DEFAULT_BASE_URL = "https://rutracker.org/forum"

    val CATEGORIES = listOf(
        RuTrackerCategory("rutracker_arthouse", "🎨 Авторское и редкое кино", 124),
        RuTrackerCategory("rutracker_hot_movies", "🔥 Горячие новинки RuTracker", 7),
        RuTrackerCategory("rutracker_classics", "🏆 Классика мирового кино", 22),
        RuTrackerCategory("rutracker_series", "🍿 Зарубежные сериалы", 189)
    )

    fun getCleanBaseUrl(url: String): String {
        if (url.isBlank()) return DEFAULT_BASE_URL
        return try {
            val u = URL(url)
            val base = "${u.protocol}://${u.host}"
            if (u.path.contains("/forum")) "$base/forum" else "$base/forum"
        } catch (_: Exception) {
            DEFAULT_BASE_URL
        }
    }

    suspend fun fetchCategoryMovies(
        baseUrl: String,
        cookies: String,
        userAgent: String,
        category: RuTrackerCategory,
        limit: Int = 20
    ): List<MovieCatalogItem> = withContext(Dispatchers.IO) {
        val cleanBase = getCleanBaseUrl(baseUrl)
        // Сортировка по сидам: o=10 (seeds), s=2 (desc)
        val trackerUrl = "$cleanBase/tracker.php?f=${category.forumId}&o=10&s=2"
        val fallbackForumUrl = "$cleanBase/viewforum.php?f=${category.forumId}"

        var movies = queryHtml(trackerUrl, cleanBase, cookies, userAgent, category, limit)
        if (movies.isEmpty()) {
            movies = queryHtml(fallbackForumUrl, cleanBase, cookies, userAgent, category, limit)
        }
        movies
    }

    private fun queryHtml(
        targetUrl: String,
        baseUrl: String,
        cookies: String,
        userAgent: String,
        category: RuTrackerCategory,
        limit: Int
    ): List<MovieCatalogItem> {
        return try {
            val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 12000
                instanceFollowRedirects = true
                if (cookies.isNotBlank()) {
                    setRequestProperty("Cookie", cookies)
                }
                setRequestProperty(
                    "User-Agent",
                    if (userAgent.isNotBlank()) userAgent
                    else "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                setRequestProperty("Referer", "$baseUrl/index.php")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            }

            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return emptyList()
            }

            val contentType = conn.contentType ?: ""
            val charsetName = if (contentType.contains("utf-8", ignoreCase = true)) "UTF-8" else "windows-1251"
            val html = conn.inputStream.bufferedReader(charset(charsetName)).readText()
            conn.disconnect()

            parseMoviesFromHtml(html, category, limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseMoviesFromHtml(
        html: String,
        category: RuTrackerCategory,
        limit: Int
    ): List<MovieCatalogItem> {
        val result = mutableListOf<MovieCatalogItem>()
        // Регулярное выражение для поиска ссылок на темы раздач
        val topicPattern = Pattern.compile(
            "<a[^>]+href=[\"'](?:viewtopic\\.php\\?t=)?(\\d+)[\"'][^>]*class=[\"'][^\"']*tt-text[^\"']*[\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = topicPattern.matcher(html)

        while (matcher.find() && result.size < limit) {
            val topicId = matcher.group(1)?.toLongOrNull() ?: continue
            val rawHtmlTitle = matcher.group(2) ?: continue
            val rawTitle = cleanHtmlEntities(rawHtmlTitle)
            if (rawTitle.isBlank()) continue

            val parsed = parseRuTrackerTitle(rawTitle)

            // Проверяем, есть ли готовый постер для этого фильма в базе проверенных постеров
            val existingPoster = MovieCatalogService.findPoster(parsed.title, parsed.originalTitle)

            val item = MovieCatalogItem(
                id = topicId,
                title = parsed.title,
                originalTitle = parsed.originalTitle,
                overview = parsed.overview.ifBlank { "Раздача из раздела «${category.title}» на RuTracker" },
                posterUrl = existingPoster,
                releaseYear = parsed.year.ifBlank { "2024" },
                rating = 8.4,
                genre = parsed.genre.ifBlank { category.title.removePrefix("🎨 ").removePrefix("🔥 ").removePrefix("🏆 ").removePrefix("🍿 ") }
            )

            if (result.none { it.id == item.id || it.title.equals(item.title, ignoreCase = true) }) {
                result.add(item)
            }
        }
        return result
    }

    data class ParsedTitle(
        val title: String,
        val originalTitle: String,
        val year: String,
        val genre: String,
        val overview: String
    )

    fun parseRuTrackerTitle(raw: String): ParsedTitle {
        // Пример: "Идеальные дни / Perfect Days (Вим Вендерс) [2023, Япония, драма, BDRip 1080p]"
        val yearMatch = Regex("\\b(19\\d\\d|20\\d\\d)\\b").find(raw)
        val year = yearMatch?.value ?: ""

        val directorMatch = Regex("\\(([^)]+)\\)").find(raw)
        val director = directorMatch?.groupValues?.get(1)?.trim() ?: ""

        val bracketsMatch = Regex("\\[([^]]+)\\]").find(raw)
        val bracketsContent = bracketsMatch?.groupValues?.get(1)?.trim() ?: ""

        // Часть названия до скобок
        val namePart = raw.substringBefore("(").substringBefore("[").trim()
        val parts = namePart.split("/").map { it.trim() }

        val title = parts.getOrNull(0)?.ifBlank { raw.take(40) } ?: raw.take(40)
        val origTitle = parts.getOrNull(1) ?: ""

        val genre = if (bracketsContent.isNotBlank()) {
            val tokens = bracketsContent.split(",").map { it.trim() }
            tokens.filter { !it.matches(Regex(".*\\d{4}.*")) && !it.contains("rip", ignoreCase = true) && !it.contains("web", ignoreCase = true) }
                .take(2)
                .joinToString(", ")
        } else ""

        val overviewBuilder = StringBuilder()
        if (director.isNotBlank()) {
            overviewBuilder.append("Режиссер: ").append(director).append(". ")
        }
        if (bracketsContent.isNotBlank()) {
            overviewBuilder.append("Инфо: ").append(bracketsContent)
        }

        return ParsedTitle(
            title = title,
            originalTitle = origTitle,
            year = year,
            genre = genre,
            overview = overviewBuilder.toString().trim()
        )
    }

    private fun cleanHtmlEntities(text: String): String {
        return text
            .replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .trim()
    }
}
