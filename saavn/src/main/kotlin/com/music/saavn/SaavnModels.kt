package com.music.saavn

data class SaavnArtist(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val permaUrl: String? = null,
)

data class SaavnSong(
    val id: String,
    val title: String,
    val artists: List<SaavnArtist>,
    val album: String? = null,
    val albumId: String? = null,
    val duration: Int = -1,
    val imageUrl: String,
    val permaUrl: String? = null,
    val encryptedMediaUrl: String? = null,
    val explicit: Boolean = false,
)

data class SaavnAlbum(
    val id: String,
    val title: String,
    val artists: List<SaavnArtist>,
    val year: Int? = null,
    val imageUrl: String,
    val permaUrl: String? = null,
    val songCount: Int? = null,
)

data class SaavnPlaylist(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String,
    val permaUrl: String? = null,
    val songCount: Int? = null,
)

data class SaavnSearchResult(
    val songs: List<SaavnSong> = emptyList(),
    val albums: List<SaavnAlbum> = emptyList(),
    val playlists: List<SaavnPlaylist> = emptyList(),
    val artists: List<SaavnArtist> = emptyList(),
)

data class SaavnCollection(
    val type: SaavnCollectionType,
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val songs: List<SaavnSong> = emptyList(),
)

enum class SaavnCollectionType {
    SONG,
    ALBUM,
    PLAYLIST,
    ARTIST,
}

data class SaavnUrlTarget(
    val type: SaavnCollectionType,
    val token: String,
)
