package nirmal.auric.music.lyrics

import android.content.Context
import nirmal.auric.music.unison.Unison
import nirmal.auric.music.constants.UnisonLyricsEnabledKey
import nirmal.auric.music.utils.dataStore
import nirmal.auric.music.utils.get

object UnisonLyricsProvider : LyricsProvider {
    override val name: String = "Unison"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[UnisonLyricsEnabledKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Unison.getLyrics(
        videoId = id,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = duration
    ).map { convertIfTTML(it) }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        Unison.getAllLyrics(
            videoId = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
            callback = { callback(convertIfTTML(it)) }
        )
    }

    private fun convertIfTTML(content: String): String {
        return if (content.trimStart().startsWith("<tt", ignoreCase = true)) {
            val parsedLines = nirmal.auric.music.betterlyrics.TTMLParser.parseTTML(content)
            nirmal.auric.music.betterlyrics.TTMLParser.toLRC(parsedLines)
        } else {
            content
        }
    }
}
