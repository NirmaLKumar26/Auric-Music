package nirmal.auric.music.ui.screens

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.utils.parseCookieString
import nirmal.auric.music.LocalPlayerAwareWindowInsets
import nirmal.auric.music.LocalPlayerConnection
import nirmal.auric.music.R
import nirmal.auric.music.constants.InnerTubeCookieKey
import nirmal.auric.music.constants.ShowSpeedDialKey
import nirmal.auric.music.db.entities.Album
import nirmal.auric.music.db.entities.Artist
import nirmal.auric.music.db.entities.LocalItem
import nirmal.auric.music.db.entities.Playlist
import nirmal.auric.music.db.entities.Song
import nirmal.auric.music.models.toMediaMetadata
import nirmal.auric.music.playback.queues.YouTubeQueue
import nirmal.auric.music.ui.component.HomeLanguageChip
import nirmal.auric.music.ui.component.HomePosterTile
import nirmal.auric.music.ui.component.HomeRail
import nirmal.auric.music.ui.component.HomeRailRow
import nirmal.auric.music.ui.component.HomeRailTile
import nirmal.auric.music.ui.component.HomeSectionTitle
import nirmal.auric.music.ui.component.LocalMenuState
import nirmal.auric.music.ui.component.shimmer.GridItemPlaceHolder
import nirmal.auric.music.ui.component.shimmer.ShimmerHost
import nirmal.auric.music.ui.component.shimmer.TextPlaceholder
import nirmal.auric.music.ui.menu.AlbumMenu
import nirmal.auric.music.ui.menu.ArtistMenu
import nirmal.auric.music.ui.menu.SongMenu
import nirmal.auric.music.ui.menu.YouTubeAlbumMenu
import nirmal.auric.music.ui.menu.YouTubeArtistMenu
import nirmal.auric.music.ui.menu.YouTubePlaylistMenu
import nirmal.auric.music.ui.menu.YouTubeSongMenu
import nirmal.auric.music.utils.isSaavnMediaId
import nirmal.auric.music.utils.rememberPreference
import nirmal.auric.music.viewmodels.HomeViewModel
import java.net.URLEncoder

private fun NavController.navigateToPlaylistItem(playlist: PlaylistItem) {
    when (val playlistId = playlist.id.removePrefix("VL")) {
        "LM" -> navigate("auto_playlist/liked")
        "SE" -> navigate("auto_playlist/downloaded")
        else -> navigate("online_playlist/$playlistId")
    }
}

private fun ytSubtitle(item: YTItem): String? = when (item) {
    is SongItem -> item.artists.joinToString { it.name }
    is AlbumItem -> item.artists?.joinToString { it.name }
    is PlaylistItem -> item.author?.name
    is ArtistItem -> null
}

