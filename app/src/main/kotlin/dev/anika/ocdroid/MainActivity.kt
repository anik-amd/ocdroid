package dev.anika.ocdroid

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import com.google.android.material.textfield.TextInputEditText
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

    private var loadedUrl: String? = null
    private var versionChecked = false
    private var fileChooserCallback: android.webkit.ValueCallback<Array<Uri>>? = null

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

        configureWebView()

        findViewById<MaterialButton>(R.id.connectButton).setOnClickListener { saveAndConnect() }
        findViewById<MaterialButton>(R.id.retryButton).setOnClickListener {
            offlinePanel.isVisible = false
            loadServer()
        }

        urlInput.setText(settings.serverUrl)
        usernameInput.setText(settings.username)
        passwordInput.setText(settings.password)

        if (settings.isConfigured) {
            setupPanel.isVisible = false
            loadServer()
        } else {
            showSetup()
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

    private fun showSetup() {
        setupPanel.isVisible = true
        offlinePanel.isVisible = false
        progress.isVisible = false
    }

    private fun saveAndConnect() {
        val normalized = normalizeServerUrl(urlInput.text?.toString().orEmpty())
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
