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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import okhttp3.Credentials

/**
 * One screen, one WebView, plus a first-run welcome screen.
 *
 * The welcome screen is a plain onboarding page with instructions: the login itself always
 * happens inside the official OpenCode web client (scanning a pairing QR on the computer or
 * pasting its `opencode pair` link). It is also used instead of a bare error page whenever a
 * saved server cannot be reached, so the app never shows an empty screen.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: SettingsStore
    private lateinit var webView: WebView
    private lateinit var bundle: WebBundle
    private lateinit var progress: View
    private lateinit var welcomePanel: View
    private lateinit var connectStatus: TextView
    private lateinit var retryButton: View
    private lateinit var urlInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText

    private var loadedUrl: String? = null
    private var versionChecked = false
    private var webPermissionRequest: PermissionRequest? = null
    private var fileChooserCallback: android.webkit.ValueCallback<Array<Uri>>? = null

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchScanner()
        } else {
            Toast.makeText(this, R.string.camera_permission, Toast.LENGTH_LONG).show()
        }
    }

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

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@registerForActivityResult
        val payload = Pairing.parse(contents)
        if (payload != null) {
            beginOfficialPairing(payload)
            return@registerForActivityResult
        }
        val manual = normalizeServerUrl(contents)
        if (manual != null) {
            urlInput.setText(manual)
            Toast.makeText(this, R.string.discovery_found_manual, Toast.LENGTH_SHORT).show()
        } else {
            showPairingError(
                getString(R.string.pairing_invalid) + "\n\n" +
                    getString(R.string.pairing_scanned, contents.take(300)),
            )
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
        welcomePanel = findViewById(R.id.welcomePanel)
        connectStatus = findViewById(R.id.connectStatus)
        retryButton = findViewById(R.id.retryButton)
        urlInput = findViewById(R.id.urlInput)
        passwordInput = findViewById(R.id.passwordInput)

        configureWebView()

        findViewById<View>(R.id.scanButton).setOnClickListener { scanPairingCode() }
        findViewById<View>(R.id.connectButton).setOnClickListener { handleConnectInput() }
        findViewById<View>(R.id.openSettingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.helpButton).setOnClickListener { showHelp() }
        findViewById<View>(R.id.copyAgentButton).setOnClickListener { copyAgentInstruction() }
        retryButton.setOnClickListener {
            welcomePanel.isVisible = false
            webView.isVisible = true
            loadServer()
        }

        urlInput.setText(settings.serverUrl.takeIf { settings.isConfigured }.orEmpty())
        passwordInput.setText(settings.password)

        if (settings.isConfigured) {
            loadServer()
        } else {
            showWelcome(status = null, allowRetry = false)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (welcomePanel.isVisible) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else if (webView.canGoBack()) {
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
        if (settings.isConfigured) {
            if (settings.serverUrl != loadedUrl) {
                loadServer()
            }
            if (!versionChecked) {
                versionChecked = true
                lifecycleScope.launch {
                    bundle.checkForUpdates()
                }
            }
        } else {
            showWelcome(status = null, allowRetry = false)
        }
        if (settings.notificationsEnabled && settings.backgroundMonitoring) {
            MonitorService.start(this)
        }
    }

    override fun onPause() {
        ActiveSessionStore.appInForeground = false
        super.onPause()
    }

    private fun showWelcome(status: String?, allowRetry: Boolean) {
        welcomePanel.isVisible = true
        webView.isVisible = false
        progress.isVisible = false
        connectStatus.text = status ?: getString(R.string.welcome_status_no_server)
        retryButton.isVisible = allowRetry
    }

    private fun hideWelcome() {
        welcomePanel.isVisible = false
        webView.isVisible = true
    }

    private fun copyAgentInstruction() {
        runCatching {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText(
                    getString(R.string.copy_instruction),
                    getString(R.string.welcome_agent_instruction),
                )
            )
        }.onSuccess {
            Toast.makeText(this, R.string.instruction_copied, Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, R.string.pairing_invalid, Toast.LENGTH_SHORT).show()
        }
    }

    private fun scanPairingCode() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchScanner()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.scan_prompt))
            setBeepEnabled(false)
            setOrientationLocked(true)
            setBarcodeImageEnabled(false)
        }
        runCatching { scanLauncher.launch(options) }
            .onFailure { Toast.makeText(this, R.string.pairing_invalid, Toast.LENGTH_LONG).show() }
    }

    /**
     * Opens the official OpenCode connect screen and lets that screen redeem the code. The
     * previous flow redeemed the code natively, which consumed it before the web client could
     * establish its own signed-in server entry, so the web app still showed its login screen.
     */
    private fun beginOfficialPairing(payload: PairingPayload) {
        val target = Pairing.preferredServer(payload)
        if (target == null) {
            showPairingError(getString(R.string.pairing_loopback))
            return
        }

        // Do not inject the pairing code into the official login form. Let the web client's own
        // login page handle it; this avoids replacing the document and ending on a black screen.
        MonitorService.stop(this)
        settings.save(target, "opencode", "")
        urlInput.setText(target)
        passwordInput.setText("")
        welcomePanel.isVisible = false
        webView.isVisible = true
        loadServer()
    }

    private fun showPairingError(message: String) {
        if (isFinishing) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pairing_failed_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.help_title)
            .setMessage(R.string.help_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun handleConnectInput() {
        val raw = urlInput.text?.toString().orEmpty().trim()
        Pairing.parse(raw)?.let {
            beginOfficialPairing(it)
            return
        }
        if (raw.contains("/auth/connect/", ignoreCase = true) || raw.startsWith("{")) {
            Toast.makeText(this, R.string.pairing_invalid, Toast.LENGTH_LONG).show()
            return
        }

        val normalized = normalizeServerUrl(raw)
        if (normalized == null) {
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_LONG).show()
            return
        }
        settings.save(normalized, "opencode", passwordInput.text?.toString().orEmpty())
        welcomePanel.isVisible = false
        webView.isVisible = true
        loadServer()
        if (settings.notificationsEnabled && settings.backgroundMonitoring) {
            MonitorService.start(this)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun configureWebView() {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
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
                val scheme = target.scheme?.lowercase()
                if (scheme !in EXTERNAL_SCHEMES) return true
                return openExternally(target)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                progress.isVisible = true
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
                if (request.isForMainFrame && !isFinishing) {
                    val target = normalizeServerUrl(settings.serverUrl)
                        ?: return
                    showWelcome(
                        status = getString(R.string.welcome_status_unreachable, target),
                        allowRetry = true,
                    )
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
                progress.isVisible = newProgress < 100 && !welcomePanel.isVisible
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request ?: return
                val wantsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                if (!wantsCamera) {
                    request.deny()
                    return
                }
                // The official client can also scan pairing codes with its own camera UI.
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
            // The bundled login page can report a saved server entry made earlier (for example
            // a tunnel that has since been rotated). Only accept reports from servers that
            // actually answer right now.
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
        urlInput.setText(normalized)
        passwordInput.setText(token)
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
