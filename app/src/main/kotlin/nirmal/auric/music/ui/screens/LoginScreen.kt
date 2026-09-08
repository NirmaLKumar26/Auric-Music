package nirmal.auric.music.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.music.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hasCompletedLogin = remember { AtomicBoolean(false) }
    val visitorData = remember { AtomicReference("") }
    val dataSyncId = remember { AtomicReference("") }
    val webViewRef = remember { AtomicReference<WebView?>(null) }

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
                            view.loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                            view.loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")

                            if (url?.startsWith("https://music.youtube.com") != true) return
                            if (hasCompletedLogin.get()) return

                            val cookie = CookieManager.getInstance().getCookie(url)
                                ?: CookieManager.getInstance().getCookie("https://music.youtube.com")
                            if (cookie.isNullOrBlank()) return
                            if (!hasCompletedLogin.compareAndSet(false, true)) return

                            coroutineScope.launch(Dispatchers.IO) {
                                delay(500)
                                YouTube.cookie = cookie
                                YouTube.dataSyncId = dataSyncId.get().ifBlank { YouTube.dataSyncId }
                                YouTube.visitorData = visitorData.get().ifBlank { YouTube.visitorData }

                                Timber.d("Login: YouTube object initialized, validating...")

                                YouTube.accountInfo().onSuccess { account ->
                                    context.dataStore.edit { settings ->
                                        settings[InnerTubeCookieKey] = cookie
                                        val visitor = visitorData.get()
                                        val syncId = dataSyncId.get()
                                        if (visitor.isNotBlank()) settings[VisitorDataKey] = visitor
                                        if (syncId.isNotBlank()) settings[DataSyncIdKey] = syncId
                                        settings[AccountNameKey] = account.name
                                        settings[AccountEmailKey] = account.email.orEmpty()
                                        settings[AccountChannelHandleKey] = account.channelHandle.orEmpty()
                                    }
                                    Timber.d("Login: Successfully logged in as ${account.name}, restarting app...")
                                    withContext(Dispatchers.Main) {
                                        webViewRef.get()?.apply {
                                            stopLoading()
                                            clearHistory()
                                            clearCache(true)
                                            clearFormData()
                                        }
                                        restartApp()
                                    }
                                }.onFailure { error ->
                                    hasCompletedLogin.set(false)
                                    Timber.e(error, "Login: Authentication validation failed")
                                    reportException(error)
                                }
                            }
                        }
                    }
                    addJavascriptInterface(
                        GoogleLoginJsBridge(
                            onVisitorData = { visitorData.set(it) },
                            onDataSyncId = { dataSyncId.set(it.substringBefore("||")) },
                        ),
                        "Android",
                    )
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

private class GoogleLoginJsBridge(
    private val onVisitorData: (String) -> Unit,
    private val onDataSyncId: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onRetrieveVisitorData(newVisitorData: String?) {
        if (newVisitorData.isNullOrBlank() || newVisitorData == "undefined") return
        mainHandler.post { onVisitorData(newVisitorData) }
    }

    @JavascriptInterface
    fun onRetrieveDataSyncId(newDataSyncId: String?) {
        if (newDataSyncId.isNullOrBlank() || newDataSyncId == "undefined") return
        mainHandler.post { onDataSyncId(newDataSyncId) }
    }
}
