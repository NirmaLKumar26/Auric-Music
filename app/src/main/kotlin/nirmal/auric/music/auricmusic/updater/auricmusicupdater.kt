

package nirmal.auric.music.auricmusic.updater


import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import nirmal.auric.music.BuildConfig
import nirmal.auric.music.R
import coil3.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import nirmal.auric.music.auricmusic.updater.downloadmanager.UpdateDownloadWorker
import nirmal.auric.music.auricmusic.updater.downloadmanager.DownloadNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import nirmal.auric.music.ui.component.ChangelogItem
import nirmal.auric.music.ui.component.leadingItemShape
import nirmal.auric.music.ui.component.middleItemShape
import nirmal.auric.music.ui.component.endItemShape
import nirmal.auric.music.ui.component.detachedItemShape
import nirmal.auric.music.ui.component.parseMarkdown
import nirmal.auric.music.ui.component.endItemShape
import nirmal.auric.music.ui.component.detachedItemShape
import nirmal.auric.music.ui.component.AnimatedActionButton
import nirmal.auric.music.ui.component.ExpressiveIconButton
import nirmal.auric.music.ui.component.ErrorSnackbar
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.style.TextDecoration

data class ChangelogSection(val title: String, val items: List<String>)

sealed class AuricUpdateStatus {
    object Idle : AuricUpdateStatus()
    object Checking : AuricUpdateStatus()
    data class Available(
        val version: String,
        val changelog: List<ChangelogSection>,
        val size: String,
        val releaseDate: String,
        val description: String?,
        val imageUrl: String?,
        val apkUrl: String?
    ) : AuricUpdateStatus()

    data class NoUpdate(val version: String) : AuricUpdateStatus()
    data class Error(val message: String) : AuricUpdateStatus()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<AuricUpdateStatus>(AuricUpdateStatus.NoUpdate(BuildConfig.VERSION_NAME)) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var isDownloadComplete by remember { mutableStateOf(false) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val currentVersion = BuildConfig.VERSION_NAME
    val autoUpdateCheckEnabled = getAutoUpdateCheckSetting(context)

