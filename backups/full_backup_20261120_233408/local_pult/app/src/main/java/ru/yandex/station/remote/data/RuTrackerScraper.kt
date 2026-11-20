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

    val DEFAULT_CATEGORIES = listOf(
        RuTrackerCategory("rutracker_hot_movies", "🔥 Новинки зарубежного кино", 7),
        RuTrackerCategory("rutracker_russian_movies", "🇷🇺 Наше современное кино", 22),
        RuTrackerCategory("rutracker_soviet_movies", "📽️ Советское кино (СССР)", 941),
        RuTrackerCategory("rutracker_uhd_4k", "✨ Фильмы в 4K UHD", 930),
        RuTrackerCategory("rutracker_classics", "🏆 Зарубежная классика HD", 2198),
        RuTrackerCategory("rutracker_arthouse", "🎨 Авторское и артхаус", 124),
        RuTrackerCategory("rutracker_series", "🍿 Зарубежные сериалы", 189),
        RuTrackerCategory("rutracker_ru_series", "📺 Русские сериалы", 2100),
        RuTrackerCategory("rutracker_dorama", "🎎 Дорамы и азиатские сериалы", 915),
        RuTrackerCategory("rutracker_cartoons", "🧸 Мультфильмы", 208),
        RuTrackerCategory("rutracker_animation_series", "🚀 Мультсериалы", 1464),
        RuTrackerCategory("rutracker_anime", "⛩️ Аниме", 1105),
        RuTrackerCategory("rutracker_doc", "📚 Документальное кино", 1460),
        RuTrackerCategory("rutracker_concerts", "🎸 Музыкальные концерты", 314)
    )

    val CATALOG_CATEGORIES = listOf(
        RuTrackerCategory("rutracker_movies_720p", "🎞️ Фильмы в HD (720p)", 2200),
        RuTrackerCategory("rutracker_theater", "🎭 Театр, спектакли и балет", 93),
        RuTrackerCategory("rutracker_humor", "😂 Юмор, Stand-Up и КВН", 393),
        RuTrackerCategory("rutracker_cartoons_ussr", "🐻 Мультфильмы СССР", 209),
        RuTrackerCategory("rutracker_cartoons_short", "🐥 Короткометражная анимация", 114),
        RuTrackerCategory("rutracker_doc_space", "🔭 Документальное: Космос и наука", 2112),
        RuTrackerCategory("rutracker_doc_nature", "🐾 Документальное: Природа и фауна", 934),
        RuTrackerCategory("rutracker_doc_history", "🏛️ Документальное: История и эпохи", 2323),
        RuTrackerCategory("rutracker_sport", "⚽ Спортивные передачи и матчи", 257),
        RuTrackerCategory("rutracker_retro", "📼 Фильмы до 1990 года (Ретро)", 187),
        RuTrackerCategory("rutracker_short_movies", "🎞️ Короткометражное кино", 212),
        RuTrackerCategory("rutracker_asia_movies", "🐉 Азиатское кино (Восток)", 938),
        RuTrackerCategory("rutracker_india", "💃 Индийское кино (Болливуд)", 225),
        RuTrackerCategory("rutracker_music_films", "🎼 Музыкальные фильмы и мюзиклы", 940),
        RuTrackerCategory("rutracker_tv_shows", "📺 Телепередачи и развлекательные шоу", 1468),
        RuTrackerCategory("rutracker_kids_movies", "🎠 Детские фильмы и сказки", 213),
        RuTrackerCategory("rutracker_kids_series", "🦄 Мультсериалы для детей", 242),
        RuTrackerCategory("rutracker_audiobooks", "🎧 Аудиокниги и радиоспектакли", 492)
    )

    val CATEGORIES = DEFAULT_CATEGORIES + CATALOG_CATEGORIES

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

        // 1. Обогащаем первые 20 раздач полными деталями из RuTracker (постеры, синопсис, реальный рейтинг КП/IMDb)
        // 2. Для раздач с 21 по 100 сразу находим постеры из локальной базы и кинобазы
        val enriched = allMovies.mapIndexed { idx, item ->
            if (idx < 20 && (item.posterUrl.isBlank() || item.rating == 0.0)) {
                enrichTopicDetails(cleanBase, cookies, userAgent, item)
            } else if (item.posterUrl.isBlank()) {
                val local = MovieCatalogService.findPoster(item.title, item.originalTitle)
                if (local.isNotBlank()) {
                    item.copy(posterUrl = local)
                } else if (idx < 45) {
                    val online = MovieCatalogService.fetchOnlinePoster(item.title, item.originalTitle, item.releaseYear)
                    if (online.isNotBlank()) item.copy(posterUrl = online) else item
                } else {
                    item
                }
            } else {
                item
            }
        }
        enriched
    }

    suspend fun enrichTopicDetails(
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

                val topicData = extractTopicData(html)
                val parsedRating = extractRatingFromTopic(html)
                val topicSeeds = extractSeedsFromTopic(html)
                val topicSize = extractSizeFromTopic(html)

                var finalPoster = topicData.posterUrl
                if (finalPoster.isBlank()) {
                    finalPoster = MovieCatalogService.findPoster(item.title, item.originalTitle)
                }
                if (finalPoster.isBlank()) {
                    finalPoster = MovieCatalogService.fetchOnlinePoster(item.title, item.originalTitle, item.releaseYear)
                }

                var finalOverview = topicData.overview
                if (finalOverview.isBlank()) {
                    finalOverview = MovieCatalogService.fetchOnlineOverview(item.title, item.originalTitle)
                }
                if (finalOverview.isBlank()) {
                    finalOverview = item.overview
                }

                val finalRating = if (parsedRating > 0.0) parsedRating else (if (item.rating > 0.0) item.rating else 0.0)
                val finalSeeds = if (topicSeeds > 0) topicSeeds else item.seeds
                val finalSize = if (topicSize.isNotBlank()) topicSize else item.torrentSize

                item.copy(
                    posterUrl = finalPoster,
                    overview = finalOverview,
                    rating = finalRating,
                    seeds = finalSeeds,
                    torrentSize = finalSize,
                    director = topicData.director.ifBlank { item.director },
                    country = topicData.country.ifBlank { item.country },
                    cast = topicData.cast.ifBlank { item.cast },
                    voiceActing = topicData.voiceActing.ifBlank { item.voiceActing }
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

    fun extractSeedsFromTopic(html: String): Int {
        val seedMatch = Regex("""(?:Сиды|Сидов|Seeders|Seeds)\s*[:：&nbsp;]*\s*(?:<[^>]+>)*\s*(\d+)""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""class=["'][^"']*seed(?:med)?[^"']*["'][^>]*>(?:<[^>]+>)*\s*(\d+)""", RegexOption.IGNORE_CASE).find(html)
        return seedMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    fun extractSizeFromTopic(html: String): String {
        val sizeMatch = Regex("""(?:Размер|Размер файла|Размер раздачи|Size)\s*[:：&nbsp;]*\s*(?:<[^>]+>)*\s*([\d.,]+\s*(?:GB|ГБ|MB|МБ|TB|ТБ))""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""class=["'][^"']*(?:tr-dl|tor-size)[^"']*["'][^>]*>(?:<[^>]+>)*\s*([\d.,]+\s*(?:GB|ГБ|MB|МБ|TB|ТБ))""", RegexOption.IGNORE_CASE).find(html)
        return sizeMatch?.groupValues?.get(1)?.trim() ?: ""
    }

    data class ParsedTopicData(
        val posterUrl: String,
        val overview: String,
        val director: String,
        val country: String,
        val cast: String,
        val voiceActing: String
    )

    fun extractPosterAndOverview(html: String): Pair<String, String> {
        val data = extractTopicData(html)
        return Pair(data.posterUrl, data.overview)
    }

    fun extractTopicData(html: String): ParsedTopicData {
        var posterUrl = ""

        // 1. Поиск постера в разметке RuTracker
        val varImgPattern = Pattern.compile(
            """<var[^>]+class=["'][^"']*postImg[^"']*["'][^>]+title=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val mVar = varImgPattern.matcher(html)
        if (mVar.find()) {
            posterUrl = mVar.group(1)?.trim() ?: ""
        }

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

        if (posterUrl.startsWith("//")) {
            posterUrl = "https:$posterUrl"
        } else if (posterUrl.startsWith("http://")) {
            posterUrl = posterUrl.replaceFirst("http://", "https://")
        }

        // 2. Полный связный сюжет (независимо от тегов b, span, br)
        var overview = ""
        val startKeywords = listOf("описание", "сюжет", "о фильме", "синопсис")
        val lowerHtml = html.lowercase()
        var bestStartIdx = -1

        for (kw in startKeywords) {
            val idx = lowerHtml.indexOf(kw)
            if (idx != -1) {
                val after = html.substring(idx + kw.length, (idx + kw.length + 60).coerceAtMost(html.length))
                if (after.contains(":") || after.contains("：") || after.contains("</b>") || after.contains("</span>") || after.contains("</strong>")) {
                    bestStartIdx = idx + kw.length
                    break
                }
            }
        }

        if (bestStartIdx != -1) {
            var cur = bestStartIdx
            while (cur < html.length && (html[cur] == ':' || html[cur] == '：' || html[cur] == '-' || html[cur] == ' ' || html[cur] == '<' || html[cur] == '>' || html[cur] == '/' || html[cur] == '\n' || html[cur] == '\r')) {
                if (html[cur] == '<') {
                    val closeTag = html.indexOf('>', cur)
                    if (closeTag != -1) cur = closeTag + 1 else cur++
                } else {
                    cur++
                }
            }

            // Ищем реальный конец сюжета: технический блок раздачи (Качество, Формат, Видео, Аудио, Субтитры) или разделители (<hr, sp-wrap, attach, /td)
            var endIdx = (cur + 3500).coerceAtMost(html.length)
            val techMatcher = Pattern.compile(
                """(?i)(?:<hr\b|<div\s+class=["']sp-wrap|<fieldset|<div\s+class=["']attach|</td>|(?:<br\s*/?>|\n)\s*(?:<span[^>]*class=["'][^"']*post-b[^"']*["']>|<b\b|<strong\b)?\s*(?:Качество|Формат(?:\s+файла)?|Видео(?:\s*кодек|\s*формат)?|Аудио(?:\s*кодек)?|Звук(?:\s*дорожки)?|Субтитры|Продолжительность|Перевод|Релиз(?:\s*группа)?|Техданные|MediaInfo|Размер\s+файла)\s*[:：])"""
            ).matcher(html)

            if (techMatcher.find(cur) && techMatcher.start() < endIdx) {
                endIdx = techMatcher.start()
            }

            if (endIdx > cur) {
                val candidate = html.substring(cur, endIdx)
                val cleaned = cleanHtmlEntities(candidate)
                    .replace(Regex("""\n\s*\n+"""), "\n\n")
                    .trim()
                if (cleaned.length >= 20) {
                    overview = cleaned.take(3000)
                }
            }
        }

        if (overview.isBlank()) {
            val fallbackPattern = Pattern.compile(
                """(?:Описание|О фильме|Сюжет|Синопсис)\s*[:：]\s*(?:</b>)?\s*(.*?)(?:(?:<br\s*/?>|\n)\s*(?:<span[^>]*class=["'][^"']*post-b|<b\b|<strong\b|Качество|Формат|Видео|Аудио|<div|<hr)|$)""",
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL
            )
            val mFall = fallbackPattern.matcher(html)
            if (mFall.find()) {
                overview = cleanHtmlEntities(mFall.group(1) ?: "").take(2500).trim()
            }
        }

        // 3. Режиссер
        var director = ""
        val dirMatch = Regex("""(?:Режиссер|Режиссёр)\s*[:：]\s*(?:</b>)?\s*(.*?)(?:<br|\n|</b>|</div>)""", RegexOption.IGNORE_CASE).find(html)
        if (dirMatch != null) {
            director = cleanHtmlEntities(dirMatch.groupValues[1]).take(120).trim()
        }

        // 4. Страна
        var country = ""
        val countryMatch = Regex("""(?:Страна|Производство)\s*[:：]\s*(?:</b>)?\s*(.*?)(?:<br|\n|</b>)""", RegexOption.IGNORE_CASE).find(html)
        if (countryMatch != null) {
            country = cleanHtmlEntities(countryMatch.groupValues[1]).take(100).trim()
        }

        // 5. В ролях / Актеры
        var cast = ""
        val castMatch = Regex("""(?:В ролях|Актеры|Актёры)\s*[:：]\s*(?:</b>)?\s*(.*?)(?:<br\s*/?>\s*<br|<div|<b\b|\n\n)""", RegexOption.IGNORE_CASE).find(html)
        if (castMatch != null) {
            cast = cleanHtmlEntities(castMatch.groupValues[1]).take(300).trim()
        }

        // 6. Перевод / Озвучка
        var voiceActing = ""
        val voiceMatch = Regex("""(?:Перевод|Озвучивание|Озвучка|Аудио)\s*[:：]\s*(?:</b>)?\s*(.*?)(?:<br|\n|</b>)""", RegexOption.IGNORE_CASE).find(html)
        if (voiceMatch != null) {
            voiceActing = cleanHtmlEntities(voiceMatch.groupValues[1]).take(150).trim()
        }

        return ParsedTopicData(
            posterUrl = posterUrl,
            overview = overview,
            director = director,
            country = country,
            cast = cast,
            voiceActing = voiceActing
        )
    }

    suspend fun fetchTopicReviews(
        baseUrl: String,
        cookies: String,
        userAgent: String,
        topicId: Long
    ): List<MovieReview> = withContext(Dispatchers.IO) {
        val cleanBase = getCleanBaseUrl(baseUrl)
        val topicUrl = "$cleanBase/viewtopic.php?t=$topicId"
        try {
            val conn = (URL(topicUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 9000
                instanceFollowRedirects = true
                if (cookies.isNotBlank()) setRequestProperty("Cookie", cookies)
                setRequestProperty(
                    "User-Agent",
                    if (userAgent.isNotBlank()) userAgent
                    else "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
                setRequestProperty("Referer", "$cleanBase/index.php")
            }

            if (conn.responseCode == 403 || conn.responseCode == 503 || conn.responseCode == 429) {
                conn.disconnect()
                throw RuTrackerCaptchaException("RuTracker вернул код ${conn.responseCode} (капча)")
            }

            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return@withContext emptyList()
            }

            val contentType = conn.contentType ?: ""
            val charsetName = if (contentType.contains("utf-8", ignoreCase = true)) "UTF-8" else "windows-1251"
            val html = conn.inputStream.bufferedReader(charset(charsetName)).readText()
            conn.disconnect()

            if (isCaptchaOrBlocked(conn.responseCode, html)) {
                throw RuTrackerCaptchaException("RuTracker запросил проверку пользователя на странице темы")
            }

            parseReviewsFromHtml(html)
        } catch (e: RuTrackerCaptchaException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseReviewsFromHtml(html: String): List<MovieReview> {
        val reviews = mutableListOf<MovieReview>()
        val postBlocks = html.split(Regex("""<tbody\s+id=["']post_\d+["']""", RegexOption.IGNORE_CASE))

        val userPosts = if (postBlocks.size > 2) postBlocks.drop(2) else if (postBlocks.size > 1) postBlocks.drop(1) else emptyList()

        for (post in userPosts) {
            if (reviews.size >= 8) break

            var author = ""
            val nickPattern = Pattern.compile(
                """<[a-z0-9]+\s+[^>]*class=["'][^"']*\bnick\b[^"']*["'][^>]*>(.*?)</[a-z0-9]+>""",
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL
            )
            val nickMatcher = nickPattern.matcher(post)
            if (nickMatcher.find()) {
                author = cleanHtmlEntities(nickMatcher.group(1) ?: "").trim()
            }
            if (author.isBlank()) {
                val clickMatcher = Pattern.compile("""onclickPoster\(['"]([^'"]+)['"]""", Pattern.CASE_INSENSITIVE).matcher(post)
                if (clickMatcher.find()) {
                    author = clickMatcher.group(1)?.trim() ?: ""
                }
            }
            if (author.isBlank()) {
                val simpleNick = Regex("""class=["'][^"']*nick[^"']*["'][^>]*>([^<]+)<""", RegexOption.IGNORE_CASE).find(post)
                author = simpleNick?.groupValues?.get(1)?.trim() ?: ""
            }
            if (author.isBlank()) continue

            var date = ""
            val pLinkPattern = Pattern.compile(
                """<a\s+[^>]*class=["'][^"']*\bp-link\b[^"']*["'][^>]*>(.*?)</a>""",
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL
            )
            val pLinkMatcher = pLinkPattern.matcher(post)
            if (pLinkMatcher.find()) {
                date = cleanHtmlEntities(pLinkMatcher.group(1) ?: "").trim()
            }
            if (date.isBlank()) {
                val timePattern = Pattern.compile(
                    """class=["'][^"']*post-time[^"']*["'][^>]*>(?:<[^>]+>)*\s*([0-9A-Za-zА-Яа-яЁё\s\-:.,]{5,30})""",
                    Pattern.CASE_INSENSITIVE
                )
                val timeMatcher = timePattern.matcher(post)
                if (timeMatcher.find()) {
                    date = cleanHtmlEntities(timeMatcher.group(1) ?: "").trim()
                }
            }

            val bodyIdx = post.indexOf("class=\"post_body")
            if (bodyIdx == -1) continue

            val openCloseIdx = post.indexOf(">", bodyIdx)
            if (openCloseIdx == -1) continue

            var endIdx = post.length
            val sigIdx = post.indexOf("class=\"signature", openCloseIdx)
            if (sigIdx != -1 && sigIdx < endIdx) endIdx = sigIdx
            val tdEndIdx = post.indexOf("</td>", openCloseIdx)
            if (tdEndIdx != -1 && tdEndIdx < endIdx) endIdx = tdEndIdx

            val rawBody = post.substring(openCloseIdx + 1, endIdx)

            val withoutQuotes = Pattern.compile("""<div\s+class=["']q-wrap["']>.*?</div>""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE).matcher(rawBody).replaceAll("")
            val withoutSpoilers = Pattern.compile("""<div\s+class=["']sp-wrap["']>.*?</div>""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE).matcher(withoutQuotes).replaceAll("")

            val cleanText = cleanHtmlEntities(withoutSpoilers)
                .replace(Regex("""\s+"""), " ")
                .trim()

            if (cleanText.length in 10..900) {
                reviews.add(
                    MovieReview(
                        author = author,
                        date = date,
                        text = cleanText
                    )
                )
            }
        }
        return reviews
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

                var sizeText = ""
                val sizeMatch = Regex("""class=["'][^"']*tr-dl[^"']*["'][^>]*>(?:<[^>]+>)*\s*([\d.,]+\s*(?:GB|ГБ|MB|МБ|TB|ТБ))""", RegexOption.IGNORE_CASE).find(row)
                    ?: Regex("""class=["'][^"']*tor-size[^"']*["'][^>]*>(?:<[^>]+>)*\s*([\d.,]+\s*(?:GB|ГБ|MB|МБ|TB|ТБ))""", RegexOption.IGNORE_CASE).find(row)
                    ?: Regex("""([\d.,]+\s*(?:GB|ГБ|MB|МБ|TB|ТБ))""", RegexOption.IGNORE_CASE).find(row)
                if (sizeMatch != null) {
                    sizeText = cleanHtmlEntities(sizeMatch.groupValues[1]).trim()
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
                    seeds = seeds,
                    torrentSize = sizeText
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
                    seeds = 0,
                    torrentSize = ""
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
