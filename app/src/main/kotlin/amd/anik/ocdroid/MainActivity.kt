package amd.anik.ocdroid

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.launch
import okhttp3.Credentials

/**
 * One screen, one WebView. There is no custom login page: when no server is configured the
 * app opens the official web client's own "Connect to a server" page (bundled in the APK),
 * and every setting except the server token lives in the hidden SettingsActivity.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: SettingsStore
    private lateinit var webView: WebView
    private lateinit var bundle: WebBundle
    private lateinit var progress: View
    private lateinit var offlinePanel: View

    private var loadedUrl: String? = null
    private var versionChecked = false
    private var webPermissionRequest: PermissionRequest? = null
    private var fileChooserCallback: android.webkit.ValueCallback<Array<Uri>>? = null

    private val webCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = webPermissionRequest ?: return@registerForActivityResult
        webPermissionRequest = null
        if (granted && !isFinishing) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        } else {
            request.deny()
            Toast.makeText(this, R.string.camera_permission, Toast.LENGTH_LONG).show()
        }
    }

    private val fileChooser = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileChooserCallback ?: return@registerForActivityResult
        fileChooserCallback = null
        callback.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        webView.addJavascriptInterface(NativeBridge(), "OCDroidNative")
        bundle = WebBundle(this, settings)
        progress = findViewById(R.id.progress)
        offlinePanel = findViewById(R.id.offlinePanel)

        configureWebView()

        findViewById<View>(R.id.retryButton).setOnClickListener {
            offlinePanel.isVisible = false
            if (settings.isConfigured) loadServer() else openLoginPage()
        }

        if (settings.isConfigured) {
            loadServer()
        } else {
            openLoginPage()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Never leave the bundled login page: there is nothing to go back to.
                if (webView.canGoBack() && !isLoginPage(webView.url)) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        ActiveSessionStore.appInForeground = true
        ActiveSessionStore.activeSessionId?.let {
            MonitorService.cancelSessionNotifications(this, it)
        }
        if (settings.isConfigured && settings.serverUrl != loadedUrl) {
            loadServer()
        }
        if (!versionChecked && settings.isConfigured) {
            versionChecked = true
            lifecycleScope.launch {
                bundle.checkForUpdates()
            }
        }
        if (settings.notificationsEnabled && settings.backgroundMonitoring) {
            MonitorService.start(this)
        }
    }

    override fun onPause() {
        ActiveSessionStore.appInForeground = false
        super.onPause()
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun configureWebView() {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        // The bundled login page is our own asset, loaded from file:///android_asset/web/.
        // file access must be on for that page to open at all; universal access lets the
        // official client redeem pairing codes cross-origin when it lives on file://.
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = false
        webSettings.loadsImagesAutomatically = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webSettings.setSupportMultipleWindows(false)
        webSettings.javaScriptCanOpenWindowsAutomatically = false
        webSettings.setGeolocationEnabled(false)
        webSettings.allowFileAccessFromFileURLs = false
        webSettings.allowUniversalAccessFromFileURLs = true
        webSettings.builtInZoomControls = false
        webSettings.displayZoomControls = false
        webSettings.textZoom = 100
        webSettings.userAgentString = webSettings.userAgentString.replace("; wv", "")

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, true)
        }
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.setBackgroundColor(0xFF0B0F17.toInt())
        webView.isVerticalScrollBarEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (request.method != "GET") return null
                if (!isServerUrl(request.url.toString())) return null
                return bundle.response(request.url, request.isForMainFrame)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url
                if (isServerUrl(target.toString())) return false
                if (isLoginPage(target.toString())) return false
                val scheme = target.scheme?.lowercase()
                if (scheme !in EXTERNAL_SCHEMES) return true
                return openExternally(target)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                progress.isVisible = true
                offlinePanel.isVisible = false
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.isVisible = false
                // Re-applied per document; the SPA keeps this listener across in-app navigation.
                view.evaluateJavascript(WebViewPatches.ENTER_INSERTS_NEWLINE, null)
                if (isServerUrl(url)) {
                    view.evaluateJavascript(WebViewPatches.COMMAND_PALETTE, null)
                    view.evaluateJavascript(WebViewPatches.nativeBridge, null)
                    trackSession(url)
                } else if (isLoginPage(url)) {
                    // The bundled login page lives on file:// but must still report the
                    // server entry the official client saved while the user signed in.
                    view.evaluateJavascript(WebViewPatches.nativeBridge, null)
                }
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                trackSession(url)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    progress.isVisible = false
                    offlinePanel.isVisible = true
                }
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView,
                handler: android.webkit.HttpAuthHandler,
                host: String,
                realm: String
            ) {
                val serverHost = ServerOrigin.host(settings.serverUrl)
                if (host.equals(serverHost, ignoreCase = true) && settings.password.isNotEmpty()) {
                    handler.proceed(settings.username, settings.password)
                } else {
                    // No stored credentials: let the official client's own login flow run.
                    handler.cancel()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.isVisible = newProgress < 100
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request ?: return
                val wantsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                if (!wantsCamera) {
                    request.deny()
                    return
                }
                // The bundled login page is served from file:// when no server is configured.
                // Allow its QR scanner to request the camera too.
                if (!isServerOrigin(request.origin) && request.origin.scheme != "file") {
                    request.deny()
                    return
                }
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                } else {
                    webPermissionRequest?.deny()
                    webPermissionRequest = request
                    webCameraPermission.launch(Manifest.permission.CAMERA)
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                if (webPermissionRequest === request) webPermissionRequest = null
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: android.webkit.ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                val intent = if (fileChooserParams.isCaptureEnabled) {
                    Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                } else {
                    fileChooserParams.createIntent().apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                }
                return try {
                    fileChooser.launch(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    fileChooserCallback = null
                    false
                }
            }
        }

        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            runCatching {
                val downloadUri = Uri.parse(url)
                val scheme = downloadUri.scheme?.lowercase()
                if (scheme != "http" && scheme != "https") {
                    Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_LONG).show()
                } else {
                    val request = DownloadManager.Request(downloadUri)
                        .setMimeType(mimeType)
                        .addRequestHeader("User-Agent", userAgent)
                    // Never send the server token or cookies to an arbitrary download host. The
                    // official client can link build artifacts or attachments from a CDN.
                    if (isServerUrl(url)) {
                        if (settings.password.isNotEmpty()) {
                            request.addRequestHeader(
                                "Authorization",
                                Credentials.basic(settings.username, settings.password)
                            )
                        }
                        CookieManager.getInstance().getCookie(url)?.let {
                            request.addRequestHeader("Cookie", it)
                        }
                    }
                    val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    manager.enqueue(request)
                }
            }.onFailure {
                Toast.makeText(this, R.string.offline_title, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun loadServer() {
        val config = settings.config
        loadedUrl = config.url
        val headers = mutableMapOf<String, String>()
        if (config.password.isNotEmpty()) {
            headers["Authorization"] = Credentials.basic(config.username, config.password)
        }
        runCatching {
            webView.setHttpAuthUsernamePassword(
                ServerOrigin.host(config.url).orEmpty(),
                "",
                config.username,
                config.password,
            )
        }
        webView.loadUrl(config.url, headers)
    }

    /**
     * Opens the official web client's own login page. Without a configured server the page
     * is bundled in the APK, so scanning a pairing QR code with the camera works even before
     * any server address is known. The official client owns everything on that page.
     */
    private fun openLoginPage() {
        loadedUrl = null
        webView.loadUrl(LOGIN_PAGE_URL)
    }

    private fun isLoginPage(url: String?): Boolean {
        return url != null && url.startsWith("file:///android_asset/web/")
    }

    private fun openExternally(uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        return try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun isServerUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return ServerOrigin.same(settings.serverUrl, url)
    }

    private fun isServerOrigin(origin: Uri): Boolean {
        return ServerOrigin.same(settings.serverUrl, origin.toString())
    }

    /**
     * The official client keeps the open conversation in its URL. Mirroring that here lets the
     * monitor suppress a notification for the conversation the user is reading, and lets the
     * activity dismiss an older notification when any conversation is opened.
     */
    private fun trackSession(url: String?) {
        val sessionId = SessionRoutes.sessionId(url)
        if (sessionId == null) {
            ActiveSessionStore.activeSessionId = null
            return
        }
        ActiveSessionStore.activeSessionId = sessionId
        MonitorService.cancelSessionNotifications(this, sessionId)
    }

    private fun handleServerCredentials(url: String, token: String) {
        if (isServerUrl(webView.url)) {
            // The page runs from the server itself; the token belongs to it. Keep the address
            // the WebView is actually using instead of switching to another one the official
            // client happened to pick, otherwise host matching for bundled assets breaks.
            val normalized = webView.url?.let(::normalizeServerUrl)
                ?: normalizeServerUrl(url)
                ?: return
            finishSignIn(normalized, token)
        } else {
            // The bundled login page runs from file:// and reports whoever the official client
            // just stored there. Its storage survives reloads, so the list can hold a stale
            // entry (a tunnel that was rotated, a laptop that went offline); only accept the
            // report when that server actually answers right now.
            val normalized = normalizeServerUrl(url) ?: return
            lifecycleScope.launch {
                if (!Pairing.reachable(normalized)) return@launch
                if (isFinishing || isDestroyed) return@launch
                finishSignIn(normalized, token)
            }
        }
    }

    private fun finishSignIn(normalized: String, token: String) {
        if (settings.serverUrl == normalized && settings.password == token) return
        settings.save(normalized, "opencode", token)
        loadedUrl = normalized
        if (settings.notificationsEnabled && settings.backgroundMonitoring) {
            MonitorService.start(this)
        }
        if (!isServerUrl(webView.url)) {
            // Hand the fresh token to the server copy of the web client. The official client
            // consumes auth_token at boot and strips it from the URL itself. No Basic auth
            // header here: relaying page loads through it produced black screens before.
            val target = normalized.trimEnd('/') +
                "/?auth_token=" + java.net.URLEncoder.encode(token, "UTF-8")
            runCatching { webView.loadUrl(target) }
        }
    }

    /**
     * Only these three operations are exposed. They cannot read arbitrary data or navigate the
     * WebView; the page can merely report the server token and the current conversation.
     */
    inner class NativeBridge {
        @JavascriptInterface
        fun onServerConnected(url: String?, token: String?) {
            if (url.isNullOrBlank() || token.isNullOrBlank()) return
            runOnUiThread {
                if (!isFinishing && !isDestroyed) handleServerCredentials(url, token)
            }
        }

        @JavascriptInterface
        fun onSessionOpened(sessionId: String?) {
            val id = sessionId
                ?.take(200)
                ?.takeIf { SESSION_ID.matches(it) }
                ?: return
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                ActiveSessionStore.activeSessionId = id
                MonitorService.cancelSessionNotifications(this@MainActivity, id)
            }
        }

        @JavascriptInterface
        fun onSessionClosed() {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) ActiveSessionStore.activeSessionId = null
            }
        }
    }

    override fun onDestroy() {
        ActiveSessionStore.appInForeground = false
        ActiveSessionStore.activeSessionId = null
        webPermissionRequest?.deny()
        webPermissionRequest = null
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        webView.removeJavascriptInterface("OCDroidNative")
        webView.destroy()
        super.onDestroy()
    }

    private companion object {
        const val LOGIN_PAGE_URL = "file:///android_asset/web/index.html"
        val SESSION_ID = Regex("[A-Za-z0-9_-]{1,200}")
        val EXTERNAL_SCHEMES = setOf("http", "https", "mailto", "tel")
    }
}
