package amd.anik.ocdroid

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTest {

    @Test
    fun parsesRemotePairingLink() {
        val payload = Pairing.parse(
            "https://abc123.opentunnel.xyz/auth/connect/CODE_abc-123",
        )
        assertNotNull(payload)
        assertEquals("CODE_abc-123", payload!!.code)
        assertEquals(listOf("https://abc123.opentunnel.xyz"), payload!!.urls)
    }

    @Test
    fun parsesQrPayloadWithSeveralAddresses() {
        val payload = Pairing.parse(
            """{"code":"abc_123","urls":["http://192.168.1.5:4096","https://abc123.opentunnel.xyz"]}""",
        )
        assertNotNull(payload)
        assertEquals("abc_123", payload!!.code)
        assertEquals(
            listOf("http://192.168.1.5:4096", "https://abc123.opentunnel.xyz"),
            payload!!.urls,
        )
    }

    @Test
    fun removesDuplicateAddresses() {
        val payload = Pairing.parse(
            """{"code":"abc","urls":["http://192.168.1.5:4096/","http://192.168.1.5:4096"]}""",
        )
        assertEquals(listOf("http://192.168.1.5:4096"), payload!!.urls)
    }

    @Test
    fun rejectsMalformedInput() {
        assertNull(Pairing.parse("not a link"))
        assertNull(Pairing.parse("ftp://example.com/auth/connect/abc"))
        assertNull(Pairing.parse("https://example.com/auth/connect/"))
        assertNull(Pairing.parse("https://example.com/auth/connect/bad/code"))
        assertNull(Pairing.parse("""{"code":"abc"}"""))
        assertNull(Pairing.parse("""{"code":"","urls":["https://example.com"]}"""))
        assertNull(Pairing.parse("""{"code":"abc","urls":[]}"""))
    }

    @Test
    fun leavesOrdinaryServerUrlsAlone() {
        assertNull(Pairing.parse("https://example.com"))
        assertNull(Pairing.parse("http://192.168.1.5:4096"))
    }

    @Test
    fun prefersAnAddressThePhoneCanReach() {
        assertEquals(
            "https://remote.example",
            Pairing.preferredServer(
                PairingPayload(
                    listOf("http://127.0.0.1:4096", "https://remote.example"),
                    "code",
                ),
            ),
        )
        assertEquals(
            "https://remote.example",
            Pairing.preferredServer(
                PairingPayload(
                    listOf("http://192.168.1.5:4096", "https://remote.example"),
                    "code",
                ),
            ),
        )
        assertNull(
            Pairing.preferredServer(
                PairingPayload(listOf("http://127.0.0.1:4096", "http://localhost:4096"), "code"),
            ),
        )
    }

    @Test
    fun identifiesLoopbackAddresses() {
        assertTrue(Pairing.isLoopback("http://127.0.0.1:4096"))
        assertTrue(Pairing.isLoopback("http://localhost:4096/auth/connect/abc"))
        assertTrue(Pairing.isLoopback("http://[::1]:4096"))
        assertFalse(Pairing.isLoopback("http://192.168.1.5:4096"))
        assertFalse(Pairing.isLoopback("https://abc.opentunnel.xyz"))
    }

    @Test
    fun redeemsTokenWithoutASecondHealthCheck() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"token":"pair-token"}"""),
        )
        server.start()
        try {
            val base = server.url("/").toString().trimEnd('/')
            val result = Pairing.redeem(PairingPayload(listOf(base), "abc_123"))
            assertTrue(result is PairingResult.Paired)
            assertEquals("pair-token", (result as PairingResult.Paired).token)
            assertEquals("/auth/connect/abc_123", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun reportsExpiredPairingCode() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401))
        server.start()
        try {
            val base = server.url("/").toString().trimEnd('/')
            assertEquals(
                PairingResult.Expired,
                Pairing.redeem(PairingPayload(listOf(base), "spent")),
            )
        } finally {
            server.shutdown()
        }
    }
}
