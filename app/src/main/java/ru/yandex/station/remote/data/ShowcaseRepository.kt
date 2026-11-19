package ru.yandex.station.remote.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class ShowcaseRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("yandex_station_showcase", Context.MODE_PRIVATE)

    companion object {
        const val KEY_RUTRACKER_SAVED = "rutracker_showcase_movies"
        private const val PREFIX_CATEGORY = "showcase_cat_"
    }

    private val STOP_WORDS = listOf(
        "правила", "правило", "faq", "инструкц", "навигатор", "объявлен", "внимани",
        "обсужден", "опрос", "каталог", "модератор", "оформлен", "флудилк", "курилк",
        "заявк", "шаблон", "архив", "помощь", "как скачивать", "список раздач", "поздравлен"
    )

    fun isRealMovie(movie: MovieCatalogItem): Boolean {
        val fullText = (movie.title + " " + movie.originalTitle + " " + movie.overview).lowercase()
        if (STOP_WORDS.any { fullText.contains(it) }) return false
        val hasYear = Regex("""\b(19\d\d|20\d\d)\b""").find(movie.title + " " + movie.releaseYear + " " + movie.overview) != null
        return hasYear || movie.originalTitle.isNotBlank()
    }

    fun getRuTrackerMovies(): List<MovieCatalogItem> {
        return getCategoryMovies(KEY_RUTRACKER_SAVED)
    }

    fun addRuTrackerMovie(movie: MovieCatalogItem) {
        if (!isRealMovie(movie)) return
        val current = getRuTrackerMovies().toMutableList()
        current.removeAll { it.title.equals(movie.title, ignoreCase = true) || it.id == movie.id }
        current.add(0, movie)
        if (current.size > 100) {
            current.removeAt(current.size - 1)
        }
        saveCategoryMovies(KEY_RUTRACKER_SAVED, current)
    }

    fun getCategoryMovies(categoryId: String): List<MovieCatalogItem> {
        val key = if (categoryId == KEY_RUTRACKER_SAVED) KEY_RUTRACKER_SAVED else "$PREFIX_CATEGORY$categoryId"
        val jsonStr = prefs.getString(key, null) ?: return emptyList()
        val list = mutableListOf<MovieCatalogItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    MovieCatalogItem(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        title = obj.optString("title", ""),
                        originalTitle = obj.optString("originalTitle", ""),
                        overview = obj.optString("overview", ""),
                        posterUrl = obj.optString("posterUrl", ""),
                        backdropUrl = obj.optString("backdropUrl", ""),
                        releaseYear = obj.optString("releaseYear", ""),
                        rating = obj.optDouble("rating", 8.0),
                        genre = obj.optString("genre", "RuTracker"),
                        seeds = obj.optInt("seeds", 0)
                    )
                )
            }
        } catch (_: Exception) {}

        val filtered = list.filter { isRealMovie(it) }
        if (filtered.size != list.size) {
            saveCategoryMovies(categoryId, filtered)
        }
        return filtered
    }

    fun saveCategoryMovies(categoryId: String, movies: List<MovieCatalogItem>) {
        val key = if (categoryId == KEY_RUTRACKER_SAVED) KEY_RUTRACKER_SAVED else "$PREFIX_CATEGORY$categoryId"
        val arr = JSONArray()
        for (m in movies.filter { isRealMovie(it) }.take(100)) {
            val obj = JSONObject().apply {
                put("id", m.id)
                put("title", m.title)
                put("originalTitle", m.originalTitle)
                put("overview", m.overview)
                put("posterUrl", m.posterUrl)
                put("backdropUrl", m.backdropUrl)
                put("releaseYear", m.releaseYear)
                put("rating", m.rating)
                put("genre", m.genre)
                put("seeds", m.seeds)
            }
            arr.put(obj)
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    fun clearRuTrackerMovies() {
        prefs.edit().remove(KEY_RUTRACKER_SAVED).apply()
    }

    fun clearAllShowcase() {
        val editor = prefs.edit()
        editor.remove(KEY_RUTRACKER_SAVED)
        for (cat in RuTrackerScraper.CATEGORIES) {
            editor.remove("$PREFIX_CATEGORY${cat.id}")
        }
        editor.apply()
    }
}
