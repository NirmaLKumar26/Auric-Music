package nirmal.auric.music.ui.screens.settings

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.navigation.NavController
import nirmal.auric.music.LocalPlayerAwareWindowInsets
import nirmal.auric.music.R
import nirmal.auric.music.ui.component.IconButton
import nirmal.auric.music.ui.component.Material3SettingsGroup
import nirmal.auric.music.ui.component.Material3SettingsItem
import nirmal.auric.music.auricmusic.component.UpdateInfoDialog
import nirmal.auric.music.ui.utils.backToMain
import nirmal.auric.music.auricmusic.updater.getAutoUpdateCheckSetting
import nirmal.auric.music.auricmusic.updater.saveAutoUpdateCheckSetting
import nirmal.auric.music.auricmusic.updater.getUpdateAvailableState
import nirmal.auric.music.auricmusic.updater.saveUpdateAvailableState
import nirmal.auric.music.auricmusic.updater.getUpdateNotificationsSetting
import nirmal.auric.music.auricmusic.updater.saveUpdateNotificationsSetting
import android.widget.Toast
import androidx.compose.ui.res.pluralStringResource
import nirmal.auric.music.auricmusic.updater.getDownloadedApkCount
import nirmal.auric.music.auricmusic.updater.clearDownloadedApks
import nirmal.auric.music.auricmusic.updater.getBetaUpdatesSetting
import nirmal.auric.music.auricmusic.updater.saveBetaUpdatesSetting
import nirmal.auric.music.auricmusic.updater.autoClearOldApks
import androidx.compose.material3.MaterialTheme
import nirmal.auric.music.BuildConfig
import org.json.JSONObject

