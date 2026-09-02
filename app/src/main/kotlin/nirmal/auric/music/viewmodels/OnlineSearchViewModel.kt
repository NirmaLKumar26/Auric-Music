

package nirmal.auric.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.SearchSummaryPage
import nirmal.auric.music.constants.HideExplicitKey
import nirmal.auric.music.constants.HideVideoSongsKey
import nirmal.auric.music.constants.HideYoutubeShortsKey
import nirmal.auric.music.models.ItemsPage
import nirmal.auric.music.saavn.FILTER_JIOSAAVN
import nirmal.auric.music.saavn.toPlaylistItem
import nirmal.auric.music.saavn.toSongItem
import nirmal.auric.music.saavn.toYtItems
import nirmal.auric.music.utils.dataStore
import nirmal.auric.music.utils.get
import nirmal.auric.music.utils.reportException
import com.music.innertube.pages.SearchSummary
import com.music.saavn.Saavn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val query = try {
        URLDecoder.decode(savedStateHandle.get<String>("query")!!, "UTF-8")
    } catch (e: IllegalArgumentException) {
        savedStateHandle.get<String>("query")!!
    }
    val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

    init {
        viewModelScope.launch {
            filter.collect { filter ->
                if (filter == null) {
                    if (summaryPage == null) {
                        YouTube
                            .searchSummary(query)
                            .onSuccess {
                                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                                val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                                val youtubePage = it.filterExplicit(
                                    hideExplicit,
                                ).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts)
                                val saavn = Saavn.search(query).getOrNull()
                                val saavnSongs = saavn?.songs
                                    ?.filter { song -> !hideExplicit || !song.explicit }
                                    ?.take(8)
                                    ?.map { song -> song.toSongItem() }
                                    .orEmpty()
                                val saavnPlaylists = saavn?.playlists
                                    ?.take(8)
                                    ?.map { playlist -> playlist.toPlaylistItem() }
                                    .orEmpty()
                                summaryPage = youtubePage.copy(
                                    summaries = youtubePage.summaries + listOfNotNull(
                                        saavnSongs.takeIf { it.isNotEmpty() }?.let {
                                            SearchSummary(title = "JioSaavn", items = it)
                                        },
                                        saavnPlaylists.takeIf { it.isNotEmpty() }?.let {
                                            SearchSummary(title = "JioSaavn Playlists", items = it)
                                        },
                                    )
                                )
                            }.onFailure {
                                val saavn = Saavn.search(query).getOrNull()
                                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                                val saavnSongs = saavn?.songs
                                    ?.filter { song -> !hideExplicit || !song.explicit }
                                    ?.map { song -> song.toSongItem() }
                                    .orEmpty()
                                val saavnPlaylists = saavn?.playlists
                                    ?.map { playlist -> playlist.toPlaylistItem() }
                                    .orEmpty()
                                if (saavnSongs.isNotEmpty() || saavnPlaylists.isNotEmpty()) {
                                    summaryPage = SearchSummaryPage(
                                        listOfNotNull(
                                            saavnSongs.takeIf { it.isNotEmpty() }?.let {
                                                SearchSummary(title = "JioSaavn", items = it)
                                            },
                                            saavnPlaylists.takeIf { it.isNotEmpty() }?.let {
                                                SearchSummary(title = "JioSaavn Playlists", items = it)
                                            },
                                        )
                                    )
                                } else {
                                    reportException(it)
                                }
                            }
                    }
                } else if (filter == FILTER_JIOSAAVN) {
                    if (viewStateMap[filter.value] == null) {
                        Saavn.search(query)
                            .onSuccess { result ->
                                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                                val items = result.toYtItems()
                                    .filter { item -> !hideExplicit || !item.explicit }
                                viewStateMap[filter.value] = ItemsPage(items, continuation = "2")
                            }
                            .onFailure {
                                reportException(it)
                            }
                    }
                } else {
                    if (viewStateMap[filter.value] == null) {
                        YouTube
                            .search(query, filter)
                            .onSuccess { result ->
                                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                                val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                                viewStateMap[filter.value] =
                                    ItemsPage(
                                        result.items
                                            .distinctBy { it.id }
                                            .filterExplicit(
                                                hideExplicit,
                                            )
                                            .let { items ->
                                                if (filter.value == YouTube.SearchFilter.FILTER_VIDEO.value) items
                                                else items.filterVideoSongs(hideVideoSongs)
                                            }
                                            .filterYoutubeShorts(hideYoutubeShorts),
                                        result.continuation,
                                    )
                            }.onFailure {
                                reportException(it)
                            }
                    }
                }
            }
        }
    }

    fun loadMore() {
        val filter = filter.value?.value
        viewModelScope.launch {
            if (filter == null) return@launch
            val viewState = viewStateMap[filter] ?: return@launch
            val continuation = viewState.continuation
            if (continuation != null) {
                if (filter == FILTER_JIOSAAVN.value) {
                    val page = continuation.toIntOrNull() ?: return@launch
                    val searchResult = Saavn.search(query, page).getOrNull() ?: return@launch
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val newItems = searchResult.toYtItems()
                        .filter { item -> !hideExplicit || !item.explicit }
                    viewStateMap[filter] = ItemsPage(
                        (viewState.items + newItems).distinctBy { it.id },
                        continuation = (page + 1).toString().takeIf { newItems.isNotEmpty() }
                    )
                    return@launch
                }
                val searchResult =
                    YouTube.searchContinuation(continuation).getOrNull() ?: return@launch
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                val newItems = searchResult.items
                    .filterExplicit(hideExplicit)
                    .let { items ->
                        if (filter == YouTube.SearchFilter.FILTER_VIDEO.value) items
                        else items.filterVideoSongs(hideVideoSongs)
                    }
                    .filterYoutubeShorts(hideYoutubeShorts)
                viewStateMap[filter] = ItemsPage(
                    (viewState.items + newItems).distinctBy { it.id },
                    searchResult.continuation
                )
            }
        }
    }
}
