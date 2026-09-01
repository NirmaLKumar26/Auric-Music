package com.music.saavn

object SaavnIds {
    const val PREFIX = "saavn:"
    const val SONG = "saavn:song:"
    const val ALBUM = "saavn:album:"
    const val PLAYLIST = "saavn:playlist:"
    const val ARTIST = "saavn:artist:"

    fun song(id: String) = "$SONG$id"
    fun album(id: String) = "$ALBUM$id"
    fun playlist(id: String) = "$PLAYLIST$id"
    fun artist(id: String) = "$ARTIST$id"

    fun isSaavn(id: String) = id.startsWith(PREFIX)
    fun isSong(id: String) = id.startsWith(SONG)

    fun songPid(id: String): String? =
        id.takeIf { it.startsWith(SONG) }?.removePrefix(SONG)?.takeIf { it.isNotBlank() }
}