    LaunchedEffect(Unit) {
        DownloadNotificationManager.initialize(context)
    }

    
    LaunchedEffect(Unit) {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData("update_download")
            .observeForever { workInfos ->
                val workInfo = workInfos?.firstOrNull() ?: return@observeForever

                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        isDownloading = true
                        downloadProgress = workInfo.progress.getFloat("progress", 0f)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        isDownloading = false
                        isDownloadComplete = true
                        val filePath = workInfo.outputData.getString("file_path")
                        if (filePath != null) {
                            downloadedFile = File(filePath)
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        isDownloading = false
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.download_failed))
                        }
                    }
                    WorkInfo.State.CANCELLED -> {
                        isDownloading = false
                        downloadProgress = 0f
                    }
                    else -> {}
                }
            }
    }

    
    LaunchedEffect(isDownloadComplete, downloadedFile) {
        if (isDownloadComplete && downloadedFile != null) {
            if (!downloadedFile!!.exists()) {
                isDownloadComplete = false
                downloadedFile = null
                downloadProgress = 0f
            }
        }
    }

    fun triggerUpdateCheck() {
        status = AuricUpdateStatus.Checking
        scope.launch {
            
            delay(1000L)
            checkForUpdate(
                context = context,
                onSuccess = { tag, isAvailable, changelog, size, date, description, imageUrl, apkUrl ->
                    saveLastCheckedTime(context, LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy, h:mm a")))
                    saveUpdateAvailableState(context, isAvailable)
                    status = if (isAvailable) {
                        AuricUpdateStatus.Available(
                            version = tag,
                            changelog = changelog,
                            size = size,
                            releaseDate = date,
                            description = description,
                            imageUrl = imageUrl,
                            apkUrl = apkUrl
                        )
                    } else {
                        AuricUpdateStatus.NoUpdate(tag)
                    }
                },
                onError = {
                    status = AuricUpdateStatus.Error(context.getString(R.string.cant_check_updates))
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        if (autoUpdateCheckEnabled) {
            triggerUpdateCheck()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    val titleText = if (status is AuricUpdateStatus.Available) {
                        buildAnnotatedString {
                            append(stringResource(R.string.new_update) + " ")
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append((status as AuricUpdateStatus.Available).version)
                            }
                        }
                    } else {
                        AnnotatedString(stringResource(R.string.settings_check_updates_title))
                    }
                    Text(text = titleText, maxLines = 1)
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                        ExpressiveIconButton(
                            onClick = { navController.navigateUp() },
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.cancel),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WindowInsets.navigationBars.asPaddingValues())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (val currentStatus = status) {
                        is AuricUpdateStatus.Idle, is AuricUpdateStatus.Checking, is AuricUpdateStatus.NoUpdate, is AuricUpdateStatus.Error -> {
                            AnimatedActionButton(
                                text = stringResource(R.string.check_for_update),
                                onClick = { triggerUpdateCheck() },
                                enabled = currentStatus !is AuricUpdateStatus.Checking && !isDownloading,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        is AuricUpdateStatus.Available -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AnimatedActionButton(
                                    text = stringResource(R.string.later),
                                    onClick = { navController.navigateUp() },
                                    modifier = Modifier.weight(1f),
                                    isOutlined = true,
                                    enabled = !isDownloading
                                )
                                AnimatedActionButton(
                                    text = if (isDownloading) "${(downloadProgress * 100).toInt()}%" else if (isDownloadComplete) stringResource(R.string.install) else stringResource(R.string.update_available),
                                    onClick = {
                                        if (isDownloadComplete) {
                                            val file = downloadedFile
                                            if (file == null || !file.exists()) {
                                                isDownloadComplete = false
                                                downloadedFile = null
                                                downloadProgress = 0f
                                                return@AnimatedActionButton
                                            }
                                            if (!context.canRequestApkInstalls()) {
                                                context.openUnknownSourcesSettings()
                                                return@AnimatedActionButton
                                            }
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
                                            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "application/vnd.android.package-archive")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            runCatching {
                                                ContextCompat.startActivity(context, installIntent, null)
                                            }
                                        } else {
                                            enqueueApkDownload(
                                                context = context,
                                                apkUrl = currentStatus.apkUrl
                                                    ?: "https://github.com/NirmaLKumar26/Auric-Music/releases/download/${currentStatus.version}/auricmusic.apk",
                                                version = currentStatus.version,
                                                size = currentStatus.size,
                                            )
                                            isDownloading = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isDownloading || isDownloadComplete
                                )
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { ErrorSnackbar(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .widthIn(max = 700.dp)
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    val contentModifier = if (status is AuricUpdateStatus.Available) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.fillParentMaxSize()
                    }

                    Box(
                        modifier = contentModifier,
                        contentAlignment = Alignment.Center
                    ) {
                        when (val currentStatus = status) {
                            is AuricUpdateStatus.Checking -> {
                                androidx.compose.material3.ContainedLoadingIndicator(
                                    modifier = Modifier.size(64.dp)
                                )
                            }

                            is AuricUpdateStatus.NoUpdate -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.deployed_app_update),
                                        contentDescription = null,
                                        modifier = Modifier.size(120.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(
                                        text = stringResource(R.string.on_latest_version),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.current_version_v, currentStatus.version),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            is AuricUpdateStatus.Error -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.error),
                                        contentDescription = null,
                                        modifier = Modifier.size(120.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(
                                        text = currentStatus.message,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            is AuricUpdateStatus.Available -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.release_date_v, currentStatus.releaseDate),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.update_size_v, currentStatus.size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    if (!currentStatus.imageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = currentStatus.imageUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp)
                                                .clip(RoundedCornerShape(24.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                    }
                                    if (!currentStatus.description.isNullOrBlank()) {
                                        val annotatedText = currentStatus.description.parseMarkdown()

                                        ClickableText(
                                            text = annotatedText,
                                            onClick = { offset ->
                                                annotatedText.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                                                    ContextCompat.startActivity(context, Intent(Intent.ACTION_VIEW, Uri.parse(it.item)), null)
                                                }
                                            },
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 20.sp
                                            ),
                                            modifier = Modifier.padding(bottom = 24.dp)
                                        )
                                    }
                                    
                                    currentStatus.changelog.forEach { section ->
                                        Text(
                                            text = section.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                        )
                                        section.items.forEachIndexed { index, item ->
                                            val shape = when {
                                                section.items.size == 1 -> detachedItemShape()
                                                index == 0 -> leadingItemShape()
                                                index == section.items.size - 1 -> endItemShape()
                                                else -> middleItemShape()
                                            }
                                            ChangelogItem(text = item, shape = shape)
                                            if (index != section.items.size - 1) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                            }
                                        }
                                    }
                                    
                                    if (currentStatus.changelog.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(24.dp))
                                    }

                                    if (isDownloading) {
                                        if (downloadProgress > 0f) {
                                            androidx.compose.material3.LinearProgressIndicator(
                                                progress = downloadProgress,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                            )
                                        } else {
                                            androidx.compose.material3.LinearProgressIndicator(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(24.dp))
                                    }

                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}



const val PREFS_NAME = "settings"
const val KEY_AUTO_UPDATE_CHECK = "auto_update_check"
const val KEY_LAST_CHECKED_TIME = "last_checked_time"
const val KEY_BETA_UPDATES = "beta_updates"
const val KEY_UPDATE_AVAILABLE = "update_available"

fun getUpdateAvailableState(context: Context): Boolean {
    val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return sharedPrefs.getBoolean(KEY_UPDATE_AVAILABLE, false)
}

fun saveUpdateAvailableState(context: Context, available: Boolean) {
    val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    sharedPrefs.edit().putBoolean(KEY_UPDATE_AVAILABLE, available).apply()
}

fun getAutoUpdateCheckSetting(context: Context): Boolean {
    val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return sharedPrefs.getBoolean(KEY_AUTO_UPDATE_CHECK, true)
}

fun saveAutoUpdateCheckSetting(context: Context, enabled: Boolean) {
    val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    sharedPrefs.edit().putBoolean(KEY_AUTO_UPDATE_CHECK, enabled).apply()
}

const val KEY_UPDATE_NOTIFICATIONS = "update_notifications"

fun getUpdateNotificationsSetting(context: Context): Boolean {
    val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return sharedPrefs.getBoolean(KEY_UPDATE_NOTIFICATIONS, true)
}

fun saveUpdateNotificationsSetting(context: Context, enabled: Boolean) {
    val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    sharedPrefs.edit().putBoolean(KEY_UPDATE_NOTIFICATIONS, enabled).apply()
}

fun saveLastCheckedTime(context: Context, timestamp: String) {
    val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    sharedPrefs.edit().putString(KEY_LAST_CHECKED_TIME, timestamp).apply()
}

fun getLastCheckedTime(context: Context): String {
    val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return sharedPrefs.getString(KEY_LAST_CHECKED_TIME, "") ?: ""
}

fun getBetaUpdatesSetting(context: Context): Boolean {
    val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return sharedPrefs.getBoolean(KEY_BETA_UPDATES, false)
}

fun saveBetaUpdatesSetting(context: Context, enabled: Boolean) {
    val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    sharedPrefs.edit().putBoolean(KEY_BETA_UPDATES, enabled).apply()
}

private fun formatGitHubDate(githubDate: String): String = try {
    val githubFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    val displayFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, h:mm a")
    val dateTime = LocalDateTime.parse(githubDate, githubFormatter)
    dateTime.format(displayFormatter)
} catch (e: Exception) {
    githubDate
}


private const val GITHUB_REPO = "NirmaLKumar26/Auric-Music"
private const val GITHUB_USER_AGENT = "Auric-Music-App"
private const val GITHUB_API_VERSION = "2022-11-28"

fun enqueueApkDownload(
    context: Context,
    apkUrl: String,
    version: String,
    size: String,
) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val downloadRequest = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
        .setInputData(
            workDataOf(
                "apk_url" to apkUrl,
                "version" to version,
                "file_size" to size,
            )
        )
        .setConstraints(constraints)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            10,
            java.util.concurrent.TimeUnit.SECONDS,
        )
        .addTag("update_download")
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "update_download",
        ExistingWorkPolicy.REPLACE,
        downloadRequest,
    )
}

fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
    val latestVersionClean = latestVersion.removePrefix("b").removePrefix("v")
    val currentVersionClean = currentVersion.removePrefix("b").removePrefix("v")

    val latestParts = latestVersionClean.split(".").map { it.toIntOrNull() ?: 0 }
    val currentParts = currentVersionClean.split(".").map { it.toIntOrNull() ?: 0 }
    
    
    for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
        val latest = latestParts.getOrElse(i) { 0 }
        val current = currentParts.getOrElse(i) { 0 }
        when {
            latest > current -> return true
            latest < current -> return false
        }
    }
    
    
    if (latestVersionClean == currentVersionClean) {
        val latestIsBeta = latestVersion.startsWith("b")
        val currentIsBeta = currentVersion.startsWith("b")
        
        if (currentIsBeta && !latestIsBeta) return true
    }
    
    return false
}


private fun githubGet(url: String, accept: String = "application/vnd.github+json"): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 15_000
    connection.readTimeout = 15_000
    connection.setRequestProperty("User-Agent", GITHUB_USER_AGENT)
    connection.setRequestProperty("Accept", accept)
    connection.setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            error("GitHub HTTP $code: ${body.take(200)}")
        }
        return body
    } finally {
        connection.disconnect()
    }
}