data class UpcomingUpdateData(
    val version: String,
    val releaseDate: String,
    val features: List<String>,
    val bugFixes: List<String>,
    val contributors: List<String>
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, highlightKey: String? = null) {
    val scrollState = androidx.compose.foundation.rememberScrollState()

    val context = LocalContext.current
    var autoUpdateEnabled by remember { mutableStateOf(getAutoUpdateCheckSetting(context)) }
    var updateNotificationsEnabled by remember { mutableStateOf(getUpdateNotificationsSetting(context)) }
    var betaUpdatesEnabled by remember { mutableStateOf(getBetaUpdatesSetting(context)) }
    val isUpdateAvailable = getUpdateAvailableState(context) && autoUpdateEnabled
    var apkCount by remember { mutableStateOf(getDownloadedApkCount(context)) }
    var showInfoDialog by remember { mutableStateOf(false) }

    var upcomingUpdate by remember { mutableStateOf<UpcomingUpdateData?>(null) }
    var isLoadingUpcoming by remember { mutableStateOf(false) }
    var showUpcomingDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        autoClearOldApks(context)
        apkCount = getDownloadedApkCount(context)

        isLoadingUpcoming = true
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val text = java.net.URL("https://raw.githubusercontent.com/NirmaLKumar26/Auric-Music/main/upcomingupdate.json?t=${System.currentTimeMillis()}").readText()
                val parsed = org.json.JSONObject(text).getJSONObject("upcoming_update")
                val features = mutableListOf<String>()
                val fArray = parsed.optJSONArray("features")
                if (fArray != null) for (i in 0 until fArray.length()) features.add(fArray.getString(i))
                
                val bugs = mutableListOf<String>()
                val bArray = parsed.optJSONArray("bug_fixes")
                if (bArray != null) for (i in 0 until bArray.length()) bugs.add(bArray.getString(i))
                
                val contribs = mutableListOf<String>()
                val cArray = parsed.optJSONArray("contributors")
                if (cArray != null) for (i in 0 until cArray.length()) contribs.add(cArray.getString(i))
                
                upcomingUpdate = UpcomingUpdateData(
                    version = parsed.optString("version", "Next Release"),
                    releaseDate = parsed.optString("release_date", "TBD"),
                    features = features,
                    bugFixes = bugs,
                    contributors = contribs
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoadingUpcoming = false
        }
    }

    if (showInfoDialog) {
        UpdateInfoDialog(onDismiss = { showInfoDialog = false })
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(scrollState = scrollState, 
            title = stringResource(R.string.app_updates_title),
            items = listOf(
                Material3SettingsItem(
    isHighlighted = (highlightKey == stringResource(R.string.system_update)),
                    icon = painterResource(R.drawable.update),
                    title = { Text(stringResource(R.string.system_update)) },
                    description = {
                        if (isUpdateAvailable) {
                            Text(
                                text = stringResource(R.string.update_available),
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(stringResource(R.string.app_update_uptodate))
                        }
                    },
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://auricmusic.tndev.in"))
                        context.startActivity(intent)
                    }
                ),
                Material3SettingsItem(
    isHighlighted = (highlightKey == stringResource(R.string.version, BuildConfig.VERSION_NAME)),
                    icon = painterResource(R.drawable.info),
                    title = {
                        Text(stringResource(R.string.version, BuildConfig.VERSION_NAME))
                    }
                ),
                
                Material3SettingsItem(
    isHighlighted = (highlightKey == stringResource(R.string.auto_update_check)),
                    icon = painterResource(R.drawable.update),
                    title = { Text(stringResource(R.string.auto_update_check)) },
                    description = { Text(stringResource(R.string.auto_update_check_subtitle)) },
                    trailingContent = {
                        Switch(
                            checked = autoUpdateEnabled,
                            onCheckedChange = { enabled ->
                                autoUpdateEnabled = enabled
                                saveAutoUpdateCheckSetting(context, enabled)
                                if (!enabled) {
                                    saveUpdateAvailableState(context, false)
                                }
                            },
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (autoUpdateEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = {
                        autoUpdateEnabled = !autoUpdateEnabled
                        saveAutoUpdateCheckSetting(context, autoUpdateEnabled)
                        if (!autoUpdateEnabled) {
                            saveUpdateAvailableState(context, false)
                        }
                    }
                ),

                Material3SettingsItem(
    isHighlighted = (highlightKey == stringResource(R.string.update_notifications)),
                    icon = painterResource(R.drawable.notification),
                    title = { Text(stringResource(R.string.update_notifications)) },
                    description = { Text(stringResource(R.string.update_notifications_subtitle)) },
                    trailingContent = {
                        Switch(
                            checked = updateNotificationsEnabled,
                            onCheckedChange = { enabled ->
                                updateNotificationsEnabled = enabled
                                saveUpdateNotificationsSetting(context, enabled)
                            },
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (updateNotificationsEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = {
                        updateNotificationsEnabled = !updateNotificationsEnabled
                        saveUpdateNotificationsSetting(context, updateNotificationsEnabled)
                    }
                ),






            )
        )
        
        

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(scrollState = scrollState, 
            title = stringResource(R.string.commits),
            items = listOf(
                Material3SettingsItem(
    isHighlighted = (highlightKey == stringResource(R.string.commits)),
                    icon = painterResource(R.drawable.commit),
                    title = { Text(stringResource(R.string.commits)) },
                    description = { Text(stringResource(R.string.view_commit_history)) },
                    onClick = { navController.navigate("settings/commits") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(scrollState = scrollState, 
            title = "Upcoming Update",
            items = listOf(
                Material3SettingsItem(
                    isHighlighted = false,
                    icon = painterResource(R.drawable.update),
                    title = { Text(upcomingUpdate?.version ?: "Next Release") },
                    description = {
                        if (isLoadingUpcoming) {
                            Text("Fetching upcoming update info...")
                        } else if (upcomingUpdate != null) {
                            Text("Release Date: ${upcomingUpdate!!.releaseDate}\nTap to view details")
                        } else {
                            Text("Failed to load upcoming update.")
                        }
                    },
                    onClick = {
                        if (upcomingUpdate != null) {
                            showUpcomingDialog = true
                        }
                    }
                )
            )
        )

        if (showUpcomingDialog && upcomingUpdate != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showUpcomingDialog = false },
                title = { Text(upcomingUpdate!!.version) },
                text = {
                    Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        Text("Release Date: ${upcomingUpdate!!.releaseDate}", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        
                        if (upcomingUpdate!!.features.isNotEmpty()) {
                            Text("Features:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            upcomingUpdate!!.features.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                            Spacer(Modifier.height(8.dp))
                        }
                        
                        if (upcomingUpdate!!.bugFixes.isNotEmpty()) {
                            Text("Bug Fixes:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            upcomingUpdate!!.bugFixes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                            Spacer(Modifier.height(8.dp))
                        }
                        
                        if (upcomingUpdate!!.contributors.isNotEmpty()) {
                            Text("Contributors:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            upcomingUpdate!!.contributors.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showUpcomingDialog = false }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.update_settings_title)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}
