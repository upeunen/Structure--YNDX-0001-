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

class RuTrackerCaptchaException(message: String = "RuTracker запросил проверку капчи или подтверждение пользователя") : Exception(message)

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

    fun isCaptchaOrBlocked(statusCode: Int, html: String): Boolean {
        if (statusCode == 403 || statusCode == 503 || statusCode == 429) {
            return true
        }
        val lower = html.lowercase()
        return lower.contains("cf-turnstile") ||
               lower.contains("cf-browser-verification") ||
               lower.contains("challenge-running") ||
               lower.contains("form-captcha") ||
               lower.contains("captcha-form") ||
               lower.contains("security check") ||
               lower.contains("ddos-guard") ||
               lower.contains("проверка безопасности") ||
               lower.contains("подтвердите, что вы человек") ||
               lower.contains("введите код") ||
               lower.contains("cap_code") ||
               lower.contains("just a moment...") ||
               lower.contains("checking your browser") ||
               lower.contains("attention required! | cloudflare") ||
               lower.contains("cloudflare ray id")
    }

    fun extractRatingFromTopic(html: String): Double {
        // 1. Поиск рейтинга Кинопоиска (приоритет)
        val kpMatch = Regex("""(?:Кинопоиск|КиноПоиск|Kinopoisk|КП)\s*[:：\-]\s*(\d+[.,]\d+)""", RegexOption.IGNORE_CASE).find(html)
        if (kpMatch != null) {
            val score = kpMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (score != null && score in 1.0..10.0) return score
        }

        // 2. Поиск рейтинга IMDb
        val imdbMatch = Regex("""(?:IMDb|IMDB)\s*[:：\-]\s*(\d+[.,]\d+)""", RegexOption.IGNORE_CASE).find(html)
        if (imdbMatch != null) {
            val score = imdbMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (score != null && score in 1.0..10.0) return score
        }

        // 3. Общий маркер оценки/рейтинга
        val generalMatch = Regex("""(?:Рейтинг|Оценка)\s*[:：\-]\s*(\d+[.,]\d+)""", RegexOption.IGNORE_CASE).find(html)
        if (generalMatch != null) {
            val score = generalMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (score != null && score in 1.0..10.0) return score
        }

        return 0.0
    }

    suspend fun fetchCategoryMovies(
        baseUrl: String,
        cookies: String,
        userAgent: String,
        category: RuTrackerCategory,
        limit: Int = 100
    ): List<MovieCatalogItem> = withContext(Dispatchers.IO) {
        val cleanBase = getCleanBaseUrl(baseUrl)
        val allMovies = mutableListOf<MovieCatalogItem>()

        // Страница 1 (start=0, до 50 раздач)
        val trackerUrlPage1 = "$cleanBase/tracker.php?f=${category.forumId}&o=10&s=2&start=0"
        val page1 = queryHtml(trackerUrlPage1, cleanBase, cookies, userAgent, category, 50)
        allMovies.addAll(page1)

        // Страница 2 (start=50, следующие 50 раздач — режим «листать-перелистать»)
        if (page1.isNotEmpty() && limit > 50) {
            try {
                kotlinx.coroutines.delay(400)
                val trackerUrlPage2 = "$cleanBase/tracker.php?f=${category.forumId}&o=10&s=2&start=50"
                val page2 = queryHtml(trackerUrlPage2, cleanBase, cookies, userAgent, category, 50)
                for (m in page2) {
                    if (allMovies.none { it.id == m.id || it.title.equals(m.title, ignoreCase = true) }) {
                        allMovies.add(m)
                    }
                }
            } catch (e: RuTrackerCaptchaException) {
                throw e
            } catch (_: Exception) {}
        }

        // Резервный сбор через viewforum.php, если трекер недоступен
        if (allMovies.isEmpty()) {
            val fallbackForumUrl = "$cleanBase/viewforum.php?f=${category.forumId}"
            val forumMovies = queryHtml(fallbackForumUrl, cleanBase, cookies, userAgent, category, limit)
            allMovies.addAll(forumMovies)
        }

        // Обогащаем первые 20 раздач полными деталями (постеры, синопсис, реальный рейтинг КП/IMDb)
        val enriched = allMovies.mapIndexed { idx, item ->
            if (idx < 20 && (item.posterUrl.isBlank() || item.rating == 0.0)) {
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

            if (conn.responseCode == 403 || conn.responseCode == 503 || conn.responseCode == 429) {
                conn.disconnect()
                throw RuTrackerCaptchaException("RuTracker ответил статусом ${conn.responseCode} (требуется капча)")
            }

            if (conn.responseCode in 200..299) {
                val contentType = conn.contentType ?: ""
                val charsetName = if (contentType.contains("utf-8", ignoreCase = true)) "UTF-8" else "windows-1251"
                val html = conn.inputStream.bufferedReader(charset(charsetName)).readText()
                conn.disconnect()

                if (isCaptchaOrBlocked(conn.responseCode, html)) {
                    throw RuTrackerCaptchaException("RuTracker запросил проверку пользователя на странице темы")
                }

                val (poster, overview) = extractPosterAndOverview(html)
                val parsedRating = extractRatingFromTopic(html)

                var finalPoster = poster
                if (finalPoster.isBlank()) {
                    finalPoster = MovieCatalogService.findPoster(item.title, item.originalTitle)
                }
                if (finalPoster.isBlank()) {
                    finalPoster = MovieCatalogService.fetchOnlinePoster(item.title, item.originalTitle, item.releaseYear)
                }
                val finalOverview = if (overview.isNotBlank()) overview else item.overview
                val finalRating = if (parsedRating > 0.0) parsedRating else (if (item.rating > 0.0) item.rating else 0.0)

                item.copy(
                    posterUrl = finalPoster,
                    overview = finalOverview,
                    rating = finalRating
                )
            } else {
                conn.disconnect()
                item
            }
        } catch (e: RuTrackerCaptchaException) {
            throw e
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

            if (conn.responseCode == 403 || conn.responseCode == 503 || conn.responseCode == 429) {
                conn.disconnect()
                throw RuTrackerCaptchaException("RuTracker вернул код ${conn.responseCode} (требуется капча)")
            }

            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return emptyList()
            }

            val contentType = conn.contentType ?: ""
            val charsetName = if (contentType.contains("utf-8", ignoreCase = true)) "UTF-8" else "windows-1251"
            val html = conn.inputStream.bufferedReader(charset(charsetName)).readText()
            conn.disconnect()

            if (isCaptchaOrBlocked(conn.responseCode, html)) {
                throw RuTrackerCaptchaException("RuTracker запросил проверку пользователя на странице списка")
            }

            parseMoviesFromHtml(html, category, limit)
        } catch (e: RuTrackerCaptchaException) {
            throw e
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

        // Разбираем по строкам таблицы, чтобы точно сопоставить раздачу и количество её сидов
        val rows = html.split(Regex("<tr[^>]*>", RegexOption.IGNORE_CASE))
        if (rows.size > 2) {
            for (row in rows) {
                if (result.size >= limit) break
                val matcher = topicPattern.matcher(row)
                if (!matcher.find()) continue
                val topicId = matcher.group(1)?.toLongOrNull() ?: continue
                val rawHtmlTitle = matcher.group(2) ?: continue
                val rawTitle = cleanHtmlEntities(rawHtmlTitle)

                if (!isValidMovieTopic(rawTitle)) continue

                var seeds = 0
                val seedMatcher = Pattern.compile(
                    """class=["'][^"']*seedmed[^"']*["'][^>]*>(?:<[^>]+>)*\s*(\d+)""",
                    Pattern.CASE_INSENSITIVE
                ).matcher(row)
                if (seedMatcher.find()) {
                    seeds = seedMatcher.group(1)?.toIntOrNull() ?: 0
                } else {
                    val altSeedMatcher = Pattern.compile(
                        """(?:seedmed|seed)[^>]*>.*?(\d+)""",
                        Pattern.CASE_INSENSITIVE
                    ).matcher(row)
                    if (altSeedMatcher.find()) {
                        seeds = altSeedMatcher.group(1)?.toIntOrNull() ?: 0
                    }
                }

                val parsed = parseRuTrackerTitle(rawTitle)
                val existingPoster = MovieCatalogService.findPoster(parsed.title, parsed.originalTitle)

                val item = MovieCatalogItem(
                    id = topicId,
                    title = parsed.title,
                    originalTitle = parsed.originalTitle,
                    overview = parsed.overview.ifBlank { "Раздача из раздела «${category.title}» на RuTracker" },
                    posterUrl = existingPoster,
                    releaseYear = parsed.year.ifBlank { "2024" },
                    rating = 0.0,
                    genre = parsed.genre.ifBlank { category.title.removePrefix("🎨 ").removePrefix("🔥 ").removePrefix("🏆 ").removePrefix("🍿 ") },
                    seeds = seeds
                )

                if (result.none { it.id == item.id || it.title.equals(item.title, ignoreCase = true) }) {
                    result.add(item)
                }
            }
        } else {
            val matcher = topicPattern.matcher(html)
            while (matcher.find() && result.size < limit) {
                val topicId = matcher.group(1)?.toLongOrNull() ?: continue
                val rawHtmlTitle = matcher.group(2) ?: continue
                val rawTitle = cleanHtmlEntities(rawHtmlTitle)

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
                    rating = 0.0,
                    genre = parsed.genre.ifBlank { category.title.removePrefix("🎨 ").removePrefix("🔥 ").removePrefix("🏆 ").removePrefix("🍿 ") },
                    seeds = 0
                )

                if (result.none { it.id == item.id || it.title.equals(item.title, ignoreCase = true) }) {
                    result.add(item)
                }
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
