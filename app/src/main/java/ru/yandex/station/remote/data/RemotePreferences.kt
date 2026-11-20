package ru.yandex.station.remote.data

import android.content.Context
import android.content.SharedPreferences

class RemotePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("yandex_station_remote_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IP = "station_ip"
        private const val KEY_PORT = "station_port"
        private const val KEY_RUTRACKER_LOGIN = "rutracker_login"
        private const val KEY_RUTRACKER_PASS = "rutracker_pass"
        private const val KEY_LAST_RUTRACKER_URL = "last_rutracker_url"
        private const val KEY_RUTRACKER_COOKIES = "rutracker_cookies"
        private const val KEY_RUTRACKER_USER_AGENT = "rutracker_ua"
        private const val KEY_RUTRACKER_LAST_SYNC = "rutracker_last_sync"
        private const val KEY_ENABLED_CATEGORIES = "rutracker_enabled_categories"
        private const val KEY_CUSTOM_CATEGORIES = "rutracker_custom_categories"
        private const val KEY_VOICE_BEEP_ENABLED = "voice_beep_enabled"

        const val DEFAULT_IP = "192.168.100.6"
        const val DEFAULT_PORT = 5555
    }

    var ip: String
        get() = prefs.getString(KEY_IP, DEFAULT_IP) ?: DEFAULT_IP
        set(value) = prefs.edit().putString(KEY_IP, value.trim()).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var rutrackerLogin: String
        get() = prefs.getString(KEY_RUTRACKER_LOGIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RUTRACKER_LOGIN, value).apply()

    var rutrackerPassword: String
        get() = prefs.getString(KEY_RUTRACKER_PASS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RUTRACKER_PASS, value).apply()

    var lastRutrackerUrl: String
        get() = prefs.getString(KEY_LAST_RUTRACKER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_RUTRACKER_URL, value).apply()

    var rutrackerCookies: String
        get() = prefs.getString(KEY_RUTRACKER_COOKIES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RUTRACKER_COOKIES, value).apply()

    var rutrackerUserAgent: String
        get() = prefs.getString(KEY_RUTRACKER_USER_AGENT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RUTRACKER_USER_AGENT, value).apply()

    var rutrackerLastSyncTimestamp: Long
        get() = prefs.getLong(KEY_RUTRACKER_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_RUTRACKER_LAST_SYNC, value).apply()

    var customAddedCategories: Set<String>
        get() = prefs.getStringSet(KEY_CUSTOM_CATEGORIES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_CUSTOM_CATEGORIES, value).apply()

    var enabledRuTrackerCategories: Set<String>
        get() {
            val defaultSet = RuTrackerScraper.DEFAULT_CATEGORIES.map { it.id }.toSet()
            return prefs.getStringSet(KEY_ENABLED_CATEGORIES, null) ?: defaultSet
        }
        set(value) = prefs.edit().putStringSet(KEY_ENABLED_CATEGORIES, value).apply()

    var voiceBeepEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_BEEP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_BEEP_ENABLED, value).apply()
}
