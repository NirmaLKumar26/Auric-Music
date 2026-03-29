package nirmal.auric.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import nirmal.auric.music.LocalDatabase
import nirmal.auric.music.R
import nirmal.auric.music.db.entities.PlaylistEntity
import nirmal.auric.music.db.entities.PlaylistSongMap
import nirmal.auric.music.models.toMediaMetadata
import nirmal.auric.music.ui.component.IconButton
import nirmal.auric.music.ui.utils.backToMain
import nirmal.auric.music.utils.SpotifyImportHelper
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyImportScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()

    var spotifyUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var importedSongs by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var playlistName by remember { mutableStateOf("") }
    var importProgress by remember { mutableIntStateOf(0) }
    var totalTracks by remember { mutableIntStateOf(0) }
    var isImporting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Spotify Import",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily(Font(R.font.zalando_sans_expanded)),
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = spotifyUrl,
                onValueChange = { spotifyUrl = it },
                label = { Text("Spotify Playlist URL") },
                placeholder = { Text("https://open.spotify.com/playlist/...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Button(
                onClick = {
                    if (spotifyUrl.isBlank()) {
                        Toast.makeText(context, "Please enter a Spotify playlist URL", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        isLoading = true
                        statusText = "Fetching playlist..."
                        try {
                            val (name, songs) = SpotifyImportHelper.getPlaylistSongs(spotifyUrl)
                            playlistName = name
                            importedSongs = songs
                            totalTracks = songs.size
                            statusText = if (songs.isEmpty()) {
                                "No songs found. Check the URL and try again."
                            } else {
                                "Found $totalTracks tracks in \"$name\""
                            }
                        } catch (e: Exception) {
                            statusText = "Error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && !isImporting,
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text("Fetch Playlist")
            }

            if (importedSongs.isNotEmpty()) {
                Button(
                    onClick = {
                        scope.launch {
                            isImporting = true
                            importProgress = 0
                            statusText = "Importing to Auric Music..."

                            val foundIds = mutableListOf<String>()
                            val failed = mutableListOf<String>()

                            for ((index, pair) in importedSongs.withIndex()) {
                                val (title, artist) = pair
                                importProgress = index + 1
                                statusText = "Searching: $title ($importProgress/$totalTracks)"

                                val videoId = SpotifyImportHelper.searchYouTubeForSong(title, artist)
                                if (videoId != null) {
                                    foundIds.add(videoId)
                                } else {
                                    failed.add("$title - $artist")
                                }
                            }

                            if (foundIds.isNotEmpty()) {
                                withContext(Dispatchers.IO) {
                                    val songMetadataList = foundIds.mapNotNull { songId ->
                                        try {
                                            com.echo.innertube.YouTube.queue(listOf(songId))
                                                .getOrNull()?.firstOrNull()?.let { ytSong ->
                                                    songId to ytSong.toMediaMetadata()
                                                }
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }

                                    database.query {
                                        val playlist = PlaylistEntity(
                                            name = playlistName,
                                            browseId = null,
                                            bookmarkedAt = LocalDateTime.now(),
                                            isEditable = true,
                                        )
                                        insert(playlist)
                                        songMetadataList.forEachIndexed { idx, (songId, metadata) ->
                                            insert(metadata)
                                            insert(
                                                PlaylistSongMap(
                                                    songId = songId,
                                                    playlistId = playlist.id,
                                                    position = idx,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }

                            statusText = "Done! Imported ${foundIds.size}/$totalTracks songs" +
                                if (failed.isNotEmpty()) ". ${failed.size} tracks not found." else ""
                            isImporting = false

                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Playlist \"$playlistName\" created with ${foundIds.size} songs",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isImporting && !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                    ),
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onTertiary,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text("Import to Auric Music")
                }

                if (isImporting) {
                    LinearProgressIndicator(
                        progress = { importProgress.toFloat() / totalTracks.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (statusText.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            if (importedSongs.isNotEmpty()) {
                Text(
                    text = "Tracks ($totalTracks)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(importedSongs) { index, (title, artist) ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            leadingContent = {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
