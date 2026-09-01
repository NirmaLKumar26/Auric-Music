package nirmal.auric.music.saavn

import com.music.innertube.YouTube
import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.saavn.SaavnAlbum
import com.music.saavn.SaavnArtist
import com.music.saavn.SaavnIds
import com.music.saavn.SaavnPlaylist
import com.music.saavn.SaavnSearchResult
import com.music.saavn.SaavnSong

val FILTER_JIOSAAVN = YouTube.SearchFilter("jiosaavn")

fun SaavnSong.toSongItem(): SongItem = SongItem(
    id = SaavnIds.song(id),
    title = title,
    artists = artists.map { it.toArtist() }.ifEmpty { listOf(Artist(name = "JioSaavn", id = null)) },
    album = album?.let { Album(name = it, id = albumId?.let(SaavnIds::album).orEmpty()) },
    duration = duration.takeIf { it > 0 },
    thumbnail = imageUrl,
    explicit = explicit,
)

fun SaavnAlbum.toAlbumItem(): AlbumItem = AlbumItem(
    browseId = SaavnIds.album(id),
    playlistId = SaavnIds.album(id),
    title = title,
    artists = artists.map { it.toArtist() },
    year = year,
    thumbnail = imageUrl,
)

fun SaavnPlaylist.toPlaylistItem(): PlaylistItem = PlaylistItem(
    id = SaavnIds.playlist(id),
    title = title,
    author = subtitle?.let { Artist(name = it, id = null) },
    songCountText = songCount?.let { "$it songs" },
    thumbnail = imageUrl,
    playEndpoint = null,
    shuffleEndpoint = null,
    radioEndpoint = null,
)

fun SaavnArtist.toArtistItem(): ArtistItem = ArtistItem(
    id = SaavnIds.artist(id.ifBlank { name }),
    title = name,
    thumbnail = imageUrl,
    channelId = null,
    playEndpoint = null,
    shuffleEndpoint = null,
    radioEndpoint = null,
)

fun SaavnSearchResult.toYtItems(): List<YTItem> = buildList {
    addAll(songs.map { it.toSongItem() })
    addAll(albums.map { it.toAlbumItem() })
    addAll(playlists.map { it.toPlaylistItem() })
    addAll(artists.map { it.toArtistItem() })
}

private fun SaavnArtist.toArtist() = Artist(
    name = name,
    id = id.takeIf { it.isNotBlank() }?.let(SaavnIds::artist),
)