private fun pickApkAsset(assets: JSONArray): JSONObject? {
    val apks = buildList {
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                add(asset)
            }
        }
    }
    if (apks.isEmpty()) return null
    fun nameOf(asset: JSONObject) = asset.optString("name").lowercase()
    return apks.firstOrNull { name ->
        val n = nameOf(name)
        (n.contains("arm64") || n.contains("foss") || n.contains("release")) && !n.contains("debug")
    } ?: apks.firstOrNull { !nameOf(it).contains("debug") }
        ?: apks.firstOrNull { nameOf(it).contains("arm64") }
        ?: apks.first()
}

private fun parseChangelogJson(json: String): Triple<List<ChangelogSection>, String?, String?> {
    val changelogData = JSONObject(json)
    val changelogList = mutableListOf<ChangelogSection>()
    val description = changelogData.optString("description").takeIf { it.isNotEmpty() }
    val imageUrl = changelogData.optString("image").takeIf { it.isNotEmpty() }
    val changelogArray = changelogData.optJSONArray("changelog")
    if (changelogArray != null) {
        for (j in 0 until changelogArray.length()) {
            val sectionObj = changelogArray.optJSONObject(j) ?: continue
            val title = sectionObj.optString("title")
            val itemsArray = sectionObj.optJSONArray("items") ?: continue
            val itemsList = mutableListOf<String>()
            for (k in 0 until itemsArray.length()) {
                itemsList.add(itemsArray.getString(k))
            }
            changelogList.add(ChangelogSection(title, itemsList))
        }
    }
    return Triple(changelogList, description, imageUrl)
}

