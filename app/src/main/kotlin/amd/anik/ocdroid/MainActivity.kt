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
    private var pairingInProgress = false
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

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@registerForActivityResult
        val payload = Pairing.parse(contents)
        if (payload != null) {
            startPairing(payload)
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
                        if (!isFinishing && !pairingInProgress) discoveryStatus.text = message
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (isFinishing || pairingInProgress) return@launch

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
            setOrientationLocked(false)
            setBarcodeImageEnabled(false)
        }
        runCatching { scanLauncher.launch(options) }
            .onFailure { Toast.makeText(this, R.string.pairing_invalid, Toast.LENGTH_LONG).show() }
    }

    private fun startPairing(payload: PairingPayload) {
        pairingInProgress = true
        setupPanel.isVisible = true
        offlinePanel.isVisible = false
        discoveryStatus.isVisible = true
        discoveryStatus.text = getString(R.string.pairing_progress)
        setDiscoveryBusy(true)
        discoveryJob?.cancel()

        lifecycleScope.launch {
            val result = try {
                Pairing.redeem(payload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PairingResult.Unreachable
            }
            pairingInProgress = false
            setDiscoveryBusy(false)
            when (result) {
                is PairingResult.Paired -> {
                    settings.save(result.url, "opencode", result.token)
                    urlInput.setText(result.url)
                    usernameInput.setText("opencode")
                    passwordInput.setText(result.token)
                    setupPanel.isVisible = false
                    offlinePanel.isVisible = false
                    loadServer()
                    if (settings.notificationsEnabled && settings.backgroundMonitoring) {
                        MonitorService.start(this@MainActivity)
                    }
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.pairing_success, result.url),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                PairingResult.Expired -> {
                    val message = getString(R.string.pairing_expired)
                    discoveryStatus.text = message
                    showPairingError(message)
                }
                PairingResult.Unsupported -> {
                    val message = getString(R.string.pairing_unsupported)
                    discoveryStatus.text = message
                    showPairingError(message)
                }
                PairingResult.Unreachable -> {
                    val loopbackOnly = payload.urls.isNotEmpty() && payload.urls.all { Pairing.isLoopback(it) }
                    val message = if (loopbackOnly) {
                        getString(R.string.pairing_loopback)
                    } else {
                        getString(R.string.pairing_unreachable)
                    }
                    discoveryStatus.text = message
                    showPairingError(message)
                }
            }
        }
    }

    private fun showPairingError(message: String) {
        if (isFinishing) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pairing_failed_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun setDiscoveryBusy(busy: Boolean) {
        findViewById<MaterialButton>(R.id.discoverButton).isEnabled = !busy
        findViewById<MaterialButton>(R.id.scanButton).isEnabled = !busy
        findViewById<MaterialButton>(R.id.connectButton).isEnabled = !busy
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
            startPairing(it)
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
        settings.save(normalized, usernameInput.text?.toString().orEmpty(), passwordInput.text?.toString().orEmpty())
        setupPanel.isVisible = false
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

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        webView.destroy()
        super.onDestroy()
    }
}
