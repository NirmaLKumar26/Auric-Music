package nirmal.auric.music.ui.screens.saavn

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nirmal.auric.music.LocalDatabase
import nirmal.auric.music.LocalPlayerConnection
import nirmal.auric.music.R
import nirmal.auric.music.db.entities.PlaylistEntity
import nirmal.auric.music.db.entities.PlaylistSongMap
import nirmal.auric.music.extensions.toMediaItem
import nirmal.auric.music.models.toMediaMetadata
import nirmal.auric.music.playback.queues.ListQueue
import nirmal.auric.music.saavn.toSongItem
import nirmal.auric.music.ui.component.IconButton as AppIconButton
import nirmal.auric.music.ui.component.LocalMenuState
import nirmal.auric.music.ui.component.YouTubeListItem
import nirmal.auric.music.ui.menu.YouTubeSongMenu
import nirmal.auric.music.ui.utils.backToMain
import nirmal.auric.music.viewmodels.SaavnCollectionViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SaavnCollectionScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: SaavnCollectionViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val collection by viewModel.collection.collectAsState()
    val error by viewModel.error.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val songs = collection?.songs.orEmpty().map { it.toSongItem() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
    ) {
        TopAppBar(
            title = {
                Text(
                    text = collection?.title ?: stringResource(R.string.filter_jiosaavn),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                AppIconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                if (songs.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = collection?.title,
                                    items = songs.map { it.toMediaItem() },
                                )
                            )
                        }
                    ) {
                        Icon(painterResource(R.drawable.play), contentDescription = null)
                    }
                    IconButton(
                        onClick = {
                            val title = collection?.title ?: return@IconButton
                            coroutineScope.launch(Dispatchers.IO) {
                                database.transaction {
                                    val playlist = PlaylistEntity(
                                        name = title,
                                        thumbnailUrl = collection?.imageUrl,
                                        isLocal = true,
                                    )
                                    insert(playlist)
                                    songs.map { it.toMediaMetadata() }
                                        .onEach(::insert)
                                        .mapIndexed { index, song ->
                                            PlaylistSongMap(
                                                songId = song.id,
                                                playlistId = playlist.id,
                                                position = index,
                                            )
                                        }
                                        .forEach(::insert)
                                }
                            }
                        }
                    ) {
                        Icon(painterResource(R.drawable.playlist_add), contentDescription = null)
                    }
                }
            },
            scrollBehavior = scrollBehavior,
        )

        when {
            collection == null && error == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            songs.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error ?: stringResource(R.string.no_results_found))
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Bottom).asPaddingValues(),
                ) {
                    collection?.subtitle?.let { subtitle ->
                        item {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
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
                                    } else {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = collection?.title,
                                                items = songs.map { it.toMediaItem() },
                                                startIndex = index,
                                            )
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
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}
