package ru.yandex.station.remote.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class YouTubeItem(
    val id: String,
    val title: String,
    val urlOrQuery: String,
    val isUrl: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class YouTubeHistoryRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("youtube_history_prefs", Context.MODE_PRIVATE)

    fun getHistory(): List<YouTubeItem> {
        val jsonStr = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<YouTubeItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    YouTubeItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        urlOrQuery = obj.getString("urlOrQuery"),
                        isUrl = obj.getBoolean("isUrl"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun addToHistory(item: YouTubeItem) {
        val current = getHistory().toMutableList()
        // Убираем дубликаты по ссылке или запросу
        current.removeAll { it.urlOrQuery.equals(item.urlOrQuery, ignoreCase = true) }
        current.add(0, item)
        // Ограничение: максимум 20 последних
        val limited = current.take(20)

        val jsonArray = JSONArray()
        for (it in limited) {
            val obj = JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("urlOrQuery", it.urlOrQuery)
                put("isUrl", it.isUrl)
                put("timestamp", it.timestamp)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    companion object {
        private const val KEY_HISTORY = "history_items"
    }
}
