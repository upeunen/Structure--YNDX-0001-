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

    fun getRuTrackerMovies(): List<MovieCatalogItem> {
        return getCategoryMovies(KEY_RUTRACKER_SAVED)
    }

    fun addRuTrackerMovie(movie: MovieCatalogItem) {
        val current = getRuTrackerMovies().toMutableList()
        current.removeAll { it.title.equals(movie.title, ignoreCase = true) || it.id == movie.id }
        current.add(0, movie)
        if (current.size > 80) {
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
                        genre = obj.optString("genre", "RuTracker")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveCategoryMovies(categoryId: String, movies: List<MovieCatalogItem>) {
        val key = if (categoryId == KEY_RUTRACKER_SAVED) KEY_RUTRACKER_SAVED else "$PREFIX_CATEGORY$categoryId"
        val arr = JSONArray()
        for (m in movies.take(50)) {
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
            }
            arr.put(obj)
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    fun clearRuTrackerMovies() {
        prefs.edit().remove(KEY_RUTRACKER_SAVED).apply()
    }
}
