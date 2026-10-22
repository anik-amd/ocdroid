package amd.anik.ocdroid

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DiscoveredServer(
    val url: String,
    val source: Source,
    val version: String? = null,
    val requiresPassword: Boolean = false,
) {
    enum class Source { MDNS, LOOPBACK, LAN }
}

/**
 * Finds OpenCode servers the user can reach without typing an address.
 *
 * Two mechanisms are used:
 *  - mDNS/DNS-SD, which works when the computer runs `opencode web --mdns`.
 *  - A short /24 scan of the current Wi-Fi on the default OpenCode port 4096.
 *
 * Every candidate is verified against /api/info (v2) or /global/health (v1) before it is
 * shown, so random web servers and routers on the network are ignored.
 */
class ServerDiscovery(
    private val context: Context,
    private val username: String,
    private val password: String,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(1200, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .callTimeout(2500, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun discover(onProgress: (String) -> Unit = {}): List<DiscoveredServer> = coroutineScope {
        onProgress("Searching your Wi-Fi for OpenCode…")

        val mdns = async { runCatching { discoverMdns() }.getOrDefault(emptyList()) }
        val direct = async {
            probeAll(
                listOf(
                    "http://127.0.0.1:4096",
                    "http://localhost:4096",
                    "http://opencode.local:4096",
                ),
                DiscoveredServer.Source.LOOPBACK,
            )
        }
        val lan = async { runCatching { discoverLan(onProgress) }.getOrDefault(emptyList()) }

        (mdns.await() + direct.await() + lan.await())
            .distinctBy { it.url }
            .sortedBy { it.source.ordinal }
    }

    @Suppress("DEPRECATION")
    private suspend fun discoverMdns(): List<DiscoveredServer> = coroutineScope {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return@coroutineScope emptyList()

        val resolved = ConcurrentHashMap<String, NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName.orEmpty()
                if (!name.contains("opencode", ignoreCase = true)) return
                runCatching {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            if (info.port > 0) resolved[info.serviceName] = info
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                resolved.remove(serviceInfo.serviceName)
            }
        }

        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = runCatching {
            wifi?.createMulticastLock("ocdroid-mdns")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()

        try {
            runCatching { nsd.discoverServices("_http._tcp", NsdManager.PROTOCOL_DNS_SD, listener) }
            delay(4_500)
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
            runCatching { lock?.takeIf { it.isHeld }?.release() }
        }

        val urls = resolved.values.mapNotNull { info ->
            val address = info.host?.hostAddress?.substringBefore('%') ?: return@mapNotNull null
            val host = if (address.contains(':')) "[$address]" else address
            "http://$host:${info.port}"
        }
        probeAll(urls, DiscoveredServer.Source.MDNS)
    }

    private suspend fun discoverLan(onProgress: (String) -> Unit): List<DiscoveredServer> = coroutineScope {
        val hosts = localSubnets().flatMap { it }.distinct()
        if (hosts.isEmpty()) return@coroutineScope emptyList()

        onProgress("Checking ${hosts.size} addresses on your network…")
        val gate = Semaphore(48)
        val found = ConcurrentHashMap<String, DiscoveredServer>()

        hosts.map { host ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    if (!isActive || !portOpen(host)) return@withPermit
                    probe("http://$host:4096", DiscoveredServer.Source.LAN)?.let { found[it.url] = it }
                }
            }
        }.awaitAll()

        found.values.toList()
    }

    private suspend fun probeAll(
        urls: List<String>,
        source: DiscoveredServer.Source,
    ): List<DiscoveredServer> = coroutineScope {
        urls.distinct()
            .map { url -> async(Dispatchers.IO) { probe(url, source) } }
            .awaitAll()
            .filterNotNull()
    }

    private fun probe(base: String, source: DiscoveredServer.Source): DiscoveredServer? {
        val info = get("$base/api/info")
        if (info != null) {
            if (info.code in 200..299 && looksLikeServerInfo(info.body)) {
                return DiscoveredServer(base, source, version = parseVersion(info.body))
            }
            if (isOpenCodeAuth(info)) {
                return DiscoveredServer(base, source, requiresPassword = true)
            }
        }

        val health = get("$base/global/health")
        if (health != null) {
            if (health.code in 200..299 && health.body.contains("\"healthy\"") && health.body.contains("\"version\"")) {
                return DiscoveredServer(base, source, version = parseVersion(health.body))
            }
            if (isOpenCodeAuth(health)) {
                return DiscoveredServer(base, source, requiresPassword = true)
            }
        }

        val root = get("$base/")
        if (root != null && root.code in 200..299 &&
            root.body.contains("_assets/index-") && root.body.contains("opencode", ignoreCase = true)
        ) {
            return DiscoveredServer(base, source)
        }
        return null
    }

    private fun get(url: String): HttpResult? = runCatching {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/html;q=0.8")
        if (password.isNotEmpty()) {
            builder.header("Authorization", Credentials.basic(username.ifBlank { "opencode" }, password))
        }
        client.newCall(builder.build()).execute().use { response ->
            HttpResult(
                code = response.code,
                body = response.peekBody(64 * 1024).string(),
                challenge = response.header("WWW-Authenticate"),
            )
        }
    }.getOrNull()

    private fun looksLikeServerInfo(body: String): Boolean =
        body.contains("\"version\"") && (body.contains("\"urls\"") || body.contains("\"paths\""))

    private fun isOpenCodeAuth(result: HttpResult): Boolean {
        if (result.code != 401) return false
        return result.body.contains("UnauthorizedError") ||
            result.body.contains("Authentication required") ||
            result.challenge?.contains("Secure Area", ignoreCase = true) == true
    }

    private fun parseVersion(body: String): String? = runCatching {
        JSONObject(body).optString("version").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun portOpen(host: String): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, 4096), 300) }
        true
    }.getOrDefault(false)

    /**
     * Returns /24 host addresses for every site-local IPv4 interface. Scans are deliberately
     * capped at 254 addresses so discovery finishes in a few seconds even on large subnets.
     */
    private fun localSubnets(): List<List<String>> {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val links = manager.allNetworks.flatMap { network ->
            runCatching { manager.getLinkProperties(network)?.linkAddresses.orEmpty() }
                .getOrDefault(emptyList())
        }
        return links.mapNotNull { link ->
            val ip = link.address as? Inet4Address ?: return@mapNotNull null
            if (ip.isLoopbackAddress || !ip.isSiteLocalAddress) return@mapNotNull null
            val bytes = ip.address
            val octets = bytes.map { it.toInt() and 0xff }
            val prefix = "${octets[0]}.${octets[1]}.${octets[2]}"
            (1..254)
                .filter { it != octets[3] }
                .map { "$prefix.$it" }
        }.distinctBy { it.firstOrNull()?.substringBeforeLast('.') }
    }

    private data class HttpResult(
        val code: Int,
        val body: String,
        val challenge: String?,
    )
}
