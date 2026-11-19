package ru.yandex.station.remote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
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
        RuTrackerCategory("rutracker_hot_movies", "🔥 Новинки кино", 7),
        RuTrackerCategory("rutracker_uhd_4k", "📽️ Фильмы в 4K UHD", 930),
        RuTrackerCategory("rutracker_classics", "🏆 Классика мирового кино", 22),
        RuTrackerCategory("rutracker_series", "🍿 Зарубежные сериалы", 189),
        RuTrackerCategory("rutracker_ru_series", "📺 Русские сериалы", 2100),
        RuTrackerCategory("rutracker_cartoons", "🧸 Мультфильмы", 208),
        RuTrackerCategory("rutracker_doc", "📚 Документальное кино", 1460)
    )

    private val STOP_WORDS = listOf(
        "правила", "правило", "faq", "инструкц", "навигатор", "объявлен", "внимани",
        "обсужден", "опрос", "каталог", "модератор", "оформлен", "флудилк", "курилк",
        "заявк", "поиск фильм", "ищу фильм", "шаблон", "архив", "помощь", "как скачивать",
        "благодарност", "вопрос-ответ", "релиз-групп", "информация", "рейтинг трекера",
        "статистика", "жалобы", "техподдержка", "список раздач", "поздравлен"
    )

    private val VIDEO_MARKERS = listOf(
        "bdrip", "webrip", "web-dl", "hdtv", "1080p", "720p", "2160p", "4k", "dvd",
        "hdr", "remux", "сезон", "серии", "серия", "s01", "s02", "episodes",
        "dub", "mvo", "avo", "sub", "дубляж", "многоголосый", "перевод", "avc", "hevc"
    )

    fun isValidMovieTopic(rawTitle: String): Boolean {
        val lower = rawTitle.lowercase()
        // 1. Отсекаем стоп-слова
        if (STOP_WORDS.any { lower.contains(it) }) return false

        // 2. Наличие года (19xx или 20xx) - обязательный признак фильма на RuTracker
        val hasYear = Regex("""\b(19\d\d|20\d\d)\b""").find(rawTitle) != null
        if (!hasYear) return false

        // 3. Наличие маркера видео/сериала или слэша (русское / оригинальное название)
        val hasSlash = rawTitle.contains("/")
        val hasVideoMarker = VIDEO_MARKERS.any { lower.contains(it) }

        return hasSlash || hasVideoMarker
    }

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

        // Обогащаем раздачи реальными постерами и синопсисом прямо из страниц тем RuTracker
        val enriched = movies.mapIndexed { idx, item ->
            if (idx < 12 && item.posterUrl.isBlank()) {
                enrichTopicDetails(cleanBase, cookies, userAgent, item)
            } else {
                item
            }
        }
        enriched
    }

    private suspend fun enrichTopicDetails(
        baseUrl: String,
        cookies: String,
        userAgent: String,
        item: MovieCatalogItem
    ): MovieCatalogItem = withContext(Dispatchers.IO) {
        val topicUrl = "$baseUrl/viewtopic.php?t=${item.id}"
        try {
            val conn = (URL(topicUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 8000
                instanceFollowRedirects = true
                if (cookies.isNotBlank()) setRequestProperty("Cookie", cookies)
                setRequestProperty(
                    "User-Agent",
                    if (userAgent.isNotBlank()) userAgent
                    else "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
                setRequestProperty("Referer", "$baseUrl/index.php")
            }
            if (conn.responseCode in 200..299) {
                val contentType = conn.contentType ?: ""
                val charsetName = if (contentType.contains("utf-8", ignoreCase = true)) "UTF-8" else "windows-1251"
                val html = conn.inputStream.bufferedReader(charset(charsetName)).readText()
                conn.disconnect()

                val (poster, overview) = extractPosterAndOverview(html)
                var finalPoster = poster
                if (finalPoster.isBlank()) {
                    finalPoster = MovieCatalogService.findPoster(item.title, item.originalTitle)
                }
                if (finalPoster.isBlank()) {
                    finalPoster = MovieCatalogService.fetchOnlinePoster(item.title, item.originalTitle, item.releaseYear)
                }
                val finalOverview = if (overview.isNotBlank()) overview else item.overview

                item.copy(
                    posterUrl = finalPoster,
                    overview = finalOverview
                )
            } else {
                conn.disconnect()
                item
            }
        } catch (_: Exception) {
            item
        }
    }

    fun extractPosterAndOverview(html: String): Pair<String, String> {
        var posterUrl = ""

        // 1. Поиск постера в разметке RuTracker
        // <var class="postImg postImgAligned img-right" title="URL">
        // или <var class="postImg" title="URL">
        val varImgPattern = Pattern.compile(
            """<var[^>]+class=["'][^"']*postImg[^"']*["'][^>]+title=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val mVar = varImgPattern.matcher(html)
        if (mVar.find()) {
            posterUrl = mVar.group(1)?.trim() ?: ""
        }

        // 2. Если не нашли через var, ищем тег img с классом postImg
        if (posterUrl.isBlank()) {
            val imgPattern = Pattern.compile(
                """<img[^>]+class=["'][^"']*postImg[^"']*["'][^>]+src=["']([^"']+)["']""",
                Pattern.CASE_INSENSITIVE
            )
            val mImg = imgPattern.matcher(html)
            if (mImg.find()) {
                posterUrl = mImg.group(1)?.trim() ?: ""
            }
        }

        // 3. Запасной поиск: любой тег var или img со ссылкой на картинку
        if (posterUrl.isBlank()) {
            val anyImgPattern = Pattern.compile(
                """(?:title|src)=["'](https?://[^"']+\.(?:jpg|jpeg|png|webp)[^"']*)["']""",
                Pattern.CASE_INSENSITIVE
            )
            val mAny = anyImgPattern.matcher(html)
            if (mAny.find()) {
                posterUrl = mAny.group(1)?.trim() ?: ""
            }
        }

        // Нормализация URL постера
        if (posterUrl.startsWith("//")) {
            posterUrl = "https:$posterUrl"
        } else if (posterUrl.startsWith("http://")) {
            posterUrl = posterUrl.replaceFirst("http://", "https://")
        }

        // 4. Извлечение синопсиса сюжета
        var overview = ""
        val overviewPattern = Pattern.compile(
            """(?:Описание|О фильме|Сюжет|Синопсис)\s*:\s*(?:</b>)?\s*(.*?)(?:<br\s*/?>|<div|<span|<b|\n)""",
            Pattern.CASE_INSENSITIVE
        )
        val mOver = overviewPattern.matcher(html)
        if (mOver.find()) {
            val rawOver = mOver.group(1) ?: ""
            overview = cleanHtmlEntities(rawOver).take(450).trim()
        }

        return Pair(posterUrl, overview)
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

            // СТРОГАЯ ФИЛЬТРАЦИЯ: пропускаем только реальные фильмы и сериалы!
            if (!isValidMovieTopic(rawTitle)) continue

            val parsed = parseRuTrackerTitle(rawTitle)

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
        val yearMatch = Regex("""\b(19\d\d|20\d\d)\b""").find(raw)
        val year = yearMatch?.value ?: ""

        val directorMatch = Regex("""\(([^)]+)\)""").find(raw)
        val director = directorMatch?.groupValues?.get(1)?.trim() ?: ""

        val bracketsMatch = Regex("""\[([^]]+)\]""").find(raw)
        val bracketsContent = bracketsMatch?.groupValues?.get(1)?.trim() ?: ""

        val namePart = raw.substringBefore("(").substringBefore("[").trim()
        val parts = namePart.split("/").map { it.trim() }

        val title = parts.getOrNull(0)?.ifBlank { raw.take(40) } ?: raw.take(40)
        val origTitle = parts.getOrNull(1) ?: ""

        val genre = if (bracketsContent.isNotBlank()) {
            val tokens = bracketsContent.split(",").map { it.trim() }
            tokens.filter { !it.matches(Regex(""".*\d{4}.*""")) && !it.contains("rip", ignoreCase = true) && !it.contains("web", ignoreCase = true) }
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
