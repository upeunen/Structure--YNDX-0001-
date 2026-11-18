package ru.yandex.station.remote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class MovieCatalogItem(
    val id: Long,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val posterUrl: String,
    val backdropUrl: String,
    val releaseYear: String,
    val rating: Double,
    val genre: String = ""
)

data class MovieSection(
    val id: String,
    val title: String,
    val movies: List<MovieCatalogItem>
)

object MovieCatalogService {
    private const val API_KEY = "4ef0d7355d9ffb5151e987764708ce96"
    private const val BASE_URL = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"

    private suspend fun fetchMovies(endpointUrl: String, defaultGenre: String = ""): List<MovieCatalogItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MovieCatalogItem>()
        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val results = root.optJSONArray("results") ?: return@withContext emptyList()
                for (i in 0 until results.length()) {
                    val obj = results.getJSONObject(i)
                    val genreIds = obj.optJSONArray("genre_ids")
                    // Исключаем жанр «Ужасы» (ID 27)
                    var isHorror = false
                    if (genreIds != null) {
                        for (g in 0 until genreIds.length()) {
                            if (genreIds.optInt(g) == 27) {
                                isHorror = true
                                break
                            }
                        }
                    }
                    if (isHorror) continue

                    val posterPath = obj.optString("poster_path", "")
                    if (posterPath.isBlank() || posterPath == "null") continue

                    val title = obj.optString("title").ifBlank { obj.optString("name", "") }
                    val origTitle = obj.optString("original_title").ifBlank { obj.optString("original_name", "") }
                    val releaseDate = obj.optString("release_date").ifBlank { obj.optString("first_air_date", "") }
                    val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
                    val rating = obj.optDouble("vote_average", 0.0)
                    val backdropPath = obj.optString("backdrop_path", "")

                    items.add(
                        MovieCatalogItem(
                            id = obj.optLong("id", System.currentTimeMillis()),
                            title = title,
                            originalTitle = origTitle,
                            overview = obj.optString("overview", ""),
                            posterUrl = "$IMAGE_BASE_URL$posterPath",
                            backdropUrl = if (backdropPath.isNotBlank()) "$IMAGE_BASE_URL$backdropPath" else "",
                            releaseYear = year,
                            rating = (rating * 10).toInt() / 10.0,
                            genre = defaultGenre
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        items
    }

    suspend fun loadMainSections(): List<MovieSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<MovieSection>()

        // 1. В тренде
        val trending = fetchMovies("$BASE_URL/trending/movie/week?api_key=$API_KEY&language=ru-RU", "В тренде")
        if (trending.isNotEmpty()) {
            sections.add(MovieSection("trending", "🔥 Сейчас в тренде", trending))
        }

        // 2. Новинки
        val nowPlaying = fetchMovies("$BASE_URL/movie/now_playing?api_key=$API_KEY&language=ru-RU&page=1", "Новинки")
        if (nowPlaying.isNotEmpty()) {
            sections.add(MovieSection("now_playing", "⭐ Новинки кино", nowPlaying))
        }

        // 3. Фантастика
        val scifi = fetchMovies("$BASE_URL/discover/movie?api_key=$API_KEY&language=ru-RU&with_genres=878&without_genres=27&sort_by=popularity.desc", "Фантастика")
        if (scifi.isNotEmpty()) {
            sections.add(MovieSection("scifi", "🚀 Фантастика", scifi))
        }

        // 4. Приключения
        val adventure = fetchMovies("$BASE_URL/discover/movie?api_key=$API_KEY&language=ru-RU&with_genres=12&without_genres=27&sort_by=popularity.desc", "Приключения")
        if (adventure.isNotEmpty()) {
            sections.add(MovieSection("adventure", "🗺️ Приключения", adventure))
        }

        // 5. Драмы
        val drama = fetchMovies("$BASE_URL/discover/movie?api_key=$API_KEY&language=ru-RU&with_genres=18&without_genres=27&sort_by=popularity.desc", "Драмы")
        if (drama.isNotEmpty()) {
            sections.add(MovieSection("drama", "🎭 Драмы", drama))
        }

        // 6. Комедии
        val comedy = fetchMovies("$BASE_URL/discover/movie?api_key=$API_KEY&language=ru-RU&with_genres=35&without_genres=27&sort_by=popularity.desc", "Комедии")
        if (comedy.isNotEmpty()) {
            sections.add(MovieSection("comedy", "😂 Комедии", comedy))
        }

        // 7. Боевики
        val action = fetchMovies("$BASE_URL/discover/movie?api_key=$API_KEY&language=ru-RU&with_genres=28&without_genres=27&sort_by=popularity.desc", "Боевики")
        if (action.isNotEmpty()) {
            sections.add(MovieSection("action", "💥 Боевики", action))
        }

        // 8. Мультфильмы
        val animation = fetchMovies("$BASE_URL/discover/movie?api_key=$API_KEY&language=ru-RU&with_genres=16&without_genres=27&sort_by=popularity.desc", "Мультфильмы")
        if (animation.isNotEmpty()) {
            sections.add(MovieSection("animation", "🎨 Мультфильмы", animation))
        }

        // 9. Сериалы
        val tvSeries = fetchMovies("$BASE_URL/trending/tv/week?api_key=$API_KEY&language=ru-RU", "Сериалы")
        if (tvSeries.isNotEmpty()) {
            sections.add(MovieSection("tv_series", "📺 Популярные сериалы", tvSeries))
        }

        // 10. Топ фильмов
        val topRated = fetchMovies("$BASE_URL/movie/top_rated?api_key=$API_KEY&language=ru-RU&page=1", "Топ")
        if (topRated.isNotEmpty()) {
            sections.add(MovieSection("top_rated", "🏆 Шедевры с высоким рейтингом", topRated))
        }

        sections
    }

    suspend fun searchMovies(query: String): List<MovieCatalogItem> = withContext(Dispatchers.IO) {
        val encoded = try { URLEncoder.encode(query.trim(), "UTF-8") } catch (_: Exception) { query }
        fetchMovies("$BASE_URL/search/movie?api_key=$API_KEY&language=ru-RU&query=$encoded")
    }
}
