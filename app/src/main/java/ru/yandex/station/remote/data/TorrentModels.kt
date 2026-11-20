package ru.yandex.station.remote.data

data class MovieItem(
    val id: String,
    val title: String,
    val year: String = "",
    val quality: String = "1080p",
    val size: String = "2.4 GB",
    val seeds: Int = 120,
    val magnetUrl: String,
    val category: String = "Новинки",
    val description: String = "",
    val isFavorite: Boolean = false,
    val posterUrl: String = ""
)

data class TorrServerAddResponse(
    val hash: String? = null,
    val error: String? = null
)

data class TorrentVideoFile(
    val id: Int,
    val name: String,
    val path: String,
    val sizeBytes: Long
) {
    val isAudio: Boolean get() {
        val lower = "$path $name".lowercase()
        val audioExts = listOf(".flac", ".mp3", ".m4a", ".aac", ".ogg", ".oga", ".wav", ".wma", ".ape", ".wv", ".opus", ".alac", ".ac3", ".dts", ".mka")
        return audioExts.any { lower.contains(it) }
    }
}

data class PendingEpisodeSelection(
    val torrentHash: String,
    val movie: MovieItem,
    val files: List<TorrentVideoFile>,
    val lastFileId: Int = -1,
    val lastPositionSec: Long = 0L
)

data class PendingPeerRetry(
    val torrentHash: String,
    val movie: MovieItem
)

data class BufferingStatus(
    val isVisible: Boolean = false,
    val title: String = "",
    val fileName: String = "",
    val torrentHash: String = "",
    val fileId: Int = 0,
    val currentBytes: Long = 0L,
    val targetBytes: Long = 10 * 1024 * 1024L, // 10 МБ буфер для плавного просмотра
    val downloadSpeedBytes: Long = 0L,
    val activePeers: Int = 0,
    val totalPeers: Int = 0,
    val isSlowWarning: Boolean = false,
    val movie: MovieItem? = null,
    val file: TorrentVideoFile? = null
)
