package nirmal.auric.music.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
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
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue" +
        "%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%26hl%3Den%26app%3Ddesktop"

private const val VISITOR_JS = """
(function(){
  try {
    var visitor = null;
    var syncId = null;
    if (window.ytcfg && ytcfg.get) {
      visitor = ytcfg.get('VISITOR_DATA') || visitor;
      syncId = ytcfg.get('DATASYNC_ID') || syncId;
    }
    if (window.yt && yt.config_) {
      visitor = visitor || yt.config_.VISITOR_DATA;
      syncId = syncId || yt.config_.DATASYNC_ID;
    }
    if (visitor) Android.onRetrieveVisitorData(String(visitor));
    if (syncId) Android.onRetrieveDataSyncId(String(syncId));
  } catch (e) {}
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val coroutineScope = rememberCoroutineScope()
    val completing = remember { AtomicBoolean(false) }
    val visitorData = remember { AtomicReference("") }
    val dataSyncId = remember { AtomicReference("") }
    val webViewRef = remember { AtomicReference<WebView?>(null) }

    fun leaveLogin() {
        if (!navController.popBackStack() && !navController.navigateUp()) {
            navController.backToMain()
        }
    }

    DisposableEffect(activity) {
        val window = activity?.window
        val previousMode = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        onDispose {
            if (previousMode != null) {
                window.setSoftInputMode(previousMode)
            }
        }
    }

    fun persistAndFinish(cookie: String) {
        if (!completing.compareAndSet(false, true)) return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                YouTube.cookie = cookie
                val visitor = visitorData.get()
                val syncId = dataSyncId.get()
                YouTube.visitorData = visitor.ifBlank { YouTube.visitorData }
                YouTube.dataSyncId = syncId.ifBlank { YouTube.dataSyncId }

                val account = YouTube.accountInfo().getOrElse { error ->
                    throw error
                }

                context.dataStore.edit { settings ->
                    settings[InnerTubeCookieKey] = cookie
                    if (visitor.isNotBlank()) settings[VisitorDataKey] = visitor
                    if (syncId.isNotBlank()) settings[DataSyncIdKey] = syncId
                    settings[AccountNameKey] = account.name
                    settings[AccountEmailKey] = account.email.orEmpty()
                    settings[AccountChannelHandleKey] = account.channelHandle.orEmpty()
                }

                withContext(Dispatchers.Main) {
                    CookieManager.getInstance().flush()
                    Toast.makeText(context, context.getString(R.string.login_successful), Toast.LENGTH_SHORT).show()
                    navController.navigateUp()
                }
            } catch (e: Exception) {
                completing.set(false)
                Timber.e(e, "Google login validation failed")
                reportException(e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.login_failed), Toast.LENGTH_LONG).show()
                }
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
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    setBackgroundColor(Color.WHITE)
                    isNestedScrollingEnabled = false
                    val jsBridge = GoogleLoginJsBridge(
                        onVisitorData = { visitorData.set(it) },
                        onDataSyncId = { dataSyncId.set(it.substringBefore("||")) },
                    )
                    configureGoogleLoginWebView(
                        webView = this,
                        jsBridge = jsBridge,
                        onLoggedIn = ::persistAndFinish,
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

@SuppressLint("SetJavaScriptEnabled")
private fun configureGoogleLoginWebView(
    webView: WebView,
    jsBridge: GoogleLoginJsBridge,
    onLoggedIn: (String) -> Unit,
) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
        loadWithOverviewMode = true
        useWideViewPort = true
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        cacheMode = WebSettings.LOAD_DEFAULT
        userAgentString = chromeLikeUserAgent(userAgentString)
        hideWebViewRequestedWithHeader(this)
    }
    webView.webViewClient = GoogleLoginWebViewClient(onLoggedIn)
    webView.addJavascriptInterface(jsBridge, "Android")
    webView.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
}

private fun hideWebViewRequestedWithHeader(settings: WebSettings) {
    runCatching {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
        }
    }
}

private fun chromeLikeUserAgent(original: String): String =
    original
        .replace("; wv", "")
        .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")

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

private class GoogleLoginWebViewClient(
    private val onLoggedIn: (String) -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return shouldOverrideGoogleLoginUrl(request.url?.toString())
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
        return shouldOverrideGoogleLoginUrl(url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        view.evaluateJavascript(VISITOR_JS, null)
        if (!url.isYoutubeMusicSignedIn()) return
        val cookie = youtubeMusicCookies()
        if (cookie.hasSapisid()) {
            CookieManager.getInstance().flush()
            onLoggedIn(cookie)
        }
    }
}

private fun shouldOverrideGoogleLoginUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://")) return false
    return true
}

private fun String?.isYoutubeMusicSignedIn(): Boolean {
    val value = this ?: return false
    return value.startsWith("https://music.youtube.com") &&
        !value.contains("/signin") &&
        !value.contains("accounts.google.com")
}

private fun youtubeMusicCookies(): String {
    val cookieManager = CookieManager.getInstance()
    val merged = LinkedHashMap<String, String>()
    listOf(
        "https://music.youtube.com",
        "https://www.youtube.com",
        "https://youtube.com",
        "https://accounts.google.com",
    ).forEach { host ->
        cookieManager.getCookie(host)?.split(';')?.forEach { part ->
            val trimmed = part.trim()
            val eq = trimmed.indexOf('=')
            if (eq > 0) {
                merged[trimmed.substring(0, eq)] = trimmed.substring(eq + 1)
            }
        }
    }
    return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
}

private fun String.hasSapisid(): Boolean =
    split(';').any { part ->
        val name = part.trim().substringBefore('=').uppercase()
        name == "SAPISID" || name == "__SECURE-1PSID" || name == "__SECURE-3PSID"
    }
