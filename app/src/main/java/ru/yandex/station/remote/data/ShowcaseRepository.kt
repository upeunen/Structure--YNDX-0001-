package ru.yandex.station.remote.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class ShowcaseRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("yandex_station_showcase", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_RUTRACKER_MOVIES = "rutracker_showcase_movies"
    }

    fun getRuTrackerMovies(): List<MovieCatalogItem> {
        val jsonStr = prefs.getString(KEY_RUTRACKER_MOVIES, null) ?: return emptyList()
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

    fun addRuTrackerMovie(movie: MovieCatalogItem) {
        val current = getRuTrackerMovies().toMutableList()
        current.removeAll { it.title.equals(movie.title, ignoreCase = true) || it.id == movie.id }
        current.add(0, movie)
        if (current.size > 80) {
            current.removeAt(current.size - 1)
        }
        val arr = JSONArray()
        for (m in current) {
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
        prefs.edit().putString(KEY_RUTRACKER_MOVIES, arr.toString()).apply()
    }

    fun clearRuTrackerMovies() {
        prefs.edit().remove(KEY_RUTRACKER_MOVIES).apply()
    }
}
