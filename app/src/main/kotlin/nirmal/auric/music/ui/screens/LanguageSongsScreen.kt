package nirmal.auric.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.models.PlaylistItem
import nirmal.auric.music.LocalPlayerAwareWindowInsets
import nirmal.auric.music.LocalPlayerConnection
import nirmal.auric.music.R
import nirmal.auric.music.extensions.toMediaItem
import nirmal.auric.music.playback.queues.ListQueue
import nirmal.auric.music.playback.queues.YouTubeQueue
import nirmal.auric.music.models.toMediaMetadata
import nirmal.auric.music.ui.component.HomeRail
import nirmal.auric.music.ui.component.HomeRailRow
import nirmal.auric.music.ui.component.HomeRailTile
import nirmal.auric.music.ui.component.IconButton as AppIconButton
import nirmal.auric.music.ui.component.LocalMenuState
import nirmal.auric.music.ui.component.YouTubeListItem
import nirmal.auric.music.ui.menu.YouTubePlaylistMenu
import nirmal.auric.music.ui.menu.YouTubeSongMenu
import nirmal.auric.music.ui.utils.backToMain
import nirmal.auric.music.utils.isSaavnMediaId
import nirmal.auric.music.viewmodels.LanguageSongsViewModel
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LanguageSongsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LanguageSongsViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val songs by viewModel.songs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(listState, songs.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (lastVisible != null && songs.isNotEmpty() && lastVisible >= songs.size - 3) {
                    viewModel.loadMore()
                }
            }
    }

    fun openPlaylist(item: PlaylistItem) {
        if (item.id.isSaavnMediaId()) {
            navController.navigate(
                "saavn/playlist/${URLEncoder.encode(item.id.removePrefix("saavn:playlist:"), "UTF-8")}"
            )
        } else {
            navController.navigate("online_playlist/${item.id.removePrefix("VL")}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = viewModel.language.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                AppIconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
            actions = {
                if (songs.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = viewModel.language.title,
                                    items = songs.map { it.toMediaItem() },
                                )
                            )
                        }
                    ) {
                        Icon(painterResource(R.drawable.play), contentDescription = null)
                    }
                }
            },
            scrollBehavior = scrollBehavior,
        )

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            songs.isEmpty() && playlists.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_results_found))
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom)
                        .asPaddingValues(),
                ) {
                    if (playlists.isNotEmpty()) {
                        item(key = "playlists") {
                            HomeRail(title = stringResource(R.string.playlists)) {
                                HomeRailRow(items = playlists, key = { it.id }) { item ->
                                    HomeRailTile(
                                        title = item.title,
                                        subtitle = item.author?.name,
                                        thumbnailUrl = item.thumbnail,
                                        modifier = Modifier.combinedClickable(
                                            onClick = { openPlaylist(item) },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    YouTubePlaylistMenu(
                                                        playlist = item,
                                                        coroutineScope = scope,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    itemsIndexed(songs, key = { _, item -> item.id }) { index, item ->
                        YouTubeListItem(
                            item = item,
                            isActive = mediaMetadata?.id == item.id,
                            isPlaying = isPlaying,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    if (item.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else if (item.id.isSaavnMediaId()) {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = viewModel.language.title,
                                                items = songs.map { it.toMediaItem() },
                                                startIndex = index,
                                            )
                                        )
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue.radio(item.toMediaMetadata())
                                        )
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        YouTubeSongMenu(
                                            song = item,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ),
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
