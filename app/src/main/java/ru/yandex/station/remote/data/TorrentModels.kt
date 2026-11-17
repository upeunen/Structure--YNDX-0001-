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
    val isFavorite: Boolean = false
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
)

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
