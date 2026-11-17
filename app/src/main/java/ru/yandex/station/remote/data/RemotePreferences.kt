package ru.yandex.station.remote.data

import android.content.Context
import android.content.SharedPreferences

class RemotePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("yandex_station_remote_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IP = "station_ip"
        private const val KEY_PORT = "station_port"
        const val DEFAULT_IP = "192.168.100.6"
        const val DEFAULT_PORT = 5555
    }

    var ip: String
        get() = prefs.getString(KEY_IP, DEFAULT_IP) ?: DEFAULT_IP
        set(value) = prefs.edit().putString(KEY_IP, value.trim()).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()
}
