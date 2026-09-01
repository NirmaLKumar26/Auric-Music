package nirmal.auric.music.playback

import nirmal.auric.music.db.entities.SongEntity

interface ISyncUtils {
    fun likeSong(song: SongEntity)
}
