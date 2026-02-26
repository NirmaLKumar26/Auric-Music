package nirmal.auric.music.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nirmal.auric.music.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * In-app dialog that appears when an update is detected automatically.
 * Lets the user download the APK with a progress bar and then trigger the
 * system package installer, without leaving the app.
 *
 * @param version   The new version string (e.g. "1.2.3")
 * @param apkUrl    Direct download URL for the release APK from GitHub assets
 * @param onDismiss Called when the user taps "Later" or dismisses the dialog
 */
@Composable
fun AutoUpdateDialog(
    version: String,
    apkUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedApkUri by remember { mutableStateOf<Uri?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun startDownload() {
        isDownloading = true
        errorMessage = null
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(apkUrl).build()
                val response = client.newCall(request).execute()
                val body = response.body
                    ?: throw IllegalStateException("Empty response body")

                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val apkFile = File(context.cacheDir, "auric_update.apk")
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                        withContext(Dispatchers.Main) { downloadProgress = progress }
                    }
                }
                outputStream.close()
                inputStream.close()

                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.FileProvider",
                    apkFile
                )
                withContext(Dispatchers.Main) {
                    downloadedApkUri = apkUri
                    isDownloading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    errorMessage = e.localizedMessage ?: "Unknown error"
                }
            }
        }
    }

    fun installApk(uri: Uri) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        icon = {
            Icon(
                painter = painterResource(R.drawable.update),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(text = "Update Available") },
        text = {
            Column {
                Text(
                    text = "Auric Music $version is available. Download and install it now?",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (isDownloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Downloading… ${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Download failed: $errorMessage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        downloadedApkUri != null -> installApk(downloadedApkUri!!)
                        !isDownloading -> startDownload()
                    }
                },
                enabled = !isDownloading,
            ) {
                Text(
                    text = when {
                        downloadedApkUri != null -> "Install Now"
                        isDownloading -> "Downloading…"
                        else -> "Download & Install"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDownloading,
            ) {
                Text("Later")
            }
        },
    )
}