private fun localSubtitle(item: LocalItem): String? = when (item) {
    is Song -> item.artists.joinToString { it.name }
    is Album -> item.artists.joinToString { it.name }
    else -> null
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val quickPicks by viewModel.quickPicks.collectAsState()
    val aiRecommendedPlaylist by viewModel.aiRecommendedPlaylist.collectAsState()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val similarRecommendations by viewModel.similarRecommendations.collectAsState()
    val accountPlaylists by viewModel.accountPlaylists.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val explorePage by viewModel.explorePage.collectAsState()
    val dailyDiscover by viewModel.dailyDiscover.collectAsState()
    val communityPlaylists by viewModel.communityPlaylists.collectAsState()
    val saavnTrending by viewModel.saavnTrending.collectAsState()
    val speedDialItems by viewModel.speedDialItems.collectAsState()
    val selectedChip by viewModel.selectedChip.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val accountName by viewModel.accountName.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val (showSpeedDial) = rememberPreference(ShowSpeedDialKey, true)
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }
    val pullRefreshState = rememberPullToRefreshState()
    val lazylistState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()
    val displayName = remember(isLoggedIn, accountName) {
        when {
            isLoggedIn && accountName.isNotBlank() && accountName != "Guest" -> accountName.substringBefore(" ")
            else -> "there"
        }
    }
    val trendingPlaylists = remember(saavnTrending, communityPlaylists, accountPlaylists, homePage) {
        buildList {
            addAll(saavnTrending)
            communityPlaylists.orEmpty().forEach { add(it.playlist) }
            addAll(accountPlaylists.orEmpty())
            homePage?.sections.orEmpty().forEach { section ->
                addAll(section.items.filterIsInstance<PlaylistItem>())
            }
        }.distinctBy { it.id }.take(10)
    }
    val voiceSearch = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            navController.navigate("search/${URLEncoder.encode(spoken, "UTF-8")}")
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lazylistState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val len = lazylistState.layoutInfo.totalItemsCount
                if (lastVisibleIndex != null && len > 6 && lastVisibleIndex >= len - 2) {
                    viewModel.loadMoreYouTubeItems(homePage?.continuation)
                }
            }
    }

    NetworkReload(onReload = viewModel::refresh)

    if (selectedChip != null) {
        BackHandler { viewModel.toggleChip(selectedChip) }
    }

    fun playLocal(item: LocalItem) {
        if (item is Song) {
            if (item.id == mediaMetadata?.id) playerConnection.togglePlayPause()
            else playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
        }
    }

    fun playYt(item: YTItem) {
        when (item) {
            is SongItem -> playerConnection.playQueue(
                YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata())
            )
            is AlbumItem -> if (item.id.isSaavnMediaId()) {
                navController.navigate("saavn/album/${URLEncoder.encode(item.id.removePrefix("saavn:album:"), "UTF-8")}")
            } else {
                navController.navigate("album/${item.id}")
            }
            is ArtistItem -> if (item.id.isSaavnMediaId()) {
                navController.navigate("saavn/artist/${URLEncoder.encode(item.id.removePrefix("saavn:artist:"), "UTF-8")}")
            } else {
                navController.navigate("artist/${item.id}")
            }
            is PlaylistItem -> if (item.id.isSaavnMediaId()) {
                navController.navigate("saavn/playlist/${URLEncoder.encode(item.id.removePrefix("saavn:playlist:"), "UTF-8")}")
            } else {
                navController.navigateToPlaylistItem(item)
            }
        }
    }

    fun openLocalMenu(item: LocalItem) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            when (item) {
                is Song -> SongMenu(originalSong = item, navController = navController, onDismiss = menuState::dismiss)
                is Album -> AlbumMenu(originalAlbum = item, navController = navController, onDismiss = menuState::dismiss)
                is Artist -> ArtistMenu(originalArtist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
                is Playlist -> {}
            }
        }
    }

    fun openYtMenu(item: YTItem) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
            }
        }
    }

    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        }
    ) {
        LazyColumn(
            state = lazylistState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(key = "header", contentType = "header") {
                HomeGreetingHeader(
                    name = displayName,
                    imageUrl = if (isLoggedIn) accountImageUrl else null,
                )
            }

            item(key = "search", contentType = "search") {
                HomeSearchBar(
                    onSearchClick = { navController.navigate(Screens.Search.route) },
                    onVoiceClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search songs")
                        }
                        runCatching { voiceSearch.launch(intent) }
                            .onFailure { navController.navigate(Screens.Search.route) }
                    },
                )
            }

            if (trendingPlaylists.isNotEmpty()) {
                item(key = "trending", contentType = "posters") {
                    Column {
                        HomeSectionTitle(title = stringResource(R.string.trending_playlist))
                        HomeRailRow(
                            items = trendingPlaylists,
                            key = { it.id },
                        ) { item ->
                            HomePosterTile(
                                title = item.title,
                                subtitle = item.author?.name,
                                thumbnailUrl = item.thumbnail,
                                modifier = Modifier.combinedClickable(
                                    onClick = { playYt(item) },
                                    onLongClick = { openYtMenu(item) },
                                ),
                            )
                        }
                    }
                }
            }

            item(key = "languages", contentType = "languages") {
                Column {
                    HomeSectionTitle(title = stringResource(R.string.home_languages))
                    HomeRailRow(
                        items = HomeLanguage.All,
                        key = { it.code },
                    ) { language ->
                        HomeLanguageChip(
                            language = language,
                            modifier = Modifier.clickable {
                                navController.navigate("language/${language.code}")
                            },
                        )
                    }
                }
            }

            if (showSpeedDial && speedDialItems.isNotEmpty()) {
                item(key = "speed_dial", contentType = "rail") {
                    HomeRail(title = stringResource(R.string.speed_dial)) {
                        HomeRailRow(
                            items = speedDialItems,
                            key = { it.id },
                        ) { item ->
                            HomeRailTile(
                                title = item.title,
                                subtitle = ytSubtitle(item),
                                thumbnailUrl = item.thumbnail,
                                isCircle = item is ArtistItem,
                                isActive = item.id == mediaMetadata?.id || item.id == mediaMetadata?.album?.id,
                                modifier = Modifier.combinedClickable(
                                    onClick = { playYt(item) },
                                    onLongClick = { openYtMenu(item) },
                                ),
                            )
                        }
                    }
                }
            }

            aiRecommendedPlaylist?.takeIf { it.second.isNotEmpty() }?.let { (playlist, songs) ->
                item(key = "ai", contentType = "rail") {
                    HomeRail(
                        title = playlist.title,
                        onTitleClick = { navController.navigate("local_playlist/${playlist.id}") },
                    ) {
                        HomeRailRow(items = songs.distinctBy { it.id }.take(10), key = { it.id }) { song ->
                            HomeRailTile(
                                title = song.title,
                                subtitle = localSubtitle(song),
                                thumbnailUrl = song.thumbnailUrl,
                                isActive = song.id == mediaMetadata?.id,
                                modifier = Modifier.combinedClickable(
                                    onClick = { playLocal(song) },
                                    onLongClick = { openLocalMenu(song) },
                                ),
                            )
                        }
                    }
                }
            }

            quickPicks?.takeIf { it.isNotEmpty() }?.let { picks ->
                item(key = "quick_picks", contentType = "rail") {
                    HomeRail(title = stringResource(R.string.quick_picks)) {
                        HomeRailRow(
                            items = picks.distinctBy { it.id },
                            key = { it.id },
                        ) { song ->
                            HomeRailTile(
                                title = song.title,
                                subtitle = localSubtitle(song),
                                thumbnailUrl = song.thumbnailUrl,
                                isActive = song.id == mediaMetadata?.id,
                                modifier = Modifier.combinedClickable(
                                    onClick = { playLocal(song) },
                                    onLongClick = { openLocalMenu(song) },
                                ),
                            )
                        }
                    }
                }
            }

            keepListening?.takeIf { it.isNotEmpty() }?.let { items ->
                item(key = "keep_listening", contentType = "rail") {
                    HomeRail(title = stringResource(R.string.keep_listening)) {
                        HomeRailRow(
                            items = items.distinctBy { it.id },
                            key = { it.id },
                        ) { item ->
                            HomeRailTile(
                                title = item.title,
                                subtitle = localSubtitle(item),
                                thumbnailUrl = item.thumbnailUrl,
                                isCircle = item is Artist,
                                isActive = item.id == mediaMetadata?.id || item.id == mediaMetadata?.album?.id,
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        when (item) {
                                            is Song -> playLocal(item)
                                            is Album -> navController.navigate("album/${item.id}")
                                            is Artist -> navController.navigate("artist/${item.id}")
                                            is Playlist -> {}
                                        }
                                    },
                                    onLongClick = { openLocalMenu(item) },
                                ),
                            )
                        }
                    }
                }
            }

            accountPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                item(key = "account_playlists", contentType = "rail") {
                    HomeRail(title = stringResource(R.string.playlists)) {
                        HomeRailRow(
                            items = playlists.distinctBy { it.id },
                            key = { it.id },
                        ) { item ->
                            HomeRailTile(
                                title = item.title,
                                subtitle = ytSubtitle(item),
                                thumbnailUrl = item.thumbnail,
                                modifier = Modifier.combinedClickable(
                                    onClick = { playYt(item) },
                                    onLongClick = { openYtMenu(item) },
                                ),
                            )
                        }
                    }
                }
            }

            forgottenFavorites?.takeIf { it.isNotEmpty() }?.let { songs ->
                item(key = "forgotten", contentType = "rail") {
                    HomeRail(title = stringResource(R.string.forgotten_favorites)) {
                        HomeRailRow(
                            items = songs.distinctBy { it.id },
                            key = { it.id },
                        ) { song ->
                            HomeRailTile(
                                title = song.title,
                                subtitle = localSubtitle(song),
                                thumbnailUrl = song.thumbnailUrl,
                                isActive = song.id == mediaMetadata?.id,
                                modifier = Modifier.combinedClickable(
                                    onClick = { playLocal(song) },
                                    onLongClick = { openLocalMenu(song) },
                                ),
                            )
                        }
                    }
                }
            }

            dailyDiscover?.takeIf { it.isNotEmpty() }?.let { discovers ->
                item(key = "daily_discover", contentType = "rail") {
                    HomeRail(title = stringResource(R.string.similar_to) + " " + discovers.first().seed.title) {
                        HomeRailRow(
                            items = discovers.map { it.recommendation }.distinctBy { it.id },
                            key = { it.id },
                        ) { item ->
                            HomeRailTile(
                                title = item.title,
                                subtitle = ytSubtitle(item),
                                thumbnailUrl = item.thumbnail,
                                isActive = item.id == mediaMetadata?.id,
                                modifier = Modifier.combinedClickable(
                                    onClick = { playYt(item) },
                                    onLongClick = { openYtMenu(item) },
                                ),
                            )
                        }
                    }
                }
            }

            communityPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                item(key = "community", contentType = "rail") {
                    HomeRail(title = stringResource(R.string.from_the_community)) {
                        HomeRailRow(
                            items = playlists.distinctBy { it.playlist.id },
                            key = { it.playlist.id },
                        ) { item ->
                            HomeRailTile(
                                title = item.playlist.title,
                                subtitle = item.playlist.author?.name,
                                thumbnailUrl = item.playlist.thumbnail,
                                modifier = Modifier.combinedClickable(
                                    onClick = { playYt(item.playlist) },
                                    onLongClick = { openYtMenu(item.playlist) },
                                ),
                            )
                        }
                    }
                }
            }

            similarRecommendations.orEmpty().forEachIndexed { index, recommendation ->
                item(key = "similar_${index}_${recommendation.title.id}", contentType = "rail") {
                    HomeRail(title = stringResource(R.string.similar_to) + " " + recommendation.title.title) {
                        HomeRailRow(
                            items = recommendation.items.distinctBy { it.id },
                            key = { it.id },
                        ) { item ->
                            HomeRailTile(
                                title = item.title,
                                subtitle = ytSubtitle(item),
                                thumbnailUrl = item.thumbnail,
                                isCircle = item is ArtistItem,
                                isActive = item.id == mediaMetadata?.id || item.id == mediaMetadata?.album?.id,
                                modifier = Modifier.combinedClickable(
                                    onClick = { playYt(item) },
                                    onLongClick = { openYtMenu(item) },
                                ),
                            )
                        }
                    }
                }
            }

            homePage?.sections.orEmpty().forEachIndexed { index, section ->
                item(
                    key = "yt_${index}_${section.endpoint?.browseId.orEmpty()}_${section.title}",
                    contentType = "rail",
                ) {
                    HomeRail(
                        title = section.title,
                        onTitleClick = section.endpoint?.let { endpoint ->
                            {
                                navController.navigate(
                                    "youtube_browse/${endpoint.browseId}?params=${endpoint.params.orEmpty()}"
                                )
                            }
                        },
                    ) {
                        HomeRailRow(
                            items = section.items.distinctBy { it.id },
                            key = { it.id },
                        ) { item ->
                            HomeRailTile(
                                title = item.title,
                                subtitle = ytSubtitle(item),
                                thumbnailUrl = item.thumbnail,
                                isCircle = item is ArtistItem,
                                isActive = item.id == mediaMetadata?.id || item.id == mediaMetadata?.album?.id,
                                modifier = Modifier.combinedClickable(
                                    onClick = { playYt(item) },
                                    onLongClick = { openYtMenu(item) },
                                ),
                            )
                        }
                    }
                }
            }

            explorePage?.newReleaseAlbums?.takeIf { it.isNotEmpty() }?.let { albums ->
                item(key = "new_releases", contentType = "rail") {
                    HomeRail(
                        title = stringResource(R.string.new_release_albums),
                        onTitleClick = { navController.navigate("new_release") },
                    ) {
                        HomeRailRow(
                            items = albums.distinctBy { it.id },
                            key = { it.id },
                        ) { item ->
                            HomeRailTile(
                                title = item.title,
                                subtitle = ytSubtitle(item),
                                thumbnailUrl = item.thumbnail,
                                isActive = item.id == mediaMetadata?.album?.id,
                                modifier = Modifier.combinedClickable(
                                    onClick = { playYt(item) },
                                    onLongClick = { openYtMenu(item) },
                                ),
                            )
                        }
                    }
                }
            }

            explorePage?.moodAndGenres?.takeIf { it.isNotEmpty() }?.let { moods ->
                item(key = "moods", contentType = "moods") {
                    HomeRail(
                        title = stringResource(R.string.mood_and_genres),
                        onTitleClick = { navController.navigate("mood_and_genres") },
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(moods, key = { it.title }) { mood ->
                                MoodAndGenresButton(
                                    title = mood.title,
                                    onClick = {
                                        navController.navigate(
                                            "youtube_browse/${mood.endpoint.browseId}?params=${mood.endpoint.params}"
                                        )
                                    },
                                    modifier = Modifier.width(168.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading && homePage == null && quickPicks.isNullOrEmpty() && keepListening.isNullOrEmpty()) {
                item(key = "shimmer", contentType = "shimmer") {
                    ShimmerHost {
                        TextPlaceholder(height = 28.dp, modifier = Modifier.padding(16.dp).width(180.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(4) { GridItemPlaceHolder() }
                        }
                    }
                }
            }

            item(key = "bottom") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HomeGreetingHeader(
    name: String,
    imageUrl: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_hello),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "$name 👋",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun HomeSearchBar(
    onSearchClick: () -> Unit,
    onVoiceClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onSearchClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.home_search_here),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onVoiceClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.mic),
                contentDescription = stringResource(R.string.search),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

