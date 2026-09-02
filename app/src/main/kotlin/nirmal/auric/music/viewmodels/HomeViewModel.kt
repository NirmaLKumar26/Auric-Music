

package nirmal.auric.music.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage
import com.music.innertube.utils.completed
import nirmal.auric.music.constants.HideExplicitKey
import nirmal.auric.music.constants.HideVideoSongsKey
import nirmal.auric.music.constants.HideYoutubeShortsKey
import nirmal.auric.music.constants.InnerTubeCookieKey
import nirmal.auric.music.constants.QuickPicks
import nirmal.auric.music.constants.QuickPicksKey
import nirmal.auric.music.db.MusicDatabase
import nirmal.auric.music.db.entities.Album
import nirmal.auric.music.db.entities.LocalItem
import nirmal.auric.music.db.entities.Song
import nirmal.auric.music.db.entities.SpeedDialItem
import nirmal.auric.music.extensions.filterVideoSongs
import nirmal.auric.music.extensions.toEnum
import nirmal.auric.music.models.SimilarRecommendation
import nirmal.auric.music.saavn.toPlaylistItem
import nirmal.auric.music.utils.SyncUtils
import nirmal.auric.music.utils.dataStore
import nirmal.auric.music.utils.get
import nirmal.auric.music.utils.reportException
import com.music.saavn.Saavn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)

