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
import android.view.Gravity
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
import android.widget.LinearLayout
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Credentials
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var settings: SettingsStore
    private lateinit var webView: WebView
    private lateinit var bundle: WebBundle
    private lateinit var progress: View
    private lateinit var setupPanel: View
    private lateinit var offlinePanel: View
    private lateinit var urlInput: TextInputEditText
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var discoveryStatus: TextView
    private lateinit var discoveryResults: LinearLayout

    private var loadedUrl: String? = null
    private var versionChecked = false
    private var discoveryJob: Job? = null
    private var pendingConnectValue: String? = null
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
            beginOfficialPairing(contents, payload)
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
        setupPanel = findViewById(R.id.setupPanel)
        offlinePanel = findViewById(R.id.offlinePanel)
        urlInput = findViewById(R.id.urlInput)
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        discoveryStatus = findViewById(R.id.discoveryStatus)
        discoveryResults = findViewById(R.id.discoveryResults)

        configureWebView()

        findViewById<MaterialButton>(R.id.connectButton).setOnClickListener { saveAndConnect() }
        findViewById<MaterialButton>(R.id.retryButton).setOnClickListener {
            offlinePanel.isVisible = false
            loadServer()
        }
        findViewById<MaterialButton>(R.id.discoverButton).setOnClickListener { startDiscovery() }
        findViewById<MaterialButton>(R.id.scanButton).setOnClickListener { scanPairingCode() }
        findViewById<MaterialButton>(R.id.helpButton).setOnClickListener { showHelp() }
        findViewById<MaterialButton>(R.id.findServerButton).setOnClickListener {
            showSetup(runDiscovery = true)
        }

        urlInput.setText(settings.serverUrl)
        usernameInput.setText(settings.username)
        passwordInput.setText(settings.password)

        if (settings.isConfigured) {
            setupPanel.isVisible = false
            loadServer()
        } else {
            showSetup()
            startDiscovery()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
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

    private fun showSetup(runDiscovery: Boolean = false) {
        setupPanel.isVisible = true
        offlinePanel.isVisible = false
        progress.isVisible = false
        if (runDiscovery) startDiscovery()
    }

    private fun startDiscovery() {
        if (discoveryJob?.isActive == true) return

        val username = usernameInput.text?.toString()?.ifBlank { settings.username } ?: settings.username
        val password = passwordInput.text?.toString().orEmpty()
        discoveryResults.removeAllViews()
        discoveryStatus.isVisible = true
        discoveryStatus.text = getString(R.string.discovering)

        val discovery = ServerDiscovery(this, username, password)
        discoveryJob = lifecycleScope.launch {
            val found = try {
                discovery.discover { message ->
                    runOnUiThread {
                        if (!isFinishing) discoveryStatus.text = message
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (isFinishing) return@launch

            discoveryStatus.text = if (found.isEmpty()) {
                getString(R.string.discovery_none)
            } else {
                getString(R.string.discovery_found, found.size)
            }
            found.forEach(::addDiscoveryResult)
        }
    }

    private fun addDiscoveryResult(server: DiscoveredServer) {
        val source = when (server.source) {
            DiscoveredServer.Source.MDNS -> getString(R.string.source_mdns)
            DiscoveredServer.Source.LAN -> getString(R.string.source_lan)
            DiscoveredServer.Source.LOOPBACK -> getString(R.string.source_loopback)
        }
        val label = buildString {
            append(server.url)
            append("  ·  ")
            append(source)
            if (!server.version.isNullOrBlank()) {
                append("  ·  v")
                append(server.version)
            }
            if (server.requiresPassword) append("  ·  password")
        }
        val button = MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            text = label
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener { chooseServer(server) }
        }
        val margin = (8 * resources.displayMetrics.density).toInt()
        discoveryResults.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = margin },
        )
    }

    private fun chooseServer(server: DiscoveredServer) {
        urlInput.setText(server.url)
        if (server.requiresPassword && passwordInput.text.isNullOrEmpty()) {
            passwordInput.requestFocus()
            discoveryStatus.text = getString(R.string.discovery_auth, server.url)
            return
        }
        saveAndConnect()
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
    private fun beginOfficialPairing(raw: String, payload: PairingPayload) {
        val target = Pairing.preferredServer(payload)
        if (target == null) {
            val message = getString(R.string.pairing_loopback)
            discoveryStatus.isVisible = true
            discoveryStatus.text = message
            showPairingError(message)
            return
        }

        pendingConnectValue = raw.trim()
        discoveryJob?.cancel()
        MonitorService.stop(this)
        settings.save(target, "opencode", "")
        urlInput.setText(target)
        usernameInput.setText("opencode")
        passwordInput.setText("")
        setupPanel.isVisible = false
        offlinePanel.isVisible = false
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

    private fun saveAndConnect() {
        val raw = urlInput.text?.toString().orEmpty().trim()
        Pairing.parse(raw)?.let {
            beginOfficialPairing(raw, it)
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
        pendingConnectValue = null
        settings.save(normalized, usernameInput.text?.toString().orEmpty(), passwordInput.text?.toString().orEmpty())
        setupPanel.isVisible = false
        offlinePanel.isVisible = false
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
        webSettings.allowFileAccess = false
        webSettings.allowContentAccess = false
        webSettings.loadsImagesAutomatically = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webSettings.setSupportMultipleWindows(false)
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
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.setBackgroundColor(0xFF0B0F17.toInt())
        webView.isVerticalScrollBarEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (request.method != "GET") return null
                val serverHost = runCatching { Uri.parse(settings.serverUrl).host }.getOrNull()
                if (request.url.host != serverHost) return null
                return bundle.response(request.url, request.isForMainFrame)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url
                val serverHost = runCatching { Uri.parse(settings.serverUrl).host }.getOrNull()
                if (target.host == serverHost) return false
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
                    val pending = pendingConnectValue
                    pendingConnectValue = null
                    view.evaluateJavascript(
                        WebViewPatches.nativeBridge(JSONObject.quote(pending.orEmpty())),
                        null,
                    )
                    trackSession(url)
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
                val serverHost = runCatching { Uri.parse(settings.serverUrl).host }.getOrNull()
                if (host == serverHost && settings.password.isNotEmpty()) {
                    handler.proceed(settings.username, settings.password)
                } else {
                    handler.cancel()
                    if (host == serverHost) {
                        showSetup()
                        discoveryStatus.isVisible = true
                        discoveryStatus.text = getString(R.string.server_needs_password)
                    }
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
                if (!wantsCamera || !isServerOrigin(request.origin)) {
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
                val intent = fileChooserParams.createIntent().apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
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
                val request = DownloadManager.Request(Uri.parse(url))
                    .setMimeType(mimeType)
                    .addRequestHeader("User-Agent", userAgent)
                if (settings.password.isNotEmpty()) {
                    request.addRequestHeader(
                        "Authorization",
                        Credentials.basic(settings.username, settings.password)
                    )
                }
                CookieManager.getInstance().getCookie(url)?.let {
                    request.addRequestHeader("Cookie", it)
                }
                val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
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
                Uri.parse(config.url).host.orEmpty(),
                "",
                config.username,
                config.password,
            )
        }
        webView.loadUrl(config.url, headers)
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
        val serverHost = runCatching { Uri.parse(settings.serverUrl).host }.getOrNull() ?: return false
        return runCatching { Uri.parse(url).host }.getOrNull() == serverHost
    }

    private fun isServerOrigin(origin: Uri): Boolean {
        val serverHost = runCatching { Uri.parse(settings.serverUrl).host }.getOrNull() ?: return false
        return origin.host == serverHost
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
        // A pairing token works on every address of the same server. Keep the address the
        // WebView is actually using instead of switching to another one the official client
        // happened to pick, otherwise host matching for bundled assets and links breaks.
        if (!isServerUrl(webView.url)) return
        val normalized = webView.url?.let(::normalizeServerUrl)
            ?: normalizeServerUrl(url)
            ?: return
        if (settings.serverUrl == normalized && settings.password == token) return

        settings.save(normalized, "opencode", token)
        urlInput.setText(normalized)
        usernameInput.setText("opencode")
        passwordInput.setText(token)
        loadedUrl = normalized
        if (settings.notificationsEnabled && settings.backgroundMonitoring) {
            MonitorService.start(this)
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
        val SESSION_ID = Regex("[A-Za-z0-9_-]{1,200}")
    }
}
