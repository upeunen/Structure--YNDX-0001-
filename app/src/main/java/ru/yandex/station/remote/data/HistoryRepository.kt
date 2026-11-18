package ru.yandex.station.remote.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class HistoryRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("yandex_station_cinema_history", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HISTORY = "history_list"
        private const val KEY_FAVORITES = "favorites_list"
        private const val KEY_WATCH_LATER = "watch_later_list"
        private const val KEY_PROGRESS_PREFIX = "progress_"
        private const val KEY_LAST_FILE_PREFIX = "last_file_"
    }

    data class SavedProgress(
        val fileId: Int,
        val fileName: String,
        val positionSec: Long
    )

    fun savePlaybackProgress(torrentHash: String, fileId: Int, fileName: String, positionSec: Long) {
        val h = torrentHash.lowercase()
        prefs.edit()
            .putLong("${KEY_PROGRESS_PREFIX}${h}_$fileId", positionSec)
            .putInt("${KEY_LAST_FILE_PREFIX}$h", fileId)
            .putString("${KEY_LAST_FILE_PREFIX}${h}_name", fileName)
            .apply()
    }

    fun getFilePositionSec(torrentHash: String, fileId: Int): Long {
        return prefs.getLong("${KEY_PROGRESS_PREFIX}${torrentHash.lowercase()}_$fileId", 0L)
    }

    fun getLastProgressForTorrent(torrentHash: String): SavedProgress? {
        val h = torrentHash.lowercase()
        val fileId = prefs.getInt("${KEY_LAST_FILE_PREFIX}$h", -1)
        if (fileId < 0) return null
        val fileName = prefs.getString("${KEY_LAST_FILE_PREFIX}${h}_name", "") ?: ""
        val pos = prefs.getLong("${KEY_PROGRESS_PREFIX}${h}_$fileId", 0L)
        return SavedProgress(fileId = fileId, fileName = fileName, positionSec = pos)
    }

    private fun isLegacyCurated(id: String): Boolean {
        return id.startsWith("nov_") || id.startsWith("top_") ||
            id.startsWith("ser_") || id.startsWith("cls_") ||
            id.startsWith("search_help_")
    }

    fun getHistory(): List<MovieItem> {
        return readList(KEY_HISTORY).filterNot { isLegacyCurated(it.id) }
    }

    fun addToHistory(movie: MovieItem) {
        val current = getHistory().toMutableList()
        current.removeAll { it.id == movie.id || it.magnetUrl == movie.magnetUrl }
        current.add(0, movie)
        if (current.size > 50) {
            current.removeAt(current.size - 1)
        }
        saveList(KEY_HISTORY, current)
    }

    fun removeFromHistory(movieId: String) {
        val current = getHistory().toMutableList()
        current.removeAll { it.id == movieId }
        saveList(KEY_HISTORY, current)
    }

    fun getFavorites(): List<MovieItem> {
        return readList(KEY_FAVORITES)
    }

    fun getWatchLater(): List<MovieItem> {
        return readList(KEY_WATCH_LATER)
    }

    fun addToWatchLater(movie: MovieItem) {
        val current = getWatchLater().toMutableList()
        current.removeAll { it.id == movie.id || it.magnetUrl == movie.magnetUrl }
        current.add(0, movie.copy(category = "Смотреть позже"))
        if (current.size > 100) current.removeAt(current.size - 1)
        saveList(KEY_WATCH_LATER, current)
    }

    fun removeFromWatchLater(movieId: String) {
        val current = getWatchLater().toMutableList()
        current.removeAll { it.id == movieId }
        saveList(KEY_WATCH_LATER, current)
    }

    fun isFavorite(movieId: String): Boolean {
        return getFavorites().any { it.id == movieId }
    }

    fun removeFromFavorites(movieId: String) {
        val current = getFavorites().toMutableList()
        current.removeAll { it.id == movieId }
        saveList(KEY_FAVORITES, current)
    }

    fun toggleFavorite(movie: MovieItem): Boolean {
        val current = getFavorites().toMutableList()
        val exists = current.any { it.id == movie.id }
        if (exists) {
            current.removeAll { it.id == movie.id }
            saveList(KEY_FAVORITES, current)
            return false
        } else {
            current.add(0, movie.copy(isFavorite = true))
            saveList(KEY_FAVORITES, current)
            return true
        }
    }

    private fun readList(key: String): List<MovieItem> {
        val jsonString = prefs.getString(key, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<MovieItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    MovieItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        year = obj.optString("year", ""),
                        quality = obj.optString("quality", "1080p"),
                        size = obj.optString("size", ""),
                        seeds = obj.optInt("seeds", 0),
                        magnetUrl = obj.getString("magnetUrl"),
                        category = obj.optString("category", "История"),
                        description = obj.optString("description", ""),
                        isFavorite = obj.optBoolean("isFavorite", false)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveList(key: String, list: List<MovieItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("year", item.year)
                put("quality", item.quality)
                put("size", item.size)
                put("seeds", item.seeds)
                put("magnetUrl", item.magnetUrl)
                put("category", item.category)
                put("description", item.description)
                put("isFavorite", item.isFavorite)
            }
            array.put(obj)
        }
        prefs.edit().putString(key, array.toString()).apply()
    }
}