data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isRandomizing = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        (try { it[QuickPicksKey] } catch(e: Exception) { null }).toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val dailyDiscover = MutableStateFlow<List<DailyDiscoverItem>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val saavnTrending = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    val aiRecommendedPlaylist = database.playlistsByNameAsc()
        .map { playlists -> playlists.find { it.playlist.name == "Recommended by AI" } }
        .flatMapLatest { playlist -> 
            if (playlist != null && playlist.songCount > 0) {
                database.playlistSongs(playlist.playlist.id).map { playlistSongs -> 
                    playlist to playlistSongs.map { it.song }.take(RAIL_LIMIT)
                }
            } else {
                flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    val speedDialItems: StateFlow<List<YTItem>> =
        combine(
            database.speedDialDao.getAll(),
            keepListening,
            quickPicks
        ) { pinned, keepListening, quick ->
            val pinnedItems = pinned.map { it.toYTItem() }
            val filled = pinnedItems.toMutableList()
            val targetSize = 8

            if (filled.size < targetSize) {
                
                keepListening?.let { k ->
                    val needed = targetSize - filled.size
                    val available = k.filter { item ->
                        filled.none { p -> p.id == item.id }
                    }.mapNotNull { item ->
                        when (item) {
                            is Song -> SongItem(
                                id = item.id,
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                thumbnail = item.thumbnailUrl ?: "",
                                explicit = false
                            )
                            is Album -> AlbumItem(
                                browseId = item.id,
                                playlistId = item.album.playlistId ?: "",
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                year = item.album.year,
                                thumbnail = item.thumbnailUrl ?: ""
                            )
                            else -> null
                        }
                    }
                    filled.addAll(available.take(needed))
                }
            }

            if (filled.size < targetSize) {
                
                quick?.let { q ->
                    val needed = targetSize - filled.size
                    val available = q.filter { song ->
                        filled.none { p -> p.id == song.id }
                    }.map { song ->
                        SongItem(
                            id = song.id,
                            title = song.title,
                            artists = song.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = song.thumbnailUrl ?: "",
                            explicit = false
                        )
                    }
                    filled.addAll(available.take(needed))
                }
            }

            filled.take(targetSize)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun getRandomItem(): YTItem? {
        try {
            isRandomizing.value = true
            
            kotlinx.coroutines.delay(1000)

            val userSongs = mutableListOf<YTItem>()
            val otherSources = mutableListOf<YTItem>()

            quickPicks.value?.let { songs ->
                userSongs.addAll(songs.map { song ->
                    SongItem(
                        id = song.id,
                        title = song.title,
                        artists = song.artists.map { Artist(name = it.name, id = it.id) },
                        thumbnail = song.thumbnailUrl ?: "",
                        explicit = false
                    )
                })
            }

            keepListening.value?.let { items ->
                items.forEach { item ->
                    when (item) {
                        is Song -> userSongs.add(SongItem(
                            id = item.id,
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = item.thumbnailUrl ?: "",
                            explicit = false
                        ))
                        is Album -> otherSources.add(AlbumItem(
                            browseId = item.id,
                            playlistId = item.album.playlistId ?: "",
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            year = item.album.year,
                            thumbnail = item.thumbnailUrl ?: ""
                        ))
                        else -> {}
                    }
                }
            }

            otherSources.addAll(allYtItems.value)

            
            val item = if (userSongs.isNotEmpty() && (otherSources.isEmpty() || Random.nextFloat() < 0.8f)) {
                userSongs.distinctBy { it.id }.shuffled().firstOrNull()
            } else {
                otherSources.distinctBy { it.id }.shuffled().firstOrNull()
            } ?: userSongs.firstOrNull() ?: otherSources.firstOrNull()

            return item
        } finally {
            isRandomizing.value = false
        }
    }

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    fun togglePin(item: YTItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val speedDialItem = SpeedDialItem.fromYTItem(item)
            val isPinned = database.speedDialDao.isPinned(speedDialItem.id).first()
            if (isPinned) {
                database.speedDialDao.delete(speedDialItem.id)
            } else {
                database.speedDialDao.insert(speedDialItem)
            }
        }
    }
    
    private var lastProcessedCookie: String? = null
    
    private var isProcessingAccountData = false

    private suspend fun getDailyDiscover() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val likedSongs = database.likedSongsByCreateDateAsc().first()
        if (likedSongs.isEmpty()) return

        val seed = likedSongs.shuffled().firstOrNull() ?: return
        val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
            ?: return
        YouTube.related(endpoint).onSuccess { page ->
            val recommendations = page.songs
                .filter { item ->
                    if (hideVideoSongs && item.isVideoSong) return@filter false
                    !item.explicit && item.id != seed.id
                }
                .distinctBy { it.id }
                .take(RAIL_LIMIT)
            if (recommendations.isEmpty()) return@onSuccess
            dailyDiscover.value = recommendations.map { recommendation ->
                DailyDiscoverItem(
                    seed = seed,
                    recommendation = recommendation,
                    relatedEndpoint = endpoint,
                )
            }
        }
    }

    private suspend fun getQuickPicks() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val relatedSongs = database.quickPicks().first().filterVideoSongs(hideVideoSongs)
                val forgotten = database.forgottenFavorites().first()
                    .filterVideoSongs(hideVideoSongs)
                    .take(6)
                val combined = (relatedSongs + forgotten)
                    .distinctBy { it.id }
                    .shuffled()
                    .take(RAIL_LIMIT)
                quickPicks.value = combined.ifEmpty { relatedSongs.shuffled().take(RAIL_LIMIT) }
            }
            QuickPicks.LAST_LISTEN -> {
                val song = database.events().first().firstOrNull()?.song
                if (song != null && database.hasRelatedSongs(song.id)) {
                    quickPicks.value = database.getRelatedSongs(song.id).first()
                        .filterVideoSongs(hideVideoSongs)
                        .shuffled()
                        .take(RAIL_LIMIT)
                }
            }
        }
    }

    private suspend fun getCommunityPlaylists() {
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 4
        val artistSeeds = database.mostPlayedArtists(fromTimeStamp, limit = 6).first()
            .filter { it.artist.isYouTubeArtist }
            .shuffled()
            .take(2)

        val candidatePlaylists = java.util.Collections.synchronizedList(mutableListOf<PlaylistItem>())
        coroutineScope {
            artistSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    YouTube.artist(seed.id).onSuccess { page ->
                        page.sections.forEach { section ->
                            section.items.filterIsInstance<PlaylistItem>().forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" &&
                                    playlist.author?.name != "YouTube" &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
        }

        communityPlaylists.value = candidatePlaylists
            .distinctBy { it.id }
            .shuffled()
            .take(6)
            .map { CommunityPlaylistItem(it, emptyList()) }
    }

    
    private suspend fun loadLocalDataPhase() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)

        getQuickPicks()

        forgottenFavorites.value = database.forgottenFavorites().first()
            .filterVideoSongs(hideVideoSongs).shuffled().take(RAIL_LIMIT)

        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 2
        val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 10, offset = 5).first()
            .filterVideoSongs(hideVideoSongs).shuffled().take(6)
        val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 6, offset = 2).first()
            .filter { it.album.thumbnailUrl != null }.shuffled().take(4)
        val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp).first()
            .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(4)
        keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists)
            .shuffled()
            .take(RAIL_LIMIT)

        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album }
    }

    
    private suspend fun loadSimilarRecommendations() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 2

        coroutineScope {
            val artistDeferreds = database.mostPlayedArtists(fromTimeStamp, limit = 8).first()
                .filter { it.artist.isYouTubeArtist }
                .shuffled().take(SIMILAR_LIMIT)
                .map { artist ->
                    async(Dispatchers.IO) {
                        val items = mutableListOf<YTItem>()
                        YouTube.artist(artist.id).onSuccess { page ->
                            page.sections.takeLast(2).forEach { section -> items += section.items }
                        }
                        SimilarRecommendation(
                            title = artist,
                            items = items
                                .distinctBy { item -> item.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .take(RAIL_LIMIT)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            similarRecommendations.value = artistDeferreds.awaitAll().filterNotNull()
        }
    }

    
    private suspend fun loadNetworkDataPhase() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        YouTube.home().onSuccess { page ->
            homePage.value = page.copy(
                sections = page.sections.mapNotNull { section ->
                    val filteredItems = section.items
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                        .filterYoutubeShorts(hideYoutubeShorts)
                        .take(RAIL_LIMIT)
                    if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                }.take(HOME_SECTIONS_LIMIT)
            )
        }.onFailure { reportException(it) }

        allYtItems.value = homePage.value?.sections?.flatMap { it.items }.orEmpty()

        if (YouTube.cookie != null) {
            viewModelScope.launch(Dispatchers.IO) { loadAccountPlaylists() }
        }
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.explore().onSuccess { page ->
                explorePage.value = page.copy(
                    newReleaseAlbums = page.newReleaseAlbums.filterExplicit(hideExplicit).take(RAIL_LIMIT),
                    moodAndGenres = page.moodAndGenres.take(MOOD_LIMIT),
                )
            }.onFailure { reportException(it) }
        }
        viewModelScope.launch(Dispatchers.IO) { getDailyDiscover() }
        viewModelScope.launch(Dispatchers.IO) { getCommunityPlaylists() }
        viewModelScope.launch(Dispatchers.IO) { loadSimilarRecommendations() }
        viewModelScope.launch(Dispatchers.IO) {
            saavnTrending.value = Saavn.featuredPlaylists(RAIL_LIMIT).getOrNull()
                ?.map { it.toPlaylistItem() }
                .orEmpty()
        }
    }

    private suspend fun load() {
        isLoading.value = true
        loadLocalDataPhase()
        val hasLocal = !quickPicks.value.isNullOrEmpty() ||
            !keepListening.value.isNullOrEmpty() ||
            !forgottenFavorites.value.isNullOrEmpty()
        if (hasLocal) isLoading.value = false
        loadNetworkDataPhase()
        isLoading.value = false
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        if ((homePage.value?.sections?.size ?: 0) >= HOME_SECTIONS_LIMIT) return
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val nextSections = YouTube.home(continuation).getOrNull()
            if (nextSections != null) {
                val remaining = HOME_SECTIONS_LIMIT - (homePage.value?.sections?.size ?: 0)
                val newSections = nextSections.sections.mapNotNull { section ->
                    val filteredItems = section.items
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                        .filterYoutubeShorts(hideYoutubeShorts)
                        .take(RAIL_LIMIT)
                    if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                }.take(remaining)
                if (newSections.isNotEmpty()) {
                    homePage.value = nextSections.copy(
                        chips = homePage.value?.chips,
                        continuation = if ((homePage.value?.sections.orEmpty().size + newSections.size) >= HOME_SECTIONS_LIMIT) {
                            null
                        } else {
                            nextSections.continuation
                        },
                        sections = homePage.value?.sections.orEmpty() + newSections
                    )
                }
            }
            _isLoadingMore.value = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            previousHomePage.value = homePage.value
        }

        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            val nextSections = YouTube.home(params = chip.endpoint?.params).getOrNull() ?: return@launch

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = nextSections.sections.mapNotNull { section ->
                    val filteredItems = section.items
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                        .filterYoutubeShorts(hideYoutubeShorts)
                        .take(RAIL_LIMIT)
                    if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                }.take(HOME_SECTIONS_LIMIT)
            )
            selectedChip.value = chip
        }
    }

    private suspend fun loadAccountPlaylists() {
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
            accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>()
                .filterNot { it.id == "SE" }
                .filterYoutubeShorts(hideYoutubeShorts)
                .take(RAIL_LIMIT)
        }.onFailure {
            reportException(it)
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                isRefreshing.value = true
                load()
            } finally {
                isRefreshing.value = false
            }
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }
    }

    init {

        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { (try { it[InnerTubeCookieKey] } catch(e: Exception) { null }) }
                .distinctUntilChanged()
                .first()

            load()
        }

        
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }

        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { (try { it[InnerTubeCookieKey] } catch(e: Exception) { null }) }
                .collect { cookie ->
                    
                    if (isProcessingAccountData) return@collect

                    
                    lastProcessedCookie = cookie
                    isProcessingAccountData = true

                    try {
                        if (cookie != null && cookie.isNotEmpty()) {

                            
                            YouTube.cookie = cookie

                            
                            YouTube.accountInfo().onSuccess { info ->
                                accountName.value = info.name
                                accountImageUrl.value = info.thumbnailUrl
                            }.onFailure {
                                reportException(it)
                            }
                        } else {
                            accountName.value = "Guest"
                            accountImageUrl.value = null
                            accountPlaylists.value = null
                        }
                    } finally {
                        isProcessingAccountData = false
                    }
                }
        }

        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { (try { it[HideYoutubeShortsKey] } catch(e: Exception) { null }) ?: false }
                .distinctUntilChanged()
                .collect {
                    if (YouTube.cookie != null && accountPlaylists.value != null) {
                        loadAccountPlaylists()
                    }
                }
        }
    }

    companion object {
        const val RAIL_LIMIT = 10
        const val SIMILAR_LIMIT = 2
        const val HOME_SECTIONS_LIMIT = 6
        const val MOOD_LIMIT = 10
    }
}
