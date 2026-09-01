package nirmal.auric.music.playback

import nirmal.auric.music.models.MediaMetadata

data class LyricsWithProvider(
    val lyrics: String?,
    val providerName: String
)

interface ILyricsHelper {
    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider
}
