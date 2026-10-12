package nirmal.auric.music.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.music.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nirmal.auric.music.R
import nirmal.auric.music.constants.AccountChannelHandleKey
import nirmal.auric.music.constants.AccountEmailKey
import nirmal.auric.music.constants.AccountNameKey
import nirmal.auric.music.constants.DataSyncIdKey
import nirmal.auric.music.constants.InnerTubeCookieKey
import nirmal.auric.music.constants.VisitorDataKey
import nirmal.auric.music.ui.component.IconButton
import nirmal.auric.music.ui.utils.backToMain
import nirmal.auric.music.utils.dataStore
import nirmal.auric.music.utils.reportException
import timber.log.Timber
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val YOUTUBE_MUSIC_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

private data class PendingLogin(
    val cookie: String,
    val visitorData: String,
    val dataSyncId: String,
    val accountName: String,
    val accountEmail: String,
    val accountChannelHandle: String,
)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hasCompletedLogin = remember { AtomicBoolean(false) }
    val webViewRef = remember { AtomicReference<WebView?>(null) }
    var pendingLogin by remember { mutableStateOf<PendingLogin?>(null) }

    fun leaveLogin() {
        if (!navController.popBackStack() && !navController.navigateUp()) {
            navController.backToMain()
        }
    }

    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    fun confirmLogin(login: PendingLogin) {
        coroutineScope.launch(Dispatchers.IO) {
            context.dataStore.edit { settings ->
                settings[InnerTubeCookieKey] = login.cookie
                if (login.visitorData.isNotBlank()) settings[VisitorDataKey] = login.visitorData
                if (login.dataSyncId.isNotBlank()) settings[DataSyncIdKey] = login.dataSyncId
                settings[AccountNameKey] = login.accountName
                settings[AccountEmailKey] = login.accountEmail
                settings[AccountChannelHandleKey] = login.accountChannelHandle
            }
            withContext(Dispatchers.Main) {
                webViewRef.get()?.apply {
                    stopLoading()
                    clearHistory()
                    clearCache(true)
                    clearFormData()
                }
                restartApp()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login)) },
                navigationIcon = {
                    IconButton(
                        onClick = { leaveLogin() },
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
        bottomBar = {
            pendingLogin?.let { login ->
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.confirm_youtube_music_account),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = login.accountEmail.ifBlank { login.accountName },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                        )
                        Button(
                            onClick = { confirmLogin(login) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.continue_with_account, login.accountName))
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (url?.startsWith("https://music.youtube.com") != true) return
                            if (hasCompletedLogin.get()) return

                            val cookie = CookieManager.getInstance().getCookie(url)
                                ?: CookieManager.getInstance().getCookie("https://music.youtube.com")
                            if (cookie.isNullOrBlank()) return
                            if (!hasCompletedLogin.compareAndSet(false, true)) return

                            view.evaluateJavascript(YT_MUSIC_SESSION_PROBE_SCRIPT) { rawProbe ->
                                val probe = parseYtMusicSessionProbe(rawProbe)
                                if (probe?.loggedIn != true || "SAPISID=" !in cookie) {
                                    hasCompletedLogin.set(false)
                                    return@evaluateJavascript
                                }
                                coroutineScope.launch(Dispatchers.IO) {
                                    val syncId = probe.delegatedSessionId
                                        ?: probe.dataSyncId?.substringBefore("||")
                                        ?: ""
                                    YouTube.cookie = cookie
                                    YouTube.dataSyncId = syncId.ifBlank { YouTube.dataSyncId }
                                    YouTube.visitorData = probe.visitorData.orEmpty()

                                    Timber.d("Login: validating selected YouTube Music account")
                                    YouTube.accountInfo().onSuccess { account ->
                                        withContext(Dispatchers.Main) {
                                            pendingLogin = PendingLogin(
                                                cookie = cookie,
                                                visitorData = probe.visitorData.orEmpty(),
                                                dataSyncId = syncId,
                                                accountName = account.name,
                                                accountEmail = account.email.orEmpty(),
                                                accountChannelHandle = account.channelHandle.orEmpty(),
                                            )
                                        }
                                    }.onFailure { error ->
                                        hasCompletedLogin.set(false)
                                        Timber.e(error, "Login: Authentication validation failed")
                                        reportException(error)
                                    }
                                }
                            }
                        }
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    webViewRef.set(this)
                    loadUrl(YOUTUBE_MUSIC_LOGIN_URL)
                }
            },
            update = {},
            onRelease = { view ->
                if (webViewRef.get() === view) {
                    webViewRef.set(null)
                }
                releaseLoginWebView(view)
            },
        )
    }

    BackHandler {
        val current = webViewRef.get()
        if (current?.canGoBack() == true) {
            current.goBack()
        } else {
            leaveLogin()
        }
    }
}

private val releasedLoginWebViews =
    Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<WebView, Boolean>()))

private fun releaseLoginWebView(view: WebView?) {
    if (view == null || !releasedLoginWebViews.add(view)) return
    runCatching {
        view.stopLoading()
        view.removeJavascriptInterface("Android")
        view.webChromeClient = null
        view.webViewClient = WebViewClient()
        view.loadUrl("about:blank")
    }
}
