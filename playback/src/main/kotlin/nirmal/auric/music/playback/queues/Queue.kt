

package nirmal.auric.music.playback.queues

import androidx.media3.common.MediaItem
import nirmal.auric.music.extensions.metadata
import nirmal.auric.music.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) {
                withFilteredItems(items.filterExplicit())
            } else {
                this
            }

        fun filterVideoSongs(disableVideos: Boolean = false) =
            if (disableVideos) {
                withFilteredItems(items.filterVideoSongs(true))
            } else {
                this
            }

        private fun withFilteredItems(newItems: List<MediaItem>): Status {
            val currentItem = items.getOrNull(mediaItemIndex)
            val newIndex = if (currentItem != null) {
                val occurrence = items.take(mediaItemIndex + 1).count { it.mediaId == currentItem.mediaId } - 1
                newItems.withIndex()
                    .filter { (_, item) -> item.mediaId == currentItem.mediaId }
                    .getOrNull(occurrence)
                    ?.index
                    ?: -1
            } else {
                -1
            }
            return copy(
                items = newItems,
                mediaItemIndex = if (newIndex >= 0) {
                    newIndex
                } else {
                    mediaItemIndex.coerceIn(0, (newItems.size - 1).coerceAtLeast(0))
                },
            )
        }
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it.metadata?.isVideoSong == true }
    } else {
        this
    }