suspend fun checkForUpdate(
    context: Context,
    onSuccess: (tag: String, isAvailable: Boolean, changelog: List<ChangelogSection>, size: String, date: String, description: String?, imageUrl: String?, apkUrl: String?) -> Unit,
    onError: () -> Unit,
) {
    withContext(Dispatchers.IO) {
        try {
            val includeBeta = getBetaUpdatesSetting(context)
            val targetRelease = if (includeBeta) {
                val releases = JSONArray(githubGet("https://api.github.com/repos/$GITHUB_REPO/releases?per_page=15"))
                (0 until releases.length())
                    .map { releases.getJSONObject(it) }
                    .firstOrNull { !it.optBoolean("draft", false) }
                    ?: error("No GitHub releases found")
            } else {
                JSONObject(githubGet("https://api.github.com/repos/$GITHUB_REPO/releases/latest"))
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val targetTagName = targetRelease.getString("tag_name")
            val shouldShow = isNewerVersion(targetTagName, currentVersion)

            if (shouldShow) {
                val changelogList = mutableListOf<ChangelogSection>()
                var description: String? = null
                var imageUrl: String? = null
                try {
                    val changelogJson = githubGet(
                        "https://github.com/$GITHUB_REPO/releases/download/$targetTagName/changelog.json",
                        accept = "application/json",
                    )
                    val parsed = parseChangelogJson(changelogJson)
                    changelogList.addAll(parsed.first)
                    description = parsed.second
                    imageUrl = parsed.third
                } catch (_: Exception) {
                    var body = targetRelease.optString("body", context.getString(R.string.no_changelog_available))
                    val imageRegex = Regex("!\\[(.*?)\\]\\((.*?)\\)")
                    val match = imageRegex.find(body)
                    if (match != null) {
                        imageUrl = match.groupValues[2]
                        body = body.replace(match.value, "").trim()
                    }
                    description = body
                }

                val publishedAt = targetRelease.getString("published_at")
                val formattedReleaseDate = formatGitHubDate(publishedAt)
                val apkAsset = pickApkAsset(targetRelease.optJSONArray("assets") ?: JSONArray())
                val apkDownloadUrl = apkAsset?.optString("browser_download_url").orEmpty()
                val apkSizeInMB = apkAsset?.optLong("size")?.let { bytes ->
                    String.format("%.1f", bytes / (1024.0 * 1024.0))
                }.orEmpty()

                if (apkDownloadUrl.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onSuccess(
                            targetTagName,
                            true,
                            changelogList,
                            apkSizeInMB,
                            formattedReleaseDate,
                            description,
                            imageUrl,
                            apkDownloadUrl,
                        )
                    }
                    return@withContext
                }
                Log.w("UpdateCheck", "GitHub release $targetTagName has no APK asset")
            }

            withContext(Dispatchers.Main) {
                onSuccess(currentVersion, false, emptyList(), "", "", null, null, null)
            }
        } catch (e: Exception) {
            Log.e("UpdateCheck", "Error checking for updates: ${e.message}", e)
            withContext(Dispatchers.Main) { onError() }
        }
    }
}
fun String.extractUrls(): List<Pair<IntRange, String>> {
    val urlPattern = Pattern.compile(
        "(?:^|[\\s])((https?://|www\\.|pic\\.)[\\w-]+(\\.[\\w-]+)+([/?].*)?)"
    )
    val matcher = urlPattern.matcher(this)
    val urlList = mutableListOf<Pair<IntRange, String>>()

    while (matcher.find()) {
        val url = matcher.group(1)?.trim() ?: continue
        val range = IntRange(matcher.start(1), matcher.end(1) - 1)
        
        val fullUrl = if (url.startsWith("http")) url else "https://$url"
        urlList.add(range to fullUrl)
    }

    return urlList
}

private fun Context.canRequestApkInstalls(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
    return try {
        packageManager.canRequestPackageInstalls()
    } catch (_: SecurityException) {
        false
    }
}

private fun Context.openUnknownSourcesSettings() {
    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
}
