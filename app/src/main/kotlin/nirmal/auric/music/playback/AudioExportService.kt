package nirmal.auric.music.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.music.innertube.YouTube
import com.music.saavn.Saavn
import nirmal.auric.music.R
import nirmal.auric.music.constants.AudioQuality
import nirmal.auric.music.constants.ExportingSongIdsKey
import nirmal.auric.music.constants.ExportedSongIdsKey
import nirmal.auric.music.constants.ExportProgressKey
import nirmal.auric.music.constants.encodeExportProgressMap
import nirmal.auric.music.constants.parseExportProgressMap
import nirmal.auric.music.utils.YTPlayerUtils
import nirmal.auric.music.utils.dataStore
import nirmal.auric.music.utils.isSaavnMediaId
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

class AudioExportService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .proxy(YouTube.proxy)
            .proxyAuthenticator { _, response ->
                YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val songId = intent?.getStringExtra(EXTRA_SONG_ID) ?: return START_NOT_STICKY
        val songTitle = intent.getStringExtra(EXTRA_SONG_TITLE).orEmpty()
        val songArtist = intent.getStringExtra(EXTRA_SONG_ARTIST).orEmpty()
        val songAlbum = intent.getStringExtra(EXTRA_SONG_ALBUM).orEmpty()
        val targetDirectoryUri = intent.getStringExtra(EXTRA_TARGET_DIRECTORY_URI) ?: return START_NOT_STICKY

        startExportForeground(songTitle)
        serviceScope.launch {
            exportSong(
                songId = songId,
                songTitle = songTitle,
                songArtist = songArtist,
                songAlbum = songAlbum,
                targetDirectoryUri = targetDirectoryUri,
            )
        }
        return START_NOT_STICKY
    }

    private fun startExportForeground(songTitle: String) {
        val manager = getSystemService<NotificationManager>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.export_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.exporting_percent, 1))
            .setContentText(songTitle.ifBlank { getString(R.string.export_processing_audio) })
            .setSmallIcon(R.drawable.file_export)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 1, false)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun updateNotification(title: String, percent: Int, indeterminate: Boolean = false) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.exporting_percent, percent.coerceIn(0, 99)))
            .setContentText(title)
            .setSmallIcon(R.drawable.file_export)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), indeterminate)
            .build()
        getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun exportSong(
        songId: String,
        songTitle: String,
        songArtist: String,
        songAlbum: String,
        targetDirectoryUri: String,
    ) {
        val safeTitle = sanitizeTitle(songTitle.ifBlank { songId })
        addExportingSongId(songId)
        reportProgress(songId, songTitle, 1)

        val tempSourceFile = File.createTempFile("export_source_", ".audio", cacheDir)
        val tempMp3File = File.createTempFile("export_result_", ".mp3", cacheDir)

        try {
            if (songId.isSaavnMediaId()) {
                reportProgress(songId, songTitle, 5)
                val streamUrl = Saavn.streamUrl(songId, Saavn.QUALITY_320).getOrThrow()
                reportProgress(songId, songTitle, 8)
                downloadDirectUrl(streamUrl, tempSourceFile) { percent ->
                    reportProgress(songId, songTitle, percent)
                }
            } else {
                reportProgress(songId, songTitle, 3)
                val connectivityManager = getSystemService<ConnectivityManager>()
                    ?: error("No connectivity manager")
                val playbackData = YTPlayerUtils.playerResponseForPlayback(
                    videoId = songId,
                    audioQuality = AudioQuality.OPUS,
                    connectivityManager = connectivityManager,
                    preferHighestBitrate = true,
                ).getOrThrow()
                Timber.i(
                    "Export using itag=${playbackData.format.itag} mime=${playbackData.format.mimeType} bitrate=${playbackData.format.bitrate}",
                )
                reportProgress(songId, songTitle, 8)

                downloadStream(playbackData, tempSourceFile) { percent ->
                    reportProgress(songId, songTitle, percent)
                }
            }
            reportProgress(songId, songTitle, 90)
            val converted = convertToMp3(
                sourceFile = tempSourceFile,
                outputFile = tempMp3File,
                songTitle = songTitle,
                songArtist = songArtist,
                songAlbum = songAlbum,
                year = null,
                artworkFile = null,
                onProgress = { percent -> reportProgress(songId, songTitle, percent) },
            )
            val exportFile = if (converted) tempMp3File else tempSourceFile
            val (extension, mimeType) = if (converted) {
                "mp3" to "audio/mpeg"
            } else {
                detectAudioFormat(exportFile)
            }
            writeOutputFile(safeTitle, targetDirectoryUri, exportFile, extension, mimeType)
            addExportedSongId(songId)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.export_complete, "$safeTitle.$extension"),
                    Toast.LENGTH_LONG,
                ).show()
            }
        } catch (e: Throwable) {
            Timber.e(e, "Export failed for songId=$songId")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.export_failed, e.message ?: e.javaClass.simpleName),
                    Toast.LENGTH_LONG,
                ).show()
            }
        } finally {
            tempSourceFile.delete()
            tempMp3File.delete()
            clearExportProgress(songId)
            removeExportingSongId(songId)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun downloadStream(
        playbackData: YTPlayerUtils.PlaybackData,
        destFile: File,
        onProgress: suspend (Int) -> Unit = {},
    ) {
        val expectedLength = playbackData.format.contentLength?.takeIf { it > 0 } ?: 0L
        var bytesWritten = 0L
        var lastReportedPercent = -1
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        destFile.outputStream().use { output ->
            var offset = 0L
            while (true) {
                if (expectedLength > 0 && offset >= expectedLength) break
                val end = if (expectedLength > 0) {
                    minOf(offset + CHUNK_LENGTH - 1, expectedLength - 1)
                } else {
                    offset + CHUNK_LENGTH - 1
                }
                val requestBuilder = Request.Builder()
                    .url(playbackData.streamUrl)
                    .header("Range", "bytes=$offset-$end")
                YouTube.cookie?.let { requestBuilder.header("Cookie", it) }

                val chunkBytes = httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.code == 416) return@use -1L
                    if (!response.isSuccessful) {
                        error("Stream request failed with ${response.code}")
                    }
                    val body = response.body ?: error("No response body")
                    var written = 0L
                    body.byteStream().use { input ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            written += read
                        }
                    }
                    written
                }
                if (chunkBytes < 0L) break
                if (chunkBytes == 0L) break
                bytesWritten += chunkBytes
                offset += chunkBytes
                val percent = if (expectedLength > 0) {
                    (8 + (bytesWritten * 80) / expectedLength).toInt().coerceIn(8, 88)
                } else {
                    (8 + bytesWritten / 80_000L).toInt().coerceIn(8, 85)
                }
                if (percent >= lastReportedPercent + 1) {
                    lastReportedPercent = percent
                    onProgress(percent)
                }
                if (chunkBytes < CHUNK_LENGTH) break
            }
            output.flush()
        }
        if (destFile.length() <= 0L) {
            error("Downloaded audio is empty")
        }
        if (expectedLength > 0 && destFile.length() < expectedLength * 8 / 10) {
            error("Incomplete export source: wrote ${destFile.length()} of $expectedLength bytes")
        }
    }

    private suspend fun downloadDirectUrl(
        url: String,
        destFile: File,
        onProgress: suspend (Int) -> Unit = {},
    ) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", SAAVN_USER_AGENT)
            .header("Referer", "https://www.jiosaavn.com/")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Stream request failed with ${response.code}")
            val body = response.body ?: error("No response body")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L
            var bytesWritten = 0L
            var lastReportedPercent = -1
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            body.byteStream().use { input ->
                destFile.outputStream().use { output ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesWritten += read
                        val percent = if (totalBytes > 0) {
                            (8 + (bytesWritten * 80) / totalBytes).toInt().coerceIn(8, 88)
                        } else {
                            (8 + bytesWritten / 80_000L).toInt().coerceIn(8, 85)
                        }
                        if (percent >= lastReportedPercent + 1) {
                            lastReportedPercent = percent
                            onProgress(percent)
                        }
                    }
                    output.flush()
                }
            }
        }
        if (destFile.length() <= 0L) error("Downloaded audio is empty")
    }

    private suspend fun convertToMp3(
        sourceFile: File,
        outputFile: File,
        songTitle: String,
        songArtist: String,
        songAlbum: String,
        year: Int?,
        artworkFile: File?,
        onProgress: suspend (Int) -> Unit,
    ): Boolean {
        val command = buildFfmpegCommand(
            inputPath = sourceFile.absolutePath,
            outputPath = outputFile.absolutePath,
            title = songTitle,
            artist = songArtist,
            album = songAlbum,
            year = year,
            coverPath = artworkFile?.absolutePath,
        )
        onProgress(91)
        val converted = withTimeoutOrNull(90_000) {
            kotlinx.coroutines.coroutineScope {
                val ticker = launch {
                    var percent = 91
                    while (true) {
                        delay(1_000)
                        if (percent < 98) percent++
                        onProgress(percent)
                    }
                }
                try {
                    suspendCancellableCoroutine { continuation ->
                        val session = try {
                            FFmpegKit.executeAsync(command) { ffmpegSession ->
                                val ok = ffmpegSession.returnCode != null &&
                                    ReturnCode.isSuccess(ffmpegSession.returnCode)
                                if (continuation.isActive) continuation.resume(ok)
                            }
                        } catch (t: Throwable) {
                            Timber.e(t, "FFmpegKit is unavailable")
                            if (continuation.isActive) continuation.resume(false)
                            return@suspendCancellableCoroutine
                        }
                        continuation.invokeOnCancellation {
                            runCatching { FFmpegKit.cancel(session.sessionId) }
                        }
                    }
                } finally {
                    ticker.cancel()
                }
            }
        }
        if (converted != true) {
            Timber.e("FFmpeg convert failed or timed out")
            runCatching { FFmpegKit.cancel() }
            return false
        }
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            Timber.e("Exported MP3 file is empty")
            return false
        }
        onProgress(99)
        return true
    }

    private fun detectAudioFormat(file: File): Pair<String, String> {
        val header = file.inputStream().use { stream ->
            val bytes = ByteArray(12)
            val read = stream.read(bytes)
            if (read <= 0) ByteArray(0) else bytes.copyOf(read)
        }
        if (header.size >= 3 && header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) {
            return "mp3" to "audio/mpeg"
        }
        if (header.size >= 2 && header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0) {
            return "mp3" to "audio/mpeg"
        }
        if (header.size >= 8 && String(header, 4, 4, Charsets.US_ASCII) == "ftyp") {
            return "m4a" to "audio/mp4"
        }
        if (header.size >= 4 && String(header, 0, 4, Charsets.US_ASCII) == "OggS") {
            return "ogg" to "audio/ogg"
        }
        if (header.size >= 4 &&
            header[0] == 0x1A.toByte() &&
            header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() &&
            header[3] == 0xA3.toByte()
        ) {
            return "webm" to "audio/webm"
        }
        return "m4a" to "audio/mp4"
    }

    private fun writeOutputFile(
        safeTitle: String,
        targetDirectoryUri: String,
        sourceFile: File,
        extension: String = "mp3",
        mimeType: String = "audio/mpeg",
    ) {
        val fileName = "$safeTitle.$extension"
        val uri = Uri.parse(targetDirectoryUri)
        if (uri.scheme == "file") {
            val folder = File(uri.path ?: error("Invalid export directory"))
            if (!folder.exists() && !folder.mkdirs()) error("Unable to create export directory")
            sourceFile.copyTo(File(folder, fileName), overwrite = true)
        } else {
            val destinationDir = DocumentFile.fromTreeUri(this, uri)
                ?: error("Export directory unavailable")
            destinationDir.findFile(fileName)?.delete()
            val outputFile = destinationDir.createFile(mimeType, fileName)
                ?: error("Unable to create output file")
            sourceFile.inputStream().use { input ->
                contentResolver.openOutputStream(outputFile.uri, "w")!!.use { input.copyTo(it) }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun reportProgress(songId: String, title: String, percent: Int, indeterminate: Boolean = false) {
        val clamped = percent.coerceIn(0, 99)
        updateExportProgress(songId, clamped)
        updateNotification(title, clamped, indeterminate)
    }

    private suspend fun addExportedSongId(songId: String) {
        dataStore.edit { preferences ->
            val current = preferences[ExportedSongIdsKey].orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val updated = listOf(songId) + current.filterNot { it == songId }
            preferences[ExportedSongIdsKey] = updated.take(1000).joinToString(",")
        }
    }

    private suspend fun addExportingSongId(songId: String) {
        dataStore.edit { preferences ->
            val current = preferences[ExportingSongIdsKey].orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val updated = listOf(songId) + current.filterNot { it == songId }
            preferences[ExportingSongIdsKey] = updated.take(1000).joinToString(",")
        }
    }

    private suspend fun removeExportingSongId(songId: String) {
        dataStore.edit { preferences ->
            val current = preferences[ExportingSongIdsKey].orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            preferences[ExportingSongIdsKey] = current.filterNot { it == songId }.joinToString(",")
        }
    }

    private suspend fun updateExportProgress(songId: String, percent: Int) {
        dataStore.edit { preferences ->
            val current = parseExportProgressMap(preferences[ExportProgressKey].orEmpty()).toMutableMap()
            current[songId] = percent.coerceIn(0, 100)
            preferences[ExportProgressKey] = encodeExportProgressMap(current)
        }
    }

    private suspend fun clearExportProgress(songId: String) {
        dataStore.edit { preferences ->
            val current = parseExportProgressMap(preferences[ExportProgressKey].orEmpty()).toMutableMap()
            current.remove(songId)
            preferences[ExportProgressKey] = encodeExportProgressMap(current)
        }
    }

    companion object {
        private const val EXTRA_SONG_ID = "extra_song_id"
        private const val EXTRA_SONG_TITLE = "extra_song_title"
        private const val EXTRA_SONG_ARTIST = "extra_song_artist"
        private const val EXTRA_SONG_ALBUM = "extra_song_album"
        private const val EXTRA_ARTWORK_URL = "extra_artwork_url"
        private const val EXTRA_TARGET_DIRECTORY_URI = "extra_target_directory_uri"
        private const val CHANNEL_ID = "audio_export"
        private const val NOTIFICATION_ID = 0xE401
        private const val CHUNK_LENGTH = 512 * 1024L
        private const val SAAVN_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        fun start(
            context: Context,
            songId: String,
            songTitle: String,
            songArtist: String,
            songAlbum: String,
            artworkUrl: String,
            targetDirectoryUri: String,
        ) {
            val intent = Intent(context, AudioExportService::class.java).apply {
                putExtra(EXTRA_SONG_ID, songId)
                putExtra(EXTRA_SONG_TITLE, songTitle)
                putExtra(EXTRA_SONG_ARTIST, songArtist)
                putExtra(EXTRA_SONG_ALBUM, songAlbum)
                putExtra(EXTRA_ARTWORK_URL, artworkUrl)
                putExtra(EXTRA_TARGET_DIRECTORY_URI, targetDirectoryUri)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        private fun sanitizeTitle(title: String): String =
            title
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { "song_${System.currentTimeMillis()}" }

        private fun buildFfmpegCommand(
            inputPath: String,
            outputPath: String,
            title: String,
            artist: String,
            album: String,
            year: Int?,
            coverPath: String?,
        ): String {
            val escapedInput = inputPath.ffmpegEscape()
            val escapedOutput = outputPath.ffmpegEscape()
            val titleMeta = title.ffmpegEscape()
            val artistMeta = artist.ffmpegEscape()
            val albumMeta = album.ffmpegEscape()
            val yearMeta = year?.toString()?.ffmpegEscape()
            val dateFlags = if (yearMeta != null) " -metadata date='$yearMeta' -metadata year='$yearMeta'" else ""
            val audioEncode = "-c:a libmp3lame -b:a 320k -q:a 0 -ar 48000 -ac 2"
            return if (coverPath != null) {
                val escapedCover = coverPath.ffmpegEscape()
                "-y -i '$escapedInput' -i '$escapedCover' -map 0:a -map 1:v -c:v mjpeg -disposition:v attached_pic $audioEncode -id3v2_version 3 -metadata title='$titleMeta' -metadata artist='$artistMeta' -metadata album='$albumMeta'$dateFlags -metadata:s:v title='Album cover' -metadata:s:v comment='Cover (front)' '$escapedOutput'"
            } else {
                "-y -i '$escapedInput' $audioEncode -id3v2_version 3 -metadata title='$titleMeta' -metadata artist='$artistMeta' -metadata album='$albumMeta'$dateFlags '$escapedOutput'"
            }
        }

        private fun String.ffmpegEscape(): String = replace("'", "'\\''")
    }
}
