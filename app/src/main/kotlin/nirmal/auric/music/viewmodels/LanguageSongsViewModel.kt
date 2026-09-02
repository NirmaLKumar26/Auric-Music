package nirmal.auric.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.saavn.Saavn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nirmal.auric.music.saavn.toPlaylistItem
import nirmal.auric.music.saavn.toSongItem
import nirmal.auric.music.ui.screens.HomeLanguage
import javax.inject.Inject

@HiltViewModel
class LanguageSongsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val language: HomeLanguage = HomeLanguage.fromCode(
        savedStateHandle.get<String>("lang").orEmpty()
    ) ?: HomeLanguage.All.first()

    private val _songs = MutableStateFlow<List<SongItem>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _playlists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val playlists = _playlists.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    private var page = 1
    private var hasMore = true

    init {
        viewModelScope.launch { load(reset = true) }
    }

    fun loadMore() {
        if (_isLoadingMore.value || _isLoading.value || !hasMore) return
        viewModelScope.launch { load(reset = false) }
    }

    private suspend fun load(reset: Boolean) {
        if (reset) {
            page = 1
            hasMore = true
            _isLoading.value = true
        } else {
            _isLoadingMore.value = true
        }
        try {
            val result = Saavn.search(language.searchQuery, page).getOrNull()
            val newSongs = result?.songs.orEmpty().map { it.toSongItem() }
            val newPlaylists = result?.playlists.orEmpty().map { it.toPlaylistItem() }
            if (reset) {
                _songs.value = newSongs.distinctBy { it.id }
                _playlists.value = newPlaylists.distinctBy { it.id }
                if (_songs.value.isEmpty()) {
                    YouTube.search(language.searchQuery, YouTube.SearchFilter.FILTER_SONG)
                        .onSuccess { pageResult ->
                            _songs.value = pageResult.items.filterIsInstance<SongItem>()
                                .distinctBy { it.id }
                        }
                    hasMore = false
                } else {
                    hasMore = newSongs.isNotEmpty()
                    page = 2
                }
            } else {
                if (newSongs.isEmpty()) {
                    hasMore = false
                } else {
                    _songs.value = (_songs.value + newSongs).distinctBy { it.id }
                    _playlists.value = (_playlists.value + newPlaylists).distinctBy { it.id }
                    page += 1
                }
            }
        } finally {
            _isLoading.value = false
            _isLoadingMore.value = false
        }
    }
}
